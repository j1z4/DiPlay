package com.shilapi.xcertplay.desktop

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScreenDecoderTest {
    private val logs = CopyOnWriteArrayList<String>()
    private val diagnostics = CopyOnWriteArrayList<String>()
    private val surface = FakeSurface()
    private val fake = FakeDecoder()
    private var decoder: ScreenDecoder? = null

    private fun start(decoder: H264Decoder = fake): ScreenDecoder =
        ScreenDecoder("cluster", 111, surface, logs::add) { decoder }.also {
            it.setDiagnosticHandler(diagnostics::add)
            this.decoder = it
        }

    @After fun close() {
        decoder?.close()
    }

    @Test fun keyframeIsPrefixedWithParameterSetsAndTheFirstFrameReportedOnce() {
        val sps = byteArrayOf(0x67, 1, 2)
        val pps = byteArrayOf(0x68, 3)
        val screen = start()
        screen.onConfig(avcRecord(sps, pps))

        screen.onFrame(nalUnit(IDR_HEADER, 7))
        screen.onFrame(nalUnit(SLICE_HEADER, 8))
        surface.awaitFrame()
        surface.awaitFrame()

        val idr = ANNEX_B_START_CODE + byteArrayOf(IDR_HEADER.toByte(), 7)
        assertArrayEquals(ANNEX_B_START_CODE + sps + ANNEX_B_START_CODE + pps + idr, fake.inputs[0])
        assertArrayEquals(ANNEX_B_START_CODE + byteArrayOf(SLICE_HEADER.toByte(), 8), fake.inputs[1])
        assertEquals(listOf("first frame rendered"), diagnostics)
        assertTrue(logs.contains("cluster: H.264 parameter sets sps=3 pps=2"))
        assertTrue(logs.contains("cluster: first frame rendered 16x8"))
    }

    @Test fun slicesBeforeAKeyframeAreDroppedAndOneKeyframeRequested() {
        val recovered = CountDownLatch(1)
        val recoveries = AtomicInteger()
        val screen = start()
        screen.setRecoveryHandler {
            recoveries.incrementAndGet()
            recovered.countDown()
        }

        screen.onFrame(nalUnit(SLICE_HEADER, 1))
        screen.onFrame(nalUnit(SLICE_HEADER, 2))
        screen.onFrame(nalUnit(IDR_HEADER, 3))
        surface.awaitFrame()

        assertTrue(recovered.await(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        // The second slice falls inside the one-second recovery interval.
        assertEquals(1, recoveries.get())
        assertEquals(1, fake.inputs.size)
        assertTrue(logs.contains("cluster: requesting keyframe (waiting for keyframe)"))
    }

    @Test fun rejectedDataRequestsAKeyframe() {
        val recovered = CountDownLatch(1)
        val screen = start(FakeDecoder(accept = false))
        screen.setRecoveryHandler { recovered.countDown() }

        screen.onFrame(nalUnit(IDR_HEADER, 3))

        assertTrue(recovered.await(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(logs.contains("cluster: requesting keyframe (decoder rejected data)"))
        assertEquals(0, surface.shown.get())
    }

    @Test fun aFullQueueDropsTheBacklogAndAsksForAKeyframe() {
        val release = CountDownLatch(1)
        val stuck = object : H264Decoder {
            override fun decode(annexB: ByteArray, onImage: (BufferedImage) -> Unit): Boolean {
                release.await(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                return true
            }

            override fun close() = Unit
        }
        val recovered = CountDownLatch(1)
        val screen = ScreenDecoder("cluster", 111, surface, logs::add, queueCapacity = 1) { stuck }.also { decoder = it }
        screen.setRecoveryHandler { recovered.countDown() }

        // One unit may be in the decoder and one in the queue; the third cannot wait.
        repeat(3) { screen.onFrame(nalUnit(IDR_HEADER, it)) }

        assertTrue(recovered.await(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(logs.contains("cluster: requesting keyframe (decode queue full)"))
        release.countDown()
    }

    @Test fun tornDownStreamClearsTheSurfaceAndReportsItsNextFirstFrame() {
        val screen = start()
        screen.onFrame(nalUnit(IDR_HEADER, 1))
        surface.awaitFrame()

        screen.setActive(false)
        screen.onFrame(nalUnit(IDR_HEADER, 2))
        surface.awaitFrame()

        assertEquals(1, surface.cleared.get())
        assertEquals(listOf("first frame rendered", "first frame rendered"), diagnostics)
        assertTrue(logs.contains("cluster: stream 111 active=false"))
    }
}
