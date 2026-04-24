package com.imax.player.core.player

import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.model.PlayerEngineType
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class PlayerManager @Inject constructor(
    private val exoPlayerProvider: Provider<ExoPlayerEngine>,
    private val vlcPlayerProvider: Provider<VlcPlayerEngine>,
    private val settingsDataStore: SettingsDataStore
) {
    private data class PlaybackRequest(
        val url: String,
        val startPosition: Long,
        val profile: PlaybackProfile
    )

    private val managerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val switchMutex = Mutex() // Prevents concurrent engine switching

    private var currentEngine: PlayerEngine? = null
    private var stateCollectionJob: Job? = null
    private var lastPlaybackRequest: PlaybackRequest? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _activeEngineName = MutableStateFlow<String?>(null)
    val activeEngineName: StateFlow<String?> = _activeEngineName.asStateFlow()

    private val _switchState = MutableStateFlow(EngineSwitchState.IDLE)
    val switchState: StateFlow<EngineSwitchState> = _switchState.asStateFlow()

    private var defaultAudioLang: String = "eng"
    private var defaultSubtitleLang: String = "eng"
    private var autoEnableSubtitles: Boolean = false
    private var preferredAspectRatio: AspectRatioMode = AspectRatioMode.FIT
    private var preferredVideoQualityMode: VideoQualityMode = VideoQualityMode.AUTO
    private var defaultPlaybackSpeed: Float = 1f

    private var cachedBufferMs: Long = 30000L
    private var cachedLatencyMode: String = "BALANCED"
    private var cachedPreferHw: Boolean = true

    fun getEngine(): PlayerEngine? = currentEngine
    fun getActiveEngineType(): PlayerEngineType? = activeEngineTypeFor(currentEngine)

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
        defaultPlaybackSpeed = settings.defaultPlaybackSpeed

        cachedBufferMs = settings.bufferDurationMs.toLong()
        cachedLatencyMode = settings.liveLatencyMode
        cachedPreferHw = settings.preferHwDecoding

        switchEngine(PlayerEngineType.fromStoredValue(settings.playerEngine))
    }

    suspend fun updatePreferredEngine(targetType: PlayerEngineType) {
        settingsDataStore.updatePlayerEngine(targetType.name)
        if (activeEngineTypeFor(currentEngine) == targetType) {
            _activeEngineName.value = currentEngine?.engineName
            _switchState.value = EngineSwitchState.SUCCESS
            return
        }

        if (currentEngine != null) {
            switchEngine(targetType)
        }
    }

    private fun startCollectingState() {
        stateCollectionJob?.cancel()
        val engine = currentEngine ?: return
        stateCollectionJob = managerScope.launch {
            engine.state.collect { engineState ->
                _playerState.value = engineState
            }
        }
    }

    /**
     * Safely switches the playback engine.
     * Guarantees old engine is released before the new one is instantiated.
     */
    suspend fun switchEngine(targetType: PlayerEngineType) {
        switchMutex.withLock {
            _switchState.value = EngineSwitchState.SWITCHING

            try {
                val previousState = _playerState.value
                val pendingPlaybackRequest = lastPlaybackRequest?.takeIf {
                    previousState.playbackState !in listOf(
                        PlaybackState.IDLE,
                        PlaybackState.STOPPED,
                        PlaybackState.ENDED,
                        PlaybackState.ERROR
                    ) && previousState.isPlaybackConfirmed
                }?.let { request ->
                    request.copy(
                        startPosition = if (request.profile == PlaybackProfile.LIVE) {
                            0L
                        } else {
                            previousState.currentPosition.coerceAtLeast(0L)
                        }
                    )
                }

                // 1. Stop collecting state
                stateCollectionJob?.cancel()
                stateCollectionJob = null

                // 2. Safely release current engine completely
                currentEngine?.let { oldEngine ->
                    withContext(Dispatchers.Main.immediate) {
                        try {
                            oldEngine.stop()
                        } catch (e: Exception) {
                            Timber.w(e, "Error stopping old engine")
                        }
                    }
                    // VLC native release blocks thread, dispatch it safely if needed.
                    withContext(if (oldEngine is VlcPlayerEngine) Dispatchers.IO else Dispatchers.Main.immediate) {
                        try {
                            oldEngine.release()
                        } catch (e: Exception) {
                            Timber.w(e, "Error releasing old engine")
                        }
                    }
                }
                currentEngine = null
                _activeEngineName.value = null
                _playerState.value = PlayerState(
                    aspectRatioMode = preferredAspectRatio,
                    videoQualityMode = preferredVideoQualityMode,
                    playbackSpeed = defaultPlaybackSpeed
                )

                // 3. Create new engine via Provider (Lazy Init)
                val newEngine = when (targetType) {
                    PlayerEngineType.EXOPLAYER -> exoPlayerProvider.get()
                    PlayerEngineType.VLC -> vlcPlayerProvider.get()
                }

                // Verify availability, fallback if needed
                val finalEngine = if (!newEngine.isAvailable() && targetType == PlayerEngineType.EXOPLAYER) {
                    Timber.w("Requested EXOPLAYER but it's unavailable. Falling back to VLC.")
                    vlcPlayerProvider.get()
                } else {
                    newEngine
                }

                // 4. Initialize new engine
                withContext(Dispatchers.Main.immediate) {
                    finalEngine.setPlaybackConfiguration(cachedBufferMs, cachedLatencyMode, cachedPreferHw)
                    finalEngine.initialize()
                    finalEngine.setAspectRatio(preferredAspectRatio)
                    finalEngine.setVideoQualityMode(preferredVideoQualityMode)
                    finalEngine.setPlaybackSpeed(defaultPlaybackSpeed)

                    // Subtitle/Audio logic
                    if (finalEngine is ExoPlayerEngine) {
                        finalEngine.setPreferredAudioLanguage(defaultAudioLang)
                        if (autoEnableSubtitles && defaultSubtitleLang.lowercase() !in listOf("off", "none")) {
                            finalEngine.setPreferredSubtitleLanguage(defaultSubtitleLang)
                        } else if (defaultSubtitleLang.lowercase() in listOf("off", "none")) {
                            finalEngine.disableSubtitles()
                        }
                    } else if (finalEngine is VlcPlayerEngine) {
                        finalEngine.setPreferredAudioLanguage(defaultAudioLang)
                        if (autoEnableSubtitles && defaultSubtitleLang.lowercase() !in listOf("off", "none")) {
                            finalEngine.setPreferredSubtitleLanguage(defaultSubtitleLang)
                        } else if (defaultSubtitleLang.lowercase() in listOf("off", "none")) {
                            finalEngine.disableSubtitles()
                        }
                    }
                }

                currentEngine = finalEngine
                _activeEngineName.value = finalEngine.engineName
                startCollectingState()

                pendingPlaybackRequest?.let { request ->
                    finalEngine.play(
                        url = request.url,
                        startPosition = request.startPosition,
                        profile = request.profile
                    )
                }

                _switchState.value = EngineSwitchState.SUCCESS
                Timber.d("Successfully switched to engine: ${finalEngine.engineName}")

            } catch (e: Exception) {
                Timber.e(e, "Failed to switch engine")
                currentEngine = null
                _activeEngineName.value = null
                _switchState.value = EngineSwitchState.ERROR
            }
        }
    }

    suspend fun play(
        url: String,
        startPosition: Long = 0L,
        profile: PlaybackProfile = PlaybackProfile.VOD
    ) {
        lastPlaybackRequest = PlaybackRequest(
            url = url,
            startPosition = startPosition,
            profile = profile
        )
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.play(url, startPosition, profile)
            if (defaultPlaybackSpeed != 1f) {
                currentEngine?.setPlaybackSpeed(defaultPlaybackSpeed)
            }
        }
    }

    suspend fun pause() {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.pause()
        }
    }

    suspend fun resume() {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.resume()
        }
    }

    suspend fun stop() {
        lastPlaybackRequest = null
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.stop()
        }
    }

    suspend fun seekTo(position: Long) {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.seekTo(position)
        }
    }

    suspend fun seekForward(ms: Long) {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.seekForward(ms)
        }
    }

    suspend fun seekBackward(ms: Long) {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.seekBackward(ms)
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        defaultPlaybackSpeed = speed
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.setPlaybackSpeed(speed)
        }
    }

    suspend fun selectAudioTrack(index: Int) {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.selectAudioTrack(index)
        }
    }

    suspend fun selectSubtitleTrack(index: Int) {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.selectSubtitleTrack(index)
        }
    }

    suspend fun disableSubtitles() {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.disableSubtitles()
        }
    }

    suspend fun setAspectRatio(mode: AspectRatioMode) {
        preferredAspectRatio = mode
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.setAspectRatio(mode)
        }
        settingsDataStore.updateAspectRatio(mode.name.lowercase())
    }

    suspend fun setVideoQualityMode(mode: VideoQualityMode) {
        preferredVideoQualityMode = mode
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.setVideoQualityMode(mode)
        }
        settingsDataStore.updateVideoQualityMode(mode.name)
    }

    suspend fun selectVideoTrack(index: Int) {
        withContext(Dispatchers.Main.immediate) {
            if (index < 0) {
                currentEngine?.setVideoQualityMode(VideoQualityMode.AUTO)
                return@withContext
            }
            currentEngine?.selectVideoTrack(index)
        }
    }

    fun release() {
        managerScope.launch(Dispatchers.Main.immediate) {
            try {
                switchMutex.withLock {
                    stateCollectionJob?.cancel()
                    stateCollectionJob = null
                    lastPlaybackRequest = null

                    val engineToRelease = currentEngine
                    if (engineToRelease != null) {
                        try {
                            engineToRelease.stop()
                        } catch (exception: Exception) {
                            Timber.w(exception, "Error stopping playback engine")
                        }

                        withContext(if (engineToRelease is VlcPlayerEngine) Dispatchers.IO else Dispatchers.Main.immediate) {
                            try {
                                engineToRelease.release()
                            } catch (exception: Exception) {
                                Timber.w(exception, "Error releasing playback engine")
                            }
                        }
                    }

                    currentEngine = null
                    _activeEngineName.value = null
                    _switchState.value = EngineSwitchState.IDLE
                    _playerState.value = PlayerState(
                        aspectRatioMode = preferredAspectRatio,
                        videoQualityMode = preferredVideoQualityMode,
                        playbackSpeed = defaultPlaybackSpeed
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Error releasing playback pipeline")
            }
        }
    }

    private fun activeEngineTypeFor(engine: PlayerEngine?): PlayerEngineType? {
        return when (engine) {
            is ExoPlayerEngine -> PlayerEngineType.EXOPLAYER
            is VlcPlayerEngine -> PlayerEngineType.VLC
            else -> null
        }
    }
}
