package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay

/**
 * Experimental instrument-cluster display (CarPlay stream type 111), advertised next to the main
 * screen when DesktopSettings.clusterDisplay is on. The iPhone draws Apple Maps and its turn card
 * into it; OpenPlay shows the stream in a second, touch-less window.
 */
object ClusterDisplay {
    /** A wide 8:3 panel, as in-car clusters are; the window shows the stream at this size. */
    const val WIDTH = 1280
    const val HEIGHT = 480
    const val WINDOW_TITLE = "$APP_NAME Cluster"

    /** Map plus the iPhone's own turn card: the richest of the cluster contents the iPhone offers. */
    val CONTENT = CarPlayClusterDisplay.Content.INSTRUMENTS

    /**
     * No stream scaling (the window is the panel) and the balanced virtual safe area: nothing
     * overlays the window, so the car marker only has to stay off the edges.
     */
    fun config(): AirPlayDisplayConfig = CarPlayClusterDisplay.config(
        widthPixels = WIDTH,
        heightPixels = HEIGHT,
        scalePercent = NO_SCALING_PERCENT,
        content = CONTENT,
        baseSafeArea = CarPlayClusterDisplay.VIRTUAL_SAFE_AREA_PERCENT,
    )

    private const val NO_SCALING_PERCENT = 100
}
