package com.imax.player.core.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import timber.log.Timber
import javax.inject.Inject

/**
 * Production VLC player engine.
 *
 * KEY DESIGN — Surface-first with SurfaceHolder.Callback:
 * 1. play() queues URL if no surface is ready
 * 2. setSurface() registers SurfaceHolder.Callback
 * 3. surfaceCreated() → attach vout + trigger pending play
 * 4. surfaceChanged() → update window size (this is where real dimensions arrive)
 * 5. surfaceDestroyed() → detach vout
 *
 * This ensures VLC NEVER renders to a 0x0 or null surface.
 */
class VlcPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerEngine {

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val engineName: String = "VLC"

    private var scope: CoroutineScope? = null
    private var progressJob: Job? = null
    private var vlcAvailable: Boolean? = null

    // Surface lifecycle state
    private var currentSurfaceView: SurfaceView? = null
    private var surfaceReady = false  // true only AFTER surfaceCreated callback

    // Pending playback — queued until surface is ready
    private var pendingUrl: String? = null
    private var pendingStartPos: Long = 0
    private var currentAspectMode: AspectRatioMode = AspectRatioMode.FIT

    override fun isAvailable(): Boolean {
        if (vlcAvailable != null) return vlcAvailable!!
        return try {
            Class.forName("org.videolan.libvlc.LibVLC")
            vlcAvailable = true
            true
        } catch (e: ClassNotFoundException) {
            Timber.w("libVLC not available on this device")
            vlcAvailable = false
            false
        } catch (e: UnsatisfiedLinkError) {
            Timber.w(e, "libVLC native libraries not available")
            vlcAvailable = false
            false
        }
    }

    override fun initialize() {
        if (!isAvailable()) {
            _state.value = _state.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = "VLC engine not available."
            )
            return
        }

        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        if (libVLC != null) return // Already initialized

        try {
            val options = arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--avcodec-hw=any",
                "--subsdec-encoding=UTF-8",
                "--aout=opensles",
                "--audio-time-stretch"
                // Don't force --vout; let VLC auto-detect for best compatibility
            )

            libVLC = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVLC!!).apply {
                setEventListener { event -> handleVlcEvent(event) }
            }

            Timber.d("VLC engine initialized")
        } catch (e: Exception) {
            Timber.e(e, "VLC init failed")
            _state.value = _state.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = "VLC init failed: ${e.localizedMessage}"
            )
        }
    }

    override fun release() {
        Timber.d("VLC release() called")
        stopProgressTracking()
        scope?.cancel()
        scope = null
        pendingUrl = null
        pendingStartPos = 0
        currentAspectMode = AspectRatioMode.FIT

        // Remove surface callback and detach
        cleanupSurface()

        try { mediaPlayer?.stop() } catch (e: Exception) { Timber.w(e, "VLC stop error") }
        try { mediaPlayer?.release() } catch (e: Exception) { Timber.w(e, "VLC mp release error") }
        try { libVLC?.release() } catch (e: Exception) { Timber.w(e, "LibVLC release error") }

        mediaPlayer = null
        libVLC = null
        _state.value = PlayerState()
        Timber.d("VLC release() done")
    }

    /**
     * Play URL. If surface is not ready yet, queue for later.
     */
    override fun play(url: String, startPosition: Long) {
        initialize()
        if (mediaPlayer == null || libVLC == null) return

        if (!surfaceReady) {
            Timber.d("VLC play queued (surface not ready): $url")
            pendingUrl = url
            pendingStartPos = startPosition
            _state.value = _state.value.copy(playbackState = PlaybackState.BUFFERING)
            return
        }

        startPlaybackInternal(url, startPosition)
    }

    /**
     * Actually start playback — called ONLY when surface is attached and ready.
     */
    private fun startPlaybackInternal(url: String, startPosition: Long) {
        val mp = mediaPlayer ?: return
        val vlc = libVLC ?: return

        try {
            _state.value = _state.value.copy(playbackState = PlaybackState.BUFFERING)
            try {
                mp.stop()
            } catch (_: Exception) {
            }

            val media = Media(vlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=1500")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")
            media.addOption(":http-user-agent=iMAX Player/Android")
            media.addOption(":input-repeat=0")

            mp.media = media
            media.release()
            mp.play()

            if (startPosition > 0) {
                scope?.launch {
                    delay(500)
                    if (mp.isPlaying || mp.length > 0) {
                        mp.time = startPosition
                    }
                }
            }

            Timber.d("VLC playback started: $url")
        } catch (e: Exception) {
            Timber.e(e, "VLC play failed: $url")
            _state.value = _state.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = "VLC playback failed: ${e.localizedMessage}"
            )
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SURFACE MANAGEMENT — THE CORE VIDEO FIX
    //  Uses SurfaceHolder.Callback for lifecycle-safe attach
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Called from PlayerScreen when AndroidView creates the SurfaceView.
     *
     * Registers a SurfaceHolder.Callback. VLC vout is attached
     * ONLY in surfaceCreated() — NOT here — because at this point
     * the SurfaceView has no valid Surface yet (width=0, height=0).
     */
    fun setSurface(surface: SurfaceView) {
        Timber.d("setSurface() called")

        // Remove old callback if switching surfaces
        currentSurfaceView?.holder?.removeCallback(surfaceHolderCallback)

        currentSurfaceView = surface
        surfaceReady = false

        // Register callback — surfaceCreated() will do the actual VLC attach
        surface.holder.addCallback(surfaceHolderCallback)

        // If the surface is already valid (holder.surface != null && width > 0),
        // attach immediately. This handles the case where the surface was created
        // before we registered the callback.
        val holder = surface.holder
        if (holder.surface != null && holder.surface.isValid) {
            Timber.d("Surface already valid on setSurface(), attaching immediately")
            attachVlcToSurface(surface)
        }
    }

    /**
     * Called from PlayerScreen on dispose. Cleans up surface references.
     */
    fun detachSurface() {
        Timber.d("detachSurface() called")
        cleanupSurface()
    }

    /**
     * Update window size (called from AndroidView update{}).
     */
    fun updateSurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val mp = mediaPlayer ?: return
        try {
            val vout = mp.vlcVout
            if (vout.areViewsAttached()) {
                vout.setWindowSize(width, height)
            }
            applyAspectRatioMode()
        } catch (e: Exception) {
            Timber.w(e, "Error updating surface size")
        }
    }

    /**
     * The critical SurfaceHolder.Callback — this is how we know
     * when the underlying Surface is ACTUALLY ready for rendering.
     */
    private val surfaceHolderCallback = object : SurfaceHolder.Callback {

        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.d("surfaceCreated() — attaching VLC vout")
            currentSurfaceView?.let { attachVlcToSurface(it) }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Timber.d("surfaceChanged() ${width}x${height}")
            if (width > 0 && height > 0) {
                try {
                    mediaPlayer?.vlcVout?.setWindowSize(width, height)
                    applyAspectRatioMode()
                } catch (e: Exception) {
                    Timber.w(e, "Error on surfaceChanged")
                }
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Timber.d("surfaceDestroyed() — detaching VLC vout")
            detachVlcFromSurface()
        }
    }

    /**
     * Attach VLC video output to the given SurfaceView.
     * Called from surfaceCreated or when surface is already valid.
     */
    private fun attachVlcToSurface(surface: SurfaceView) {
        val mp = mediaPlayer
        if (mp == null) {
            Timber.w("attachVlcToSurface: mediaPlayer is null")
            surfaceReady = false
            return
        }

        try {
            val vout = mp.vlcVout

            // Detach existing views first
            if (vout.areViewsAttached()) {
                try {
                    vout.detachViews()
                } catch (e: Exception) {
                    Timber.w(e, "Error detaching old views before reattach")
                }
            }

            vout.setVideoView(surface)

            // Set window size — at this point the surface HAS real dimensions
            val w = surface.holder.surfaceFrame.width()
            val h = surface.holder.surfaceFrame.height()
            if (w > 0 && h > 0) {
                vout.setWindowSize(w, h)
                Timber.d("VLC vout window size set: ${w}x${h}")
            }

            vout.addCallback(vlcVoutCallback)
            vout.attachViews()
            surfaceReady = true
            applyAspectRatioMode()

            Timber.d("VLC vout attached successfully")

            // Fire pending play if queued
            val url = pendingUrl
            if (url != null) {
                val pos = pendingStartPos
                pendingUrl = null
                pendingStartPos = 0
                Timber.d("Starting pending VLC playback")
                startPlaybackInternal(url, pos)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to attach VLC vout")
            surfaceReady = false
        }
    }

    /**
     * Detach VLC video output from surface.
     */
    private fun detachVlcFromSurface() {
        surfaceReady = false
        val mp = mediaPlayer ?: return
        try {
            val vout = mp.vlcVout
            if (vout.areViewsAttached()) {
                vout.removeCallback(vlcVoutCallback)
                vout.detachViews()
                Timber.d("VLC vout detached")
            }
        } catch (e: Exception) {
            Timber.w(e, "Error detaching VLC vout")
        }
    }

    /**
     * Full cleanup of surface references and callbacks.
     */
    private fun cleanupSurface() {
        surfaceReady = false
        // Remove SurfaceHolder callback
        try {
            currentSurfaceView?.holder?.removeCallback(surfaceHolderCallback)
        } catch (e: Exception) {
            Timber.w(e, "Error removing SurfaceHolder callback")
        }
        // Detach VLC vout
        detachVlcFromSurface()
        currentSurfaceView = null
    }

    private val vlcVoutCallback = object : IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: IVLCVout) {
            Timber.d("VLC Vout callback: surfaces created")
        }

        override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
            Timber.d("VLC Vout callback: surfaces destroyed")
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Playback controls
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    override fun pause() { mediaPlayer?.pause() }
    override fun resume() { mediaPlayer?.play() }
    override fun stop() {
        mediaPlayer?.stop()
        _state.value = PlayerState()
    }

    override fun seekTo(position: Long) { mediaPlayer?.time = position }
    override fun seekForward(ms: Long) {
        val mp = mediaPlayer ?: return
        mp.time = minOf(mp.time + ms, mp.length)
    }
    override fun seekBackward(ms: Long) {
        val mp = mediaPlayer ?: return
        mp.time = maxOf(mp.time - ms, 0)
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.rate = speed
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Track selection
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    override fun selectAudioTrack(index: Int) {
        val mp = mediaPlayer ?: return
        val tracks = mp.audioTracks ?: return
        if (index in tracks.indices) {
            mp.audioTrack = tracks[index].id
            _state.value = _state.value.copy(selectedAudioTrack = index)
            updateTrackInfo()
        }
    }

    override fun selectSubtitleTrack(index: Int) {
        val mp = mediaPlayer ?: return
        val tracks = mp.spuTracks ?: return
        if (index in tracks.indices) {
            mp.spuTrack = tracks[index].id
            _state.value = _state.value.copy(selectedSubtitleTrack = index)
            updateTrackInfo()
        }
    }

    override fun disableSubtitles() {
        mediaPlayer?.spuTrack = -1
        _state.value = _state.value.copy(selectedSubtitleTrack = -1)
    }

    fun selectAudioTrackByLanguage(langCode: String): Boolean {
        val mp = mediaPlayer ?: return false
        val tracks = mp.audioTracks ?: return false
        for ((idx, desc) in tracks.withIndex()) {
            val name = (desc.name ?: "").lowercase()
            if (matchesLanguage(name, langCode)) {
                mp.audioTrack = desc.id
                _state.value = _state.value.copy(selectedAudioTrack = idx)
                Timber.d("VLC auto-selected audio: ${desc.name} for $langCode")
                return true
            }
        }
        return false
    }

    fun selectSubtitleTrackByLanguage(langCode: String): Boolean {
        val mp = mediaPlayer ?: return false
        val tracks = mp.spuTracks ?: return false
        for ((idx, desc) in tracks.withIndex()) {
            val name = (desc.name ?: "").lowercase()
            if (matchesLanguage(name, langCode)) {
                mp.spuTrack = desc.id
                _state.value = _state.value.copy(selectedSubtitleTrack = idx)
                Timber.d("VLC auto-selected subtitle: ${desc.name} for $langCode")
                return true
            }
        }
        return false
    }

    private fun matchesLanguage(trackName: String, langCode: String): Boolean {
        val n = langCode.lowercase()
        return when (n) {
            "tur", "tr", "turkish" -> trackName.contains("tur") || trackName.contains("turkish") || trackName.contains("türk")
            "eng", "en", "english" -> trackName.contains("eng") || trackName.contains("english")
            "ara", "ar", "arabic" -> trackName.contains("ara") || trackName.contains("arabic") || trackName.contains("عرب")
            "deu", "de", "german" -> trackName.contains("deu") || trackName.contains("ger") || trackName.contains("german")
            "fra", "fr", "french" -> trackName.contains("fra") || trackName.contains("fre") || trackName.contains("french")
            "spa", "es", "spanish" -> trackName.contains("spa") || trackName.contains("spanish")
            else -> trackName.contains(n)
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Aspect ratio
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    override fun setAspectRatio(mode: AspectRatioMode) {
        currentAspectMode = mode
        _state.value = _state.value.copy(aspectRatioMode = mode)
        applyAspectRatioMode()
    }

    private fun applyAspectRatioMode() {
        val mp = mediaPlayer ?: return
        val surfaceView = currentSurfaceView
        val surfaceWidth = surfaceView?.width?.takeIf { it > 0 }
            ?: surfaceView?.holder?.surfaceFrame?.width()?.takeIf { it > 0 }
            ?: 0
        val surfaceHeight = surfaceView?.height?.takeIf { it > 0 }
            ?: surfaceView?.holder?.surfaceFrame?.height()?.takeIf { it > 0 }
            ?: 0

        when (currentAspectMode) {
            AspectRatioMode.AUTO -> {
                mp.aspectRatio = null
                mp.scale = 0f
            }
            AspectRatioMode.FIT -> {
                mp.aspectRatio = null
                mp.scale = 0f
            }
            AspectRatioMode.FILL -> {
                mp.aspectRatio = null
                mp.scale = calculateFillScale(surfaceWidth, surfaceHeight) ?: 0f
            }
            AspectRatioMode.ZOOM -> {
                mp.aspectRatio = null
                mp.scale = calculateFillScale(surfaceWidth, surfaceHeight)?.times(1.1f) ?: 1.2f
            }
            AspectRatioMode.STRETCH -> {
                if (surfaceWidth > 0 && surfaceHeight > 0) {
                    mp.aspectRatio = "${surfaceWidth}:${surfaceHeight}"
                    mp.scale = 0f
                } else {
                    mp.aspectRatio = null
                    mp.scale = 0f
                }
            }
            AspectRatioMode.ORIGINAL -> {
                mp.aspectRatio = null
                mp.scale = 1f
            }
            AspectRatioMode.FORCE_16_9 -> {
                mp.aspectRatio = "16:9"
                mp.scale = 0f
            }
            AspectRatioMode.FORCE_4_3 -> {
                mp.aspectRatio = "4:3"
                mp.scale = 0f
            }
        }
        Timber.d(
            "VLC aspect ratio set: mode=$currentAspectMode, aspectRatio=${mp.aspectRatio}, " +
                "scale=${mp.scale}, surface=${surfaceWidth}x${surfaceHeight}"
        )
    }

    private fun calculateFillScale(surfaceWidth: Int, surfaceHeight: Int): Float? {
        val videoWidth = _state.value.videoWidth
        val videoHeight = _state.value.videoHeight
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return null
        }
        val widthScale = surfaceWidth.toFloat() / videoWidth.toFloat()
        val heightScale = surfaceHeight.toFloat() / videoHeight.toFloat()
        return maxOf(widthScale, heightScale)
    }

    override fun getView(): Any? = mediaPlayer
    fun getMediaPlayer(): MediaPlayer? = mediaPlayer
    fun getLibVLC(): LibVLC? = libVLC

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Event handling
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private fun handleVlcEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                _state.value = _state.value.copy(playbackState = PlaybackState.BUFFERING)
            }
            MediaPlayer.Event.Playing -> {
                ensureDefaultAudioTrackSelected()
                _state.value = _state.value.copy(
                    playbackState = PlaybackState.PLAYING,
                    isPlaying = true,
                    duration = mediaPlayer?.length ?: 0
                )
                updateTrackInfo()
                startProgressTracking()
            }
            MediaPlayer.Event.Paused -> {
                _state.value = _state.value.copy(playbackState = PlaybackState.PAUSED, isPlaying = false)
                stopProgressTracking()
            }
            MediaPlayer.Event.Stopped -> {
                _state.value = _state.value.copy(playbackState = PlaybackState.STOPPED, isPlaying = false)
                stopProgressTracking()
            }
            MediaPlayer.Event.EndReached -> {
                _state.value = _state.value.copy(playbackState = PlaybackState.ENDED, isPlaying = false)
                stopProgressTracking()
            }
            MediaPlayer.Event.EncounteredError -> {
                Timber.e("VLC playback error")
                _state.value = _state.value.copy(
                    playbackState = PlaybackState.ERROR,
                    isPlaying = false,
                    errorMessage = "VLC playback error"
                )
                stopProgressTracking()
            }
            MediaPlayer.Event.Buffering -> {
                val pct = event.buffering
                if (pct < 100f) {
                    _state.value = _state.value.copy(playbackState = PlaybackState.BUFFERING)
                } else {
                    _state.value = _state.value.copy(
                        playbackState = if (_state.value.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
                    )
                }
            }
            MediaPlayer.Event.Vout -> {
                val mp = mediaPlayer ?: return
                val vt = mp.currentVideoTrack
                val vw = vt?.width ?: 0
                val vh = vt?.height ?: 0
                if (vw > 0 && vh > 0) {
                    _state.value = _state.value.copy(videoWidth = vw, videoHeight = vh)
                    currentSurfaceView?.let { sv ->
                        val sw = sv.holder.surfaceFrame.width()
                        val sh = sv.holder.surfaceFrame.height()
                        if (sw > 0 && sh > 0) {
                            try { mp.vlcVout.setWindowSize(sw, sh) } catch (_: Exception) {}
                        }
                    }
                }
                applyAspectRatioMode()
            }
            MediaPlayer.Event.TimeChanged -> {
                val mp = mediaPlayer ?: return
                _state.value = _state.value.copy(
                    currentPosition = mp.time,
                    duration = maxOf(mp.length, 0)
                )
            }
        }
    }

    private fun updateTrackInfo() {
        val mp = mediaPlayer ?: return

        val audioTracks = mp.audioTracks?.mapIndexed { idx, desc ->
            TrackInfo(index = idx, name = desc.name ?: "Audio ${idx + 1}", language = "", isSelected = desc.id == mp.audioTrack)
        } ?: emptyList()

        val subtitleTracks = mp.spuTracks?.mapIndexed { idx, desc ->
            TrackInfo(index = idx, name = desc.name ?: "Subtitle ${idx + 1}", language = "", isSelected = desc.id == mp.spuTrack)
        } ?: emptyList()

        val vt = mp.currentVideoTrack
        val resolution = if (vt != null) "${vt.width}x${vt.height}" else ""
        val codec = vt?.codec ?: ""

        _state.value = _state.value.copy(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            currentVideoResolution = resolution,
            currentVideoCodec = codec
        )
    }

    private fun ensureDefaultAudioTrackSelected() {
        val mp = mediaPlayer ?: return
        val tracks = mp.audioTracks ?: return
        if (tracks.isEmpty()) return

        val selectedTrackId = mp.audioTrack
        if (tracks.none { it.id == selectedTrackId }) {
            mp.audioTrack = tracks.first().id
            Timber.d("VLC applied default audio track fallback")
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope?.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    _state.value = _state.value.copy(
                        currentPosition = mp.time,
                        duration = maxOf(mp.length, 0)
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
