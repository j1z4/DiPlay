package com.shilapi.xcertplay.desktop

import java.awt.image.BufferedImage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** How long a test waits for a decode thread. */
const val DECODE_TIMEOUT_SECONDS = 5L

/** H.264 NAL header bytes: an IDR slice (type 5) and a non-IDR slice (type 1). */
const val IDR_HEADER = 0x65
const val SLICE_HEADER = 0x41
val ANNEX_B_START_CODE = byteArrayOf(0, 0, 0, 1)

/** Records each access unit and answers every one with a 16x8 picture, or rejects them all. */
class FakeDecoder(private val accept: Boolean = true) : H264Decoder {
    val inputs = CopyOnWriteArrayList<ByteArray>()

    override fun decode(annexB: ByteArray, onImage: (BufferedImage) -> Unit): Boolean {
        inputs += annexB
        if (!accept) return false
        onImage(BufferedImage(16, 8, BufferedImage.TYPE_3BYTE_BGR))
        return true
    }

    override fun close() = Unit
}

/** Collects rendered frames so a test can wait for the decode thread instead of sleeping. */
class FakeSurface : VideoSurface {
    private val frames = LinkedBlockingQueue<BufferedImage>()
    val shown = AtomicInteger()
    val cleared = AtomicInteger()

    override fun showFrame(next: BufferedImage) {
        shown.incrementAndGet()
        frames.add(next)
    }

    override fun clearVideo() {
        cleared.incrementAndGet()
    }

    fun awaitFrame(): BufferedImage =
        frames.poll(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: error("no frame rendered")
}

/** An AVCDecoderConfigurationRecord carrying one SPS and one PPS. */
fun avcRecord(sps: ByteArray, pps: ByteArray): ByteArray =
    byteArrayOf(1, 0x42, 0, 0x1e, 0xff.toByte(), 0xe1.toByte(), 0, sps.size.toByte()) + sps +
        byteArrayOf(1, 0, pps.size.toByte()) + pps

/** One NAL unit, length-prefixed as the screen stream delivers it. */
fun nalUnit(vararg payload: Int): ByteArray =
    byteArrayOf(0, 0, 0, payload.size.toByte()) + ByteArray(payload.size) { payload[it].toByte() }
