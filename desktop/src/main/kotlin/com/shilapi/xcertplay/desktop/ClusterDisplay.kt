package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay.Content
import java.awt.Dimension

/**
 * Experimental instrument-cluster display (CarPlay stream type 111), advertised next to the main
 * screen when DesktopSettings.clusterDisplay is on. The iPhone draws Apple Maps and its turn card
 * into it; OpenPlay shows the stream in a second, touch-less window. Size, frame rate and the
 * content shown at start come from the Cluster tab ([SettingsSchema.CLUSTER_WIDTH] and friends).
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
    fun config(values: SettingsValues): AirPlayDisplayConfig = CarPlayClusterDisplay.config(
        widthPixels = values[SettingsSchema.CLUSTER_WIDTH],
        heightPixels = values[SettingsSchema.CLUSTER_HEIGHT],
        scalePercent = NO_SCALING_PERCENT,
        content = content(values[SettingsSchema.CLUSTER_CONTENT]),
        baseSafeArea = CarPlayClusterDisplay.VIRTUAL_SAFE_AREA_PERCENT,
    ).copy(fps = values[SettingsSchema.CLUSTER_FPS])

    /** The cluster window at the stream's size, so frames are shown pixel for pixel. */
    fun windowSize(values: SettingsValues): Dimension = config(values).let { Dimension(it.widthPixels, it.heightPixels) }

    private const val NO_SCALING_PERCENT = 100
    private const val CONTENT_MAP = "map"
    private const val CONTENT_TURN_CARD = "turncard"
}
