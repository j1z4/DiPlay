package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.airplay.VideoCodec
import com.shilapi.xcertplay.media.MediaCodecSupport
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Renders the main CarPlay screen on Windows: Annex B access units are decoded by FFmpeg on a
 * dedicated thread and painted into [window]. Mirrors AndroidMediaSink's keyframe recovery and
 * first-frame reporting so DiPlay's session logic behaves the same.
 */
class DesktopMediaSink(
    private val window: VideoWindow,
    private val log: (String) -> Unit,
) : MediaSink, Closeable {
    private val queue = LinkedBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    private val audio = ConcurrentHashMap<AudioStreamId, DesktopAudioOutput>()
    @Volatile private var parameterSets: ByteArray = ByteArray(0)
    @Volatile private var recovery: () -> Unit = {}
    @Volatile private var diagnostic: (String) -> Unit = {}
    @Volatile private var running = true
    @Volatile private var firstFrameReported = false
    @Volatile private var awaitingKeyframe = true
    private var lastRecoveryNanos = 0L

    private val decodeThread = Thread(::decodeLoop, "desktop-video-decode").apply {
        isDaemon = true
        start()
    }

    override fun onVideoCodec(type: Int, codec: VideoCodec) {
        if (type != MAIN_SCREEN) return
        if (codec != VideoCodec.H264) log("video: unsupported codec $codec; only H.264 is configured")
    }

    override fun onVideoConfig(type: Int, codecData: ByteArray) {
        if (type != MAIN_SCREEN) return
        val (sps, pps) = MediaCodecSupport.avcParameterSets(codecData)
        parameterSets = START_CODE + sps + START_CODE + pps
        awaitingKeyframe = true
        log("video: H.264 parameter sets sps=${sps.size} pps=${pps.size}")
    }

    override fun onVideoFrame(type: Int, naluBytes: ByteArray) {
        if (type != MAIN_SCREEN) return
        val annexB = MediaCodecSupport.toAnnexB(naluBytes)
        if (annexB.isEmpty()) return
        if (!queue.offer(annexB)) {
            // Fell behind: drop the backlog and resume from a fresh keyframe rather than lag.
            queue.clear()
            awaitingKeyframe = true
            requestKeyframe("decode queue full")
        }
    }

    override fun setVideoRecoveryHandler(type: Int, handler: () -> Unit) {
        if (type == MAIN_SCREEN) recovery = handler
    }

    override fun setVideoDiagnosticHandler(type: Int, handler: (String) -> Unit) {
        if (type == MAIN_SCREEN) diagnostic = handler
    }

    override fun onScreenStreamActive(type: Int, active: Boolean) {
        if (type != MAIN_SCREEN) return
        log("video: main screen active=$active")
        if (!active) {
            queue.clear()
            firstFrameReported = false
            awaitingKeyframe = true
            window.clearVideo()
        }
    }

    override fun onAudioStarted(id: AudioStreamId, format: AudioFormat, firstSample: Int) {
        audio.put(id, DesktopAudioOutput(id.label(), format, log))?.close()
    }

    override fun onAudioRtp(id: AudioStreamId, format: AudioFormat, rtp: ByteArray, sample: Int) {
        audio.computeIfAbsent(id) { DesktopAudioOutput(it.label(), format, log) }.offer(rtp)
    }

    override fun onAudioStopped(id: AudioStreamId) {
        audio.remove(id)?.close()
    }

    override fun close() {
        running = false
        decodeThread.interrupt()
        audio.values.forEach(DesktopAudioOutput::close)
        audio.clear()
    }

    private fun AudioStreamId.label() = "$type-$audioType"

    private fun decodeLoop() {
        FfmpegH264Decoder().use { decoder ->
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

    private fun decodeUnit(decoder: FfmpegH264Decoder, unit: ByteArray) {
        val keyframe = MediaCodecSupport.isRandomAccess(unit, VideoCodec.H264)
        if (awaitingKeyframe && !keyframe) {
            requestKeyframe("waiting for keyframe")
            return
        }
        awaitingKeyframe = false
        val input = if (keyframe) parameterSets + unit else unit
        val accepted = decoder.decode(input) { image ->
            window.showFrame(image)
            if (!firstFrameReported) {
                firstFrameReported = true
                diagnostic("first frame rendered")
                log("video: first frame rendered ${image.width}x${image.height}")
            }
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
        log("video: requesting keyframe ($reason)")
        recovery()
    }

    private companion object {
        const val MAIN_SCREEN = 110
        const val QUEUE_CAPACITY = 90
        const val POLL_MILLIS = 250L
        const val RECOVERY_INTERVAL_NANOS = 1_000_000_000L
        val START_CODE = byteArrayOf(0, 0, 0, 1)
    }
}
