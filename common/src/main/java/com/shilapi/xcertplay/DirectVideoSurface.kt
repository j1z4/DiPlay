package com.shilapi.xcertplay

import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Chooses between the default TextureView and a SurfaceView for the main CarPlay picture.
 *
 * A TextureView hands each decoded buffer to HWUI as a GPU texture. Some GPU drivers cannot import
 * the buffers a CPU decoder produces: Raspberry Pi 5 on emteria aborts RenderThread with
 * "Invalid GrBackendTexture" right after the first frame. Its default H.264 decoder is
 * c2.ffmpeg.h264.decoder, which reports itself as hardware accelerated, so the FFmpeg-backed
 * Codec2 family is matched by name. Those heads render through a SurfaceView instead, which
 * SurfaceFlinger composites directly.
 */
object DirectVideoSurface {
    data class DecoderSummary(val name: String, val mime: String, val hardware: Boolean)

    private const val FFMPEG_CODEC_PREFIX = "c2.ffmpeg."

    /** [decoders] in MediaCodecList order; the first AVC entry is what createDecoderByType picks. */
    fun required(decoders: List<DecoderSummary>): Boolean {
        val default = decoders.firstOrNull { it.mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
            ?: return true
        return !default.hardware || default.name.startsWith(FFMPEG_CODEC_PREFIX, ignoreCase = true)
    }

    fun required(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val decoders = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder }
                .flatMap { info ->
                    info.supportedTypes.map { DecoderSummary(info.name, it, info.isHardwareAccelerated) }
                }
        }.getOrNull() ?: return false
        return required(decoders)
    }
}
