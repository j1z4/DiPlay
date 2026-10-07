package com.shilapi.xcertplay.desktop

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Opens the dashboard; with autoStart and complete settings, CarPlay starts right away. */
fun main() {
    val log: (String) -> Unit = { println("${LocalTime.now()} $it") }
    val store = DesktopStore()
    val settings = store.loadSettings()
    SwingUtilities.invokeLater {
        if (windowsUsesDarkTheme()) FlatDarkLaf.setup() else FlatLightLaf.setup()
        SettingsDashboard(store, log).show(startNow = settings.autoStart && settings.problems().isEmpty())
    }
}

/** Reads the Windows app theme (AppsUseLightTheme = 0 means dark). */
private fun windowsUsesDarkTheme(): Boolean = runCatching {
    val process = ProcessBuilder(
        "reg", "query", """HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize""", "/v", "AppsUseLightTheme",
    ).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor(REG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    Regex("""AppsUseLightTheme\s+REG_DWORD\s+0x0+\b""").containsMatchIn(output)
}.getOrDefault(false)

private const val REG_TIMEOUT_SECONDS = 5L
