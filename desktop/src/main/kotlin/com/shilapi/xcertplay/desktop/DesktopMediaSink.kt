package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.MediaSink
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.VideoCodec
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders CarPlay on Windows: the main screen (stream 110) and, when a cluster window is given,
 * the instrument cluster (stream 111) each get a [ScreenDecoder] painting into their window, while
 * audio plays through [DesktopAudioOutput] and microphone uplinks come from [DesktopMicrophone].
 */
class DesktopMediaSink(
    window: VideoSurface,
    clusterWindow: VideoSurface?,
    private val log: (String) -> Unit,
    newDecoder: () -> H264Decoder = { FfmpegH264Decoder() },
) : MediaSink, Closeable {
    private val screens: Map<Int, ScreenDecoder> = buildMap {
        put(MAIN_SCREEN, ScreenDecoder("video", MAIN_SCREEN, window, log, newDecoder))
        if (clusterWindow != null) put(ALT_SCREEN, ScreenDecoder("cluster", ALT_SCREEN, clusterWindow, log, newDecoder))
    }
    private val audio = ConcurrentHashMap<AudioStreamId, DesktopAudioOutput>()
    private val microphones = ConcurrentHashMap<AudioStreamId, DesktopMicrophone>()

    override fun onVideoCodec(type: Int, codec: VideoCodec) {
        screens[type]?.onCodec(codec)
    }

    override fun onVideoConfig(type: Int, codecData: ByteArray) {
        screens[type]?.onConfig(codecData)
    }

    override fun onVideoFrame(type: Int, naluBytes: ByteArray) {
        screens[type]?.onFrame(naluBytes)
    }

    override fun setVideoRecoveryHandler(type: Int, handler: () -> Unit) {
        screens[type]?.setRecoveryHandler(handler)
    }

    override fun setVideoDiagnosticHandler(type: Int, handler: (String) -> Unit) {
        screens[type]?.setDiagnosticHandler(handler)
    }

    override fun onScreenStreamActive(type: Int, active: Boolean) {
        val screen = screens[type]
        if (screen == null) {
            if (type == ALT_SCREEN && active) log("cluster: iPhone set up stream 111 but the cluster display is off; ignoring it")
            return
        }
        // The iPhone sets up stream 111 only after taking the altScreen feature SETUP offered.
        if (type == ALT_SCREEN && active) log("cluster: altScreen negotiated; iPhone set up stream 111")
        screen.setActive(active)
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

    override fun onMicrophoneStarted(id: AudioStreamId, config: MicrophoneConfig) {
        // Runs on the downlink thread; a missing microphone must not stop playback.
        val microphone = microphones.computeIfAbsent(id) { DesktopMicrophone(config, log) }
        if (!microphone.start()) microphones.remove(id, microphone)
    }

    override fun onMicrophoneStopped(id: AudioStreamId) {
        microphones.remove(id)?.close()
    }

    override fun close() {
        screens.values.forEach(ScreenDecoder::close)
        audio.values.forEach(DesktopAudioOutput::close)
        audio.clear()
        microphones.values.forEach(DesktopMicrophone::close)
        microphones.clear()
    }

    private fun AudioStreamId.label() = "$type-$audioType"

    private companion object {
        const val MAIN_SCREEN = 110
        const val ALT_SCREEN = 111
    }
}
