package com.shilapi.xcertplay.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Rectangle
import java.awt.RenderingHints

class VideoWindowGeometryTest {
    @Test fun keepingAspectLetterboxesAWideVideoInATallerPanel() {
        assertEquals(Rectangle(0, 120, 800, 360), VideoWindow.videoRect(800, 600, 1280, 576, keepAspect = true))
    }

    @Test fun keepingAspectPillarboxesATallVideoInAWiderPanel() {
        assertEquals(Rectangle(200, 0, 400, 600), VideoWindow.videoRect(800, 600, 400, 600, keepAspect = true))
    }

    @Test fun stretchingFillsTheWholePanel() {
        assertEquals(Rectangle(0, 0, 800, 600), VideoWindow.videoRect(800, 600, 1280, 576, keepAspect = false))
    }

    @Test fun scalingChoicesMapToJava2dInterpolation() {
        assertEquals(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR, VideoWindow.interpolationHint("nearest"))
        assertEquals(RenderingHints.VALUE_INTERPOLATION_BILINEAR, VideoWindow.interpolationHint("bilinear"))
        assertEquals(RenderingHints.VALUE_INTERPOLATION_BICUBIC, VideoWindow.interpolationHint("bicubic"))
        assertEquals(RenderingHints.VALUE_INTERPOLATION_BILINEAR, VideoWindow.interpolationHint("purple"))
    }
}
