package com.imax.player.core.player

import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.model.PlayerEngineType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class EngineSwitchState {
    IDLE, SWITCHING, SUCCESS, FAILED
}

/**
 * Manages the active player engine and handles switching between ExoPlayer and VLC.
 *
 * KEY DESIGN for ANR prevention:
 * - switchEngine() is fully async — heavy native release runs on Dispatchers.Default
 * - AtomicBoolean guard prevents re-entrant/concurrent switch calls
 * - Old engine is stopped+released BEFORE new engine is initialized
 * - State flow collection is properly cancelled between switches
 */
@Singleton
class PlayerManager @Inject constructor(
    private val exoPlayerEngine: ExoPlayerEngine,
    private val vlcPlayerEngine: VlcPlayerEngine,
    private val settingsDataStore: SettingsDataStore
) {
    private var currentEngine: PlayerEngine = exoPlayerEngine
    private var currentUrl: String = ""
    private var currentPosition: Long = 0

    private val _playerState = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _activeEngineName = MutableStateFlow(currentEngine.engineName)
    val activeEngineName: StateFlow<String> = _activeEngineName.asStateFlow()

    private val _switchState = MutableStateFlow(EngineSwitchState.IDLE)
    val switchState: StateFlow<EngineSwitchState> = _switchState.asStateFlow()

    val engineName: String get() = currentEngine.engineName

    private var stateCollectionJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Atomic guard — prevents concurrent/re-entrant engine switches
    private val isSwitching = AtomicBoolean(false)

    // Cached settings for track preferences
    private var defaultAudioLang: String = "eng"
    private var defaultSubtitleLang: String = "eng"
    private var autoEnableSubtitles: Boolean = false
    private var preferredAspectRatio: AspectRatioMode = AspectRatioMode.FIT
    private var preferredVideoQualityMode: VideoQualityMode = VideoQualityMode.AUTO

    fun getEngine(): PlayerEngine = currentEngine

    private fun startCollectingState() {
        stateCollectionJob?.cancel()
        stateCollectionJob = managerScope.launch {
            currentEngine.state.collect { engineState ->
                _playerState.value = engineState
            }
        }
    }

    suspend fun initializeWithSettings() {
        val settings = settingsDataStore.settings.first()

        defaultAudioLang = settings.defaultAudioLanguage
        defaultSubtitleLang = settings.defaultSubtitleLanguage
        autoEnableSubtitles = settings.autoEnableSubtitles
        preferredAspectRatio = AspectRatioMode.entries.firstOrNull {
            it.name.equals(settings.aspectRatio, ignoreCase = true)
        } ?: AspectRatioMode.FIT
        preferredVideoQualityMode = VideoQualityMode.entries.firstOrNull {
            it.name.equals(settings.videoQualityMode, ignoreCase = true)
        } ?: VideoQualityMode.AUTO

        currentEngine = when (settings.playerEngine) {
            PlayerEngineType.EXOPLAYER -> exoPlayerEngine
            PlayerEngineType.VLC -> {
                if (vlcPlayerEngine.isAvailable()) vlcPlayerEngine
                else {
                    Timber.w("VLC not available, falling back to ExoPlayer")
                    exoPlayerEngine
                }
            }
        }
        _activeEngineName.value = currentEngine.engineName
        
        if (currentEngine is ExoPlayerEngine) {
            currentEngine.initialize()
        } else {
            withContext(Dispatchers.IO) {
                currentEngine.initialize()
            }
        }
        currentEngine.setAspectRatio(preferredAspectRatio)
        currentEngine.setVideoQualityMode(preferredVideoQualityMode)
        startCollectingState()

        if (currentEngine is ExoPlayerEngine) {
            val exo = currentEngine as ExoPlayerEngine
            exo.setPreferredAudioLanguage(defaultAudioLang)
            if (autoEnableSubtitles && defaultSubtitleLang.lowercase() !in listOf("off", "none")) {
                exo.setPreferredSubtitleLanguage(defaultSubtitleLang)
            } else if (defaultSubtitleLang.lowercase() in listOf("off", "none")) {
                exo.disableSubtitles()
            }
        }
    }

    fun play(url: String, startPosition: Long = 0) {
        currentUrl = url
        currentPosition = startPosition
        currentEngine.play(url, startPosition)

        if (currentEngine is VlcPlayerEngine) {
            managerScope.launch {
                delay(2000)
                applyVlcDefaultTracks()
            }
        }
    }

    private fun applyVlcDefaultTracks() {
        val vlc = currentEngine as? VlcPlayerEngine ?: return
        if (defaultAudioLang.isNotBlank() && defaultAudioLang.lowercase() !in listOf("none", "off")) {
            vlc.selectAudioTrackByLanguage(defaultAudioLang)
        }
        val subLang = defaultSubtitleLang.lowercase()
        if (subLang in listOf("off", "none")) {
            vlc.disableSubtitles()
        } else if (autoEnableSubtitles && defaultSubtitleLang.isNotBlank()) {
            vlc.selectSubtitleTrackByLanguage(defaultSubtitleLang)
        }
    }

    /**
     * ASYNC engine switch — THE KEY ANR FIX.
     *
     * Flow:
     * 1. AtomicBoolean guard prevents re-entrant calls
     * 2. State → SWITCHING (UI shows overlay, disables buttons)
     * 3. Save playback state from old engine (Main thread, fast)
     * 4. Stop state collection
     * 5. Stop + release old engine on Dispatchers.Default (OFF Main thread)
     * 6. Initialize new engine on Main (required for ExoPlayer)
     * 7. Start playback on new engine
     * 8. State → SUCCESS
     *
     * If anything fails → rollback to old engine
     */
    fun switchEngine() {
        // Re-entrancy guard — if already switching, ignore
        if (!isSwitching.compareAndSet(false, true)) {
            Timber.w("Engine switch already in progress, ignoring duplicate call")
            return
        }

        _switchState.value = EngineSwitchState.SWITCHING

        val oldEngine = currentEngine
        // Capture state while engine is still alive (fast, Main-safe)
        val savedPosition = try { oldEngine.state.value.currentPosition } catch (_: Exception) { 0L }
        val savedSpeed = try { oldEngine.state.value.playbackSpeed } catch (_: Exception) { 1f }
        val savedAspect = try { oldEngine.state.value.aspectRatioMode } catch (_: Exception) { AspectRatioMode.FIT }
        val savedQualityMode = preferredVideoQualityMode

        managerScope.launch {
            try {
                // Step 1: Stop collecting state from old engine
                stateCollectionJob?.cancel()
                stateCollectionJob = null

                // Step 2: Release old engine SAFELY
                // - ExoPlayer MUST run on Main (prevent codec leak / IllegalStateException)
                // - VLC MUST run on IO (its native JNI calls block for 300+ms)
                if (oldEngine is ExoPlayerEngine) {
                    try {
                        Timber.d("Releasing old ExoPlayer on Main thread")
                        oldEngine.stop()
                        oldEngine.release()
                    } catch (e: Exception) {
                        Timber.w(e, "Error releasing old ExoPlayer")
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        try {
                            Timber.d("Releasing old VLC on IO thread")
                            oldEngine.stop()
                            oldEngine.release()
                        } catch (e: Exception) {
                            Timber.w(e, "Error releasing old VLC")
                        }
                    }
                }

                // Step 3: Determine new engine
                val newEngine = if (oldEngine is ExoPlayerEngine) {
                    if (!vlcPlayerEngine.isAvailable()) {
                        throw IllegalStateException("VLC engine not available on this device")
                    }
                    Timber.d("Switching to VLC engine")
                    vlcPlayerEngine
                } else {
                    Timber.d("Switching to ExoPlayer engine")
                    exoPlayerEngine
                }

                // Step 4: Initialize new engine (VLC on IO, Exo on Main)
                currentEngine = newEngine
                _activeEngineName.value = currentEngine.engineName
                if (newEngine is ExoPlayerEngine) {
                    newEngine.initialize()
                } else {
                    withContext(Dispatchers.IO) {
                        newEngine.initialize()
                    }
                }
                startCollectingState()

                // Step 5: Apply language preferences
                if (newEngine is ExoPlayerEngine) {
                    newEngine.setPreferredAudioLanguage(defaultAudioLang)
                    if (autoEnableSubtitles && defaultSubtitleLang.lowercase() !in listOf("off", "none")) {
                        newEngine.setPreferredSubtitleLanguage(defaultSubtitleLang)
                    }
                }

                // Step 6: Start playback with saved state
                if (currentUrl.isNotEmpty()) {
                    newEngine.play(currentUrl, savedPosition)
                    if (savedSpeed != 1f) newEngine.setPlaybackSpeed(savedSpeed)
                    newEngine.setAspectRatio(savedAspect)
                    newEngine.setVideoQualityMode(savedQualityMode)

                    if (newEngine is VlcPlayerEngine) {
                        launch {
                            delay(2000)
                            applyVlcDefaultTracks()
                        }
                    }
                }

                // Step 7: Persist selection
                launch {
                    val engineType = if (newEngine is ExoPlayerEngine) PlayerEngineType.EXOPLAYER else PlayerEngineType.VLC
                    settingsDataStore.updatePlayerEngine(engineType)
                }

                _switchState.value = EngineSwitchState.SUCCESS
                delay(1500)
                _switchState.value = EngineSwitchState.IDLE

            } catch (e: Exception) {
                Timber.e(e, "Engine switch failed, attempting rollback")
                try {
                    currentEngine = oldEngine
                    _activeEngineName.value = currentEngine.engineName
                    oldEngine.initialize()
                    oldEngine.setAspectRatio(savedAspect)
                    oldEngine.setVideoQualityMode(savedQualityMode)
                    startCollectingState()
                    if (currentUrl.isNotEmpty()) {
                        oldEngine.play(currentUrl, savedPosition)
                        if (savedSpeed != 1f) oldEngine.setPlaybackSpeed(savedSpeed)
                    }
                } catch (rollbackError: Exception) {
                    Timber.e(rollbackError, "Rollback also failed")
                }
                _switchState.value = EngineSwitchState.FAILED
                delay(3000)
                _switchState.value = EngineSwitchState.IDLE
            } finally {
                // Always release the guard
                isSwitching.set(false)
            }
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        preferredAspectRatio = mode
        currentEngine.setAspectRatio(mode)
        managerScope.launch { settingsDataStore.updateAspectRatio(mode.name.lowercase()) }
    }

    fun setVideoQualityMode(mode: VideoQualityMode) {
        preferredVideoQualityMode = mode
        currentEngine.setVideoQualityMode(mode)
        managerScope.launch { settingsDataStore.updateVideoQualityMode(mode.name) }
    }

    fun selectVideoTrack(index: Int) {
        currentEngine.selectVideoTrack(index)
    }

    fun tryFallback(): Boolean {
        if ((currentEngine is ExoPlayerEngine && vlcPlayerEngine.isAvailable()) || currentEngine is VlcPlayerEngine) {
            Timber.d("Attempting player engine fallback from ${currentEngine.engineName}")
            switchEngine()
            return true
        }
        return false
    }

    /**
     * Release player — runs heavy work off Main thread.
     */
    fun release() {
        stateCollectionJob?.cancel()
        val engineToRelease = currentEngine
        if (engineToRelease is ExoPlayerEngine) {
            managerScope.launch(Dispatchers.Main) {
                try {
                    engineToRelease.stop()
                    engineToRelease.release()
                } catch (e: Exception) { Timber.w(e, "Error releasing ExoPlayer") }
            }
        } else {
            managerScope.launch(Dispatchers.IO) {
                try {
                    engineToRelease.stop()
                    engineToRelease.release()
                } catch (e: Exception) { Timber.w(e, "Error releasing VLC") }
            }
        }
    }
}
