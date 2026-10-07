package com.shilapi.xcertplay.desktop

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * Opens the dashboard; with autoStart and complete settings, CarPlay starts right away.
 * `--snapshot <file.png>` renders the dashboard off-screen to a PNG and exits.
 */
fun main(args: Array<String>) {
    val log: (String) -> Unit = { println("${LocalTime.now()} $it") }
    val store = DesktopStore()
    val settings = store.loadSettings()
    val snapshot = args.indexOf("--snapshot").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    SwingUtilities.invokeLater {
        if (windowsUsesDarkTheme()) FlatDarkLaf.setup() else FlatLightLaf.setup()
        val dashboard = SettingsDashboard(store, log)
        if (snapshot != null) {
            dashboard.snapshot(java.io.File(snapshot))
            Runtime.getRuntime().halt(0)
        }
        dashboard.show(startNow = settings.autoStart && settings.problems().isEmpty())
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
