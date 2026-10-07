package com.shilapi.xcertplay.desktop

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import java.io.File
import java.time.LocalTime
import javax.imageio.ImageIO
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * Opens the dashboard; with autoStart and complete settings, CarPlay starts right away.
 * `--snapshot <file.png>` renders the dashboard off-screen to a PNG and exits;
 * `--cluster-snapshot <file.png> [map.png]` renders the gauges-style cluster the same way.
 */
fun main(args: Array<String>) {
    val store = DesktopStore()
    val settings = store.loadSettings()
    val logFile = FileLogging.install(store.root, settings.advanced[SettingsSchema.LOG_FILES])
    Diagnostics.applyLogLevel(settings.advanced)
    val log: (String) -> Unit = { println("${LocalTime.now()} $it") }
    log("$APP_NAME starting; log ${logFile.absolutePath}")
    val snapshot = args.indexOf("--snapshot").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val clusterSnapshot = args.indexOf("--cluster-snapshot").takeIf { it >= 0 }
    if (clusterSnapshot != null) {
        renderClusterSnapshot(settings.advanced, args.getOrNull(clusterSnapshot + 1), args.getOrNull(clusterSnapshot + 2))
        return
    }
    SwingUtilities.invokeLater {
        if (windowsUsesDarkTheme()) FlatDarkLaf.setup() else FlatLightLaf.setup()
        val dashboard = SettingsDashboard(store, log)
        if (snapshot != null) {
            val tab = args.getOrNull(args.indexOf("--snapshot") + 2)?.toIntOrNull() ?: 0
            dashboard.snapshot(java.io.File(snapshot), tab)
            Runtime.getRuntime().halt(0)
        }
        dashboard.show(startNow = settings.autoStart && settings.problems().isEmpty())
    }
}

/** Renders the gauges-style cluster at its panel size, with [mapPath] (if given) as the iPhone map. */
private fun renderClusterSnapshot(values: SettingsValues, target: String?, mapPath: String?) {
    requireNotNull(target) { "--cluster-snapshot needs an output .png path" }
    val gauges = values.with(SettingsSchema.CLUSTER_STYLE, "gauges")
    val panel = ClusterDisplay.windowSize(gauges)
    val map = mapPath?.let { path -> requireNotNull(ImageIO.read(File(path))) { "Cannot read $path as an image" } }
    val readings = ClusterReadings.of(SimulatedVehicle.configured(gauges), gauges)
    val image = InstrumentPanelPainter(gauges).render(panel.width, panel.height, readings, map, "Waiting for the iPhone map")
    ImageIO.write(image, "png", File(target))
}

/** Reads the Windows app theme (AppsUseLightTheme = 0 means dark). */
internal fun windowsUsesDarkTheme(): Boolean = runCatching {
    val process = ProcessBuilder(
        "reg", "query", """HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize""", "/v", "AppsUseLightTheme",
    ).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor(REG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    Regex("""AppsUseLightTheme\s+REG_DWORD\s+0x0+\b""").containsMatchIn(output)
}.getOrDefault(false)

private const val REG_TIMEOUT_SECONDS = 5L
