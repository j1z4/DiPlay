package com.shilapi.xcertplay.desktop

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import kotlin.math.min

/** A cluster window CarPlaySession opens under the CarPlay window and closes with the run. */
interface ClusterSurface : VideoSurface {
    fun open(below: VideoWindow)
    fun close()
}

/**
 * The gauges-style cluster: OpenPlay's instrument panel with the iPhone's cluster map, which
 * arrives as stream 111 frames through [showFrame], in the window between the dials. The dials
 * read [readings] and ease towards it, so they sweep up from zero when the window opens. The
 * window starts at [panelSize] shrunk to fit under the CarPlay window, and can be resized freely.
 */
class InstrumentClusterWindow(
    private val panelSize: Dimension,
    private val painter: InstrumentPanelPainter,
    private val readings: () -> ClusterReadings,
) : ClusterSurface {
    @Volatile private var map: BufferedImage? = null
    @Volatile private var shown: ClusterReadings? = null
    private val panel = PanelView()
    private val frame = JFrame(ClusterDisplay.WINDOW_TITLE).apply {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        background = Color.BLACK
        contentPane = panel
    }
    private val timer = Timer(FRAME_MILLIS) { tick() }.apply { isCoalesce = true }

    init {
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) = timer.stop()
        })
    }

    override fun open(below: VideoWindow) = SwingUtilities.invokeLater {
        panel.preferredSize = fitted(below)
        frame.pack()
        val anchor = below.visibleBounds()
        if (anchor == null) frame.setLocationRelativeTo(null) else frame.setLocation(anchor.x, anchor.y + anchor.height)
        // Never past the taskbar: a tall panel under a tall CarPlay window overlaps it instead.
        val usable = usableScreen()
        frame.setLocation(frame.x, frame.y.coerceAtMost(usable.y + usable.height - frame.height).coerceAtLeast(usable.y))
        frame.isVisible = true
        timer.start()
    }

    override fun close() = SwingUtilities.invokeLater {
        timer.stop()
        frame.dispose()
    }

    override fun showFrame(next: BufferedImage) {
        map = next
        panel.repaint()
    }

    override fun clearVideo() {
        map = null
        panel.repaint()
    }

    /** Moves the shown speed and power a step towards the car's, then repaints. */
    private fun tick() {
        if (!frame.isShowing || (frame.extendedState and JFrame.ICONIFIED) != 0) return
        val target = readings()
        val previous = shown
        shown = if (previous == null) {
            target.copy(speedKmh = 0.0, powerKw = 0.0)
        } else {
            target.copy(
                speedKmh = previous.speedKmh + (target.speedKmh - previous.speedKmh) * EASING,
                powerKw = previous.powerKw + (target.powerKw - previous.powerKw) * EASING,
            )
        }
        panel.repaint()
    }

    /** The panel size, scaled down (keeping its shape) to fit the screen's width and the space under [below]. */
    private fun fitted(below: VideoWindow): Dimension {
        val screen = usableScreen()
        val anchor = below.visibleBounds()
        val roomBelow = if (anchor == null) screen.height else screen.y + screen.height - (anchor.y + anchor.height)
        val height = (maxOf(roomBelow, screen.height / 3) - TITLE_BAR_ALLOWANCE) * SCREEN_FILL
        val scale = min(1.0, min(screen.width * SCREEN_FILL / panelSize.width, height / panelSize.height))
        return Dimension((panelSize.width * scale).toInt(), (panelSize.height * scale).toInt())
    }

    /** The screen this window is on, without the taskbar. */
    private fun usableScreen(): Rectangle {
        val config = frame.graphicsConfiguration
        val bounds = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        return Rectangle(
            bounds.x + insets.left, bounds.y + insets.top,
            bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom,
        )
    }

    private inner class PanelView : JPanel() {
        init {
            background = Color.BLACK
            isDoubleBuffered = true
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val current = shown ?: return
            painter.paint(graphics as Graphics2D, width, height, current, map, WAITING)
        }
    }

    private companion object {
        const val FRAME_MILLIS = 33
        const val EASING = 0.08
        const val SCREEN_FILL = 0.95
        const val TITLE_BAR_ALLOWANCE = 40
        const val WAITING = "Waiting for the iPhone map"
    }
}
