package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.AirPlayListenerIdentity
import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2WirelessCarPlayEndpoint
import com.shilapi.xcertplay.transport.Iap2WirelessControlClient
import com.shilapi.xcertplay.transport.Iap2WirelessIdentification
import com.shilapi.xcertplay.transport.Iap2WirelessLinkRole
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import com.shilapi.xcertplay.transport.forWirelessLink
import java.io.Closeable
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** What the receiver reports to its host (console or UI). */
interface ReceiverEvents {
    fun onStatus(message: String) = Unit
    fun onSessionActive(session: AirPlaySession) = Unit
    fun onSessionEnded() = Unit
}

/**
 * Wireless CarPlay on Windows over an existing Wi-Fi network, mirroring CarPlayController's
 * runWireless: Bluetooth iAP2 bootstrap hands the iPhone the network and AirPlay endpoint, the
 * iPhone joins over Wi-Fi, and iAP2 continues inside the AirPlay session's tunnel.
 */
class WirelessReceiver(
    private val settings: DesktopSettings,
    private val store: DesktopStore,
    private val media: AirPlayMediaHandler,
    private val events: ReceiverEvents,
    private val log: (String) -> Unit,
) : Closeable {
    private val tunnelExecutor = Executors.newSingleThreadExecutor { Thread(it, "iap2-tunnel").apply { isDaemon = true } }
    private val handoffRequested = AtomicBoolean(false)
    private val tunnelReady = AtomicBoolean(false)
    private val closables = mutableListOf<Closeable>()
    @Volatile private var rfcomm: WindowsRfcommSocket? = null
    @Volatile private var bootstrapSession: Iap2Session? = null

    /** Runs the Bluetooth bootstrap; the AirPlay session continues on its own threads afterwards. */
    fun run() {
        Diagnostics.applyLogLevel(settings.advanced)
        val wlan = WindowsWlanInfo.current() ?: throw IOException("Connect this PC to Wi-Fi first")
        val host = wifiHostAddress(wlan)
        status("Wi-Fi ${wlan.ssid} channel ${wlan.channel}, receiver at ${host.hostAddress}")

        val identity = store.loadIdentity()
        val mfi = LocalMfiAuthenticationClient.load(settings.mfiDirectory)

        val socket = connectBluetooth().also { rfcomm = it }
        closables += socket
        val bluetoothMac = socket.localAddress

        val config = airPlayConfig(settings, DesktopStore.deviceId(identity), bluetoothMac)
        config.cluster?.let {
            log("cluster: advertising display ${it.widthPixels}x${it.heightPixels} " +
                "initialURL=${it.initialUrl}; altScreen offered in SETUP")
        }
        val server = DesktopAirPlayServer(
            bindAddress = host, config = config, identity = identity, pairings = store.pairingStore(), mfi = mfi,
            listener = sessionListener, media = media, listenerIdentity = AirPlayListenerIdentity(1), log = log,
        )
        closables += server
        val port = server.start()
        DesktopBonjour(host, server.boundConfig, identity, log).also { closables += it }.start()

        val wireless = Iap2WirelessIdentification(bluetoothMac, wlan.ssid)
        val base = identification(config.deviceId, settings.advanced)
        val endpoint = endpoint(wlan, host, port, config, identity.publicKeyHex)
        val mfiClient = Iap2MfiAuthenticationClient(mfi)
        // One car for the whole run: its battery keeps draining across tunnel reconnects.
        val vehicle = if (settings.vehicleData) SimulatedVehicle.configured(settings.advanced, log) else null
        media.setIapTunnelHandler { stream ->
            val identification = linkIdentification(base, Iap2WirelessLinkRole.RUNTIME_TUNNEL, wireless, settings.vehicleData)
            startTunnel(stream, identification, endpoint, mfiClient, vehicle)
        }

        val session = Iap2Session.openWireless(socket.duplexStream(), traceContext = "wireless-rfcomm")
        bootstrapSession = session
        status("Authenticating with iPhone")
        val result = Iap2WirelessControlClient(session, mfiClient).run(
            identification = linkIdentification(base, Iap2WirelessLinkRole.BLUETOOTH_BOOTSTRAP, wireless, settings.vehicleData),
            endpoint = endpoint,
            timeoutMillis = settings.advanced[SettingsSchema.BOOTSTRAP_TIMEOUT_SECONDS] * MILLIS_PER_SECOND,
            onStartSessionSent = { status("Waiting for iPhone to join over Wi-Fi") },
            onProgress = log,
        )
        log("bluetooth bootstrap ended terminal=${result.terminal} stage=${result.stage}")
    }

    override fun close() {
        tunnelExecutor.shutdownNow()
        runCatching { bootstrapSession?.close() }
        closables.asReversed().forEach { runCatching { it.close() } }
        closables.clear()
    }

    private val sessionListener = object : AirPlaySessionListener {
        override fun onSessionActive(session: AirPlaySession) {
            status("CarPlay session active")
            applyNightMode(session)
            events.onSessionActive(session)
        }

        override fun onSessionEnded(session: AirPlaySession) {
            status("CarPlay session ended")
            events.onSessionEnded()
        }

        override fun onCommand(session: AirPlaySession, type: String, params: Map<String, Any?>) {
            if (type == "modesChanged") log("modes: $params")
            if (type.equals("disableBluetooth", true) || type.equals("disable-bluetooth", true)) {
                handoffRequested.set(true)
                completeHandoffIfReady()
            }
        }

        override fun onTransportError(message: String) = status("Connection error: $message")

        override fun onDebugLog(message: String) = log(message)
    }

    /** The Appearance setting, sent once the event channel is up; "system" follows the Windows app theme. */
    private fun applyNightMode(session: AirPlaySession) {
        val appearance = settings.advanced[SettingsSchema.NIGHT_MODE]
        val night = nightMode(appearance, ::windowsUsesDarkTheme)
        log("appearance: $appearance -> ${if (night) "night" else "day"}")
        session.setNightMode(night)
    }

    /** An idle or locked iPhone may miss the first page; retry a few times as Android DiPlay does. */
    private fun connectBluetooth(): WindowsRfcommSocket {
        val attempts = settings.advanced[SettingsSchema.BLUETOOTH_ATTEMPTS]
        val retryMillis = settings.advanced[SettingsSchema.BLUETOOTH_RETRY_SECONDS] * MILLIS_PER_SECOND
        var lastError: IOException? = null
        for (attempt in 1..attempts) {
            status("Connecting to iPhone over Bluetooth (attempt $attempt of $attempts)")
            try {
                return WindowsRfcommSocket.connect(settings.iphoneAddress, IAP2_IPHONE_UUID)
            } catch (error: IOException) {
                lastError = error
                log("bluetooth connect attempt $attempt failed: ${error.message}")
                if (attempt < attempts) Thread.sleep(retryMillis)
            }
        }
        throw IOException("iPhone did not answer over Bluetooth; unlock it and keep Bluetooth on", lastError)
    }

    private fun startTunnel(
        stream: BlockingDuplexByteStream,
        identification: Iap2IdentificationConfig,
        endpoint: Iap2WirelessCarPlayEndpoint,
        mfiClient: Iap2MfiAuthenticationClient,
        vehicle: SimulatedVehicle?,
    ): Boolean {
        tunnelExecutor.execute {
            runCatching {
                vehicle?.let { log("vehicle: declaring a simulated EV on the Wi-Fi tunnel; ${it.describe()}") }
                val tunnel = Iap2Session.openTunnel(stream, traceContext = "wireless-tunnel")
                Iap2WirelessControlClient(tunnel, mfiClient).run(
                    identification = identification,
                    endpoint = endpoint,
                    timeoutMillis = Iap2WirelessControlClient.NO_TIMEOUT_MILLIS,
                    locationProvider = vehicle,
                    vehicleStatusProvider = vehicle,
                    onReady = {
                        tunnelReady.set(true)
                        completeHandoffIfReady()
                    },
                    onProgress = { log("iAP tunnel $it") },
                )
            }.onFailure { log("iAP tunnel ended: ${it.message}") }
        }
        return true
    }

    /** Once the iPhone asks to drop Bluetooth and the Wi-Fi iAP2 tunnel is up, RFCOMM is released. */
    private fun completeHandoffIfReady() {
        if (!handoffRequested.get() || !tunnelReady.get()) return
        runCatching { bootstrapSession?.close() }
        runCatching { rfcomm?.close() }
        status("Wireless CarPlay active; Bluetooth released")
    }

    private fun endpoint(
        wlan: WindowsWlanInfo,
        host: InetAddress,
        port: Int,
        config: AirPlayConfig,
        publicKeyHex: String,
    ) = Iap2WirelessCarPlayEndpoint(
        ssid = wlan.ssid,
        passphrase = settings.wifiPassphrase,
        channel = wlan.channel,
        // As DiPlay's Existing Wi-Fi mode: the iPhone already knows the network; WPA2 covers mixed mode.
        security = if (settings.wifiPassphrase.isEmpty()) Iap2WirelessSecurity.NONE else Iap2WirelessSecurity.WPA_WPA2,
        ipAddresses = listOf(host.hostAddress),
        airPlayPort = port,
        deviceIdentifier = config.deviceId,
        publicKey = publicKeyHex,
        sourceVersion = config.sourceVersion,
        accessPointBssid = wlan.bssid,
    )

    private fun status(message: String) {
        log("status: $message")
        events.onStatus(message)
    }

    companion object {
        private const val SOURCE_VERSION = "950.7.1"
        private const val MILLIS_PER_SECOND = 1_000L

        /** The receiver as advertised to the iPhone; the cluster display only when enabled in settings. */
        internal fun airPlayConfig(settings: DesktopSettings, deviceId: String, bluetoothMac: String): AirPlayConfig {
            val advanced = settings.advanced
            return AirPlayConfig(
                deviceName = advanced[SettingsSchema.DEVICE_NAME],
                deviceId = deviceId,
                btMac = bluetoothMac,
                sourceVersion = SOURCE_VERSION,
                main = mainDisplay(settings),
                // Experimental second display: SETUP then enables altScreen and the iPhone streams type 111.
                cluster = if (settings.clusterDisplay) ClusterDisplay.config(advanced) else null,
                rightHandDrive = advanced[SettingsSchema.RIGHT_HAND_DRIVE],
                port = advanced[SettingsSchema.AIRPLAY_PORT],
                // With audio off /info offers no audio formats, so every sound stays on the iPhone.
                disableAudioOutput = !advanced[SettingsSchema.AUDIO_ENABLED],
                manufacturer = advanced[SettingsSchema.MANUFACTURER],
                model = advanced[SettingsSchema.MODEL],
                oemLabel = advanced[SettingsSchema.DEVICE_NAME],
                // Without microphone input formats the iPhone treats the receiver as having no car audio
                // and keeps every sound on the phone; with the microphone setting off, silence is sent.
                microphone = true,
            )
        }

        /** The main screen: stream size from the core settings, physical size and bezel insets from the granular ones. */
        internal fun mainDisplay(settings: DesktopSettings): AirPlayDisplayConfig = AirPlayDisplayConfig(
            widthPixels = settings.width and 1.inv(),
            heightPixels = settings.height and 1.inv(),
            widthPhysicalMm = settings.advanced[SettingsSchema.SCREEN_WIDTH_MM],
            heightPhysicalMm = settings.advanced[SettingsSchema.SCREEN_HEIGHT_MM],
            fps = settings.fps,
            safeArea = safeArea(settings.advanced),
        )

        /** Bezel insets in stream pixels, or null (the whole screen is usable) while every inset is 0. */
        internal fun safeArea(advanced: SettingsValues): AirPlayInsets? = AirPlayInsets(
            top = advanced[SettingsSchema.SAFE_TOP],
            bottom = advanced[SettingsSchema.SAFE_BOTTOM],
            left = advanced[SettingsSchema.SAFE_LEFT],
            right = advanced[SettingsSchema.SAFE_RIGHT],
        ).takeIf { it != AirPlayInsets() }

        /** Night for the Appearance choice "night", day for "day"; "system" asks [windowsDark]. */
        internal fun nightMode(appearance: String, windowsDark: () -> Boolean): Boolean = when (appearance) {
            "night" -> true
            "day" -> false
            else -> windowsDark()
        }

        /** The IPv4 address of the Wi-Fi adapter netsh reported (Java names it by its description). */
        fun wifiHostAddress(wlan: WindowsWlanInfo): InetAddress {
            val adapter = NetworkInterface.networkInterfaces().toList().firstOrNull {
                it.isUp && (it.displayName == wlan.description || it.displayName == wlan.interfaceName)
            } ?: throw IOException("Wi-Fi adapter '${wlan.description}' not found")
            return adapter.inetAddresses.toList().firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?: throw IOException("Wi-Fi adapter '${wlan.description}' has no IPv4 address")
        }

        /** The accessory identity sent on both iAP2 links; [deviceId] is the AirPlay device id. */
        internal fun identification(deviceId: String, advanced: SettingsValues = SettingsValues.DEFAULTS) = Iap2IdentificationConfig(
            name = advanced[SettingsSchema.DEVICE_NAME],
            modelIdentifier = advanced[SettingsSchema.MODEL],
            manufacturer = advanced[SettingsSchema.MANUFACTURER],
            serialNumber = "OPENPLAY-" + deviceId.replace(":", ""),
            firmwareVersion = "0.1.0",
            hardwareVersion = "1.0",
            carPlayUsbInterfaceNumber = 0,
        )

        /**
         * Identification for one wireless link. With [vehicleData] the simulated EV (vehicle status,
         * location, wheel speed) is declared, which [forWirelessLink] keeps off the Bluetooth
         * bootstrap; without it both links carry exactly the plain identity as before.
         */
        internal fun linkIdentification(
            base: Iap2IdentificationConfig,
            role: Iap2WirelessLinkRole,
            wireless: Iap2WirelessIdentification,
            vehicleData: Boolean,
        ): Iap2IdentificationConfig {
            val declared = if (vehicleData) {
                base.copy(locationInformationEnabled = true, vehicleStatusEnabled = true, vehicleSpeedEnabled = true)
            } else {
                base
            }
            return declared.forWirelessLink(role, wireless)
        }
    }
}
