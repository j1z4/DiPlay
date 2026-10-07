package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.airplay.VideoCodec
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/** Counts decoded-video input until the desktop renderer exists; proves frames reach Windows. */
class CountingMediaSink(private val log: (String) -> Unit) : MediaSink {
    private val frames = AtomicLong()
    private val bytes = AtomicLong()

    override fun onVideoCodec(type: Int, codec: VideoCodec) = log("video stream $type codec=$codec")

    override fun onVideoConfig(type: Int, codecData: ByteArray) = log("video stream $type config bytes=${codecData.size}")

    override fun onVideoFrame(type: Int, naluBytes: ByteArray) {
        val count = frames.incrementAndGet()
        val total = bytes.addAndGet(naluBytes.size.toLong())
        if (count == 1L || count % FRAME_LOG_INTERVAL == 0L) log("video stream $type frames=$count bytes=$total")
    }

    override fun onScreenStreamActive(type: Int, active: Boolean) = log("video stream $type active=$active")

    private companion object {
        const val FRAME_LOG_INTERVAL = 300L
    }
}

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
    val receiver = WirelessReceiver(
        settings = settings,
        store = store,
        media = CarPlayMediaEngine(CountingMediaSink(log)),
        events = object : ReceiverEvents {},
        log = log,
    )
    val stopped = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(Thread {
        receiver.close()
        stopped.countDown()
    })
    try {
        receiver.run()
    } catch (error: Exception) {
        log("receiver failed: ${error.message}")
        exitProcess(1)
    }
    log("receiver running; press Ctrl+C to stop")
    stopped.await()
}
