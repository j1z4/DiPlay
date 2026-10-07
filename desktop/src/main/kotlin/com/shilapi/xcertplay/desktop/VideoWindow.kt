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
 * CarPlay surface: paints the newest decoded frame aspect-fit on black, shows a status line until
 * video arrives, and turns presses/drags into CarPlay touch contacts. The main screen may be
 * fullscreen; the instrument cluster uses it windowed with an [onTouch] that does nothing.
 */
class VideoWindow(
    private val fullscreen: Boolean,
    private val windowSize: Dimension,
    private val onTouch: (List<AirPlayContact>) -> Unit,
    private val onClose: () -> Unit,
    title: String = APP_NAME,
) : VideoSurface {
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

    /** Closes the window; its windowClosed listener still runs onClose. */
    fun close() = SwingUtilities.invokeLater { frame.dispose() }

    /** Back to the waiting screen when the session ends. */
    override fun clearVideo() {
        image = null
        panel.repaint()
    }

    private fun place(below: VideoWindow?) {
        val anchor = below?.takeIf { !it.fullscreen && it.frame.isVisible }?.frame?.bounds
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
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val target = contentRect(current)
            g.drawImage(current, target.x, target.y, target.width, target.height, null)
        }

        /** Aspect-fit rectangle of the video inside the panel. */
        fun contentRect(current: BufferedImage): Rectangle {
            val scale = minOf(width.toDouble() / current.width, height.toDouble() / current.height)
            val w = (current.width * scale).toInt()
            val h = (current.height * scale).toInt()
            return Rectangle((width - w) / 2, (height - h) / 2, w, h)
        }
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

    private companion object {
        const val STATUS_FONT_SIZE = 28
    }
}
