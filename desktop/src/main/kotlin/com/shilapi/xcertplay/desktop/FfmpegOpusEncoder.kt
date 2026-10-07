package com.shilapi.xcertplay.desktop

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_copy
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable
import org.bytedeco.javacpp.PointerPointer
import java.io.Closeable
import java.io.IOException

/**
 * Encodes 20 ms frames of 48 kHz S16LE PCM into Opus packets with FFmpeg's libopus encoder,
 * the format CarPlay negotiates for wireless call and Siri microphones.
 */
class FfmpegOpusEncoder(private val channels: Int, bitrate: Int) : Closeable {
    private val context: AVCodecContext
    private val packet: AVPacket = av_packet_alloc()
    private val frame: AVFrame = av_frame_alloc()
    private var nextPts = 0L

    init {
        val codec = avcodec_find_encoder_by_name("libopus") ?: avcodec_find_encoder(AV_CODEC_ID_OPUS)
            ?: throw IOException("FFmpeg has no Opus encoder")
        context = avcodec_alloc_context3(codec)
        context.sample_rate(SAMPLE_RATE)
        context.sample_fmt(AV_SAMPLE_FMT_S16)
        context.bit_rate(bitrate.toLong())
        context.frame_size(SAMPLES_PER_FRAME)
        av_channel_layout_default(context.ch_layout(), channels)
        val opened = avcodec_open2(context, codec, null as PointerPointer<*>?)
        if (opened < 0) throw IOException("Opus encoder open failed: $opened")
        frame.nb_samples(SAMPLES_PER_FRAME)
        frame.format(AV_SAMPLE_FMT_S16)
        frame.sample_rate(SAMPLE_RATE)
        av_channel_layout_copy(frame.ch_layout(), context.ch_layout())
        if (av_frame_get_buffer(frame, 0) < 0) throw IOException("Opus frame allocation failed")
    }

    /** Encodes one 20 ms PCM frame; returns zero or more Opus packets. */
    fun encode(pcm: ByteArray): List<ByteArray> {
        require(pcm.size == SAMPLES_PER_FRAME * channels * 2) { "Opus input must be one 20 ms frame" }
        // The encoder may still reference the previous buffer.
        if (av_frame_make_writable(frame) < 0) return emptyList()
        frame.data(0).position(0).put(*pcm)
        frame.pts(nextPts)
        nextPts += SAMPLES_PER_FRAME
        if (avcodec_send_frame(context, frame) < 0) return emptyList()
        val packets = mutableListOf<ByteArray>()
        while (true) {
            val received = avcodec_receive_packet(context, packet)
            if (received == AVERROR_EAGAIN() || received < 0) break
            val bytes = ByteArray(packet.size())
            packet.data().position(0).get(bytes)
            packets += bytes
            av_packet_unref(packet)
        }
        return packets
    }

    override fun close() {
        av_frame_free(frame)
        av_packet_free(packet)
        avcodec_free_context(context)
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val SAMPLES_PER_FRAME = 960
    }
}
