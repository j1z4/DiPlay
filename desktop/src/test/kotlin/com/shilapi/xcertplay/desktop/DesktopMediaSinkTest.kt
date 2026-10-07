package com.shilapi.xcertplay.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class DesktopMediaSinkTest {
    private val logs = CopyOnWriteArrayList<String>()
    private val main = FakeSurface()
    private val cluster = FakeSurface()

    @Test fun clusterStreamRendersIntoTheClusterWindow() {
        val sink = DesktopMediaSink(main, cluster, logs::add) { FakeDecoder() }
        try {
            sink.onScreenStreamActive(111, true)
            sink.onVideoFrame(111, nalUnit(IDR_HEADER, 1))
            cluster.awaitFrame()
        } finally {
            sink.close()
        }

        assertEquals(0, main.shown.get())
        assertTrue(logs.contains("cluster: altScreen negotiated; iPhone set up stream 111"))
        assertTrue(logs.contains("cluster: stream 111 active=true"))
        assertTrue(logs.contains("cluster: first frame rendered 16x8"))
    }

    @Test fun clusterStreamIsIgnoredWhileTheDisplayIsOff() {
        val sink = DesktopMediaSink(main, null, logs::add) { FakeDecoder() }
        try {
            sink.onScreenStreamActive(111, true)
            sink.onVideoFrame(111, nalUnit(IDR_HEADER, 1))
            sink.onVideoFrame(110, nalUnit(IDR_HEADER, 2))
            main.awaitFrame()
        } finally {
            sink.close()
        }

        assertTrue(logs.contains("cluster: iPhone set up stream 111 but the cluster display is off; ignoring it"))
        assertEquals(listOf("video: first frame rendered 16x8"), logs.filter { it.contains("first frame") })
    }
}
