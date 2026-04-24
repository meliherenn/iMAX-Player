package com.imax.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imax.player.core.datastore.AppSettings
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.model.Channel
import com.imax.player.core.model.ContentType
import com.imax.player.core.model.Episode
import com.imax.player.core.model.WatchHistoryItem
import com.imax.player.core.player.AspectRatioMode
import com.imax.player.core.player.PlaybackState
import com.imax.player.core.player.PlaybackProfile
import com.imax.player.core.player.PlayerManager
import com.imax.player.core.player.PlayerState
import com.imax.player.core.player.RetryState
import com.imax.player.core.player.SleepTimerManager
import com.imax.player.core.player.SleepTimerState
import com.imax.player.core.player.StreamRetryManager
import com.imax.player.core.player.VideoQualityMode
import com.imax.player.data.repository.ContentRepository
import com.imax.player.data.repository.EpgRepository
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.live.rankChannelsForMobile

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

data class PlayerSessionState(
    val title: String = "",
    val movie: com.imax.player.core.model.Movie? = null,
    val series: com.imax.player.core.model.Series? = null,
    val currentEpisode: Episode? = null,
    val previousEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
    val currentChannel: Channel? = null,
    val previousChannel: Channel? = null,
    val nextChannel: Channel? = null,
    val availableChannels: List<Channel> = emptyList(),
    val liveGroup: String = ""
)

data class LiveChannelSwitchState(
    val isSwitching: Boolean = false,
    val targetChannelId: Long? = null,
    val targetTitle: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository,
    private val playlistRepository: PlaylistRepository,
    val retryManager: StreamRetryManager,
    val sleepTimer: SleepTimerManager,
    private val epgRepository: EpgRepository
) : ViewModel() {
    val state: StateFlow<PlayerState> = playerManager.state
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val retryState: StateFlow<RetryState> = retryManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RetryState())
    val sleepTimerState = sleepTimer.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepTimerState())
    private val _session = MutableStateFlow(PlayerSessionState())
    val session: StateFlow<PlayerSessionState> = _session.asStateFlow()
    private val _liveChannelSwitch = MutableStateFlow(LiveChannelSwitchState())
    val liveChannelSwitch: StateFlow<LiveChannelSwitchState> = _liveChannelSwitch.asStateFlow()
    private val _playerReady = MutableStateFlow(false)
    val playerReady: StateFlow<Boolean> = _playerReady.asStateFlow()
    private val _currentEpgProgram = MutableStateFlow<com.imax.player.data.parser.EpgProgram?>(null)
    val currentEpgProgram: StateFlow<com.imax.player.data.parser.EpgProgram?> = _currentEpgProgram.asStateFlow()
    private val _nextEpgProgram = MutableStateFlow<com.imax.player.data.parser.EpgProgram?>(null)
    val nextEpgProgram: StateFlow<com.imax.player.data.parser.EpgProgram?> = _nextEpgProgram.asStateFlow()

    private var currentUrl = ""
    private var currentOriginalUrl = ""
    private var currentTitle = ""
    private var currentContentId = 0L
    private var currentContentType = ""
    private var currentGroupContext = ""
    private var isInitialized = false
    private var enableTvPlaybackWorkarounds = false
    private var currentPlaybackCandidates: List<String> = emptyList()
    private var liveChannelSwitchJob: kotlinx.coroutines.Job? = null
    private var pendingLiveChannel: Channel? = null

    private var httpRetryCount = 0
    private val maxHttpRetries = 3

    private companion object {
        private const val LIVE_WARMUP_EXTENSION_MS = 4_000L
        private const val LIVE_WARMUP_MIN_PROGRESS_MS = 2_000L
        private const val LIVE_WARMUP_MIN_BUFFER_AHEAD_MS = 500L
    }

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                saveProgressInternal()
            }
        }

        viewModelScope.launch {
            state
                .map { it.playbackState }
                .distinctUntilChanged()
                .collect { playbackState ->
                    if (playbackState == PlaybackState.ERROR && !retryManager.state.value.isRetrying) {
                        val errorMessage = state.value.errorMessage
                        val isHttpError = errorMessage?.contains("503") == true ||
                            errorMessage?.contains("HTTP_STATUS") == true ||
                            errorMessage?.contains("BAD_HTTP") == true
                        val isNonRetryablePlaybackError = isNonRetryablePlaybackError(errorMessage)
                        
                        if (isHttpError && httpRetryCount < maxHttpRetries) {
                            httpRetryCount++
                            viewModelScope.launch {
                                delay(2000L * httpRetryCount)
                                Timber.d("HTTP error retry $httpRetryCount/$maxHttpRetries")
                                if (currentUrl.isNotBlank()) {
                                    playerManager.play(
                                        url = currentUrl,
                                        startPosition = 0L,
                                        profile = currentPlaybackProfile()
                                    )
                                }
                            }
                            return@collect
                        } else {
                            httpRetryCount = 0
                            handlePlaybackError(errorMessage)

                            val autoRetry = settings.value.liveReconnectOnFailure
                            if (autoRetry &&
                                currentContentType == ContentType.LIVE.name &&
                                !isNonRetryablePlaybackError
                            ) {
                                retryManager.startRetry(
                                    errorMessage = errorMessage,
                                    onRetry = { retryCurrent() },
                                    onExhausted = { Timber.w("Auto-retry exhausted") }
                                )
                            } else if (isNonRetryablePlaybackError &&
                                currentContentType == ContentType.LIVE.name
                            ) {
                                _liveChannelSwitch.value = LiveChannelSwitchState(
                                    errorMessage = errorMessage ?: buildPlaybackFailureMessage(
                                        currentTitle.ifBlank { "This channel" }
                                    )
                                )
                            }
                        }
                    } else if (state.value.isPlaybackConfirmed) {
                        httpRetryCount = 0
                        retryManager.onPlaybackSuccess()
                    }
                }
        }
    }

    private fun handlePlaybackError(errorMessage: String?) {
        Timber.w("Playback error handled: $errorMessage")
        // Engine selection is explicit; playback recovery is handled by the
        // currently active engine plus retryManager when enabled.
    }

    private fun isNonRetryablePlaybackError(errorMessage: String?): Boolean {
        val normalized = errorMessage?.lowercase() ?: return false
        return normalized.contains("not supported on this device") ||
            normalized.contains("no_exceeds_capabilities") ||
            normalized.contains("video/hevc") ||
            normalized.contains("hvc1") ||
            normalized.contains("hev1") ||
            normalized.contains("10bit")
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimer.start(minutes) {
            viewModelScope.launch {
                playerManager.pause()
            }
        }
    }

    fun cancelSleepTimer() = sleepTimer.cancel()

    fun extendSleepTimer(minutes: Int) = sleepTimer.extend(minutes)

    fun loadEpg(epgChannelId: String) {
        if (epgChannelId.isBlank()) return
        viewModelScope.launch {
            _currentEpgProgram.value = epgRepository.getCurrentProgram(epgChannelId)
            _nextEpgProgram.value = epgRepository.getNextProgram(epgChannelId)
        }
    }

    fun init(
        url: String,
        title: String,
        startPos: Long,
        contentId: Long,
        contentType: String,
        groupContext: String
    ) {
        if (
            currentOriginalUrl == url &&
            currentTitle == title &&
            currentContentId == contentId &&
            currentContentType == contentType &&
            currentGroupContext == groupContext &&
            state.value.playbackState != PlaybackState.IDLE
        ) {
            return
        }

        val playbackCandidates = resolvePlaybackCandidates(url, contentType)
        val resolvedUrl = playbackCandidates.firstOrNull().orEmpty()

        currentOriginalUrl = url
        currentUrl = resolvedUrl
        currentPlaybackCandidates = playbackCandidates
        currentTitle = title
        currentContentId = contentId
        currentContentType = contentType
        currentGroupContext = groupContext

        viewModelScope.launch {
            clearLiveChannelSwitchError()
            if (!isInitialized) {
                _playerReady.value = false
                playerManager.initializeWithSettings()
                isInitialized = true
                _playerReady.value = true
            }
            refreshSessionContext(url, title, contentId, contentType, groupContext)

            val playbackStarted = if (contentType.equals(ContentType.LIVE.name, ignoreCase = true)) {
                startLiveChannelPlayback(playbackCandidates, startPos)
            } else {
                playerManager.play(
                    url = resolvedUrl,
                    startPosition = startPos,
                    profile = currentPlaybackProfile()
                )
                true
            }

            if (playbackStarted && contentType == ContentType.LIVE.name && contentId > 0L) {
                contentRepository.updateChannelLastWatched(contentId)
            } else if (!playbackStarted) {
                _liveChannelSwitch.value = LiveChannelSwitchState(
                    errorMessage = state.value.errorMessage
                        ?: buildPlaybackFailureMessage(title.ifBlank { "This channel" })
                )
            }
        }
    }

    fun configureTvPlayback(enabled: Boolean) {
        enableTvPlaybackWorkarounds = enabled
    }

    fun clearLiveChannelSwitchError() {
        if (_liveChannelSwitch.value.errorMessage != null && !_liveChannelSwitch.value.isSwitching) {
            _liveChannelSwitch.value = LiveChannelSwitchState()
        }
    }

    fun togglePlayPause() {
        val snapshot = state.value
        viewModelScope.launch {
            if (snapshot.isPlaying) {
                playerManager.pause()
            } else {
                playerManager.resume()
            }
        }
    }

    fun seekForward() {
        viewModelScope.launch {
            playerManager.seekForward(settings.value.seekForwardMs)
        }
    }

    fun seekBackward() {
        viewModelScope.launch {
            playerManager.seekBackward(settings.value.seekBackwardMs)
        }
    }

    fun seekTo(pos: Long) {
        viewModelScope.launch {
            playerManager.seekTo(pos)
        }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch {
            playerManager.setPlaybackSpeed(speed)
        }
    }

    fun selectAudio(index: Int) {
        viewModelScope.launch {
            playerManager.selectAudioTrack(index)
        }
    }

    fun selectSubtitle(index: Int) {
        viewModelScope.launch {
            playerManager.selectSubtitleTrack(index)
        }
    }

    fun disableSubtitles() {
        viewModelScope.launch {
            playerManager.disableSubtitles()
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        viewModelScope.launch {
            playerManager.setAspectRatio(mode)
        }
    }

    fun setVideoQualityMode(mode: VideoQualityMode) {
        viewModelScope.launch {
            playerManager.setVideoQualityMode(mode)
        }
    }

    fun selectVideoTrack(index: Int) {
        viewModelScope.launch {
            playerManager.selectVideoTrack(index)
        }
    }

    fun saveProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            saveProgressInternal()
        }
    }

    fun retryCurrent() {
        viewModelScope.launch {
            clearLiveChannelSwitchError()
            val startPosition = if (currentContentType == ContentType.LIVE.name) 0L else state.value.currentPosition
            if (currentContentType == ContentType.LIVE.name) {
                val candidates = currentPlaybackCandidates.ifEmpty {
                    resolvePlaybackCandidates(currentOriginalUrl.ifBlank { currentUrl }, currentContentType)
                }
                val started = startLiveChannelPlayback(candidates, startPosition)
                if (!started) {
                    _liveChannelSwitch.value = LiveChannelSwitchState(
                        errorMessage = state.value.errorMessage
                            ?: buildPlaybackFailureMessage(currentTitle.ifBlank { "This channel" })
                    )
                }
            } else {
                playerManager.play(
                    url = currentUrl,
                    startPosition = startPosition,
                    profile = currentPlaybackProfile()
                )
            }
        }
    }

    fun playNextEpisode() {
        session.value.nextEpisode?.let(::playEpisode)
    }

    fun playPreviousEpisode() {
        session.value.previousEpisode?.let(::playEpisode)
    }

    fun playNextChannel() {
        session.value.nextChannel?.let(::playChannel)
    }

    fun playPreviousChannel() {
        session.value.previousChannel?.let(::playChannel)
    }

    fun playChannel(channel: Channel) {
        if (
            currentContentType == ContentType.LIVE.name &&
            (currentContentId == channel.id || session.value.currentChannel?.id == channel.id)
        ) {
            Timber.d("LivePlayback playChannel ignored; already on channel=%s", channel.name)
            return
        }

        clearLiveChannelSwitchError()
        if (_liveChannelSwitch.value.isSwitching) {
            Timber.d(
                "LivePlayback queueing pending channel while switching current=%s pending=%s",
                _liveChannelSwitch.value.targetTitle,
                channel.name
            )
            pendingLiveChannel = channel
            return
        }

        Timber.d("LivePlayback scheduling channel switch to %s", channel.name)
        liveChannelSwitchJob?.cancel()
        liveChannelSwitchJob = viewModelScope.launch(Dispatchers.IO) {
            processLiveChannelSwitchQueue(channel)
        }
    }

    fun exitPlayer(onBack: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            saveProgressInternal()
        }
        onBack()
        playerManager.release()
    }

    override fun onCleared() {
        super.onCleared()
        liveChannelSwitchJob?.cancel()
        saveProgress()
        retryManager.cancel()
        playerManager.release()
    }

    private fun playEpisode(episode: Episode) {
        viewModelScope.launch {
            val series = session.value.series ?: contentRepository.getSeriesById(episode.seriesId)
            switchToContent(
                url = episode.streamUrl,
                title = buildEpisodePlayerTitle(series?.name.orEmpty(), episode),
                contentId = episode.id,
                contentType = ContentType.SERIES.name,
                startPosition = episode.lastPosition,
                groupContext = ""
            )
        }
    }

    private suspend fun processLiveChannelSwitchQueue(initialChannel: Channel) {
        var nextChannel: Channel? = initialChannel

        while (nextChannel != null) {
            Timber.d("LivePlayback processing queued channel=%s", nextChannel.name)
            pendingLiveChannel = null
            switchLiveChannel(nextChannel)
            nextChannel = pendingLiveChannel?.takeIf { it.id != currentContentId }
        }
    }

    private suspend fun switchToContent(
        url: String,
        title: String,
        contentId: Long,
        contentType: String,
        startPosition: Long,
        groupContext: String
    ) {
        withContext(Dispatchers.IO) {
            saveProgressInternal()
        }

        val resolvedUrl = resolvePlaybackUrl(url, contentType)
        currentOriginalUrl = url
        currentUrl = resolvedUrl
        currentTitle = title
        currentContentId = contentId
        currentContentType = contentType
        currentGroupContext = groupContext

        refreshSessionContext(resolvedUrl, title, contentId, contentType, groupContext)
        playerManager.play(
            url = resolvedUrl,
            startPosition = startPosition,
            profile = playbackProfileFor(contentType)
        )

        if (contentType == ContentType.LIVE.name && contentId > 0L) {
            contentRepository.updateChannelLastWatched(contentId)
        }
    }

    private suspend fun switchLiveChannel(channel: Channel) {
        if (_liveChannelSwitch.value.isSwitching) return

        val previousUrl = currentUrl
        val previousOriginalUrl = currentOriginalUrl
        val previousTitle = currentTitle
        val previousContentId = currentContentId
        val previousContentType = currentContentType
        val previousGroupContext = currentGroupContext
        val previousPlaybackCandidates = currentPlaybackCandidates
        val previousSession = session.value
        val targetGroupContext = currentGroupContext.ifBlank { channel.groupTitle }
        val playbackCandidates = resolvePlaybackCandidates(channel.streamUrl, ContentType.LIVE.name)

        _liveChannelSwitch.value = LiveChannelSwitchState(
            isSwitching = true,
            targetChannelId = channel.id,
            targetTitle = channel.name
        )

        var switchErrorMessage: String? = null

        try {
            coroutineScope {
                currentOriginalUrl = channel.streamUrl
                currentUrl = playbackCandidates.firstOrNull().orEmpty()
                currentPlaybackCandidates = playbackCandidates
                currentTitle = channel.name
                currentContentId = channel.id
                currentContentType = ContentType.LIVE.name
                currentGroupContext = targetGroupContext

                val started = startLiveChannelPlayback(playbackCandidates)
                if (!started) {
                    throw IllegalStateException("Live channel switch did not reach a stable playback state")
                }

                _liveChannelSwitch.value = LiveChannelSwitchState()

                refreshSessionContext(
                    url = channel.streamUrl,
                    title = channel.name,
                    contentId = channel.id,
                    contentType = ContentType.LIVE.name,
                    groupContext = targetGroupContext
                )
                contentRepository.updateChannelLastWatched(channel.id)
            }
        } catch (cancelled: CancellationException) {
            currentOriginalUrl = previousOriginalUrl
            currentUrl = previousUrl
            currentTitle = previousTitle
            currentContentId = previousContentId
            currentContentType = previousContentType
            currentGroupContext = previousGroupContext
            currentPlaybackCandidates = previousPlaybackCandidates
            _session.value = previousSession
            throw cancelled
        } catch (_: Exception) {
            currentOriginalUrl = previousOriginalUrl
            currentUrl = previousUrl
            currentTitle = previousTitle
            currentContentId = previousContentId
            currentContentType = previousContentType
            currentGroupContext = previousGroupContext
            currentPlaybackCandidates = previousPlaybackCandidates
            _session.value = previousSession
            switchErrorMessage = state.value.errorMessage ?: buildPlaybackFailureMessage(channel.name)

            if (previousUrl.isNotBlank()) {
                playerManager.play(
                    url = previousUrl,
                    startPosition = 0L,
                    profile = PlaybackProfile.LIVE
                )
            }
        } finally {
            _liveChannelSwitch.value = if (switchErrorMessage.isNullOrBlank()) {
                LiveChannelSwitchState()
            } else {
                LiveChannelSwitchState(errorMessage = switchErrorMessage)
            }
        }
    }

    private suspend fun startLiveChannelPlayback(
        candidates: List<String>,
        startPosition: Long = 0L
    ): Boolean {
        val uniqueCandidates = candidates
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        uniqueCandidates.forEachIndexed { candidateIndex, candidate ->
            val attempts = if (candidateIndex == 0) 2 else 1

            for (attempt in 0 until attempts) {
                Timber.d("LivePlayback: trying candidate[$candidateIndex] attempt[$attempt] url=$candidate")
                resetPlaybackAttempt()
                delay(if (attempt == 0) 140L else 220L)
                playerManager.play(
                    url = candidate,
                    startPosition = startPosition,
                    profile = PlaybackProfile.LIVE
                )

                val playbackConfirmed = awaitConfirmedPlaybackReady(
                    timeoutMs = if (attempt == 0) 7_500L else 8_500L
                )

                if (playbackConfirmed) {
                    currentUrl = candidate
                    clearLiveChannelSwitchError()
                    return true
                } else {
                    Timber.w("LivePlayback candidate failed: $candidate")

                    if (isUnavailableHttpStatus(state.value.errorMessage)) {
                        Timber.w("LivePlayback HTTP unavailable, skipping candidate: ${state.value.errorMessage}")
                        resetPlaybackAttempt()
                        break
                    }

                    if (!shouldRetryCurrentLiveCandidate(candidate, state.value)) {
                        Timber.w("LivePlayback skipping repeated attempt for non-rendering candidate: $candidate")
                        resetPlaybackAttempt()
                        break
                    }
                    resetPlaybackAttempt()
                }
            }
        }

        return false
    }

    private suspend fun refreshSessionContext(
        url: String,
        title: String,
        contentId: Long,
        contentType: String,
        groupContext: String
    ) {
        val updatedSession = withContext(Dispatchers.IO) {
            when (contentType.uppercase()) {
                ContentType.MOVIE.name -> {
                    val movie = contentRepository.getMovie(contentId)
                    PlayerSessionState(
                        title = movie?.name ?: title,
                        movie = movie
                    )
                }

                ContentType.SERIES.name -> {
                    val episode = contentRepository.getEpisode(contentId)
                    val series = episode?.let { contentRepository.getSeriesById(it.seriesId) }
                    val episodes = when {
                        episode == null -> emptyList()
                        else -> {
                            val localEpisodes = contentRepository.getAllEpisodes(episode.seriesId)
                            if (localEpisodes.isNotEmpty()) {
                                localEpisodes
                            } else if (series != null) {
                                contentRepository.syncSeriesEpisodes(series)
                                contentRepository.getAllEpisodes(series.id)
                            } else {
                                emptyList()
                            }
                        }
                    }
                    val currentIndex = episodes.indexOfFirst { it.id == episode?.id }

                    PlayerSessionState(
                        title = if (episode != null) {
                            buildEpisodePlayerTitle(series?.name.orEmpty(), episode)
                        } else {
                            title
                        },
                        series = series,
                        currentEpisode = episode,
                        previousEpisode = episodes.getOrNull(currentIndex - 1),
                        nextEpisode = episodes.getOrNull(currentIndex + 1)
                    )
                }

                ContentType.LIVE.name -> {
                    val currentChannel = contentRepository.getChannel(contentId)
                    val playlist = playlistRepository.getActivePlaylist().first()
                    val scopedChannels = if (playlist != null && groupContext.isNotBlank()) {
                        contentRepository.getChannelsByGroup(playlist.id, groupContext).first()
                    } else {
                        emptyList()
                    }
                    val primaryChannels = when {
                        scopedChannels.isNotEmpty() -> scopedChannels
                        playlist != null -> contentRepository.getChannels(playlist.id).first()
                        else -> emptyList()
                    }
                    val rankedChannels = rankChannelsForMobile(primaryChannels)
                    val currentIndex = rankedChannels.indexOfFirst { channel ->
                        channel.id == contentId || channel.streamUrl == url
                    }

                    PlayerSessionState(
                        title = currentChannel?.name ?: title,
                        currentChannel = currentChannel,
                        previousChannel = rankedChannels.getOrNull(currentIndex - 1),
                        nextChannel = rankedChannels.getOrNull(currentIndex + 1),
                        availableChannels = rankedChannels,
                        liveGroup = groupContext
                    )
                }

                else -> PlayerSessionState(title = title)
            }
        }

        _session.value = updatedSession
    }

    private suspend fun saveProgressInternal() {
        val snapshot = state.value
        if (currentContentId <= 0L) return

        when (currentContentType.uppercase()) {
            ContentType.MOVIE.name -> saveMovieProgress(snapshot)
            ContentType.SERIES.name -> saveSeriesProgress(snapshot)
            ContentType.LIVE.name -> contentRepository.updateChannelLastWatched(currentContentId)
        }
    }

    private suspend fun saveMovieProgress(snapshot: PlayerState) {
        if (snapshot.currentPosition <= 0L) return

        val movie = session.value.movie ?: contentRepository.getMovie(currentContentId) ?: return
        val totalDuration = snapshot.duration.takeIf { it > 0L } ?: movie.totalDuration
        val progress = calculateProgress(snapshot.currentPosition, totalDuration)

        contentRepository.updateMovieProgress(movie.id, snapshot.currentPosition, totalDuration)
        contentRepository.addWatchHistory(
            WatchHistoryItem(
                contentId = movie.id,
                contentType = ContentType.MOVIE,
                title = movie.name,
                posterUrl = movie.posterUrl,
                streamUrl = movie.streamUrl,
                position = snapshot.currentPosition,
                totalDuration = totalDuration,
                progress = progress
            )
        )
    }

    private suspend fun saveSeriesProgress(snapshot: PlayerState) {
        if (snapshot.currentPosition <= 0L) return

        val currentEpisode = session.value.currentEpisode ?: contentRepository.getEpisode(currentContentId) ?: return
        val currentSeries = session.value.series ?: contentRepository.getSeriesById(currentEpisode.seriesId)
        val totalDuration = snapshot.duration.takeIf { it > 0L } ?: currentEpisode.totalDuration
        val progress = calculateProgress(snapshot.currentPosition, totalDuration)

        contentRepository.updateEpisodeProgress(currentEpisode.id, snapshot.currentPosition, totalDuration)
        contentRepository.updateSeriesLastWatchedEpisode(currentEpisode.seriesId, currentEpisode.id)
        contentRepository.addWatchHistory(
            WatchHistoryItem(
                contentId = currentEpisode.id,
                contentType = ContentType.SERIES,
                title = currentEpisode.name.ifBlank { "Episode ${currentEpisode.episodeNumber}" },
                posterUrl = currentEpisode.posterUrl.ifBlank { currentSeries?.posterUrl.orEmpty() },
                streamUrl = currentEpisode.streamUrl,
                position = snapshot.currentPosition,
                totalDuration = totalDuration,
                progress = progress,
                seasonNumber = currentEpisode.seasonNumber,
                episodeNumber = currentEpisode.episodeNumber,
                seriesName = currentSeries?.name.orEmpty()
            )
        )
    }

    private fun calculateProgress(position: Long, totalDuration: Long): Float {
        return if (position > 0L && totalDuration > 0L) {
            (position.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    private fun resolvePlaybackUrl(url: String, contentType: String): String {
        val trimmedUrl = url.trim().removeSuffix(".")

        return when {
            contentType.equals(ContentType.LIVE.name, ignoreCase = true) &&
                trimmedUrl.contains("/live/") &&
                trimmedUrl.endsWith(".m3u8", ignoreCase = true) -> {
                trimmedUrl.removeSuffix(".m3u8") + ".ts"
            }

            else -> trimmedUrl
        }
    }

    private fun resolvePlaybackCandidates(url: String, contentType: String): List<String> {
        val trimmedUrl = url.trim().removeSuffix(".")
        val defaultResolvedUrl = resolvePlaybackUrl(trimmedUrl, contentType)

        if (!contentType.equals(ContentType.LIVE.name, ignoreCase = true)) {
            return listOf(defaultResolvedUrl)
        }

        return buildList {
            add(defaultResolvedUrl)
            if (trimmedUrl != defaultResolvedUrl) {
                add(trimmedUrl)
            }
            if (trimmedUrl.contains("/live/") && trimmedUrl.endsWith(".m3u8", ignoreCase = true)) {
                add(trimmedUrl.removeSuffix(".m3u8") + ".ts")
            }
            if (defaultResolvedUrl.contains("/live/") && defaultResolvedUrl.endsWith(".ts", ignoreCase = true)) {
                add(defaultResolvedUrl.removeSuffix(".ts") + ".m3u8")
            }
            if (trimmedUrl.contains("/live/") && trimmedUrl.endsWith(".ts", ignoreCase = true)) {
                add(trimmedUrl.removeSuffix(".ts") + ".m3u8")
            }
            if (enableTvPlaybackWorkarounds && trimmedUrl != defaultResolvedUrl) {
                add(defaultResolvedUrl)
            }
        }.map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun buildPlaybackFailureMessage(title: String): String {
        return "$title could not reach confirmed playback."
    }

    private suspend fun awaitConfirmedPlaybackReady(
        timeoutMs: Long,
        confirmationWindowMs: Long = 900L
    ): Boolean {
        var remainingTimeoutMs = timeoutMs
        var warmupExtensionApplied = false
        var reachedCandidateState: PlayerState? = null

        while (reachedCandidateState == null) {
            val candidateState = withTimeoutOrNull(remainingTimeoutMs) {
                state.first { snapshot ->
                    snapshot.playbackState == PlaybackState.ERROR || snapshot.isPlaybackConfirmed
                }
            }

            if (candidateState != null) {
                reachedCandidateState = candidateState
                continue
            }

            val snapshot = state.value

            if (!warmupExtensionApplied && shouldExtendLiveWarmup(snapshot)) {
                warmupExtensionApplied = true
                remainingTimeoutMs = LIVE_WARMUP_EXTENSION_MS
                Timber.w(
                    "LivePlayback confirmation warmup extended: state=%s video=%sx%s firstFrame=%s surfaceReady=%s audioSession=%d position=%d buffered=%d",
                    snapshot.playbackState,
                    snapshot.videoWidth,
                    snapshot.videoHeight,
                    snapshot.hasRenderedFirstFrame,
                    snapshot.isSurfaceReady,
                    snapshot.audioSessionId,
                    snapshot.currentPosition,
                    snapshot.bufferedPosition
                )
                continue
            }

            Timber.w(
                "LivePlayback confirmation timed out: state=%s playing=%s confirmed=%s video=%sx%s firstFrame=%s surfaceReady=%s audioSession=%d position=%d buffered=%d",
                snapshot.playbackState,
                snapshot.isPlaying,
                snapshot.isPlaybackConfirmed,
                snapshot.videoWidth,
                snapshot.videoHeight,
                snapshot.hasRenderedFirstFrame,
                snapshot.isSurfaceReady,
                snapshot.audioSessionId,
                snapshot.currentPosition,
                snapshot.bufferedPosition
            )
            return false
        }

        val confirmedState = reachedCandidateState ?: return false
        if (confirmedState.playbackState == PlaybackState.ERROR) {
            Timber.w("LivePlayback failed with player error before confirmation: %s", confirmedState.errorMessage)
            return false
        }

        delay(confirmationWindowMs)
        val confirmationSnapshot = state.value
        val stablePlayback = isStableLivePlaybackAfterConfirmation(
            confirmationSnapshot = confirmationSnapshot,
            initiallyConfirmedSnapshot = confirmedState
        )
        if (!stablePlayback) {
            Timber.w(
                "LivePlayback confirmation window expired without stable playback: state=%s confirmed=%s video=%sx%s firstFrame=%s surfaceReady=%s audioSession=%d position=%d buffered=%d",
                confirmationSnapshot.playbackState,
                confirmationSnapshot.isPlaybackConfirmed,
                confirmationSnapshot.videoWidth,
                confirmationSnapshot.videoHeight,
                confirmationSnapshot.hasRenderedFirstFrame,
                confirmationSnapshot.isSurfaceReady,
                confirmationSnapshot.audioSessionId,
                confirmationSnapshot.currentPosition,
                confirmationSnapshot.bufferedPosition
            )
        }
        return stablePlayback
    }

    private fun shouldExtendLiveWarmup(snapshot: PlayerState): Boolean {
        if (!snapshot.hasVideoTrack ||
            !snapshot.hasAudioTrack ||
            snapshot.playbackState == PlaybackState.ERROR
        ) {
            return false
        }

        if (snapshot.hasRenderedFirstFrame ||
            snapshot.videoWidth > 0 ||
            snapshot.videoHeight > 0 ||
            !snapshot.isSurfaceReady ||
            snapshot.audioSessionId <= 0
        ) {
            return false
        }

        val bufferedAheadMs =
            (snapshot.bufferedPosition - snapshot.currentPosition).coerceAtLeast(0L)

        return snapshot.currentPosition >= LIVE_WARMUP_MIN_PROGRESS_MS &&
            bufferedAheadMs >= LIVE_WARMUP_MIN_BUFFER_AHEAD_MS
    }

    private fun isUnavailableHttpStatus(errorMessage: String?): Boolean {
        val message = errorMessage.orEmpty()
        return listOf(403, 404, 410, 429, 503).any { code ->
            message.contains("Response code: $code") ||
                message.contains("HTTP $code") ||
                message.contains("code: $code")
        }
    }

    private fun shouldRetryCurrentLiveCandidate(candidate: String, snapshot: PlayerState): Boolean {
        if (!candidate.contains("/live/")) {
            return true
        }

        val videoPipelineStalled = snapshot.hasVideoTrack &&
            snapshot.hasAudioTrack &&
            snapshot.isPlaying &&
            snapshot.isSurfaceReady &&
            snapshot.audioSessionId > 0 &&
            !snapshot.hasRenderedFirstFrame &&
            snapshot.videoWidth == 0 &&
            snapshot.videoHeight == 0 &&
            snapshot.currentPosition >= LIVE_WARMUP_MIN_PROGRESS_MS

        return !videoPipelineStalled
    }

    private fun isStableLivePlaybackAfterConfirmation(
        confirmationSnapshot: PlayerState,
        initiallyConfirmedSnapshot: PlayerState
    ): Boolean {
        if (confirmationSnapshot.playbackState == PlaybackState.PLAYING &&
            confirmationSnapshot.isPlaybackConfirmed
        ) {
            return true
        }

        val wasConfirmed = initiallyConfirmedSnapshot.isPlaybackConfirmed
        if (!wasConfirmed || confirmationSnapshot.playbackState != PlaybackState.BUFFERING) {
            return false
        }

        val hasRenderableVideo = confirmationSnapshot.hasRenderedFirstFrame &&
            confirmationSnapshot.isSurfaceReady &&
            confirmationSnapshot.videoWidth > 0 &&
            confirmationSnapshot.videoHeight > 0
        val hasAudiblePlayback = confirmationSnapshot.audioSessionId > 0
        val advancedPlayback = confirmationSnapshot.currentPosition >= 250L
        val bufferedAheadMs =
            (confirmationSnapshot.bufferedPosition - confirmationSnapshot.currentPosition).coerceAtLeast(0L)

        val transientRebufferAccepted = advancedPlayback &&
            bufferedAheadMs >= 1_500L &&
            ((confirmationSnapshot.hasVideoTrack && hasRenderableVideo) ||
                (!confirmationSnapshot.hasVideoTrack && hasAudiblePlayback))

        if (transientRebufferAccepted) {
            Timber.d(
                "LivePlayback accepted after confirmed start despite transient buffering: position=%d bufferedAhead=%d video=%s audio=%s",
                confirmationSnapshot.currentPosition,
                bufferedAheadMs,
                confirmationSnapshot.hasVideoTrack,
                confirmationSnapshot.hasAudioTrack
            )
        }

        return transientRebufferAccepted
    }

    private suspend fun resetPlaybackAttempt() {
        playerManager.stop()
        delay(80L)
    }

    private fun playbackProfileFor(contentType: String): PlaybackProfile {
        return if (contentType.equals(ContentType.LIVE.name, ignoreCase = true)) {
            PlaybackProfile.LIVE
        } else {
            PlaybackProfile.VOD
        }
    }

    private fun currentPlaybackProfile(): PlaybackProfile {
        return playbackProfileFor(currentContentType)
    }

    private fun buildEpisodePlayerTitle(seriesName: String, episode: Episode): String {
        val prefix = buildString {
            if (seriesName.isNotBlank()) {
                append(seriesName)
                append(" ")
            }
            append("S${episode.seasonNumber}E${episode.episodeNumber}")
        }

        return if (episode.name.isNotBlank()) {
            "$prefix • ${episode.name}"
        } else {
            prefix
        }
    }
}
