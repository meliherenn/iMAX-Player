package com.imax.player.core.player

import android.content.Context
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.imax.player.core.common.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@OptIn(UnstableApi::class)
class ExoPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : PlayerEngine {

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val engineName: String = "ExoPlayer"

    private var scope: CoroutineScope? = null
    private var progressJob: Job? = null

    override fun initialize() {
        if (player != null) return

        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                Constants.MIN_BUFFER_MS,
                Constants.MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) startProgressTracking() else stopProgressTracking()
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.e(error, "ExoPlayer error: ${error.errorCodeName}")
                _state.value = _state.value.copy(
                    playbackState = PlaybackState.ERROR,
                    errorMessage = error.localizedMessage ?: "Playback error"
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTrackInfo(tracks)
                updateQualityInfo(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _state.value = _state.value.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                    currentVideoResolution = "${videoSize.width}x${videoSize.height}"
                )
            }
        })
    }

    override fun release() {
        stopProgressTracking()
        scope?.cancel()
        scope = null
        player?.release()
        player = null
        trackSelector = null
        _state.value = PlayerState()
    }

    override fun play(url: String, startPosition: Long) {
        initialize()
        player?.let { p ->
            val uri = android.net.Uri.parse(url)
            val mediaItem = MediaItem.fromUri(uri)
            p.setMediaItem(mediaItem)
            p.prepare()
            if (startPosition > 0) p.seekTo(startPosition)
            p.playWhenReady = true
        }
    }

    override fun pause() { player?.pause() }
    override fun resume() { player?.play() }
    override fun stop() {
        player?.stop()
        player?.clearMediaItems()
        _state.value = PlayerState()
    }

    override fun seekTo(position: Long) { player?.seekTo(position) }
    override fun seekForward(ms: Long) {
        player?.let { it.seekTo(minOf(it.currentPosition + ms, it.duration)) }
    }
    override fun seekBackward(ms: Long) {
        player?.let { it.seekTo(maxOf(it.currentPosition - ms, 0)) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun selectAudioTrack(index: Int) {
        val ts = trackSelector ?: return
        val p = player ?: return
        val tracks = p.currentTracks
        var audioIndex = 0
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                if (audioIndex == index) {
                    ts.setParameters(
                        ts.buildUponParameters()
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, 0)
                            )
                    )
                    _state.value = _state.value.copy(selectedAudioTrack = index)
                    return
                }
                audioIndex++
            }
        }
    }

    override fun selectSubtitleTrack(index: Int) {
        val ts = trackSelector ?: return
        val p = player ?: return
        val tracks = p.currentTracks
        var subIndex = 0
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                if (subIndex == index) {
                    ts.setParameters(
                        ts.buildUponParameters()
                            .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, 0)
                            )
                    )
                    _state.value = _state.value.copy(selectedSubtitleTrack = index)
                    return
                }
                subIndex++
            }
        }
    }

    override fun disableSubtitles() {
        trackSelector?.setParameters(
            trackSelector!!.buildUponParameters()
                .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
        )
        _state.value = _state.value.copy(selectedSubtitleTrack = -1)
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        // Aspect ratio is applied via Compose layout in PlayerScreen
        // Store the mode so UI can read it
        _state.value = _state.value.copy(aspectRatioMode = mode)
    }

    /**
     * Apply video quality mode via TrackSelector constraints.
     */
    override fun setVideoQualityMode(mode: VideoQualityMode) {
        val ts = trackSelector ?: return
        when (mode) {
            VideoQualityMode.AUTO -> {
                ts.setParameters(
                    ts.buildUponParameters()
                        .clearVideoSizeConstraints()
                        .setMaxVideoBitrate(Int.MAX_VALUE)
                )
            }
            VideoQualityMode.BEST -> {
                ts.setParameters(
                    ts.buildUponParameters()
                        .clearVideoSizeConstraints()
                        .setMaxVideoBitrate(Int.MAX_VALUE)
                        .setForceHighestSupportedBitrate(true)
                )
            }
            VideoQualityMode.BALANCED -> {
                ts.setParameters(
                    ts.buildUponParameters()
                        .setMaxVideoSize(1920, 1080)
                        .setMaxVideoBitrate(8_000_000)
                )
            }
            VideoQualityMode.DATA_SAVER -> {
                ts.setParameters(
                    ts.buildUponParameters()
                        .setMaxVideoSize(1280, 720)
                        .setMaxVideoBitrate(3_000_000)
                )
            }
        }
        _state.value = _state.value.copy(videoQualityMode = mode)
    }

    /**
     * Select a specific video quality track by index.
     */
    override fun selectVideoTrack(index: Int) {
        val ts = trackSelector ?: return
        val p = player ?: return
        val tracks = p.currentTracks
        var videoIdx = 0
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    if (videoIdx == index) {
                        ts.setParameters(
                            ts.buildUponParameters()
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                        )
                        return
                    }
                    videoIdx++
                }
            }
        }
    }

    /**
     * Set preferred audio language for ExoPlayer's track selector.
     * Uses ExoPlayer's built-in language matching.
     */
    fun setPreferredAudioLanguage(langCode: String) {
        val ts = trackSelector ?: return
        val normalizedLang = normalizeLanguageCode(langCode)
        if (normalizedLang.isNotEmpty()) {
            ts.setParameters(
                ts.buildUponParameters()
                    .setPreferredAudioLanguage(normalizedLang)
            )
            Timber.d("ExoPlayer preferred audio language set: $normalizedLang")
        }
    }

    /**
     * Set preferred subtitle language for ExoPlayer's track selector.
     * 'off' / 'none' disables subtitles, otherwise sets preferred language.
     */
    fun setPreferredSubtitleLanguage(langCode: String) {
        val ts = trackSelector ?: return
        val lower = langCode.lowercase()
        if (lower == "off" || lower == "none") {
            disableSubtitles()
            return
        }
        val normalizedLang = normalizeLanguageCode(langCode)
        if (normalizedLang.isNotEmpty()) {
            ts.setParameters(
                ts.buildUponParameters()
                    .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(normalizedLang)
            )
            Timber.d("ExoPlayer preferred subtitle language set: $normalizedLang")
        }
    }

    /**
     * Normalize language codes: tr→tur, en→eng, ar→ara, etc.
     */
    private fun normalizeLanguageCode(code: String): String {
        return when (code.lowercase().trim()) {
            "tr", "tur", "turkish" -> "tur"
            "en", "eng", "english" -> "eng"
            "ar", "ara", "arabic" -> "ara"
            "de", "deu", "ger", "german" -> "deu"
            "fr", "fra", "fre", "french" -> "fra"
            "es", "spa", "spanish" -> "spa"
            "ru", "rus", "russian" -> "rus"
            "it", "ita", "italian" -> "ita"
            "pt", "por", "portuguese" -> "por"
            "ja", "jpn", "japanese" -> "jpn"
            "ko", "kor", "korean" -> "kor"
            "zh", "zho", "chi", "chinese" -> "zho"
            "system" -> java.util.Locale.getDefault().isO3Language
            else -> code.lowercase().take(3)
        }
    }

    override fun getView(): Any? = player

    fun getExoPlayer(): ExoPlayer? = player

    private fun updateState() {
        val p = player ?: return
        val pbState = when (p.playbackState) {
            Player.STATE_IDLE -> PlaybackState.IDLE
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> if (p.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
            Player.STATE_ENDED -> PlaybackState.ENDED
            else -> PlaybackState.IDLE
        }
        _state.value = _state.value.copy(
            playbackState = pbState,
            isPlaying = p.isPlaying,
            currentPosition = p.currentPosition,
            duration = maxOf(p.duration, 0),
            bufferedPosition = p.bufferedPosition
        )
    }

    private fun updateTrackInfo(tracks: Tracks) {
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()
        var audioIdx = 0
        var subIdx = 0

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        audioTracks.add(
                            TrackInfo(
                                index = audioIdx,
                                name = format.label ?: "Audio ${audioIdx + 1}",
                                language = format.language ?: "",
                                isSelected = group.isTrackSelected(i)
                            )
                        )
                    }
                    audioIdx++
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        subtitleTracks.add(
                            TrackInfo(
                                index = subIdx,
                                name = format.label ?: format.language ?: "Subtitle ${subIdx + 1}",
                                language = format.language ?: "",
                                isSelected = group.isTrackSelected(i)
                            )
                        )
                    }
                    subIdx++
                }
            }
        }

        _state.value = _state.value.copy(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks
        )
    }

    /**
     * Extract available video quality options from current tracks.
     * Only produces quality options when the stream is adaptive (HLS/DASH).
     */
    private fun updateQualityInfo(tracks: Tracks) {
        val qualities = mutableListOf<QualityOption>()
        var isAdaptive = false
        var videoIdx = 0
        var currentCodec = ""
        var currentBitrate = ""
        var currentFps = ""

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                if (group.length > 1) isAdaptive = true
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val w = format.width
                    val h = format.height
                    val br = format.bitrate
                    val label = when {
                        h >= 2160 -> "4K (${w}x${h})"
                        h >= 1440 -> "1440p (${w}x${h})"
                        h >= 1080 -> "1080p (${w}x${h})"
                        h >= 720 -> "720p (${w}x${h})"
                        h >= 480 -> "480p (${w}x${h})"
                        h >= 360 -> "360p (${w}x${h})"
                        h > 0 -> "${h}p (${w}x${h})"
                        else -> "Quality ${videoIdx + 1}"
                    }

                    if (group.isTrackSelected(i)) {
                        currentCodec = format.codecs ?: format.sampleMimeType ?: ""
                        currentBitrate = if (br > 0) "${br / 1000} kbps" else ""
                        currentFps = if (format.frameRate > 0) "${format.frameRate.toInt()} fps" else ""
                    }

                    qualities.add(
                        QualityOption(
                            index = videoIdx,
                            label = label,
                            width = w,
                            height = h,
                            bitrate = br,
                            isSelected = group.isTrackSelected(i),
                            isAdaptive = group.length > 1
                        )
                    )
                    videoIdx++
                }
            }
        }

        // Add an "Auto" option at the beginning if adaptive
        if (isAdaptive) {
            qualities.add(0, QualityOption(index = -1, label = "Auto", isAdaptive = true, isSelected = _state.value.videoQualityMode == VideoQualityMode.AUTO))
        }

        _state.value = _state.value.copy(
            availableQualities = qualities,
            isAdaptiveStream = isAdaptive,
            currentVideoCodec = currentCodec,
            currentVideoBitrate = currentBitrate,
            currentVideoFps = currentFps
        )
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope?.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    _state.value = _state.value.copy(
                        currentPosition = p.currentPosition,
                        duration = maxOf(p.duration, 0),
                        bufferedPosition = p.bufferedPosition
                    )
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
