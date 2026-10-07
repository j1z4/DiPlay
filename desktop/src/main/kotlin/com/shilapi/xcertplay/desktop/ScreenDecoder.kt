package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.VideoCodec
import com.shilapi.xcertplay.media.MediaCodecSupport
import java.awt.image.BufferedImage
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Where decoded pictures go: a [VideoWindow] on screen, a stub in tests. */
interface VideoSurface {
    fun showFrame(next: BufferedImage)
    fun clearVideo()
}

/** One H.264 decoder owned by a decode thread: [FfmpegH264Decoder] in the app, a fake in tests. */
interface H264Decoder : Closeable {
    /** Decodes one Annex B access unit; false when the decoder rejected the data. */
    fun decode(annexB: ByteArray, onImage: (BufferedImage) -> Unit): Boolean
}

/**
 * Decoding state for one CarPlay screen stream (main screen 110 or cluster 111): the access-unit
 * queue, the H.264 parameter sets, keyframe recovery and first-frame reporting, with a decode
 * thread painting into one [surface]. Mirrors AndroidMediaSink's keyframe recovery so DiPlay's
 * session logic behaves the same. [label] prefixes the log lines ("video", "cluster"); once
 * [queueCapacity] access units wait for the decoder, the backlog is dropped for a fresh keyframe.
 */
class ScreenDecoder(
    private val label: String,
    private val streamType: Int,
    private val surface: VideoSurface,
    private val log: (String) -> Unit,
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val newDecoder: () -> H264Decoder = { FfmpegH264Decoder() },
) : Closeable {
    private val queue = LinkedBlockingQueue<ByteArray>(queueCapacity)
    @Volatile private var parameterSets: ByteArray = ByteArray(0)
    @Volatile private var recovery: () -> Unit = {}
    @Volatile private var diagnostic: (String) -> Unit = {}
    @Volatile private var running = true
    @Volatile private var firstFrameReported = false
    @Volatile private var awaitingKeyframe = true
    private var lastRecoveryNanos = 0L

    private val decodeThread = Thread(::decodeLoop, "$label-decode").apply {
        isDaemon = true
        start()
    }

    fun onCodec(codec: VideoCodec) {
        if (codec != VideoCodec.H264) log("$label: unsupported codec $codec; only H.264 is configured")
    }

    fun onConfig(codecData: ByteArray) {
        val (sps, pps) = MediaCodecSupport.avcParameterSets(codecData)
        parameterSets = START_CODE + sps + START_CODE + pps
        awaitingKeyframe = true
        log("$label: H.264 parameter sets sps=${sps.size} pps=${pps.size}")
    }

    fun onFrame(naluBytes: ByteArray) {
        val annexB = MediaCodecSupport.toAnnexB(naluBytes)
        if (annexB.isEmpty()) return
        if (!queue.offer(annexB)) {
            // Fell behind: drop the backlog and resume from a fresh keyframe rather than lag.
            queue.clear()
            awaitingKeyframe = true
            requestKeyframe("decode queue full")
        }
    }

    fun setRecoveryHandler(handler: () -> Unit) {
        recovery = handler
    }

    fun setDiagnosticHandler(handler: (String) -> Unit) {
        diagnostic = handler
    }

    /** Stream set up or torn down; a stream that comes back reports its first frame again. */
    fun setActive(active: Boolean) {
        log("$label: stream $streamType active=$active")
        if (active) return
        queue.clear()
        firstFrameReported = false
        awaitingKeyframe = true
        surface.clearVideo()
    }

    override fun close() {
        running = false
        decodeThread.interrupt()
    }

    private fun decodeLoop() {
        val decoder = try {
            newDecoder()
        } catch (error: Exception) {
            log("$label: no H.264 decoder: ${error.message}")
            return
        }
        decoder.use {
            while (running) {
                val unit = try {
                    queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS) ?: continue
                } catch (_: InterruptedException) {
                    return
                }
                decodeUnit(decoder, unit)
            }
        }
    }

    private fun decodeUnit(decoder: H264Decoder, unit: ByteArray) {
        val keyframe = MediaCodecSupport.isRandomAccess(unit, VideoCodec.H264)
        if (awaitingKeyframe && !keyframe) {
            requestKeyframe("waiting for keyframe")
            return
        }
        awaitingKeyframe = false
        val input = if (keyframe) parameterSets + unit else unit
        val accepted = decoder.decode(input) { image ->
            if (!firstFrameReported) {
                firstFrameReported = true
                diagnostic("first frame rendered")
                log("$label: first frame rendered ${image.width}x${image.height}")
            }
            surface.showFrame(image)
        }
        if (!accepted) {
            awaitingKeyframe = true
            requestKeyframe("decoder rejected data")
        }
    }

    /** Asks the iPhone for a keyframe, at most once per [RECOVERY_INTERVAL_NANOS]. */
    private fun requestKeyframe(reason: String) {
        val now = System.nanoTime()
        synchronized(this) {
            if (now - lastRecoveryNanos < RECOVERY_INTERVAL_NANOS) return
            lastRecoveryNanos = now
        }
        log("$label: requesting keyframe ($reason)")
        recovery()
    }

    private companion object {
        const val DEFAULT_QUEUE_CAPACITY = 90
        const val POLL_MILLIS = 250L
        const val RECOVERY_INTERVAL_NANOS = 1_000_000_000L
        val START_CODE = byteArrayOf(0, 0, 0, 1)
    }
}
