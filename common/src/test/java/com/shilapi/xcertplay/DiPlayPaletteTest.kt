package com.shilapi.xcertplay

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DiPlayPaletteTest {
    @Test fun darkPaletteUsesOpenPlaysColors() {
        // design/ui-library/tokens.css: true black, graphite surfaces, one signal green.
        val palette = DiPlayPalette.DARK
        assertEquals(Color.rgb(0, 0, 0), palette.background)
        assertEquals(Color.rgb(20, 22, 27), palette.surface)
        assertEquals(Color.rgb(29, 32, 39), palette.button)
        assertEquals(Color.rgb(40, 44, 53), palette.outline)
        assertEquals(Color.rgb(245, 247, 250), palette.railSelected)
        assertEquals(Color.rgb(11, 13, 17), palette.onRailSelected)
        assertEquals(Color.rgb(245, 247, 250), palette.primaryText)
        assertEquals(Color.rgb(141, 149, 165), palette.secondaryText)
        assertEquals(Color.rgb(47, 228, 106), palette.accent)
        assertEquals(Color.rgb(255, 189, 92), palette.warning)
        assertEquals(Color.rgb(47, 228, 106), palette.success)
        assertEquals(Color.rgb(255, 90, 95), palette.danger)
        assertEquals(Color.rgb(245, 247, 250), palette.focusFill)
        assertEquals(Color.rgb(11, 13, 17), palette.onFocus)
        assertFalse(palette.systemBarIconsAreDark)
    }

    @Test fun waitingScreenDelegationKeepsItsExactLightAndDarkColors() {
        assertEquals(
            WaitingScreenColors(Color.rgb(0, 0, 0), Color.rgb(245, 247, 250), Color.rgb(141, 149, 165)),
            WaitingScreenColors.of(night = true),
        )
        assertEquals(
            WaitingScreenColors(Color.rgb(238, 241, 245), Color.rgb(17, 20, 24), Color.rgb(93, 102, 118)),
            WaitingScreenColors.of(night = false),
        )
    }

    @Test fun selectedAndFocusedRowsKeepTheirTextReadable() {
        for (palette in listOf(DiPlayPalette.DARK, DiPlayPalette.LIGHT)) {
            assertContrastAtLeast(palette.onRailSelected, palette.railSelected, 4.5)
            assertContrastAtLeast(palette.onFocus, palette.focusFill, 4.5)
            assertContrastAtLeast(palette.onPrimary, palette.primaryFill, 4.5)
        }
    }

    @Test fun semanticRolesMeetTheirWcagContrastTargets() {
        for (palette in listOf(DiPlayPalette.DARK, DiPlayPalette.LIGHT)) {
            assertContrastAtLeast(palette.primaryText, palette.background, 4.5)
            assertContrastAtLeast(palette.secondaryText, palette.background, 4.5)
            assertContrastAtLeast(palette.accent, palette.background, 4.5)
            assertContrastAtLeast(palette.onAccent, palette.accent, 4.5)
            assertContrastAtLeast(palette.warning, palette.background, 4.5)
            assertContrastAtLeast(palette.success, palette.background, 4.5)
            // The legacy dark danger role is used for graphical/status emphasis, not body text.
            assertContrastAtLeast(palette.danger, palette.background, 3.0)
            assertContrastAtLeast(palette.overlayPrimaryText, palette.overlayBackground, 4.5)
            assertContrastAtLeast(palette.overlaySecondaryText, palette.overlayBackground, 4.5)
            assertContrastAtLeast(palette.overlayAccent, palette.overlayBackground, 4.5)
            assertContrastAtLeast(palette.overlayOnAccent, palette.overlayAccent, 4.5)
            assertContrastAtLeast(palette.overlayDanger, palette.overlayBackground, 3.0)
        }
        assertContrastAtLeast(DiPlayPalette.LIGHT.accentTrack, DiPlayPalette.LIGHT.trackOff, 3.0)
        assertContrastAtLeast(
            DiPlayPalette.LIGHT.overlayAccentTrack,
            DiPlayPalette.LIGHT.overlayTrackOff,
            3.0,
        )
        assertTrue(DiPlayPalette.LIGHT.systemBarIconsAreDark)
    }

    private fun assertContrastAtLeast(foreground: Int, background: Int, minimum: Double) {
        val foregroundLuminance = luminance(foreground)
        val backgroundLuminance = luminance(background)
        val ratio = (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
        assertTrue("Expected contrast >= $minimum, was $ratio", ratio >= minimum)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else ((normalized + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
