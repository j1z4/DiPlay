package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.MicrophoneCounters
import com.shilapi.xcertplay.airplay.MicrophonePacketizer
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioFormat as PcmFormat

/**
 * Captures the default Windows microphone for one CarPlay input stream (call or Siri) and sends
 * it to the iPhone as sealed RTP, as DiPlay's Android MicrophoneUplink does.
 */
class DesktopMicrophone(
    private val config: MicrophoneConfig,
    private val log: (String) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var line: TargetDataLine? = null
    private var socket: DatagramSocket? = null
    private var encoder: FfmpegOpusEncoder? = null
    private var worker: Thread? = null

    /** Opens the microphone and starts streaming; false when no capture device is usable. */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            val captureRate = if (config.codec == AudioCodecKind.OPUS) FfmpegOpusEncoder.SAMPLE_RATE else config.sampleRate
            val format = PcmFormat(captureRate.toFloat(), BITS, config.channels, true, false)
            encoder = if (config.codec == AudioCodecKind.OPUS) {
                FfmpegOpusEncoder(config.channels, config.bitrate ?: DEFAULT_OPUS_BITRATE)
            } else null
            line = AudioSystem.getTargetDataLine(format).apply {
                open(format, config.frameBytes * BUFFER_FRAMES)
                start()
            }
            socket = DatagramSocket()
            worker = Thread(::capture, "desktop-mic-${config.audioType}").apply {
                isDaemon = true
                start()
            }
            log("microphone ${config.audioType}: ${config.codec} ${captureRate}Hz -> ${config.host.hostAddress}:${config.port}")
            true
        } catch (error: Exception) {
            log("microphone ${config.audioType} unavailable: ${error.message}")
            close()
            false
        }
    }

    private fun capture() {
        val capture = line ?: return
        val output = socket ?: return
        val frame = ByteArray(config.frameBytes)
        val counters = MicrophoneCounters()
        try {
            while (running.get()) {
                var filled = 0
                while (filled < frame.size && running.get()) {
                    val count = capture.read(frame, filled, frame.size - filled)
                    if (count < 0) return
                    filled += count
                }
                if (filled == frame.size && running.get()) send(output, counters, frame)
            }
        } catch (error: Exception) {
            if (running.get()) log("microphone ${config.audioType} stopped: ${error.message}")
        }
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
    }
}
