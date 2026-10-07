package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.PairingStore
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties

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
)

/**
 * File-backed state for the Windows receiver under %APPDATA%\DiPlay: the AirPlay identity the
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

    /** Reads settings.properties; missing required values produce a message naming the file. */
    fun loadSettings(): DesktopSettings {
        val values = read(settingsFile)
        fun required(key: String): String = values.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Set '$key' in ${settingsFile.absolutePath}")
        return DesktopSettings(
            iphoneAddress = required("iphoneAddress"),
            wifiPassphrase = values.getProperty("wifiPassphrase").orEmpty(),
            mfiDirectory = File(required("mfiDirectory")),
            width = values.getProperty("width")?.toIntOrNull() ?: DEFAULT_WIDTH,
            height = values.getProperty("height")?.toIntOrNull() ?: DEFAULT_HEIGHT,
            fps = values.getProperty("fps")?.toIntOrNull() ?: DEFAULT_FPS,
            fullscreen = values.getProperty("fullscreen")?.trim().toBoolean(),
        )
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
        temporary.writer(Charsets.UTF_8).use { properties.store(it, "DiPlay for Windows") }
        check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) {
            "Could not save ${file.absolutePath}"
        }
    }

    companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FPS = 60

        fun defaultRoot(): File =
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "DiPlay")

        /** DiPlay's AirPlay device id: a locally administered MAC derived from the public key. */
        fun deviceId(identity: AirPlayIdentity): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.publicKey)
            val bytes = digest.copyOf(6)
            bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
            return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
        }
    }
}
