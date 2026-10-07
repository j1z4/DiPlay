package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay.Content
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.math.roundToInt

/**
 * Experimental instrument-cluster display (CarPlay stream type 111), advertised next to the main
 * screen when DesktopSettings.clusterDisplay is on. The iPhone only offers Apple Maps content for it
 * (map, turn card or both; its `altScreenURLs`), so the gauges style does what cars without CarPlay
 * Ultra do: OpenPlay draws the instruments and streams the map into a window between the dials
 * ([mapSlot]). The map-only style shows the stream on its own. Everything comes from the Cluster tab.
 */
object ClusterDisplay {
    const val WINDOW_TITLE = "$APP_NAME Cluster"

    /**
     * The cluster content for a [SettingsSchema.CLUSTER_CONTENT] value. Anything unknown shows map
     * and turn card together, the richest content the iPhone offers.
     */
    fun content(setting: String): Content = when (setting) {
        CONTENT_MAP -> Content.MAP
        CONTENT_TURN_CARD -> Content.TURN_CARD
        else -> Content.INSTRUMENTS
    }

    /**
     * The display as advertised to the iPhone: no stream scaling (the window is the panel) and the
     * balanced virtual safe area, since nothing overlays the window and the car marker only has to
     * stay off the edges. The shared config rounds the stream height to a multiple of 8 (width
     * follows, keeping the aspect), so the stream can differ from the requested size by a few pixels.
     */
    fun config(values: SettingsValues): AirPlayDisplayConfig = streamSize(values).let { size -> CarPlayClusterDisplay.config(
        widthPixels = size.width,
        heightPixels = size.height,
        scalePercent = NO_SCALING_PERCENT,
        content = content(values[SettingsSchema.CLUSTER_CONTENT]),
        baseSafeArea = CarPlayClusterDisplay.VIRTUAL_SAFE_AREA_PERCENT,
    ).copy(fps = values[SettingsSchema.CLUSTER_FPS]) }

    /** True when OpenPlay draws the gauges around the map rather than showing the map alone. */
    fun drawsGauges(values: SettingsValues): Boolean = values[SettingsSchema.CLUSTER_STYLE] != STYLE_MAP

    /**
     * The cluster window: the whole instrument panel in the gauges style, otherwise the stream's
     * size, so map frames are shown pixel for pixel.
     */
    fun windowSize(values: SettingsValues): Dimension =
        if (drawsGauges(values)) Dimension(values[SettingsSchema.CLUSTER_PANEL_WIDTH], values[SettingsSchema.CLUSTER_PANEL_HEIGHT])
        else config(values).let { Dimension(it.widthPixels, it.heightPixels) }

    /**
     * Where the iPhone map goes in a [panelWidth] x [panelHeight] instrument panel: [sharePercent]
     * of the width, centred between the dials, below the top bar. Both sides are multiples of 8, as
     * the stream is, so the map is drawn unscaled.
     */
    fun mapSlot(panelWidth: Int, panelHeight: Int, sharePercent: Int): Rectangle {
        val width = multipleOf8(panelWidth * sharePercent / 100.0)
        val height = multipleOf8(panelHeight * SLOT_HEIGHT_SHARE)
        return Rectangle((panelWidth - width) / 2, (panelHeight * SLOT_TOP_SHARE).roundToInt(), width, height)
    }

    private fun streamSize(values: SettingsValues): Dimension {
        if (!drawsGauges(values)) return Dimension(values[SettingsSchema.CLUSTER_WIDTH], values[SettingsSchema.CLUSTER_HEIGHT])
        val panel = windowSize(values)
        val slot = mapSlot(panel.width, panel.height, values[SettingsSchema.CLUSTER_MAP_SHARE])
        return Dimension(slot.width, slot.height)
    }

    private fun multipleOf8(value: Double): Int = ((value / 8).roundToInt() * 8).coerceAtLeast(8)

    private const val NO_SCALING_PERCENT = 100
    private const val STYLE_MAP = "map"
    private const val SLOT_HEIGHT_SHARE = 0.78
    private const val SLOT_TOP_SHARE = 0.13
    private const val CONTENT_MAP = "map"
    private const val CONTENT_TURN_CARD = "turncard"
}
