package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.media.MediaCodecSupport
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.AudioFormat as PcmFormat

/**
 * Plays one CarPlay audio stream (media, navigation, Siri...) on its own javax.sound line with a
 * [bufferMillis] output buffer; Windows mixes concurrent streams. RTP payloads are decoded on a
 * dedicated worker thread.
 */
class DesktopAudioOutput(
    private val name: String,
    private val format: AudioFormat,
    private val log: (String) -> Unit,
    private val bufferMillis: Int = DEFAULT_BUFFER_MILLIS,
) : Closeable {
    private val packets = LinkedBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    @Volatile private var running = true
    private val worker = Thread(::run, "desktop-audio-$name").apply {
        isDaemon = true
        start()
    }

    /** Queues one decrypted RTP packet; drops the oldest when the worker falls behind. */
    fun offer(rtp: ByteArray) {
        if (rtp.size <= RTP_HEADER_BYTES) return
        while (!packets.offer(rtp)) packets.poll()
    }

    override fun close() {
        running = false
        worker.interrupt()
    }

    private fun run() {
        val line = openLine() ?: return
        val decoder = if (format.codec == AudioCodecKind.LPCM) null else
            runCatching { FfmpegAudioDecoder(format.codec, format.sampleRate, format.channels) }
                .onFailure { log("audio $name: decoder unavailable: ${it.message}") }
                .getOrNull() ?: return line.close()
        log("audio $name: playing ${format.codec} ${format.sampleRate}Hz ${format.channels}ch buffer ${bufferMillis}ms")
        try {
            while (running) {
                val rtp = packets.poll(POLL_MILLIS, TimeUnit.MILLISECONDS) ?: continue
                val pcm = toPcm(rtp, decoder)
                if (pcm.isNotEmpty()) line.write(pcm, 0, pcm.size)
            }
        } catch (_: InterruptedException) {
            // Stream stopped.
        } finally {
            line.drain()
            line.close()
            decoder?.close()
        }
    }

    private fun toPcm(rtp: ByteArray, decoder: FfmpegAudioDecoder?): ByteArray {
        val payload = rtp.copyOfRange(RTP_HEADER_BYTES, rtp.size)
        return when (format.codec) {
            AudioCodecKind.LPCM -> swapBytes(payload)
            AudioCodecKind.AAC_LC ->
                decoder?.decode(MediaCodecSupport.adtsFrame(payload, format.sampleRate, format.channels)) ?: EMPTY
            AudioCodecKind.OPUS -> decoder?.decode(payload) ?: EMPTY
        }
    }

    private fun openLine(): SourceDataLine? = runCatching {
        val pcm = PcmFormat(format.sampleRate.toFloat(), BITS, format.channels, true, false)
        val bufferBytes = format.sampleRate * format.channels * (BITS / 8) * bufferMillis / 1000
        AudioSystem.getSourceDataLine(pcm).apply {
            open(pcm, bufferBytes)
            start()
        }
    }.onFailure { log("audio $name: no output line: ${it.message}") }.getOrNull()

    /** CarPlay LPCM is big-endian S16; javax.sound is opened little-endian. */
    private fun swapBytes(source: ByteArray): ByteArray {
        val out = ByteArray(source.size and 1.inv())
        var index = 0
        while (index + 1 < source.size) {
            out[index] = source[index + 1]
            out[index + 1] = source[index]
            index += 2
        }
        return out
    }

    private companion object {
        const val RTP_HEADER_BYTES = 12
        const val QUEUE_CAPACITY = 200
        const val POLL_MILLIS = 100L
        const val BITS = 16
        const val DEFAULT_BUFFER_MILLIS = 120
        val EMPTY = ByteArray(0)
    }
}
