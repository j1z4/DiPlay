package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.MicrophoneCounters
import com.shilapi.xcertplay.airplay.MicrophonePacketizer
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioFormat as PcmFormat

/**
 * Captures the default Windows microphone for one CarPlay input stream (call or Siri) and sends
 * it to the iPhone as sealed RTP, as DiPlay's Android MicrophoneUplink does. With [capture] off
 * the stream carries silence at the same rate: the iPhone still routes car audio to a receiver
 * with a microphone, but nothing is recorded on this PC.
 */
class DesktopMicrophone(
    private val config: MicrophoneConfig,
    private val log: (String) -> Unit,
    private val capture: Boolean = true,
    private val opusBitrate: Int = DEFAULT_OPUS_BITRATE,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val captureRate = if (config.codec == AudioCodecKind.OPUS) FfmpegOpusEncoder.SAMPLE_RATE else config.sampleRate
    /** How long one frame of audio lasts; silence is paced by it. */
    private val frameNanos = config.samplesPerPacket * NANOS_PER_SECOND / captureRate
    private var line: TargetDataLine? = null
    private var socket: DatagramSocket? = null
    private var encoder: FfmpegOpusEncoder? = null
    private var worker: Thread? = null

    /** Opens the microphone (or the silence source) and starts streaming; false when no capture device is usable. */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            encoder = if (config.codec == AudioCodecKind.OPUS) {
                FfmpegOpusEncoder(config.channels, config.bitrate ?: opusBitrate)
            } else null
            line = if (capture) openLine() else null
            socket = DatagramSocket()
            worker = Thread(::stream, "desktop-mic-${config.audioType}").apply {
                isDaemon = true
                start()
            }
            val source = if (capture) "capturing" else "sending silence"
            log("microphone ${config.audioType}: $source ${config.codec} ${captureRate}Hz -> ${config.host.hostAddress}:${config.port}")
            true
        } catch (error: Exception) {
            log("microphone ${config.audioType} unavailable: ${error.message}")
            close()
            false
        }
    }

    private fun openLine(): TargetDataLine {
        val format = PcmFormat(captureRate.toFloat(), BITS, config.channels, true, false)
        return AudioSystem.getTargetDataLine(format).apply {
            open(format, config.frameBytes * BUFFER_FRAMES)
            start()
        }
    }

    private fun stream() {
        val output = socket ?: return
        val source = line
        val frame = ByteArray(config.frameBytes)
        val counters = MicrophoneCounters()
        var dueNanos = System.nanoTime()
        try {
            while (running.get()) {
                if (source != null) {
                    if (!fill(source, frame)) return
                } else {
                    dueNanos += frameNanos
                    awaitSilence(dueNanos)
                }
                if (running.get()) send(output, counters, frame)
            }
        } catch (error: Exception) {
            if (running.get()) log("microphone ${config.audioType} stopped: ${error.message}")
        }
    }

    /** Reads one whole frame from the microphone; false once the line has ended or capture stopped. */
    private fun fill(source: TargetDataLine, frame: ByteArray): Boolean {
        var filled = 0
        while (filled < frame.size && running.get()) {
            val count = source.read(frame, filled, frame.size - filled)
            if (count < 0) return false
            filled += count
        }
        return filled == frame.size
    }

    /** Waits until the next frame of silence is due, so silence flows at the microphone's real rate. */
    private fun awaitSilence(dueNanos: Long) {
        val wait = dueNanos - System.nanoTime()
        if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait)
    }

    private fun send(output: DatagramSocket, counters: MicrophoneCounters, frame: ByteArray) {
        val bodies = encoder?.encode(frame) ?: listOf(MicrophonePacketizer.toWirePcm(frame))
        for (body in bodies) {
            val packet = MicrophonePacketizer.sealPacket(
                key = config.key,
                payloadType = config.payloadType,
                counters = counters,
                body = body,
                samples = config.rtpSamplesPerPacket,
            )
            output.send(DatagramPacket(packet, packet.size, config.host, config.port))
        }
    }

    /** Stops capture, waits for the worker to leave the encoder, then frees native resources. */
    override fun close() {
        running.set(false)
        line?.let { runCatching { it.stop(); it.close() } }
        socket?.close()
        worker?.let { thread ->
            if (thread !== Thread.currentThread()) runCatching { thread.join(CLOSE_JOIN_MILLIS) }
        }
        worker = null
        line = null
        socket = null
        encoder?.close()
        encoder = null
    }

    private companion object {
        const val BITS = 16
        const val BUFFER_FRAMES = 4
        const val DEFAULT_OPUS_BITRATE = 48_000
        const val CLOSE_JOIN_MILLIS = 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
