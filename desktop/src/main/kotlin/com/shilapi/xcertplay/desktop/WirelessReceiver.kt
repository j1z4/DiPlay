package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
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
        val wlan = WindowsWlanInfo.current() ?: throw IOException("Connect this PC to Wi-Fi first")
        val host = wifiHostAddress(wlan)
        status("Wi-Fi ${wlan.ssid} channel ${wlan.channel}, receiver at ${host.hostAddress}")

        val identity = store.loadIdentity()
        val mfi = LocalMfiAuthenticationClient.load(settings.mfiDirectory)

        val socket = connectBluetooth().also { rfcomm = it }
        closables += socket
        val bluetoothMac = socket.localAddress

        val config = airPlayConfig(DesktopStore.deviceId(identity), bluetoothMac)
        val server = DesktopAirPlayServer(
            bindAddress = host, config = config, identity = identity, pairings = store.pairingStore(), mfi = mfi,
            listener = sessionListener, media = media, listenerIdentity = AirPlayListenerIdentity(1), log = log,
        )
        closables += server
        val port = server.start()
        DesktopBonjour(host, server.boundConfig, identity, log).also { closables += it }.start()

        val wireless = Iap2WirelessIdentification(bluetoothMac, wlan.ssid)
        val base = identification(config.deviceId)
        val endpoint = endpoint(wlan, host, port, config, identity.publicKeyHex)
        val mfiClient = Iap2MfiAuthenticationClient(mfi)
        media.setIapTunnelHandler { stream ->
            startTunnel(stream, base.forWirelessLink(Iap2WirelessLinkRole.RUNTIME_TUNNEL, wireless), endpoint, mfiClient)
        }

        val session = Iap2Session.openWireless(socket.duplexStream(), traceContext = "wireless-rfcomm")
        bootstrapSession = session
        status("Authenticating with iPhone")
        val result = Iap2WirelessControlClient(session, mfiClient).run(
            identification = base.forWirelessLink(Iap2WirelessLinkRole.BLUETOOTH_BOOTSTRAP, wireless),
            endpoint = endpoint,
            timeoutMillis = BOOTSTRAP_TIMEOUT_MILLIS,
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
            events.onSessionActive(session)
        }

        override fun onSessionEnded(session: AirPlaySession) {
            status("CarPlay session ended")
            events.onSessionEnded()
        }

        override fun onCommand(session: AirPlaySession, type: String, params: Map<String, Any?>) {
            if (type.equals("disableBluetooth", true) || type.equals("disable-bluetooth", true)) {
                handoffRequested.set(true)
                completeHandoffIfReady()
            }
        }

        override fun onTransportError(message: String) = status("Connection error: $message")

        override fun onDebugLog(message: String) = log(message)
    }

    /** An idle or locked iPhone may miss the first page; retry a few times as Android DiPlay does. */
    private fun connectBluetooth(): WindowsRfcommSocket {
        var lastError: IOException? = null
        for (attempt in 1..BLUETOOTH_ATTEMPTS) {
            status("Connecting to iPhone over Bluetooth (attempt $attempt of $BLUETOOTH_ATTEMPTS)")
            try {
                return WindowsRfcommSocket.connect(settings.iphoneAddress, IAP2_IPHONE_UUID)
            } catch (error: IOException) {
                lastError = error
                log("bluetooth connect attempt $attempt failed: ${error.message}")
                if (attempt < BLUETOOTH_ATTEMPTS) Thread.sleep(BLUETOOTH_RETRY_MILLIS)
            }
        }
        throw IOException("iPhone did not answer over Bluetooth; unlock it and keep Bluetooth on", lastError)
    }

    private fun startTunnel(
        stream: BlockingDuplexByteStream,
        identification: Iap2IdentificationConfig,
        endpoint: Iap2WirelessCarPlayEndpoint,
        mfiClient: Iap2MfiAuthenticationClient,
    ): Boolean {
        tunnelExecutor.execute {
            runCatching {
                val tunnel = Iap2Session.openTunnel(stream, traceContext = "wireless-tunnel")
                Iap2WirelessControlClient(tunnel, mfiClient).run(
                    identification = identification,
                    endpoint = endpoint,
                    timeoutMillis = Iap2WirelessControlClient.NO_TIMEOUT_MILLIS,
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

    private fun airPlayConfig(deviceId: String, bluetoothMac: String) = AirPlayConfig(
        deviceName = DEVICE_NAME,
        deviceId = deviceId,
        btMac = bluetoothMac,
        sourceVersion = SOURCE_VERSION,
        main = AirPlayDisplayConfig(
            widthPixels = settings.width and 1.inv(),
            heightPixels = settings.height and 1.inv(),
            fps = settings.fps,
        ),
        manufacturer = DEVICE_NAME,
        model = DEVICE_NAME,
        oemLabel = DEVICE_NAME,
    )

    private fun identification(deviceId: String) = Iap2IdentificationConfig(
        name = DEVICE_NAME,
        modelIdentifier = DEVICE_NAME,
        manufacturer = DEVICE_NAME,
        serialNumber = "DIPLAY-" + deviceId.replace(":", ""),
        firmwareVersion = "0.1.0",
        hardwareVersion = "1.0",
        carPlayUsbInterfaceNumber = 0,
    )

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
        private const val DEVICE_NAME = "DiPlay"
        private const val SOURCE_VERSION = "950.7.1"
        private const val BOOTSTRAP_TIMEOUT_MILLIS = 5 * 60_000L
        private const val BLUETOOTH_ATTEMPTS = 4
        private const val BLUETOOTH_RETRY_MILLIS = 3_000L

        /** The IPv4 address of the Wi-Fi adapter netsh reported (Java names it by its description). */
        fun wifiHostAddress(wlan: WindowsWlanInfo): InetAddress {
            val adapter = NetworkInterface.networkInterfaces().toList().firstOrNull {
                it.isUp && (it.displayName == wlan.description || it.displayName == wlan.interfaceName)
            } ?: throw IOException("Wi-Fi adapter '${wlan.description}' not found")
            return adapter.inetAddresses.toList().firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?: throw IOException("Wi-Fi adapter '${wlan.description}' has no IPv4 address")
        }
    }
}
