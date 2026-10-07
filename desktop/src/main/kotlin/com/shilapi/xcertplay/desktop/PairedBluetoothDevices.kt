package com.shilapi.xcertplay.desktop

import java.util.concurrent.TimeUnit

/** One Bluetooth device Windows has paired, as shown in the dashboard's iPhone picker. */
data class PairedDevice(val address: String, val name: String) {
    override fun toString(): String = "$name  ($address)"
}

/** Reads Windows' paired Bluetooth devices from the BTHPORT registry key (no admin needed). */
object PairedBluetoothDevices {
    private const val DEVICES_KEY = """HKLM\SYSTEM\CurrentControlSet\Services\BTHPORT\Parameters\Devices"""
    private const val REG_TIMEOUT_SECONDS = 10L
    private val KEY_LINE = Regex("""^HKEY_LOCAL_MACHINE\\.*\\Devices\\([0-9A-Fa-f]{12})\s*$""")
    private val NAME_LINE = Regex("""^\s+Name\s+REG_BINARY\s+([0-9A-Fa-f]*)\s*$""")

    fun list(): List<PairedDevice> = runCatching {
        val process = ProcessBuilder("reg", "query", DEVICES_KEY, "/s", "/v", "Name")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(REG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        parse(output)
    }.getOrDefault(emptyList())

    /** Parses `reg query ... /s /v Name` output; devices sort by name, then address. */
    fun parse(output: String): List<PairedDevice> {
        val devices = mutableListOf<PairedDevice>()
        var address: String? = null
        for (line in output.lines()) {
            KEY_LINE.matchEntire(line.trimEnd())?.let { address = formatAddress(it.groupValues[1]) }
            val name = NAME_LINE.matchEntire(line)?.groupValues?.get(1) ?: continue
            val current = address ?: continue
            devices += PairedDevice(current, decodeName(name).ifBlank { current })
            address = null
        }
        return devices.sortedWith(compareBy({ it.name.lowercase() }, { it.address }))
    }

    private fun formatAddress(hex: String): String = hex.uppercase().chunked(2).joinToString(":")

    private fun decodeName(hex: String): String {
        val bytes = hex.chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        return String(bytes, Charsets.UTF_8).substringBefore('\u0000').trim()
    }
}
