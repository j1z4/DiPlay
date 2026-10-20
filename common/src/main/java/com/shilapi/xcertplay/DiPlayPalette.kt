package com.shilapi.xcertplay

import android.graphics.Color

/**
 * Semantic colors for DiPlay-owned chrome, in OpenPlay's design (design/ui-library/tokens.css):
 * true black, graphite surfaces, slate cards, one signal green. Focused and selected rows invert,
 * as on Apple TV. Media canvases and cluster-specific palettes stay separate.
 */
internal data class DiPlayPalette(
    val background: Int,
    val surface: Int,
    val button: Int,
    val outline: Int,
    val railSelected: Int,
    val railSelectedOutline: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val accent: Int,
    val onAccent: Int,
    val accentTrack: Int,
    val trackOff: Int,
    val warning: Int,
    val success: Int,
    val danger: Int,
    val ripple: Int,
    val focusRing: Int,
    val systemBar: Int,
    val systemBarIconsAreDark: Boolean,
    val overlayBackground: Int,
    val overlayPrimaryText: Int,
    val overlaySecondaryText: Int,
    val overlayAccent: Int,
    val overlayOnAccent: Int,
    val overlayAccentTrack: Int,
    val overlayTrackOff: Int,
    val overlayDanger: Int,
    /** Text and icons on the selected rail item. */
    val onRailSelected: Int = Color.rgb(11, 13, 17),
    /** A focused row or button fills with this, and its text turns [onFocus]. */
    val focusFill: Int = Color.rgb(245, 247, 250),
    val onFocus: Int = Color.rgb(11, 13, 17),
    /** The primary button: white on black, as OpenPlay's "Connect phone". */
    val primaryFill: Int = Color.rgb(245, 247, 250),
    val onPrimary: Int = Color.rgb(11, 13, 17),
    /** The slate card, top to bottom. */
    val cardTop: Int = Color.rgb(42, 48, 59),
    val cardBottom: Int = Color.rgb(30, 34, 42),
) {
    companion object {
        fun of(night: Boolean): DiPlayPalette = if (night) DARK else LIGHT

        val DARK = DiPlayPalette(
            background = Color.rgb(0, 0, 0),
            surface = Color.rgb(20, 22, 27),
            button = Color.rgb(29, 32, 39),
            outline = Color.rgb(40, 44, 53),
            railSelected = Color.rgb(245, 247, 250),
            railSelectedOutline = Color.rgb(245, 247, 250),
            primaryText = Color.rgb(245, 247, 250),
            secondaryText = Color.rgb(141, 149, 165),
            accent = Color.rgb(47, 228, 106),
            onAccent = Color.rgb(3, 20, 10),
            accentTrack = Color.rgb(20, 92, 46),
            trackOff = Color.rgb(52, 57, 68),
            warning = Color.rgb(255, 189, 92),
            success = Color.rgb(47, 228, 106),
            danger = Color.rgb(255, 90, 95),
            ripple = 0x22F5F7FA,
            focusRing = Color.rgb(47, 228, 106),
            systemBar = Color.rgb(0, 0, 0),
            systemBarIconsAreDark = false,
            overlayBackground = Color.rgb(20, 22, 27),
            overlayPrimaryText = Color.rgb(245, 247, 250),
            overlaySecondaryText = Color.rgb(141, 149, 165),
            overlayAccent = Color.rgb(47, 228, 106),
            overlayOnAccent = Color.rgb(3, 20, 10),
            overlayAccentTrack = Color.rgb(20, 92, 46),
            overlayTrackOff = Color.rgb(52, 57, 68),
            overlayDanger = Color.rgb(255, 90, 95),
        )

        val LIGHT = DiPlayPalette(
            background = Color.rgb(238, 241, 245),
            surface = Color.WHITE,
            button = Color.rgb(231, 235, 240),
            outline = Color.rgb(219, 224, 231),
            railSelected = Color.rgb(17, 20, 24),
            railSelectedOutline = Color.rgb(17, 20, 24),
            primaryText = Color.rgb(17, 20, 24),
            secondaryText = Color.rgb(93, 102, 118),
            accent = Color.rgb(11, 122, 51),
            onAccent = Color.WHITE,
            accentTrack = Color.rgb(11, 122, 51),
            trackOff = Color.rgb(195, 202, 212),
            warning = Color.rgb(138, 75, 0),
            success = Color.rgb(11, 122, 51),
            danger = Color.rgb(198, 47, 53),
            ripple = 0x1F111418,
            focusRing = Color.rgb(11, 122, 51),
            systemBar = Color.rgb(238, 241, 245),
            systemBarIconsAreDark = true,
            overlayBackground = Color.WHITE,
            overlayPrimaryText = Color.rgb(17, 20, 24),
            overlaySecondaryText = Color.rgb(93, 102, 118),
            overlayAccent = Color.rgb(11, 122, 51),
            overlayOnAccent = Color.WHITE,
            overlayAccentTrack = Color.rgb(11, 122, 51),
            overlayTrackOff = Color.rgb(195, 202, 212),
            overlayDanger = Color.rgb(198, 47, 53),
            onRailSelected = Color.WHITE,
            focusFill = Color.rgb(17, 20, 24),
            onFocus = Color.WHITE,
            primaryFill = Color.rgb(17, 20, 24),
            onPrimary = Color.WHITE,
            cardTop = Color.WHITE,
            cardBottom = Color.rgb(245, 247, 250),
        )
    }
}
