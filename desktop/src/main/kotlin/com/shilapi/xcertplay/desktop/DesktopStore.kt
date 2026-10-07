package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.PairingStore
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties

/** Product name shown in the window and advertised to the iPhone. */
const val APP_NAME = "OpenPlay"

/** User-editable receiver settings, read from settings.properties. */
data class DesktopSettings(
    val iphoneAddress: String,
    val wifiPassphrase: String,
    val mfiDirectory: File,
    val width: Int,
    val height: Int,
    val fps: Int,
    /** Kiosk mode for an in-car PC; windowed by default. */
    val fullscreen: Boolean = false,
    /** Start CarPlay as soon as OpenPlay opens (in-car use) instead of waiting on the dashboard. */
    val autoStart: Boolean = false,
    /** Experimental: advertise a second (instrument cluster) display to the iPhone. */
    val clusterDisplay: Boolean = false,
    /** Experimental: send simulated vehicle status (EV range/charge) and location over the tunnel. */
    val vehicleData: Boolean = false,
    /** Every granular setting declared in [SettingsSchema]. */
    val advanced: SettingsValues = SettingsValues.DEFAULTS,
) {
    /** What still has to be filled in before CarPlay can start; empty when ready. */
    fun problems(): List<String> = buildList {
        if (!BLUETOOTH_ADDRESS.matches(iphoneAddress)) add("Choose the paired iPhone")
        if (!File(mfiDirectory, "identity.pk8").isFile || !File(mfiDirectory, "certificate.p7b").isFile) {
            add("Choose the folder with identity.pk8 and certificate.p7b")
        }
        if (width < MIN_SIDE || height < MIN_SIDE) add("Resolution must be at least ${MIN_SIDE}x$MIN_SIDE")
    }

    private companion object {
        val BLUETOOTH_ADDRESS = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
        const val MIN_SIDE = 320
    }
}

/**
 * File-backed state for the Windows receiver under %APPDATA%\OpenPlay: the AirPlay identity the
 * iPhone pairs with, the paired controllers, and the settings file.
 */
class DesktopStore(val root: File = defaultRoot()) {
    private val hex = HexFormat.of()
    private val identityFile = File(root, "identity.properties")
    private val pairingsFile = File(root, "pairings.properties")
    val settingsFile = File(root, "settings.properties")

    init {
        root.mkdirs()
    }

    /** Loads the persisted identity, creating one on first run so pairings survive restarts. */
    @Synchronized
    fun loadIdentity(): AirPlayIdentity {
        val saved = read(identityFile)
        val privateKey = saved.getProperty("privateKey")
        val publicKey = saved.getProperty("publicKey")
        val pairingId = saved.getProperty("pairingId")
        if (privateKey != null && publicKey != null && pairingId != null) {
            return AirPlayIdentity(hex.parseHex(privateKey), hex.parseHex(publicKey), pairingId)
        }
        val created = AirPlayIdentity.generate()
        write(identityFile, Properties().apply {
            setProperty("privateKey", hex.formatHex(created.privateKey))
            setProperty("publicKey", hex.formatHex(created.publicKey))
            setProperty("pairingId", created.pairingId)
        })
        return created
    }

    /** A pairing store preloaded from disk that persists each new controller key. */
    fun pairingStore(): PairingStore {
        val store = PairingStore(onSave = ::savePairing)
        read(pairingsFile).forEach { (id, key) -> store.save(id.toString(), hex.parseHex(key.toString())) }
        return store
    }

    /** Reads settings.properties, filling defaults; check [DesktopSettings.problems] before starting. */
    fun loadSettings(): DesktopSettings {
        val values = read(settingsFile)
        fun text(key: String) = values.getProperty(key)?.trim().orEmpty()
        fun number(key: String, default: Int) = text(key).toIntOrNull() ?: default
        return DesktopSettings(
            iphoneAddress = text("iphoneAddress").uppercase(),
            wifiPassphrase = values.getProperty("wifiPassphrase").orEmpty(),
            mfiDirectory = File(text("mfiDirectory").ifEmpty { File(root, "offline-mfi").path }),
            width = number("width", DEFAULT_WIDTH),
            height = number("height", DEFAULT_HEIGHT),
            fps = number("fps", DEFAULT_FPS),
            fullscreen = text("fullscreen").toBoolean(),
            autoStart = text("autoStart").toBoolean(),
            clusterDisplay = text("clusterDisplay").toBoolean(),
            vehicleData = text("vehicleData").toBoolean(),
            advanced = SettingsValues(
                SettingsSchema.ALL.mapNotNull { setting -> values.getProperty(setting.key)?.let { setting.key to it } }.toMap(),
            ),
        )
    }

    /** Persists the dashboard's settings. */
    @Synchronized
    fun saveSettings(settings: DesktopSettings) {
        write(settingsFile, Properties().apply {
            setProperty("iphoneAddress", settings.iphoneAddress)
            setProperty("wifiPassphrase", settings.wifiPassphrase)
            setProperty("mfiDirectory", settings.mfiDirectory.path)
            setProperty("width", settings.width.toString())
            setProperty("height", settings.height.toString())
            setProperty("fps", settings.fps.toString())
            setProperty("fullscreen", settings.fullscreen.toString())
            setProperty("autoStart", settings.autoStart.toString())
            setProperty("clusterDisplay", settings.clusterDisplay.toString())
            setProperty("vehicleData", settings.vehicleData.toString())
            settings.advanced.entries().forEach { (key, value) -> setProperty(key, value) }
        })
    }

    @Synchronized
    private fun savePairing(id: String, key: ByteArray) {
        val pairings = read(pairingsFile)
        if (pairings.getProperty(id) == hex.formatHex(key)) return
        pairings.setProperty(id, hex.formatHex(key))
        write(pairingsFile, pairings)
    }

    private fun read(file: File): Properties = Properties().apply {
        if (file.isFile) file.reader(Charsets.UTF_8).use(::load)
    }

    private fun write(file: File, properties: Properties) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writer(Charsets.UTF_8).use { properties.store(it, APP_NAME) }
        check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) {
            "Could not save ${file.absolutePath}"
        }
    }

    companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FPS = 60

        /** %APPDATA%\OpenPlay; a folder left by the earlier DiPlay-named build is moved over once. */
        fun defaultRoot(): File {
            val base = File(System.getenv("APPDATA") ?: System.getProperty("user.home"))
            val root = File(base, APP_NAME)
            val legacy = File(base, LEGACY_FOLDER)
            if (!root.exists() && legacy.isDirectory) legacy.renameTo(root)
            return root
        }

        private const val LEGACY_FOLDER = "DiPlay"

        /** DiPlay's AirPlay device id: a locally administered MAC derived from the public key. */
        fun deviceId(identity: AirPlayIdentity): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.publicKey)
            val bytes = digest.copyOf(6)
            bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
            return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
        }
    }
}
