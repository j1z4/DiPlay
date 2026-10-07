package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay.Content
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Lifecycle the dashboard shows: idle, connecting, connected. */
enum class SessionState { STOPPED, CONNECTING, CONNECTED }

/**
 * One CarPlay run: the wireless receiver, the media sink and the CarPlay window, plus the cluster
 * window while that experiment is on. Started and stopped from the dashboard; closing the CarPlay
 * window stops the run.
 */
class CarPlaySession(
    private val store: DesktopStore,
    private val log: (String) -> Unit,
    private val onState: (SessionState, String) -> Unit,
) {
    private val active = AtomicReference<Run?>(null)

    val isRunning: Boolean get() = active.get() != null

    fun start(settings: DesktopSettings) {
        if (active.get() != null) return
        val run = Run(settings)
        if (!active.compareAndSet(null, run)) return
        onState(SessionState.CONNECTING, "Connecting to iPhone")
        run.start()
    }

    fun stop() {
        active.getAndSet(null)?.close()
        onState(SessionState.STOPPED, "Stopped")
    }

    /** True while the current run was started with the cluster display on. */
    val hasClusterDisplay: Boolean get() = active.get()?.hasCluster == true

    /**
     * Switches what the iPhone draws on the cluster (map, turn card or both) without reconnecting:
     * the same showUI command a car's cluster menu sends. False when nothing could be sent.
     */
    fun showClusterContent(content: Content): Boolean =
        clusterCommand("show ${content.url}") { it.setClusterUrl(content.url, send = true) }

    /** Stops the cluster stream (the window keeps its last frame) or shows the last content again. */
    fun setClusterVisible(visible: Boolean): Boolean =
        clusterCommand(if (visible) "show" else "hide") { it.setClusterUiShown(visible) }

    /** Zooms the cluster map one level, as a steering-wheel button would. */
    fun zoomCluster(zoomIn: Boolean): Boolean =
        clusterCommand(if (zoomIn) "zoom in" else "zoom out") { it.changeMapZoomLevel(zoomIn) }

    /** Sends one cluster command to the connected iPhone; false, with the reason logged, otherwise. */
    private fun clusterCommand(name: String, command: (AirPlaySession) -> Boolean): Boolean {
        val run = active.get() ?: return skipped(name, "CarPlay is not running")
        if (!run.hasCluster) return skipped(name, "the cluster display is off")
        val session = run.session() ?: return skipped(name, "the iPhone is not connected")
        if (!command(session)) return skipped(name, "the iPhone has not set up its cluster stream yet")
        log("cluster: $name sent")
        return true
    }

    private fun skipped(name: String, reason: String): Boolean {
        log("cluster: $name not sent, $reason")
        return false
    }

    private inner class Run(private val settings: DesktopSettings) {
        private val airPlay = AtomicReference<AirPlaySession?>(null)
        private val window = VideoWindow(
            fullscreen = settings.fullscreen,
            windowSize = Dimension(settings.width, settings.height),
            onTouch = { contacts -> airPlay.get()?.sendTouch(contacts) },
            onClose = { if (active.get() === this) stop() },
            scaling = settings.advanced[SettingsSchema.SCALING],
            keepAspect = settings.advanced[SettingsSchema.KEEP_ASPECT],
        )
        // The cluster has no input and is never the kiosk surface; closing it leaves the run going.
        private val clusterWindow = if (!settings.clusterDisplay) null else VideoWindow(
            fullscreen = false,
            windowSize = ClusterDisplay.windowSize(settings.advanced),
            onTouch = {},
            onClose = {},
            title = ClusterDisplay.WINDOW_TITLE,
        )
        val hasCluster: Boolean get() = clusterWindow != null

        fun session(): AirPlaySession? = airPlay.get()
        private val sink = DesktopMediaSink(window, clusterWindow, log, settings.advanced)
        private val receiver = WirelessReceiver(
            settings = settings,
            store = store,
            media = CarPlayMediaEngine(sink, microphoneEnabled = true),
            events = object : ReceiverEvents {
                override fun onStatus(message: String) {
                    window.setStatus(message)
                    if (airPlay.get() == null) onState(SessionState.CONNECTING, message)
                }

                override fun onSessionActive(session: AirPlaySession) {
                    airPlay.set(session)
                    onState(SessionState.CONNECTED, "CarPlay connected")
                }

                override fun onSessionEnded() {
                    airPlay.set(null)
                    window.clearVideo()
                    clusterWindow?.clearVideo()
                    if (active.get() === this@Run) onState(SessionState.CONNECTING, "Session ended; waiting for iPhone")
                }
            },
            log = log,
        )

        fun start() {
            window.show()
            clusterWindow?.show(below = window)
            thread(name = "openplay-bootstrap", isDaemon = true) {
                try {
                    receiver.run()
                } catch (error: Exception) {
                    log("receiver failed: ${error.message}")
                    window.setStatus("Could not start CarPlay: ${error.message}")
                    if (active.compareAndSet(this, null)) {
                        close()
                        onState(SessionState.STOPPED, "Could not start CarPlay: ${error.message}")
                    }
                }
            }
        }

        fun close() {
            receiver.close()
            sink.close()
            clusterWindow?.close()
            window.close()
        }
    }
}
