package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import java.awt.Dimension
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val SETTINGS_TEMPLATE = """
    # DiPlay for Windows settings
    # Bluetooth address of your paired iPhone (Windows Settings > Bluetooth & devices).
    iphoneAddress=
    # Password of the Wi-Fi network this PC and the iPhone share (blank for an open network).
    wifiPassphrase=
    # Folder containing offline-mfi identity.pk8 and certificate.p7b.
    mfiDirectory=
    width=1280
    height=720
    fps=60
    # true for a borderless fullscreen kiosk (in-car PC); false opens a normal window.
    fullscreen=false
""".trimIndent()

fun main() {
    val store = DesktopStore()
    if (!store.settingsFile.isFile) {
        store.settingsFile.writeText(SETTINGS_TEMPLATE + System.lineSeparator(), Charsets.UTF_8)
        println("Created ${store.settingsFile.absolutePath}; fill it in and run again.")
        exitProcess(1)
    }
    val settings = runCatching(store::loadSettings).getOrElse {
        System.err.println(it.message)
        exitProcess(1)
    }

    val log: (String) -> Unit = { println("${LocalTime.now()} $it") }
    val stopped = CountDownLatch(1)
    val activeSession = AtomicReference<AirPlaySession?>(null)

    val window = VideoWindow(
        fullscreen = settings.fullscreen,
        windowSize = Dimension(settings.width, settings.height),
        onTouch = { contacts -> activeSession.get()?.sendTouch(contacts) },
        onClose = { stopped.countDown() },
    )
    val sink = DesktopMediaSink(window, log)
    val receiver = WirelessReceiver(
        settings = settings,
        store = store,
        media = CarPlayMediaEngine(sink),
        events = object : ReceiverEvents {
            override fun onStatus(message: String) = window.setStatus(message)
            override fun onSessionActive(session: AirPlaySession) = activeSession.set(session)
            override fun onSessionEnded() {
                activeSession.set(null)
                window.clearVideo()
            }
        },
        log = log,
    )

    window.show()
    thread(name = "diplay-bootstrap", isDaemon = true) {
        try {
            receiver.run()
        } catch (error: Exception) {
            log("receiver failed: ${error.message}")
            window.setStatus("Could not start CarPlay: ${error.message}")
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread { stopped.countDown() })
    stopped.await()
    receiver.close()
    sink.close()
    exitProcess(0)
}
