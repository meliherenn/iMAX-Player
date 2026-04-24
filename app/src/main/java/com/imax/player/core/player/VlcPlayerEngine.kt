package com.imax.player.core.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import timber.log.Timber

@Singleton
class VlcPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerEngine {

    override val engineName: String = "VLC"

    override fun isAvailable(): Boolean {
        // VLC is shipped via libvlc-all and supports main architectures
        return true
    }

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var scope: CoroutineScope? = null
    private var progressJob: Job? = null

    private var configuredBufferMs: Long = 30_000L
    private var configuredLatencyMode: String = LiveLatencyMode.BALANCED.name
    private var configuredPreferHw: Boolean = true
    private var currentPlaybackSpeed = 1f
    private var currentAspectMode = AspectRatioMode.FIT
    private var currentVideoQualityMode = VideoQualityMode.AUTO
    private var preferredAudioLanguage: String? = null
    private var preferredSubtitleLanguage: String? = null
    private var subtitlesDisabled = false
    private var currentProfile = PlaybackProfile.VOD

    private var currentSurfaceView: SurfaceView? = null
    private var surfaceReady = false
    private var firstFrameRendered = false
    private var pendingUrl: String? = null
    private var pendingStartPos: Long = 0L

    private var playbackState: PlaybackState = PlaybackState.IDLE
    private var hasVideoTrack = false
    private var hasAudioTrack = false
    private var currentVideoWidth = 0
    private var currentVideoHeight = 0
    private var currentVideoCodec = ""
    private var currentVideoResolution = ""
    private var currentAudioTracks: List<TrackInfo> = emptyList()
    private var currentSubtitleTracks: List<TrackInfo> = emptyList()
    private var selectedAudioTrack = -1
    private var selectedSubtitleTrack = -1
    private var currentErrorMessage: String? = null

    private val _state = MutableStateFlow(
        PlayerState(
            aspectRatioMode = currentAspectMode,
            playbackSpeed = currentPlaybackSpeed,
            videoQualityMode = currentVideoQualityMode
        )
    )
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override fun initialize() {
        if (libVlc != null && mediaPlayer != null) {
            return
        }

        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

        try {
            val hwOption = if (configuredPreferHw) "--avcodec-hw=any" else "--avcodec-hw=none"
            val options = arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                hwOption,
                "--subsdec-encoding=UTF-8",
                "--aout=opensles",
                "--audio-time-stretch"
            )

            libVlc = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVlc!!).apply {
                setEventListener(::handleVlcEvent)
            }
            publishState()
            Timber.d("VLC engine initialized")
        } catch (throwable: Throwable) {
            Timber.e(throwable, "VLC init failed")
            currentErrorMessage = "VLC init failed: ${throwable.localizedMessage}"
            playbackState = PlaybackState.ERROR
            publishState()
        }
    }

    override fun release() {
        stopProgressTracking()
        pendingUrl = null
        pendingStartPos = 0L
        cleanupSurface()

        try {
            mediaPlayer?.stop()
        } catch (throwable: Throwable) {
            Timber.w(throwable, "VLC stop failed during release")
        }
        try {
            mediaPlayer?.release()
        } catch (throwable: Throwable) {
            Timber.w(throwable, "VLC player release failed")
        }
        try {
            libVlc?.release()
        } catch (throwable: Throwable) {
            Timber.w(throwable, "LibVLC release failed")
        }

        mediaPlayer = null
        libVlc = null
        scope?.cancel()
        scope = null

        playbackState = PlaybackState.IDLE
        hasVideoTrack = false
        hasAudioTrack = false
        firstFrameRendered = false
        currentVideoWidth = 0
        currentVideoHeight = 0
        currentVideoCodec = ""
        currentVideoResolution = ""
        currentAudioTracks = emptyList()
        currentSubtitleTracks = emptyList()
        selectedAudioTrack = -1
        selectedSubtitleTrack = -1
        currentErrorMessage = null

        _state.value = PlayerState(
            aspectRatioMode = currentAspectMode,
            playbackSpeed = currentPlaybackSpeed,
            videoQualityMode = currentVideoQualityMode
        )
    }

    override fun play(url: String, startPosition: Long, profile: PlaybackProfile) {
        initialize()
        currentProfile = profile
        currentErrorMessage = null
        playbackState = PlaybackState.BUFFERING
        firstFrameRendered = false
        currentVideoWidth = 0
        currentVideoHeight = 0
        currentVideoCodec = ""
        currentVideoResolution = ""
        currentAudioTracks = emptyList()
        currentSubtitleTracks = emptyList()
        selectedAudioTrack = -1
        selectedSubtitleTrack = -1
        hasVideoTrack = false
        hasAudioTrack = false

        if (!surfaceReady) {
            pendingUrl = url
            pendingStartPos = startPosition
            publishState()
            Timber.d("VLC playback queued until surface is ready: %s", url)
            return
        }

        startPlaybackInternal(url, startPosition)
    }

    override fun pause() {
        mediaPlayer?.pause()
        playbackState = PlaybackState.PAUSED
        publishState()
    }

    override fun resume() {
        mediaPlayer?.play()
        playbackState = PlaybackState.PLAYING
        publishState()
    }

    override fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (throwable: Throwable) {
            Timber.w(throwable, "VLC stop failed")
        }
        stopProgressTracking()
        pendingUrl = null
        pendingStartPos = 0L
        playbackState = PlaybackState.IDLE
        firstFrameRendered = false
        hasVideoTrack = false
        hasAudioTrack = false
        currentVideoWidth = 0
        currentVideoHeight = 0
        currentVideoCodec = ""
        currentVideoResolution = ""
        currentAudioTracks = emptyList()
        currentSubtitleTracks = emptyList()
        selectedAudioTrack = -1
        selectedSubtitleTrack = -1
        currentErrorMessage = null
        publishState()
    }

    override fun seekTo(position: Long) {
        mediaPlayer?.time = position
        publishState()
    }

    override fun seekForward(ms: Long) {
        val player = mediaPlayer ?: return
        val duration = player.length.takeIf { it > 0L } ?: Long.MAX_VALUE
        player.time = (player.time + ms).coerceAtMost(duration)
        publishState()
    }

    override fun seekBackward(ms: Long) {
        val player = mediaPlayer ?: return
        player.time = (player.time - ms).coerceAtLeast(0L)
        publishState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentPlaybackSpeed = speed
        mediaPlayer?.rate = speed
        publishState()
    }

    override fun selectAudioTrack(index: Int) {
        val player = mediaPlayer ?: return
        val tracks = player.audioTracks ?: return
        if (index !in tracks.indices) {
            return
        }
        player.audioTrack = tracks[index].id
        updateTrackInfo()
        publishState()
    }

    override fun selectSubtitleTrack(index: Int) {
        val player = mediaPlayer ?: return
        val tracks = player.spuTracks ?: return
        if (index !in tracks.indices) {
            return
        }
        subtitlesDisabled = false
        player.spuTrack = tracks[index].id
        updateTrackInfo()
        publishState()
    }

    override fun disableSubtitles() {
        subtitlesDisabled = true
        mediaPlayer?.spuTrack = -1
        selectedSubtitleTrack = -1
        updateTrackInfo()
        publishState()
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        currentAspectMode = mode
        applyAspectRatioMode()
        publishState()
    }

    override fun setVideoQualityMode(mode: VideoQualityMode) {
        currentVideoQualityMode = mode
        publishState()
    }

    override fun setPlaybackConfiguration(
        bufferDurationMs: Long,
        liveLatencyMode: String,
        preferHwDecoding: Boolean
    ) {
        configuredBufferMs = bufferDurationMs
        configuredLatencyMode = liveLatencyMode
        configuredPreferHw = preferHwDecoding
    }

    fun setPreferredAudioLanguage(langCode: String) {
        preferredAudioLanguage = langCode.trim().takeIf { it.isNotBlank() }
        applyPreferredTracks()
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        val normalized = langCode.trim()
        if (normalized.equals("off", ignoreCase = true) ||
            normalized.equals("none", ignoreCase = true)
        ) {
            subtitlesDisabled = true
            preferredSubtitleLanguage = null
            disableSubtitles()
            return
        }

        subtitlesDisabled = false
        preferredSubtitleLanguage = normalized.takeIf { it.isNotBlank() }
        applyPreferredTracks()
    }

    fun attachSurface(surfaceView: SurfaceView) {
        currentSurfaceView?.holder?.removeCallback(surfaceHolderCallback)
        currentSurfaceView = surfaceView
        surfaceReady = false
        firstFrameRendered = false
        surfaceView.holder.addCallback(surfaceHolderCallback)

        val holder = surfaceView.holder
        if (holder.surface?.isValid == true && surfaceView.width > 0 && surfaceView.height > 0) {
            attachVlcToSurface(surfaceView)
        } else {
            publishState()
        }
    }

    fun detachSurface() {
        cleanupSurface()
        publishState()
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        try {
            mediaPlayer?.vlcVout?.let { vout ->
                if (vout.areViewsAttached()) {
                    vout.setWindowSize(width, height)
                }
            }
        } catch (throwable: Throwable) {
            Timber.w(throwable, "VLC window size update failed")
        }
        applyAspectRatioMode()
        publishState()
    }

    private fun startPlaybackInternal(url: String, startPosition: Long) {
        val player = mediaPlayer ?: return
        val vlc = libVlc ?: return

        try {
            stopProgressTracking()
            playbackState = PlaybackState.BUFFERING

            try {
                player.stop()
            } catch (_: Throwable) {
            }

            val media = Media(vlc, Uri.parse(url))
            media.setHWDecoderEnabled(configuredPreferHw, false)

            val isLive = currentProfile == PlaybackProfile.LIVE ||
                url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".ts", ignoreCase = true)
            val cacheMs = if (isLive) {
                when (configuredLatencyMode.uppercase()) {
                    LiveLatencyMode.LOW_LATENCY.name -> 500L
                    LiveLatencyMode.STABLE.name -> 3_000L
                    else -> 1_500L
                }
            } else {
                configuredBufferMs
            }

            media.addOption(":network-caching=$cacheMs")
            media.addOption(":live-caching=$cacheMs")
            media.addOption(
                if (configuredLatencyMode.uppercase() == LiveLatencyMode.LOW_LATENCY.name && isLive) {
                    ":clock-jitter=0"
                } else {
                    ":clock-jitter=500"
                }
            )
            media.addOption(":clock-synchro=0")
            media.addOption(":http-user-agent=iMAX Player/Android")
            media.addOption(":input-repeat=0")

            player.media = media
            media.release()
            player.rate = currentPlaybackSpeed
            player.play()

            if (startPosition > 0L) {
                scope?.launch {
                    delay(500L)
                    if (player.isPlaying || player.length > 0L) {
                        player.time = startPosition
                        publishState()
                    }
                }
            }

            publishState()
            Timber.d("VLC playback started: %s", url)
        } catch (throwable: Throwable) {
            Timber.e(throwable, "VLC play failed: %s", url)
            currentErrorMessage = "VLC playback failed: ${throwable.localizedMessage}"
            playbackState = PlaybackState.ERROR
            publishState()
        }
    }

    private fun attachVlcToSurface(surfaceView: SurfaceView) {
        val player = mediaPlayer ?: return
        try {
            val vout = player.vlcVout
            if (vout.areViewsAttached()) {
                vout.removeCallback(vlcVoutCallback)
                vout.detachViews()
            }
            vout.setVideoView(surfaceView)
            val width = surfaceView.holder.surfaceFrame.width()
            val height = surfaceView.holder.surfaceFrame.height()
            if (width > 0 && height > 0) {
                vout.setWindowSize(width, height)
            }
            vout.addCallback(vlcVoutCallback)
            vout.attachViews()
            surfaceReady = true
            applyAspectRatioMode()
            publishState()

            val queuedUrl = pendingUrl
            if (queuedUrl != null) {
                val queuedPosition = pendingStartPos
                pendingUrl = null
                pendingStartPos = 0L
                startPlaybackInternal(queuedUrl, queuedPosition)
            }
        } catch (throwable: Throwable) {
            surfaceReady = false
            Timber.e(throwable, "Failed to attach VLC surface")
            publishState()
        }
    }

    private fun detachVlcFromSurface() {
        surfaceReady = false
        firstFrameRendered = false
        try {
            mediaPlayer?.vlcVout?.let { vout ->
                if (vout.areViewsAttached()) {
                    vout.removeCallback(vlcVoutCallback)
                    vout.detachViews()
                }
            }
        } catch (throwable: Throwable) {
            Timber.w(throwable, "Failed to detach VLC surface")
        }
    }

    private fun cleanupSurface() {
        try {
            currentSurfaceView?.holder?.removeCallback(surfaceHolderCallback)
        } catch (throwable: Throwable) {
            Timber.w(throwable, "Failed to remove SurfaceHolder callback")
        }
        detachVlcFromSurface()
        currentSurfaceView = null
    }

    private val surfaceHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            currentSurfaceView?.let(::attachVlcToSurface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            updateSurfaceSize(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            detachVlcFromSurface()
            publishState()
        }
    }

    private val vlcVoutCallback = object : IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: IVLCVout) {
            surfaceReady = true
            publishState()
        }

        override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
            surfaceReady = false
            firstFrameRendered = false
            publishState()
        }
    }

    private fun handleVlcEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                playbackState = PlaybackState.BUFFERING
                currentErrorMessage = null
            }

            MediaPlayer.Event.Buffering -> {
                if (event.buffering < 100f && playbackState != PlaybackState.PLAYING) {
                    playbackState = PlaybackState.BUFFERING
                }
            }

            MediaPlayer.Event.Playing -> {
                playbackState = PlaybackState.PLAYING
                currentErrorMessage = null
                ensureDefaultAudioTrackSelected()
                updateTrackInfo()
                applyPreferredTracks()
                startProgressTracking()
            }

            MediaPlayer.Event.Paused -> {
                playbackState = PlaybackState.PAUSED
                stopProgressTracking()
            }

            MediaPlayer.Event.Stopped -> {
                playbackState = PlaybackState.STOPPED
                stopProgressTracking()
            }

            MediaPlayer.Event.EndReached -> {
                playbackState = PlaybackState.ENDED
                stopProgressTracking()
            }

            MediaPlayer.Event.EncounteredError -> {
                playbackState = PlaybackState.ERROR
                currentErrorMessage = "VLC playback error"
                stopProgressTracking()
            }

            MediaPlayer.Event.TimeChanged -> {
                // Progress is published below.
            }

            MediaPlayer.Event.Vout -> {
                val player = mediaPlayer
                val track = player?.currentVideoTrack
                currentVideoWidth = track?.width ?: 0
                currentVideoHeight = track?.height ?: 0
                currentVideoResolution = if (currentVideoWidth > 0 && currentVideoHeight > 0) {
                    "${currentVideoWidth}x${currentVideoHeight}"
                } else {
                    ""
                }
                currentVideoCodec = track?.codec?.toString().orEmpty()
                hasVideoTrack = currentVideoWidth > 0 && currentVideoHeight > 0
                if (hasVideoTrack) {
                    firstFrameRendered = true
                }
                applyAspectRatioMode()
            }
        }
        publishState()
    }

    private fun updateTrackInfo() {
        val player = mediaPlayer ?: return
        val audioTracks = player.audioTracks?.mapIndexed { index, track ->
            TrackInfo(
                index = index,
                name = track.name ?: "Audio ${index + 1}",
                isSelected = track.id == player.audioTrack
            )
        }.orEmpty()
        val subtitleTracks = player.spuTracks?.mapIndexed { index, track ->
            TrackInfo(
                index = index,
                name = track.name ?: "Subtitle ${index + 1}",
                isSelected = track.id == player.spuTrack
            )
        }.orEmpty()

        currentAudioTracks = audioTracks
        currentSubtitleTracks = subtitleTracks
        selectedAudioTrack = audioTracks.indexOfFirst { it.isSelected }
        selectedSubtitleTrack = if (subtitlesDisabled || player.spuTrack == -1) {
            -1
        } else {
            subtitleTracks.indexOfFirst { it.isSelected }
        }
        hasAudioTrack = audioTracks.isNotEmpty()
    }

    private fun ensureDefaultAudioTrackSelected() {
        val player = mediaPlayer ?: return
        val tracks = player.audioTracks ?: return
        if (tracks.isEmpty()) {
            return
        }
        if (tracks.none { it.id == player.audioTrack }) {
            player.audioTrack = tracks.first().id
        }
    }

    private fun applyPreferredTracks() {
        val audioLanguage = preferredAudioLanguage
        if (!audioLanguage.isNullOrBlank()) {
            selectAudioTrackByLanguage(audioLanguage)
        }

        when {
            subtitlesDisabled -> disableSubtitles()
            !preferredSubtitleLanguage.isNullOrBlank() -> selectSubtitleTrackByLanguage(preferredSubtitleLanguage!!)
        }
    }

    private fun selectAudioTrackByLanguage(langCode: String): Boolean {
        val player = mediaPlayer ?: return false
        val tracks = player.audioTracks ?: return false
        for ((index, track) in tracks.withIndex()) {
            if (matchesLanguage(track.name.orEmpty(), langCode)) {
                player.audioTrack = track.id
                updateTrackInfo()
                selectedAudioTrack = index
                return true
            }
        }
        return false
    }

    private fun selectSubtitleTrackByLanguage(langCode: String): Boolean {
        val player = mediaPlayer ?: return false
        val tracks = player.spuTracks ?: return false
        for ((index, track) in tracks.withIndex()) {
            if (matchesLanguage(track.name.orEmpty(), langCode)) {
                player.spuTrack = track.id
                updateTrackInfo()
                selectedSubtitleTrack = index
                return true
            }
        }
        return false
    }

    private fun matchesLanguage(trackName: String, langCode: String): Boolean {
        val normalizedName = trackName.lowercase()
        val normalizedCode = langCode.lowercase()
        return when (normalizedCode) {
            "tur", "tr", "turkish" ->
                normalizedName.contains("tur") || normalizedName.contains("turkish") || normalizedName.contains("türk")

            "eng", "en", "english" ->
                normalizedName.contains("eng") || normalizedName.contains("english")

            "ara", "ar", "arabic" ->
                normalizedName.contains("ara") || normalizedName.contains("arabic") || normalizedName.contains("عرب")

            "deu", "de", "german" ->
                normalizedName.contains("deu") || normalizedName.contains("ger") || normalizedName.contains("german")

            "fra", "fr", "french" ->
                normalizedName.contains("fra") || normalizedName.contains("fre") || normalizedName.contains("french")

            "spa", "es", "spanish" ->
                normalizedName.contains("spa") || normalizedName.contains("spanish")

            else -> normalizedName.contains(normalizedCode)
        }
    }

    private fun applyAspectRatioMode() {
        val player = mediaPlayer ?: return
        val surfaceView = currentSurfaceView
        val surfaceWidth = surfaceView?.width?.takeIf { it > 0 }
            ?: surfaceView?.holder?.surfaceFrame?.width()?.takeIf { it > 0 }
            ?: 0
        val surfaceHeight = surfaceView?.height?.takeIf { it > 0 }
            ?: surfaceView?.holder?.surfaceFrame?.height()?.takeIf { it > 0 }
            ?: 0

        when (currentAspectMode) {
            AspectRatioMode.AUTO,
            AspectRatioMode.FIT -> {
                player.aspectRatio = null
                player.scale = 0f
            }

            AspectRatioMode.FILL -> {
                player.aspectRatio = null
                player.scale = calculateFillScale(surfaceWidth, surfaceHeight) ?: 0f
            }

            AspectRatioMode.ZOOM -> {
                player.aspectRatio = null
                player.scale = calculateFillScale(surfaceWidth, surfaceHeight)?.times(1.1f) ?: 1.2f
            }

            AspectRatioMode.STRETCH -> {
                if (surfaceWidth > 0 && surfaceHeight > 0) {
                    player.aspectRatio = "${surfaceWidth}:${surfaceHeight}"
                    player.scale = 0f
                } else {
                    player.aspectRatio = null
                    player.scale = 0f
                }
            }

            AspectRatioMode.ORIGINAL -> {
                player.aspectRatio = null
                player.scale = 1f
            }

            AspectRatioMode.FORCE_16_9 -> {
                player.aspectRatio = "16:9"
                player.scale = 0f
            }

            AspectRatioMode.FORCE_4_3 -> {
                player.aspectRatio = "4:3"
                player.scale = 0f
            }
        }
    }

    private fun calculateFillScale(surfaceWidth: Int, surfaceHeight: Int): Float? {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || currentVideoWidth <= 0 || currentVideoHeight <= 0) {
            return null
        }
        val widthScale = surfaceWidth.toFloat() / currentVideoWidth.toFloat()
        val heightScale = surfaceHeight.toFloat() / currentVideoHeight.toFloat()
        return maxOf(widthScale, heightScale)
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope?.launch {
            while (isActive) {
                publishState()
                delay(250L)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun publishState() {
        val player = mediaPlayer
        val currentPosition = player?.time?.coerceAtLeast(0L) ?: 0L
        val duration = player?.length?.takeIf { it > 0L } ?: 0L
        val isPlaying = player?.isPlaying == true && playbackState == PlaybackState.PLAYING
        val estimatedBufferedPosition = when {
            playbackState == PlaybackState.BUFFERING || playbackState == PlaybackState.PLAYING ->
                currentPosition + configuredBufferMs.coerceAtLeast(1_500L)

            else -> currentPosition
        }
        val confirmed = when {
            playbackState != PlaybackState.PLAYING -> false
            hasVideoTrack -> surfaceReady &&
                firstFrameRendered &&
                currentVideoWidth > 0 &&
                currentVideoHeight > 0

            hasAudioTrack -> currentPosition >= 250L
            else -> false
        }

        _state.value = PlayerState(
            playbackState = playbackState,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            bufferedPosition = estimatedBufferedPosition,
            playbackSpeed = currentPlaybackSpeed,
            audioTracks = currentAudioTracks,
            subtitleTracks = currentSubtitleTracks,
            selectedAudioTrack = selectedAudioTrack,
            selectedSubtitleTrack = selectedSubtitleTrack,
            videoWidth = currentVideoWidth,
            videoHeight = currentVideoHeight,
            errorMessage = currentErrorMessage,
            aspectRatioMode = currentAspectMode,
            availableQualities = emptyList(),
            videoQualityMode = currentVideoQualityMode,
            currentVideoResolution = currentVideoResolution,
            currentVideoBitrate = "",
            currentVideoCodec = currentVideoCodec,
            currentVideoFps = "",
            isAdaptiveStream = false,
            hasVideoTrack = hasVideoTrack,
            hasAudioTrack = hasAudioTrack,
            isSurfaceReady = surfaceReady,
            hasRenderedFirstFrame = firstFrameRendered,
            isPlaybackConfirmed = confirmed,
            audioSessionId = if (hasAudioTrack && playbackState == PlaybackState.PLAYING) 1 else 0
        )
    }
}
