package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayContact
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * CarPlay surface: paints the newest decoded frame aspect-fit on black (or stretched over the
 * window when [keepAspect] is off), resampled as the [scaling] choice says, shows a status line
 * until video arrives, and turns presses/drags into CarPlay touch contacts. The main screen may be
 * fullscreen; the instrument cluster uses it windowed with an [onTouch] that does nothing.
 */
class VideoWindow(
    private val fullscreen: Boolean,
    private val windowSize: Dimension,
    private val onTouch: (List<AirPlayContact>) -> Unit,
    private val onClose: () -> Unit,
    title: String = APP_NAME,
    scaling: String = SettingsSchema.SCALING.default,
    private val keepAspect: Boolean = true,
) : ClusterSurface {
    private val interpolation = interpolationHint(scaling)
    @Volatile private var image: BufferedImage? = null
    @Volatile private var status: String = "Starting $title"
    private val panel = SurfacePanel()
    private val frame = JFrame(title).apply {
        isUndecorated = fullscreen
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        background = Color.BLACK
        contentPane = panel
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) = onClose()
        })
    }

    /** Fullscreen, centred on screen, or directly under [below] (a second screen stacked beneath the first). */
    fun show(below: VideoWindow? = null) = SwingUtilities.invokeLater {
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        if (fullscreen && device.isFullScreenSupported) {
            device.fullScreenWindow = frame
        } else {
            panel.preferredSize = windowSize
            frame.pack()
            place(below)
            frame.isVisible = true
        }
        panel.requestFocusInWindow()
    }

    override fun showFrame(next: BufferedImage) {
        image = next
        panel.repaint()
    }

    fun setStatus(message: String) {
        status = message
        panel.repaint()
    }

    /** As the map-only cluster: directly under the CarPlay window. */
    override fun open(below: VideoWindow) = show(below)

    /** Where this window is on screen while it is a visible window (not fullscreen); call on the event thread. */
    internal fun visibleBounds(): Rectangle? = frame.takeIf { !fullscreen && it.isVisible }?.bounds

    /** Closes the window; its windowClosed listener still runs onClose. */
    override fun close() = SwingUtilities.invokeLater { frame.dispose() }

    /** Back to the waiting screen when the session ends. */
    override fun clearVideo() {
        image = null
        panel.repaint()
    }

    private fun place(below: VideoWindow?) {
        val anchor = below?.visibleBounds()
        if (anchor == null) frame.setLocationRelativeTo(null) else frame.setLocation(anchor.x, anchor.y + anchor.height)
    }

    private inner class SurfacePanel : JPanel() {
        init {
            background = Color.BLACK
            isFocusable = true
            isDoubleBuffered = true
            val touch = TouchAdapter()
            addMouseListener(touch)
            addMouseMotionListener(touch)
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(event: KeyEvent) {
                    // Disposing fires windowClosed, which runs onClose.
                    if (event.keyCode == KeyEvent.VK_ESCAPE) frame.dispose()
                }
            })
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g = graphics as Graphics2D
            val current = image
            if (current == null) {
                g.color = Color(0xB0B0B0)
                g.font = Font(Font.SANS_SERIF, Font.PLAIN, STATUS_FONT_SIZE)
                val width = g.fontMetrics.stringWidth(status)
                g.drawString(status, (this.width - width) / 2, this.height / 2)
                return
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation)
            val target = contentRect(current)
            g.drawImage(current, target.x, target.y, target.width, target.height, null)
        }

        /** Where the video goes inside the panel; touches map through the same rectangle. */
        fun contentRect(current: BufferedImage): Rectangle =
            videoRect(width, height, current.width, current.height, keepAspect)
    }

    private inner class TouchAdapter : MouseAdapter() {
        override fun mousePressed(event: MouseEvent) = send(event, down = true)
        override fun mouseDragged(event: MouseEvent) = send(event, down = true)
        override fun mouseReleased(event: MouseEvent) = send(event, down = false)

        private fun send(event: MouseEvent, down: Boolean) {
            val current = image ?: return
            val target = panel.contentRect(current)
            if (target.width <= 0 || target.height <= 0) return
            val x = ((event.x - target.x).toDouble() / target.width).coerceIn(0.0, 1.0)
            val y = ((event.y - target.y).toDouble() / target.height).coerceIn(0.0, 1.0)
            onTouch(listOf(AirPlayContact(id = 0, x = x, y = y, down = down)))
        }
    }

    companion object {
        private const val STATUS_FONT_SIZE = 28

        /** The Java2D resampling for a Scaling quality choice; anything unknown is bilinear. */
        internal fun interpolationHint(scaling: String): Any = when (scaling) {
            "nearest" -> RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            "bicubic" -> RenderingHints.VALUE_INTERPOLATION_BICUBIC
            else -> RenderingHints.VALUE_INTERPOLATION_BILINEAR
        }

        /** The picture aspect-fit and centred in the panel, or the whole panel when [keepAspect] is off. */
        internal fun videoRect(panelWidth: Int, panelHeight: Int, imageWidth: Int, imageHeight: Int, keepAspect: Boolean): Rectangle {
            if (!keepAspect) return Rectangle(0, 0, panelWidth, panelHeight)
            val scale = minOf(panelWidth.toDouble() / imageWidth, panelHeight.toDouble() / imageHeight)
            val w = (imageWidth * scale).toInt()
            val h = (imageHeight * scale).toInt()
            return Rectangle((panelWidth - w) / 2, (panelHeight - h) / 2, w, h)
        }
    }
}
