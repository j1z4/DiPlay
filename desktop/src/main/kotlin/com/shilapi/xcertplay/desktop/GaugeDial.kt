package com.shilapi.xcertplay.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Colours and type shared by the instrument panel. */
object ClusterTheme {
    val BACKGROUND = Color(0x07090B)
    val FACE_INNER = Color(0x161B20)
    val FACE_OUTER = Color(0x0A0D10)
    val RIM = Color(0x2B333B)
    val TRACK = Color(0x20262C)
    val TICK = Color(0x59626B)
    val TICK_MAJOR = Color(0xC9D1D8)
    val TEXT = Color(0xF2F5F7)
    val TEXT_DIM = Color(0x8D969F)
    val WARNING = Color(0xFFB547)
    val GOOD = Color(0x4CD97B)
    val SLOT_EMPTY = Color(0x12161A)

    private val ACCENTS = mapOf(
        "teal" to Color(0x3FD9B5),
        "amber" to Color(0xFFB547),
        "red" to Color(0xFF5A4E),
        "blue" to Color(0x5AA9FF),
        "white" to Color(0xE8ECEF),
    )

    /** The accent for a [SettingsSchema.CLUSTER_ACCENT] value; anything unknown is teal. */
    fun accent(name: String): Color = ACCENTS[name] ?: ACCENTS.getValue("teal")

    private val family: String by lazy {
        val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        listOf("Segoe UI Variable Display", "Segoe UI", "Bahnschrift").firstOrNull { it in installed } ?: Font.SANS_SERIF
    }

    private val fonts = ConcurrentHashMap<Pair<Int, Boolean>, Font>()

    /** The panel font at [size] (to the nearest half point), cached: the panel repaints 30 times a second. */
    fun font(size: Double, bold: Boolean = false): Font {
        val halfPoints = (size * 2).roundToInt()
        return fonts.getOrPut(halfPoints to bold) {
            Font(family, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(halfPoints / 2f)
        }
    }

    fun withAlpha(color: Color, alpha: Int) = Color(color.red, color.green, color.blue, alpha)
}

/** What one dial shows: its scale, the reading, where the value arc starts, and the text inside. */
data class DialReading(
    val scale: DialScale,
    val value: Double,
    val arcFrom: Double,
    val centre: String,
    val unit: String,
    val footer: String,
    val footerWarning: Boolean = false,
)

/**
 * One round instrument: a 270° tick ring open at the bottom, an accent arc from [DialReading.arcFrom]
 * to the reading with a soft glow and a bright tip, and the reading in large type in the middle.
 */
class GaugeDial(private val accent: Color) {
    fun paint(g: Graphics2D, centre: Point2D, radius: Double, reading: DialReading) {
        paintFace(g, centre, radius)
        paintTicks(g, centre, radius, reading.scale)
        paintValueArc(g, centre, radius, reading)
        paintText(g, centre, radius, reading)
    }

    private fun paintFace(g: Graphics2D, centre: Point2D, radius: Double) {
        g.paint = RadialGradientPaint(
            centre, radius.toFloat(), floatArrayOf(0f, 1f), arrayOf(ClusterTheme.FACE_INNER, ClusterTheme.FACE_OUTER),
            MultipleGradientPaint.CycleMethod.NO_CYCLE,
        )
        g.fill(circle(centre, radius))
        g.color = ClusterTheme.RIM
        g.stroke = BasicStroke((radius * RIM_WIDTH).toFloat())
        g.draw(circle(centre, radius))
        g.color = ClusterTheme.TRACK
        g.stroke = arcStroke(radius * ARC_WIDTH)
        g.draw(arc(centre, radius * ARC_RADIUS, 0.0, 1.0))
    }

    private fun paintTicks(g: Graphics2D, centre: Point2D, radius: Double, scale: DialScale) {
        val steps = ((scale.max - scale.min) / scale.minorStep).roundToInt()
        g.font = ClusterTheme.font(radius * LABEL_SIZE)
        for (step in 0..steps) {
            val value = scale.min + step * scale.minorStep
            val major = isMultiple(value - scale.min, scale.majorStep)
            val angle = angleOf(scale.fraction(value))
            g.color = if (major) ClusterTheme.TICK_MAJOR else ClusterTheme.TICK
            g.stroke = BasicStroke((radius * if (major) MAJOR_TICK_WIDTH else MINOR_TICK_WIDTH).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val inner = radius * if (major) MAJOR_TICK_INNER else MINOR_TICK_INNER
            g.draw(Line2D.Double(pointAt(centre, inner, angle), pointAt(centre, radius * TICK_OUTER, angle)))
            if (major) drawCentred(g, value.roundToInt().toString(), pointAt(centre, radius * LABEL_RADIUS, angle), ClusterTheme.TEXT_DIM)
        }
    }

    private fun paintValueArc(g: Graphics2D, centre: Point2D, radius: Double, reading: DialReading) {
        val from = reading.scale.fraction(reading.arcFrom)
        val to = reading.scale.fraction(reading.value)
        if (abs(to - from) > MIN_ARC_FRACTION) {
            val shape = arc(centre, radius * ARC_RADIUS, minOf(from, to), maxOf(from, to))
            g.color = ClusterTheme.withAlpha(accent, GLOW_ALPHA)
            g.stroke = arcStroke(radius * GLOW_WIDTH)
            g.draw(shape)
            g.color = accent
            g.stroke = arcStroke(radius * ARC_WIDTH)
            g.draw(shape)
        }
        val tip = pointAt(centre, radius * ARC_RADIUS, angleOf(to))
        g.color = ClusterTheme.withAlpha(accent, GLOW_ALPHA)
        g.fill(circle(tip, radius * TIP_GLOW_RADIUS))
        g.color = ClusterTheme.TEXT
        g.fill(circle(tip, radius * TIP_RADIUS))
    }

    private fun paintText(g: Graphics2D, centre: Point2D, radius: Double, reading: DialReading) {
        g.font = ClusterTheme.font(radius * VALUE_SIZE, bold = true)
        drawCentred(g, reading.centre, Point2D.Double(centre.x, centre.y - radius * VALUE_RAISE), ClusterTheme.TEXT)
        g.font = ClusterTheme.font(radius * UNIT_SIZE)
        drawCentred(g, reading.unit, Point2D.Double(centre.x, centre.y + radius * UNIT_DROP), ClusterTheme.TEXT_DIM)
        g.font = ClusterTheme.font(radius * FOOTER_SIZE)
        val footerColour = if (reading.footerWarning) ClusterTheme.WARNING else ClusterTheme.TEXT
        drawCentred(g, reading.footer, Point2D.Double(centre.x, centre.y + radius * FOOTER_DROP), footerColour)
    }

    companion object {
        /** The ring runs clockwise from 225° (lower left) through the top to -45° (lower right). */
        private const val START_DEGREES = 225.0
        private const val SWEEP_DEGREES = 270.0
        private const val RIM_WIDTH = 0.012
        private const val ARC_RADIUS = 0.88
        private const val ARC_WIDTH = 0.045
        private const val GLOW_WIDTH = 0.12
        private const val GLOW_ALPHA = 60
        private const val TIP_RADIUS = 0.028
        private const val TIP_GLOW_RADIUS = 0.07
        private const val MIN_ARC_FRACTION = 0.002
        private const val TICK_OUTER = 0.79
        private const val MAJOR_TICK_INNER = 0.70
        private const val MINOR_TICK_INNER = 0.75
        private const val MAJOR_TICK_WIDTH = 0.012
        private const val MINOR_TICK_WIDTH = 0.007
        private const val LABEL_RADIUS = 0.60
        private const val LABEL_SIZE = 0.075
        private const val VALUE_SIZE = 0.36
        private const val VALUE_RAISE = 0.02
        private const val UNIT_SIZE = 0.075
        private const val UNIT_DROP = 0.21
        private const val FOOTER_SIZE = 0.08
        private const val FOOTER_DROP = 0.62
        private const val EPSILON = 1e-6

        fun angleOf(fraction: Double): Double = START_DEGREES - SWEEP_DEGREES * fraction

        /** Draws [text] with its visual centre on [at]. */
        fun drawCentred(g: Graphics2D, text: String, at: Point2D, colour: Color) {
            val metrics = g.fontMetrics
            g.color = colour
            val x = at.x - metrics.stringWidth(text) / 2.0
            val y = at.y + (metrics.ascent - metrics.descent) / 2.0
            g.drawString(text, x.toFloat(), y.toFloat())
        }

        private fun arc(centre: Point2D, r: Double, from: Double, to: Double) = Arc2D.Double(
            centre.x - r, centre.y - r, 2 * r, 2 * r, angleOf(from), -SWEEP_DEGREES * (to - from), Arc2D.OPEN,
        )

        private fun arcStroke(width: Double) = BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        private fun circle(centre: Point2D, r: Double) = Ellipse2D.Double(centre.x - r, centre.y - r, 2 * r, 2 * r)

        /** Screen point at [r] from [centre] along [degrees], counter-clockwise from 3 o'clock. */
        private fun pointAt(centre: Point2D, r: Double, degrees: Double): Point2D {
            val radians = Math.toRadians(degrees)
            return Point2D.Double(centre.x + r * cos(radians), centre.y - r * sin(radians))
        }

        private fun isMultiple(value: Double, step: Double): Boolean {
            val remainder = abs(value % step)
            return remainder < EPSILON || abs(remainder - step) < EPSILON
        }
    }
}
