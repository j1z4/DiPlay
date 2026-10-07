package com.shilapi.xcertplay.desktop

import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the gauges-style instrument panel at any size: a speed dial on the left, a power dial with
 * the gear on the right, the iPhone's cluster map in a rounded window between them
 * ([ClusterDisplay.mapSlot]), the time and outside temperature above it and the odometer below.
 * Units, accent colour and the map window's width come from the Cluster tab.
 */
class InstrumentPanelPainter(values: SettingsValues) {
    private val units = ClusterUnits.of(values[SettingsSchema.CLUSTER_UNITS])
    private val accent = ClusterTheme.accent(values[SettingsSchema.CLUSTER_ACCENT])
    private val mapShare = values[SettingsSchema.CLUSTER_MAP_SHARE]
    private val dial = GaugeDial(accent)
    private val clock = DateTimeFormatter.ofPattern(if (units == ClusterUnits.IMPERIAL) "h:mm" else "HH:mm", Locale.US)

    /** The panel as an image, for snapshots and tests. */
    fun render(width: Int, height: Int, readings: ClusterReadings, map: BufferedImage?, status: String): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            paint(g, width, height, readings, map, status)
        } finally {
            g.dispose()
        }
        return image
    }

    fun paint(g: Graphics2D, width: Int, height: Int, readings: ClusterReadings, map: BufferedImage?, status: String) {
        g.setRenderingHints(QUALITY)
        val slot = ClusterDisplay.mapSlot(width, height, mapShare)
        val side = slot.x.toDouble()
        val radius = min(height * DIAL_HEIGHT_SHARE, side * DIAL_SIDE_SHARE)
        val left = Point2D.Double(side * DIAL_CENTRE_X, height * DIAL_CENTRE_Y)
        val right = Point2D.Double(width - side * DIAL_CENTRE_X, height * DIAL_CENTRE_Y)
        paintBackground(g, width, height, listOf(left, right), radius)
        dial.paint(g, left, radius, speedReading(readings))
        dial.paint(g, right, radius, powerReading(readings))
        paintMap(g, slot, height, map, status)
        paintTopBar(g, slot, readings, height)
        paintBottomBar(g, slot, readings, height)
    }

    private fun speedReading(readings: ClusterReadings): DialReading {
        val speed = units.speed(readings.speedKmh)
        val range = units.distance(readings.rangeKm.toDouble()).roundToInt()
        return DialReading(
            scale = units.speedScale, value = speed, arcFrom = 0.0,
            centre = speed.roundToInt().toString(), unit = units.speedLabel,
            footer = "$range ${units.distanceLabel} range", footerWarning = readings.rangeWarning,
        )
    }

    private fun powerReading(readings: ClusterReadings): DialReading {
        val charging = if (readings.charging) " · charging" else ""
        return DialReading(
            scale = POWER_SCALE, value = readings.powerKw, arcFrom = 0.0,
            centre = readings.gear.toString(), unit = "${readings.powerKw.roundToInt()} kW",
            footer = "${readings.batteryPercent.roundToInt()}% battery$charging",
            footerWarning = readings.rangeWarning,
        )
    }

    /** Near-black with a faint accent glow behind each dial and a darker floor. */
    private fun paintBackground(g: Graphics2D, width: Int, height: Int, dials: List<Point2D>, radius: Double) {
        g.color = ClusterTheme.BACKGROUND
        g.fillRect(0, 0, width, height)
        val glow = ClusterTheme.withAlpha(accent, BACKGROUND_GLOW_ALPHA)
        for (centre in dials) {
            val reach = (radius * BACKGROUND_GLOW_REACH).toFloat()
            g.paint = RadialGradientPaint(
                centre, reach, floatArrayOf(0f, 1f), arrayOf(glow, ClusterTheme.withAlpha(accent, 0)),
                MultipleGradientPaint.CycleMethod.NO_CYCLE,
            )
            g.fillRect(0, 0, width, height)
        }
        g.paint = GradientPaint(0f, height * FLOOR_START, Color(0, 0, 0, 0), 0f, height.toFloat(), Color(0, 0, 0, FLOOR_ALPHA))
        g.fillRect(0, 0, width, height)
    }

    /** The map fills its rounded window (cropped, never letterboxed); until it arrives, [status] shows there. */
    private fun paintMap(g: Graphics2D, slot: Rectangle, height: Int, map: BufferedImage?, status: String) {
        val corner = height * SLOT_CORNER
        val window = RoundRectangle2D.Double(slot.x.toDouble(), slot.y.toDouble(), slot.width.toDouble(), slot.height.toDouble(), corner, corner)
        if (map == null) {
            g.color = ClusterTheme.SLOT_EMPTY
            g.fill(window)
            g.font = ClusterTheme.font(height * SMALL_TEXT)
            GaugeDial.drawCentred(g, status, Point2D.Double(slot.centerX, slot.centerY), ClusterTheme.TEXT_DIM)
            return
        }
        val scale = max(slot.width.toDouble() / map.width, slot.height.toDouble() / map.height)
        val drawnWidth = (map.width * scale).roundToInt()
        val drawnHeight = (map.height * scale).roundToInt()
        val previousClip = g.clip
        g.clip(window)
        g.drawImage(map, slot.x + (slot.width - drawnWidth) / 2, slot.y + (slot.height - drawnHeight) / 2, drawnWidth, drawnHeight, null)
        g.clip = previousClip
    }

    /** READY or PARK on the left, the time in the middle, the outside temperature on the right. */
    private fun paintTopBar(g: Graphics2D, slot: Rectangle, readings: ClusterReadings, height: Int) {
        val y = slot.y / 2.0
        g.font = ClusterTheme.font(height * SMALL_TEXT, bold = true)
        val driving = readings.gear == 'D'
        val state = if (driving) "READY" else "PARK"
        val stateWidth = g.fontMetrics.stringWidth(state)
        GaugeDial.drawCentred(g, state, Point2D.Double(slot.x + stateWidth / 2.0, y), if (driving) ClusterTheme.GOOD else ClusterTheme.TEXT_DIM)
        g.font = ClusterTheme.font(height * SMALL_TEXT)
        GaugeDial.drawCentred(g, clock.format(readings.time), Point2D.Double(slot.centerX, y), ClusterTheme.TEXT)
        val temperature = "${units.temperature(readings.outsideC)}${units.temperatureLabel}"
        val temperatureWidth = g.fontMetrics.stringWidth(temperature)
        GaugeDial.drawCentred(g, temperature, Point2D.Double(slot.maxX - temperatureWidth / 2.0, y), ClusterTheme.TEXT)
    }

    private fun paintBottomBar(g: Graphics2D, slot: Rectangle, readings: ClusterReadings, height: Int) {
        val odometer = String.format(Locale.US, "%,d", units.distance(readings.odometerKm).roundToInt())
        val trip = String.format(Locale.US, "%.1f", units.distance(readings.tripKm))
        val label = units.distanceLabel
        g.font = ClusterTheme.font(height * SMALL_TEXT)
        val y = (slot.maxY + height) / 2.0
        GaugeDial.drawCentred(g, "$odometer $label    Trip $trip $label", Point2D.Double(slot.centerX, y), ClusterTheme.TEXT_DIM)
    }

    private companion object {
        /** Electric drive power: up to 50 kW of regeneration and 200 kW of drive. */
        val POWER_SCALE = DialScale(min = -50.0, max = 200.0, majorStep = 50.0, minorStep = 10.0)
        val QUALITY = RenderingHints(
            mapOf(
                RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
                RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE,
                RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            ),
        )
        const val DIAL_HEIGHT_SHARE = 0.40
        const val DIAL_SIDE_SHARE = 0.42
        const val DIAL_CENTRE_X = 0.54
        const val DIAL_CENTRE_Y = 0.53
        const val BACKGROUND_GLOW_ALPHA = 26
        const val BACKGROUND_GLOW_REACH = 1.4
        const val FLOOR_START = 0.6f
        const val FLOOR_ALPHA = 140
        const val SLOT_CORNER = 0.06
        const val SMALL_TEXT = 0.034
    }
}
