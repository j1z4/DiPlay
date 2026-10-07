package com.shilapi.xcertplay.desktop

import java.io.IOException
import java.util.concurrent.TimeUnit

/** The Wi-Fi connection Windows reports for one wireless interface. */
data class WindowsWlanInfo(
    val interfaceName: String,
    val description: String,
    val ssid: String,
    val bssid: ByteArray,
    val channel: Int,
    val band: String,
    val authentication: String,
) {
    override fun equals(other: Any?): Boolean =
        other is WindowsWlanInfo && interfaceName == other.interfaceName && ssid == other.ssid &&
            bssid.contentEquals(other.bssid) && channel == other.channel && authentication == other.authentication

    override fun hashCode(): Int = listOf(interfaceName, ssid, bssid.contentHashCode(), channel, authentication).hashCode()

    val isOpen: Boolean get() = authentication.equals("Open", ignoreCase = true)

    companion object {
        private const val NETSH_TIMEOUT_SECONDS = 10L
        private val FIELD = Regex("""^\s+([A-Za-z][A-Za-z ]*?)\s+:\s(.*)$""")

        /** Reads the first connected Wi-Fi interface, or null when none is connected. */
        fun current(): WindowsWlanInfo? {
            val process = ProcessBuilder("netsh", "wlan", "show", "interfaces").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(NETSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("netsh wlan show interfaces timed out")
            }
            return parse(output).firstOrNull()
        }

        /** Parses `netsh wlan show interfaces` (English) into connected interfaces only. */
        fun parse(output: String): List<WindowsWlanInfo> =
            output.split(Regex("""\r?\n\s*\r?\n"""))
                .map { block -> fields(block) }
                .filter { it["State"].equals("connected", ignoreCase = true) && !it["SSID"].isNullOrBlank() }
                .mapNotNull { f ->
                    val bssid = f["AP BSSID"]?.let(::parseMac) ?: return@mapNotNull null
                    val channel = f["Channel"]?.trim()?.toIntOrNull() ?: return@mapNotNull null
                    WindowsWlanInfo(
                        interfaceName = f["Name"].orEmpty(),
                        description = f["Description"].orEmpty(),
                        ssid = f.getValue("SSID"),
                        bssid = bssid,
                        channel = channel,
                        band = f["Band"].orEmpty(),
                        authentication = f["Authentication"].orEmpty().replace(Regex("""\s+\(.*\)$"""), "").trim(),
                    )
                }

        private fun fields(block: String): Map<String, String> =
            block.lines().mapNotNull { line ->
                FIELD.matchEntire(line.trimEnd())?.let { it.groupValues[1].trim() to it.groupValues[2].trim() }
            }.toMap()

        private fun parseMac(text: String): ByteArray? {
            val parts = text.trim().split(':', '-')
            if (parts.size != 6 || parts.any { it.length != 2 }) return null
            return ByteArray(6) { parts[it].toInt(16).toByte() }
        }
    }
}
