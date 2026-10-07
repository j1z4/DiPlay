package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AudioCodecKind
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16P
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerPointer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException

/**
 * Decodes CarPlay AAC-LC (as ADTS frames) or Opus packets with FFmpeg into interleaved
 * 16-bit little-endian PCM at the stream's sample rate and channel count.
 */
class FfmpegAudioDecoder(codecKind: AudioCodecKind, sampleRate: Int, private val channels: Int) : Closeable {
    private val context: AVCodecContext
    private val packet: AVPacket = av_packet_alloc()
    private val frame: AVFrame = av_frame_alloc()

    init {
        require(codecKind != AudioCodecKind.LPCM) { "LPCM needs no decoder" }
        val codecId = if (codecKind == AudioCodecKind.AAC_LC) AV_CODEC_ID_AAC else AV_CODEC_ID_OPUS
        val codec = avcodec_find_decoder(codecId) ?: throw IOException("FFmpeg has no $codecKind decoder")
        context = avcodec_alloc_context3(codec)
        context.sample_rate(sampleRate)
        av_channel_layout_default(context.ch_layout(), channels)
        val opened = avcodec_open2(context, codec, null as PointerPointer<*>?)
        if (opened < 0) throw IOException("FFmpeg $codecKind open failed: $opened")
    }

    /** Returns the PCM decoded from [encoded]; empty when FFmpeg needs more input or rejects it. */
    fun decode(encoded: ByteArray): ByteArray {
        val data = BytePointer(*encoded)
        try {
            packet.data(data)
            packet.size(encoded.size)
            if (avcodec_send_packet(context, packet) < 0) return EMPTY
        } finally {
            packet.data(null as BytePointer?)
            packet.size(0)
            data.deallocate()
        }
        val pcm = ByteArrayOutputStream()
        while (true) {
            val received = avcodec_receive_frame(context, frame)
            if (received == AVERROR_EAGAIN() || received == AVERROR_EOF || received < 0) break
            pcm.write(interleaveS16(frame))
        }
        return pcm.toByteArray()
    }

    /** Converts one decoded frame to interleaved S16LE with exactly [channels] channels. */
    private fun interleaveS16(decoded: AVFrame): ByteArray {
        val samples = decoded.nb_samples()
        val sourceChannels = decoded.ch_layout().nb_channels().coerceAtLeast(1)
        val format = decoded.format()
        val out = ByteArray(samples * channels * 2)
        for (index in 0 until samples) {
            for (channel in 0 until channels) {
                val source = channel.coerceAtMost(sourceChannels - 1)
                val value = sample(decoded, format, source, index, sourceChannels)
                val offset = (index * channels + channel) * 2
                out[offset] = value.toByte()
                out[offset + 1] = (value shr 8).toByte()
            }
        }
        return out
    }

    private fun sample(decoded: AVFrame, format: Int, channel: Int, index: Int, sourceChannels: Int): Int =
        when (format) {
            AV_SAMPLE_FMT_FLTP -> floatToS16(decoded.data(channel).getFloat(index * 4L))
            AV_SAMPLE_FMT_FLT -> floatToS16(decoded.data(0).getFloat((index * sourceChannels + channel) * 4L))
            AV_SAMPLE_FMT_S16P -> decoded.data(channel).getShort(index * 2L).toInt()
            AV_SAMPLE_FMT_S16 -> decoded.data(0).getShort((index * sourceChannels + channel) * 2L).toInt()
            else -> 0
        }

    private fun floatToS16(value: Float): Int = (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()

    override fun close() {
        av_frame_free(frame)
        av_packet_free(packet)
        avcodec_free_context(context)
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
