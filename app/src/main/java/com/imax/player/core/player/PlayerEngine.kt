package com.imax.player.core.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Display / Aspect Ratio modes available to the user.
 */
enum class AspectRatioMode(val label: String) {
    AUTO("Auto"),
    FIT("Fit to Screen"),
    FILL("Fill Screen"),
    ZOOM("Zoom / Crop"),
    STRETCH("Stretch"),
    ORIGINAL("Original"),
    FORCE_16_9("Force 16:9"),
    FORCE_4_3("Force 4:3")
}

/**
 * Video quality preference mode.
 */
enum class VideoQualityMode(val label: String) {
    AUTO("Auto"),
    BEST("Best Quality"),
    BALANCED("Balanced"),
    DATA_SAVER("Data Saver")
}

/**
 * Live stream latency preference.
 */
enum class LiveLatencyMode(val label: String) {
    LOW_LATENCY("Low Latency"),
    BALANCED("Balanced"),
    STABLE("Stable")
}

/**
 * Playback lifecycle states.
 */
enum class PlaybackState {
    IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR, STOPPED
}

/**
 * Represents a selectable audio or subtitle track.
 */
data class TrackInfo(
    val index: Int,
    val name: String,
    val language: String = "",
    val isSelected: Boolean = false
)

/**
 * Represents a selectable video quality track.
 */
data class QualityOption(
    val index: Int,
    val label: String,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    val isSelected: Boolean = false,
    val isAdaptive: Boolean = false
)

/**
 * Full player state exposed to the UI.
 */
data class PlayerState(
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPosition: Long = 0,
    val playbackSpeed: Float = 1f,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val selectedSubtitleTrack: Int = -1,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val errorMessage: String? = null,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    // Quality fields
    val availableQualities: List<QualityOption> = emptyList(),
    val videoQualityMode: VideoQualityMode = VideoQualityMode.AUTO,
    val currentVideoResolution: String = "",
    val currentVideoBitrate: String = "",
    val currentVideoCodec: String = "",
    val currentVideoFps: String = "",
    val isAdaptiveStream: Boolean = false
)

/**
 * Abstraction for video playback engines (ExoPlayer, VLC, etc.)
 */
interface PlayerEngine {
    val state: StateFlow<PlayerState>
    val engineName: String

    fun initialize()
    fun release()
    fun play(url: String, startPosition: Long = 0)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(position: Long)
    fun seekForward(ms: Long)
    fun seekBackward(ms: Long)
    fun setPlaybackSpeed(speed: Float)
    fun selectAudioTrack(index: Int)
    fun selectSubtitleTrack(index: Int)
    fun disableSubtitles()
    fun setAspectRatio(mode: AspectRatioMode)
    fun setVideoQualityMode(mode: VideoQualityMode) {}
    fun selectVideoTrack(index: Int) {}
    fun getView(): Any?

    /**
     * Whether this engine is available on the current device.
     * Returns false if native libraries can't be loaded, etc.
     */
    fun isAvailable(): Boolean = true
}
