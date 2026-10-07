package com.shilapi.xcertplay.desktop

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_LOW_DELAY
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
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
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.Closeable
import java.io.IOException

/**
 * Low-latency H.264 decoding with FFmpeg, producing BGR images for Swing.
 * Not thread-safe: one decode thread owns an instance.
 */
class FfmpegH264Decoder : Closeable {
    private val context: AVCodecContext
    private val packet: AVPacket = av_packet_alloc()
    private val frame: AVFrame = av_frame_alloc()
    private var scaler: SwsContext? = null
    private var rgb: BytePointer? = null
    private val images = arrayOfNulls<BufferedImage>(IMAGE_RING)
    private var nextImage = 0

    init {
        val codec = avcodec_find_decoder(AV_CODEC_ID_H264) ?: throw IOException("FFmpeg has no H.264 decoder")
        context = avcodec_alloc_context3(codec)
        context.flags(context.flags() or AV_CODEC_FLAG_LOW_DELAY)
        context.thread_type(FF_THREAD_SLICE)
        context.thread_count(DECODE_THREADS)
        val opened = avcodec_open2(context, codec, null as PointerPointer<*>?)
        if (opened < 0) throw IOException("FFmpeg H.264 open failed: $opened")
    }

    /**
     * Decodes one Annex B access unit and hands each finished picture to [onImage].
     * Returns false when FFmpeg rejected the data, so the caller can request a keyframe.
     */
    fun decode(annexB: ByteArray, onImage: (BufferedImage) -> Unit): Boolean {
        val data = BytePointer(*annexB)
        try {
            packet.data(data)
            packet.size(annexB.size)
            if (avcodec_send_packet(context, packet) < 0) return false
        } finally {
            packet.data(null as BytePointer?)
            packet.size(0)
            data.deallocate()
        }
        while (true) {
            val received = avcodec_receive_frame(context, frame)
            if (received == AVERROR_EAGAIN() || received == AVERROR_EOF) return true
            if (received < 0) return false
            onImage(toImage())
        }
    }

    private fun toImage(): BufferedImage {
        val width = frame.width()
        val height = frame.height()
        scaler = sws_getCachedContext(
            scaler, width, height, frame.format(), width, height, AV_PIX_FMT_BGR24,
            SWS_BILINEAR, null, null, null as DoublePointer?,
        )
        val stride = width * BYTES_PER_PIXEL
        val buffer = rgb?.takeIf { it.capacity() >= stride.toLong() * height }
            ?: BytePointer(stride.toLong() * height).also { rgb?.deallocate(); rgb = it }
        sws_scale(
            scaler, frame.data(), frame.linesize(), 0, height,
            PointerPointer<BytePointer>(1).put(buffer), IntPointer(1).put(stride),
        )
        val image = images[nextImage]?.takeIf { it.width == width && it.height == height }
            ?: BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR).also { images[nextImage] = it }
        nextImage = (nextImage + 1) % IMAGE_RING
        buffer.position(0).get((image.raster.dataBuffer as DataBufferByte).data)
        return image
    }

    override fun close() {
        av_frame_free(frame)
        av_packet_free(packet)
        avcodec_free_context(context)
        scaler?.let(::sws_freeContext)
        rgb?.deallocate()
    }

    private companion object {
        const val DECODE_THREADS = 2
        /** avcodec.h FF_THREAD_SLICE; slice threading adds no frame of latency, unlike frame threading. */
        const val FF_THREAD_SLICE = 2
        const val BYTES_PER_PIXEL = 3
        /** Images are reused round-robin; the window only ever paints the newest one. */
        const val IMAGE_RING = 3
    }
}
