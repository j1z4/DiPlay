package com.shilapi.xcertplay

import com.shilapi.xcertplay.DirectVideoSurface.DecoderSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class DirectVideoSurfaceTest {
    @Test fun ffmpegDefaultDecoderRequiresTheDirectSurfaceEvenWhenReportedAsHardware() {
        // Raspberry Pi 5 on emteria lists c2.ffmpeg.h264.decoder first with the hardware attribute.
        assertTrue(DirectVideoSurface.required(listOf(
            DecoderSummary("c2.ffmpeg.h264.decoder", "video/avc", hardware = true),
            DecoderSummary("c2.android.avc.decoder", "video/avc", hardware = false),
            DecoderSummary("c2.ffmpeg.hevc.decoder", "video/hevc", hardware = true),
        )))
    }

    @Test fun softwareDefaultDecoderRequiresTheDirectSurface() {
        assertTrue(DirectVideoSurface.required(listOf(
            DecoderSummary("c2.android.avc.decoder", "video/avc", hardware = false),
        )))
    }

    @Test fun hardwareDefaultDecoderKeepsTheTextureView() {
        assertFalse(DirectVideoSurface.required(listOf(
            DecoderSummary("c2.qti.avc.decoder", "video/avc", hardware = true),
            DecoderSummary("c2.android.avc.decoder", "video/avc", hardware = false),
        )))
    }

    @Test fun onlyTheFirstAvcDecoderDecides() {
        // A later hardware entry is not what createDecoderByType selects.
        assertTrue(DirectVideoSurface.required(listOf(
            DecoderSummary("c2.qti.hevc.decoder", "video/hevc", hardware = true),
            DecoderSummary("c2.android.avc.decoder", "video/avc", hardware = false),
            DecoderSummary("c2.qti.avc.decoder", "video/avc", hardware = true),
        )))
    }

    @Test fun noAvcDecoderRequiresTheDirectSurface() {
        assertTrue(DirectVideoSurface.required(emptyList()))
    }
}
