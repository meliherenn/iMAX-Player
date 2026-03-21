package com.imax.player.ui.player

import android.app.Activity
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.imax.player.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.imax.player.core.common.StringUtils
import com.imax.player.core.datastore.AppSettings
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.*
import com.imax.player.core.player.*
import com.imax.player.data.repository.ContentRepository
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.live.rankChannelsForMobile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class PlayerSessionState(
    val title: String = "",
    val movie: Movie? = null,
    val series: Series? = null,
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
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    val state: StateFlow<PlayerState> = playerManager.state
    val switchState: StateFlow<EngineSwitchState> = playerManager.switchState
    val activeEngineName: StateFlow<String> = playerManager.activeEngineName
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    private val _session = MutableStateFlow(PlayerSessionState())
    val session: StateFlow<PlayerSessionState> = _session.asStateFlow()
    private val _liveChannelSwitch = MutableStateFlow(LiveChannelSwitchState())
    val liveChannelSwitch: StateFlow<LiveChannelSwitchState> = _liveChannelSwitch.asStateFlow()
    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady.asStateFlow()

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

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                saveProgressInternal()
            }
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
                _engineReady.value = false
                playerManager.initializeWithSettings()
                isInitialized = true
                _engineReady.value = true
            }
            refreshSessionContext(url, title, contentId, contentType, groupContext)

            val playbackStarted = if (
                enableTvPlaybackWorkarounds &&
                contentType.equals(ContentType.LIVE.name, ignoreCase = true)
            ) {
                startLiveChannelPlayback(playbackCandidates, startPos)
            } else {
                playerManager.play(resolvedUrl, startPos)
                true
            }

            if (playbackStarted && contentType == ContentType.LIVE.name && contentId > 0L) {
                contentRepository.updateChannelLastWatched(contentId)
            } else if (!playbackStarted) {
                _liveChannelSwitch.value = LiveChannelSwitchState(
                    errorMessage = buildPlaybackFailureMessage(title.ifBlank { "This channel" })
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
        val s = state.value
        if (s.isPlaying) playerManager.getEngine().pause() else playerManager.getEngine().resume()
    }

    fun seekForward() {
        playerManager.getEngine().seekForward(settings.value.seekForwardMs)
    }

    fun seekBackward() {
        playerManager.getEngine().seekBackward(settings.value.seekBackwardMs)
    }

    fun seekTo(pos: Long) = playerManager.getEngine().seekTo(pos)
    fun setSpeed(speed: Float) = playerManager.getEngine().setPlaybackSpeed(speed)
    fun selectAudio(index: Int) = playerManager.getEngine().selectAudioTrack(index)
    fun selectSubtitle(index: Int) = playerManager.getEngine().selectSubtitleTrack(index)
    fun disableSubtitles() = playerManager.getEngine().disableSubtitles()

    fun setAspectRatio(mode: AspectRatioMode) {
        playerManager.setAspectRatio(mode)
    }

    fun setVideoQualityMode(mode: VideoQualityMode) {
        playerManager.setVideoQualityMode(mode)
    }

    fun selectVideoTrack(index: Int) {
        playerManager.selectVideoTrack(index)
    }

    fun switchEngine() {
        playerManager.switchEngine()
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
            if (enableTvPlaybackWorkarounds && currentContentType == ContentType.LIVE.name) {
                val candidates = currentPlaybackCandidates.ifEmpty {
                    resolvePlaybackCandidates(currentOriginalUrl.ifBlank { currentUrl }, currentContentType)
                }
                val started = startLiveChannelPlayback(candidates, startPosition)
                if (!started) {
                    _liveChannelSwitch.value = LiveChannelSwitchState(
                        errorMessage = buildPlaybackFailureMessage(currentTitle.ifBlank { "This channel" })
                    )
                }
            } else {
                playerManager.play(currentUrl, startPosition)
            }
        }
    }

    fun playNextEpisode() {
        session.value.nextEpisode?.let { episode ->
            playEpisode(episode)
        }
    }

    fun playPreviousEpisode() {
        session.value.previousEpisode?.let { episode ->
            playEpisode(episode)
        }
    }

    fun playNextChannel() {
        session.value.nextChannel?.let { channel ->
            playChannel(channel)
        }
    }

    fun playPreviousChannel() {
        session.value.previousChannel?.let { channel ->
            playChannel(channel)
        }
    }

    fun playChannel(channel: Channel) {
        if (session.value.currentChannel?.id == channel.id && currentContentType == ContentType.LIVE.name) {
            return
        }

        clearLiveChannelSwitchError()
        if (_liveChannelSwitch.value.isSwitching) {
            pendingLiveChannel = channel
            return
        }

        liveChannelSwitchJob?.cancel()
        liveChannelSwitchJob = viewModelScope.launch {
            processLiveChannelSwitchQueue(channel)
        }
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

    /**
     * Optimized exit: save progress async, navigate back immediately,
     * then release player on IO dispatcher to avoid main-thread jank.
     */
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
        playerManager.release()
    }

    private suspend fun processLiveChannelSwitchQueue(initialChannel: Channel) {
        var nextChannel: Channel? = initialChannel

        while (nextChannel != null) {
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
        playerManager.play(resolvedUrl, startPosition)

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
            switchErrorMessage = buildPlaybackFailureMessage(channel.name)

            if (previousUrl.isNotBlank()) {
                playerManager.play(previousUrl, 0L)
            }
        } finally {
            _liveChannelSwitch.value = LiveChannelSwitchState(errorMessage = switchErrorMessage)
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

            repeat(attempts) { attempt ->
                playerManager.getEngine().stop()
                delay(if (attempt == 0) 160L else 240L)
                playerManager.play(candidate, startPosition)

                val enteredTransitionState = withTimeoutOrNull(3_500L) {
                    state
                        .map { it.playbackState }
                        .drop(1)
                        .first { playbackState ->
                            playbackState == PlaybackState.BUFFERING ||
                                playbackState == PlaybackState.PLAYING ||
                                playbackState == PlaybackState.ERROR
                        }
                }

                when (enteredTransitionState) {
                    PlaybackState.PLAYING -> {
                        currentUrl = candidate
                        return true
                    }

                    PlaybackState.BUFFERING -> {
                        val settledState = withTimeoutOrNull(8_500L) {
                            state
                                .map { it.playbackState }
                                .first { playbackState ->
                                    playbackState == PlaybackState.PLAYING ||
                                        playbackState == PlaybackState.ERROR
                                }
                        }
                        if (settledState == PlaybackState.PLAYING) {
                            currentUrl = candidate
                            return true
                        }
                    }

                    else -> Unit
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

        if (
            !enableTvPlaybackWorkarounds ||
            !contentType.equals(ContentType.LIVE.name, ignoreCase = true)
        ) {
            return listOf(defaultResolvedUrl)
        }

        return buildList {
            add(trimmedUrl)
            if (defaultResolvedUrl != trimmedUrl) {
                add(defaultResolvedUrl)
            }
            if (trimmedUrl.contains("/live/") && trimmedUrl.endsWith(".ts", ignoreCase = true)) {
                add(trimmedUrl.removeSuffix(".ts") + ".m3u8")
            }
        }.map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun buildPlaybackFailureMessage(title: String): String {
        return "$title could not be opened reliably on TV. Try retrying or switching player engine."
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

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    contentId: Long,
    contentType: String,
    startPosition: Long,
    groupContext: String,
    isTv: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.state.collectAsStateWithLifecycle()
    val switchState by viewModel.switchState.collectAsStateWithLifecycle()
    val activeEngineName by viewModel.activeEngineName.collectAsStateWithLifecycle()
    val engineReady by viewModel.engineReady.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val liveChannelSwitch by viewModel.liveChannelSwitch.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChannelSheet by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val displayTitle = session.title.ifBlank { title }
    val isLivePlayback = contentType == ContentType.LIVE.name || session.currentChannel != null
    val isSeriesPlayback = contentType == ContentType.SERIES.name || session.currentEpisode != null
    val isChannelSwitching = liveChannelSwitch.isSwitching
    val showUpNext = remember(
        isTv,
        isSeriesPlayback,
        session.nextEpisode,
        playerState.currentPosition,
        playerState.duration,
        playerState.playbackState,
        showSettingsSheet
    ) {
        !isTv &&
            !showSettingsSheet &&
            isSeriesPlayback &&
            session.nextEpisode != null &&
            (
                playerState.playbackState == PlaybackState.ENDED ||
                    (
                        playerState.duration > 0L &&
                            playerState.currentPosition > 30_000L &&
                            (playerState.duration - playerState.currentPosition) <= 45_000L
                    )
            )
    }

    // Get activity for window flags
    val context = LocalContext.current
    val activity = context as? Activity
    val playbackActive = remember(playerState.playbackState, switchState) {
        playerState.playbackState == PlaybackState.PLAYING ||
            playerState.playbackState == PlaybackState.BUFFERING ||
            switchState == EngineSwitchState.SWITCHING
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // KEEP SCREEN ON — prevents screen from turning off during playback
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    DisposableEffect(activity, playbackActive) {
        if (playbackActive) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // IMMERSIVE MODE — hides system bars during video playback
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    val insetsController = remember(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
        }
    }

    // Enter immersive on first composition, exit on dispose
    DisposableEffect(activity) {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(insetsController, playbackActive, controlsVisible, showSettingsSheet, showChannelSheet) {
        insetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (playbackActive) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
                if (controlsVisible || showSettingsSheet || showChannelSheet) {
                    controller.show(WindowInsetsCompat.Type.navigationBars())
                } else {
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Keep fullscreen sticky when playback state changes
    DisposableEffect(insetsController) {
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            insetsController?.let { controller ->
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.saveProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initialize player
    LaunchedEffect(url, title, contentId, contentType, startPosition, groupContext) {
        viewModel.init(url, title, startPosition, contentId, contentType, groupContext)
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(settings.controllerAutoHideMs)
            controlsVisible = false
        }
    }

    // Handle back — optimized: navigate FIRST, release async
    BackHandler {
        when {
            showSettingsSheet -> showSettingsSheet = false
            showChannelSheet && !isChannelSwitching -> showChannelSheet = false
            else -> viewModel.exitPlayer(onBack)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isTv) {
                    Modifier.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter -> {
                                    if (!controlsVisible) { controlsVisible = true; true }
                                    else { viewModel.togglePlayPause(); true }
                                }
                                Key.DirectionRight -> { viewModel.seekForward(); controlsVisible = true; true }
                                Key.DirectionLeft -> { viewModel.seekBackward(); controlsVisible = true; true }
                                Key.DirectionUp, Key.DirectionDown -> { controlsVisible = true; true }
                                Key.Back, Key.Escape -> {
                                    if (showSettingsSheet) {
                                        showSettingsSheet = false; true
                                    } else if (controlsVisible) {
                                        controlsVisible = false; true
                                    } else {
                                        viewModel.exitPlayer(onBack); true
                                    }
                                }
                                Key.MediaPlayPause, Key.Spacebar -> { viewModel.togglePlayPause(); controlsVisible = true; true }
                                else -> false
                            }
                        } else false
                    }
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth / 3) viewModel.seekBackward()
                                else if (offset.x > screenWidth * 2 / 3) viewModel.seekForward()
                                else viewModel.togglePlayPause()
                                controlsVisible = true
                            }
                        )
                    }
                }
            )
    ) {
        // Video surface recreated gracefully: hides during SWITCHING to dispose old surface
        val engine = remember(activeEngineName, engineReady) {
            if (engineReady) viewModel.playerManager.getEngine() else null
        }
        if (!engineReady || engine == null) {
            CircularProgressIndicator(
                color = ImaxColors.Primary,
                modifier = Modifier.size(40.dp).align(Alignment.Center)
            )
        } else if (switchState == EngineSwitchState.SWITCHING) {
            Spacer(modifier = Modifier.fillMaxSize())
        } else {
            key(engine.engineName) {
                when (engine) {
                is ExoPlayerEngine -> {
                    val exoPlayer = engine.getExoPlayer()
                    val currentAspectMode = playerState.aspectRatioMode
                    if (exoPlayer != null) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val viewportAspectRatio = if (maxHeight > 0.dp) {
                                maxWidth.value / maxHeight.value
                            } else {
                                null
                            }
                            val viewportModifier = playerViewportModifier(
                                targetAspectRatio = engine.getViewportAspectRatio(currentAspectMode),
                                viewportAspectRatio = viewportAspectRatio
                            )

                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        setKeepContentOnPlayerReset(true)
                                        keepScreenOn = playbackActive
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        // Give the engine a reference so it can apply resizeMode
                                        engine.setPlayerView(this)
                                    }
                                },
                                update = { pv ->
                                    pv.player = exoPlayer
                                    pv.keepScreenOn = playbackActive
                                    pv.resizeMode = engine.getResizeModeFor(currentAspectMode)
                                    pv.videoSurfaceView?.keepScreenOn = playbackActive
                                },
                                modifier = viewportModifier
                            )
                        }
                        // Clean up PlayerView reference on dispose
                        DisposableEffect(engine.engineName) {
                            onDispose {
                                engine.clearPlayerView()
                            }
                        }
                    }
                }
                is VlcPlayerEngine -> {
                    // Surface-first VLC rendering:
                    // Create SurfaceView → call engine.setSurface() → VLC starts playback
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val viewportAspectRatio = if (maxHeight > 0.dp) {
                            maxWidth.value / maxHeight.value
                        } else {
                            null
                        }
                        val viewportModifier = playerViewportModifier(
                            targetAspectRatio = forcedViewportAspectRatio(
                                mode = playerState.aspectRatioMode,
                                videoWidth = playerState.videoWidth,
                                videoHeight = playerState.videoHeight
                            ),
                            viewportAspectRatio = viewportAspectRatio
                        )

                        AndroidView(
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    keepScreenOn = playbackActive
                                    // Ensure surface is on top (VLC needs this for proper z-order)
                                    setZOrderMediaOverlay(false)
                                }.also { surfaceView ->
                                    // Attach surface to VLC — this triggers pending playback if queued
                                    engine.setSurface(surfaceView)
                                }
                            },
                            update = { surfaceView ->
                                surfaceView.keepScreenOn = playbackActive
                                // Update surface size on recomposition/layout change
                                if (surfaceView.width > 0 && surfaceView.height > 0) {
                                    engine.updateSurfaceSize(surfaceView.width, surfaceView.height)
                                }
                            },
                            modifier = viewportModifier
                        )
                    }
                    // Detach surface on dispose to prevent leaks and stale references
                    DisposableEffect(engine.engineName) {
                        onDispose {
                            engine.detachSurface()
                        }
                    }
                    }
                }
            }
        }

        // Buffering indicator
        if (playerState.playbackState == PlaybackState.BUFFERING) {
            CircularProgressIndicator(
                color = ImaxColors.Primary,
                modifier = Modifier.size(48.dp).align(Alignment.Center)
            )
        }

        // Error state
        if (
            playerState.playbackState == PlaybackState.ERROR &&
            switchState != EngineSwitchState.SWITCHING &&
            !isChannelSwitching
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = ImaxColors.Error, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(playerState.errorMessage ?: "Playback error", color = ImaxColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.retryCurrent() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ImaxColors.TextPrimary)
                    ) { Text("Retry") }
                    OutlinedButton(
                        onClick = { viewModel.switchEngine() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ImaxColors.TextPrimary)
                    ) { Text("Switch Engine") }
                }
            }
        }

        // Engine switch transition overlay
        AnimatedVisibility(
            visible = switchState == EngineSwitchState.SWITCHING,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ImaxColors.Primary, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.switching_engine), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = isChannelSwitching,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ImaxColors.Primary, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Switching channel",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    if (liveChannelSwitch.targetTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = liveChannelSwitch.targetTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Switch success indicator
        AnimatedVisibility(
            visible = switchState == EngineSwitchState.SUCCESS,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(800)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = ImaxColors.Primary.copy(alpha = 0.9f)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Switched to $activeEngineName",
                        style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }

        // Switch failed indicator
        AnimatedVisibility(
            visible = switchState == EngineSwitchState.FAILED,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(800)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = ImaxColors.Error.copy(alpha = 0.9f)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Engine switch failed, rolled back",
                        style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = showUpNext,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 16.dp, vertical = 96.dp)
        ) {
            val nextEpisode = session.nextEpisode
            if (nextEpisode != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.up_next),
                            style = MaterialTheme.typography.labelMedium,
                            color = ImaxColors.Primary
                        )
                        Text(
                            text = episodeLabel(nextEpisode),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    controlsVisible = false
                                    viewModel.playNextEpisode()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = ImaxColors.Primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.next_episode))
                            }
                            OutlinedButton(
                                onClick = { controlsVisible = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible && switchState != EngineSwitchState.SWITCHING && !isChannelSwitching,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(
                        colors = listOf(ImaxColors.OverlayDark, Color.Transparent, Color.Transparent, ImaxColors.OverlayDark),
                        startY = 0f,
                        endY = Float.MAX_VALUE
                    ))
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerControlButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                        onClick = { viewModel.exitPlayer(onBack) })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(displayTitle, style = MaterialTheme.typography.titleLarge, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    if (!isTv && isSeriesPlayback) {
                        PlayerControlButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.previous_episode),
                            size = 22.dp,
                            onClick = { viewModel.playPreviousEpisode() },
                            modifier = Modifier.alpha(if (session.previousEpisode != null) 1f else 0.45f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        PlayerControlButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.next_episode),
                            size = 22.dp,
                            onClick = { viewModel.playNextEpisode() },
                            modifier = Modifier.alpha(if (session.nextEpisode != null) 1f else 0.45f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!isTv && isLivePlayback) {
                        PlayerControlButton(
                            icon = Icons.Filled.List,
                            contentDescription = stringResource(R.string.channel_list),
                            size = 22.dp,
                            onClick = {
                                if (!isChannelSwitching) {
                                    showChannelSheet = true
                                }
                            },
                            modifier = Modifier.alpha(if (isChannelSwitching) 0.45f else 1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Engine badge
                    Surface(shape = RoundedCornerShape(4.dp), color = ImaxColors.SurfaceVariant.copy(alpha = 0.6f)) {
                        Text(activeEngineName,
                            style = MaterialTheme.typography.labelSmall,
                            color = ImaxColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                // Center play/pause controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 40.dp else 28.dp)
                ) {
                    if (isLivePlayback && !isTv) {
                        PlayerControlButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.previous_channel),
                            size = 40.dp,
                            onClick = {
                                if (!isChannelSwitching) {
                                    viewModel.playPreviousChannel()
                                }
                            },
                            modifier = Modifier.alpha(
                                if (session.previousChannel != null && !isChannelSwitching) 1f else 0.45f
                            )
                        )
                    } else {
                        PlayerControlButton(icon = Icons.Filled.Replay10, contentDescription = "Rewind",
                            size = if (isTv) 48.dp else 40.dp,
                            onClick = { viewModel.seekBackward() })
                    }

                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(if (isTv) 72.dp else 56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .focusRequester(playPauseFocusRequester)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playerState.isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                            tint = Color.White,
                            modifier = Modifier.size(if (isTv) 48.dp else 40.dp)
                        )
                    }

                    if (isLivePlayback && !isTv) {
                        PlayerControlButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.next_channel),
                            size = 40.dp,
                            onClick = {
                                if (!isChannelSwitching) {
                                    viewModel.playNextChannel()
                                }
                            },
                            modifier = Modifier.alpha(
                                if (session.nextChannel != null && !isChannelSwitching) 1f else 0.45f
                            )
                        )
                    } else {
                        PlayerControlButton(icon = Icons.Filled.Forward10, contentDescription = "Forward",
                            size = if (isTv) 48.dp else 40.dp,
                            onClick = { viewModel.seekForward() })
                    }
                }

                // Bottom controls
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Progress bar
                    if (playerState.duration > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(StringUtils.formatDuration(playerState.currentPosition),
                                style = MaterialTheme.typography.labelSmall, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = playerState.currentPosition.toFloat(),
                                    onValueChange = { viewModel.seekTo(it.toLong()) },
                                    valueRange = 0f..playerState.duration.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ImaxColors.Primary,
                                        activeTrackColor = ImaxColors.Primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.focusable()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(StringUtils.formatDuration(playerState.duration),
                                style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    // Bottom toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speed button
                        PlayerControlButton(
                            icon = Icons.Filled.Speed,
                            contentDescription = "Speed",
                            label = "${playerState.playbackSpeed}x",
                            onClick = { /* handled in settings sheet */ showSettingsSheet = true }
                        )

                        if (!isTv && isLivePlayback) {
                            Spacer(modifier = Modifier.width(4.dp))
                            PlayerControlButton(
                                icon = Icons.Filled.List,
                                contentDescription = stringResource(R.string.channel_list),
                                label = session.liveGroup.ifBlank { stringResource(R.string.channels) },
                                onClick = {
                                    if (!isChannelSwitching) {
                                        showChannelSheet = true
                                    }
                                },
                                modifier = Modifier.alpha(if (isChannelSwitching) 0.45f else 1f)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Resolution indicator (if adaptive stream)
                        if (playerState.currentVideoResolution.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color.White.copy(alpha = 0.15f)) {
                                Text(playerState.currentVideoResolution,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Settings button (opens bottom sheet)
                        PlayerControlButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            onClick = { showSettingsSheet = true }
                        )
                    }
                }
            }
        }
    }
    if (showChannelSheet) {
        ChannelSwitchSheet(
            channels = session.availableChannels,
            currentChannelId = liveChannelSwitch.targetChannelId ?: session.currentChannel?.id ?: contentId,
            isSwitching = isChannelSwitching,
            onDismiss = {
                if (!isChannelSwitching) {
                    showChannelSheet = false
                }
            },
            onChannelSelected = { channel ->
                if (isChannelSwitching) return@ChannelSwitchSheet
                showChannelSheet = false
                controlsVisible = false
                viewModel.playChannel(channel)
            }
        )
    }

    if (showSettingsSheet) {
        PlayerSettingsSheet(
            playerState = playerState,
            engineName = activeEngineName,
            onDismiss = { showSettingsSheet = false },
            onSetAspectRatio = { viewModel.setAspectRatio(it) },
            onSetSpeed = { viewModel.setSpeed(it) },
            onSetQualityMode = { viewModel.setVideoQualityMode(it) },
            onSelectVideoTrack = { viewModel.selectVideoTrack(it) },
            onSelectAudio = { viewModel.selectAudio(it) },
            onSelectSubtitle = { viewModel.selectSubtitle(it) },
            onDisableSubtitles = { viewModel.disableSubtitles() },
            onSwitchEngine = { viewModel.switchEngine(); showSettingsSheet = false }
        )
    }
}

private fun forcedViewportAspectRatio(
    mode: AspectRatioMode,
    videoWidth: Int,
    videoHeight: Int
): Float? {
    return when (mode) {
        AspectRatioMode.FORCE_16_9 -> 16f / 9f
        AspectRatioMode.FORCE_4_3 -> 4f / 3f
        AspectRatioMode.ORIGINAL -> {
            if (videoWidth > 0 && videoHeight > 0) {
                videoWidth.toFloat() / videoHeight.toFloat()
            } else {
                null
            }
        }
        else -> null
    }
}

private fun playerViewportModifier(
    targetAspectRatio: Float?,
    viewportAspectRatio: Float?
): Modifier {
    if (
        targetAspectRatio == null ||
        viewportAspectRatio == null ||
        targetAspectRatio <= 0f ||
        viewportAspectRatio <= 0f
    ) {
        return Modifier.fillMaxSize()
    }

    return if (viewportAspectRatio > targetAspectRatio) {
        Modifier
            .fillMaxHeight()
            .aspectRatio(targetAspectRatio, matchHeightConstraintsFirst = true)
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(targetAspectRatio)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Player Settings Bottom Sheet
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    playerState: PlayerState,
    engineName: String,
    onDismiss: () -> Unit,
    onSetAspectRatio: (AspectRatioMode) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetQualityMode: (VideoQualityMode) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onSwitchEngine: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeSection by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ImaxColors.Surface,
        contentColor = ImaxColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ImaxColors.TextTertiary))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            // Header
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, color = ImaxColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

            if (activeSection == null) {
                // Main settings menu
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // Display Mode
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.AspectRatio,
                            title = stringResource(R.string.setting_display_mode),
                            subtitle = playerState.aspectRatioMode.label,
                            onClick = { activeSection = "aspect" }
                        )
                    }

                    // Video Quality
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.HighQuality,
                            title = stringResource(R.string.setting_video_quality),
                            subtitle = if (playerState.availableQualities.isNotEmpty())
                                playerState.videoQualityMode.label
                            else "Not available",
                            onClick = { activeSection = "quality" }
                        )
                    }

                    // Playback Speed
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.setting_playback_speed),
                            subtitle = "${playerState.playbackSpeed}x",
                            onClick = { activeSection = "speed" }
                        )
                    }

                    // Audio Track
                    if (playerState.audioTracks.isNotEmpty()) {
                        item {
                            val selectedAudio = playerState.audioTracks.find { it.isSelected }
                            SettingsMenuItem(
                                icon = Icons.Filled.Audiotrack,
                                title = stringResource(R.string.setting_audio_track),
                                subtitle = selectedAudio?.name ?: "Default",
                                onClick = { activeSection = "audio" }
                            )
                        }
                    }

                    // Subtitle Track
                    item {
                        val selectedSub = playerState.subtitleTracks.find { it.isSelected }
                        SettingsMenuItem(
                            icon = Icons.Filled.Subtitles,
                            title = stringResource(R.string.setting_subtitles),
                            subtitle = selectedSub?.name ?: "Off",
                            onClick = { activeSection = "subtitle" }
                        )
                    }

                    // Engine Switch
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.SwitchVideo,
                            title = stringResource(R.string.setting_player_engine),
                            subtitle = engineName,
                            onClick = onSwitchEngine
                        )
                    }

                    // Stream Info
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.setting_stream_info),
                            subtitle = if (playerState.currentVideoResolution.isNotBlank()) playerState.currentVideoResolution else "—",
                            onClick = { activeSection = "info" }
                        )
                    }
                }
            } else {
                // Section back button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeSection = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ImaxColors.TextPrimary)
                    }
                    Text(
                        when (activeSection) {
                            "aspect" -> stringResource(R.string.setting_display_mode)
                            "quality" -> stringResource(R.string.setting_video_quality)
                            "speed" -> stringResource(R.string.setting_playback_speed)
                            "audio" -> stringResource(R.string.setting_audio_track)
                            "subtitle" -> stringResource(R.string.setting_subtitles)
                            "info" -> stringResource(R.string.setting_stream_info)
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = ImaxColors.TextPrimary
                    )
                }

                HorizontalDivider(color = ImaxColors.DividerColor)

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (activeSection) {
                        "aspect" -> {
                            items(AspectRatioMode.entries.toList()) { mode ->
                                SettingsOptionItem(
                                    label = mode.label,
                                    isSelected = playerState.aspectRatioMode == mode,
                                    onClick = { onSetAspectRatio(mode); activeSection = null }
                                )
                            }
                        }
                        "quality" -> {
                            // Quality modes
                            items(VideoQualityMode.entries.toList()) { mode ->
                                SettingsOptionItem(
                                    label = mode.label,
                                    isSelected = playerState.videoQualityMode == mode,
                                    onClick = { onSetQualityMode(mode); activeSection = null }
                                )
                            }
                            // Specific resolutions
                            if (playerState.availableQualities.isNotEmpty()) {
                                item {
                                    HorizontalDivider(color = ImaxColors.DividerColor, modifier = Modifier.padding(vertical = 8.dp))
                                    Text("Available Resolutions", style = MaterialTheme.typography.labelMedium,
                                        color = ImaxColors.TextTertiary,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                                }
                                items(playerState.availableQualities) { q ->
                                    SettingsOptionItem(
                                        label = q.label,
                                        subtitle = if (q.bitrate > 0) "${q.bitrate / 1000} kbps" else null,
                                        isSelected = q.isSelected,
                                        onClick = { onSelectVideoTrack(q.index); activeSection = null }
                                    )
                                }
                            }
                        }
                        "speed" -> {
                            items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                                SettingsOptionItem(
                                    label = "${speed}x",
                                    isSelected = playerState.playbackSpeed == speed,
                                    onClick = { onSetSpeed(speed); activeSection = null }
                                )
                            }
                        }
                        "audio" -> {
                            items(playerState.audioTracks) { track ->
                                SettingsOptionItem(
                                    label = track.name,
                                    subtitle = track.language.ifEmpty { null },
                                    isSelected = track.isSelected,
                                    onClick = { onSelectAudio(track.index); activeSection = null }
                                )
                            }
                        }
                        "subtitle" -> {
                            item {
                                SettingsOptionItem(
                                    label = "Off",
                                    isSelected = playerState.selectedSubtitleTrack == -1,
                                    onClick = { onDisableSubtitles(); activeSection = null }
                                )
                            }
                            items(playerState.subtitleTracks) { track ->
                                SettingsOptionItem(
                                    label = track.name,
                                    subtitle = track.language.ifEmpty { null },
                                    isSelected = track.isSelected,
                                    onClick = { onSelectSubtitle(track.index); activeSection = null }
                                )
                            }
                        }
                        "info" -> {
                            item {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    StreamInfoRow("Engine", engineName)
                                    StreamInfoRow("Resolution", playerState.currentVideoResolution.ifBlank { "—" })
                                    StreamInfoRow("Bitrate", playerState.currentVideoBitrate.ifBlank { "—" })
                                    StreamInfoRow("Codec", playerState.currentVideoCodec.ifBlank { "—" })
                                    StreamInfoRow("FPS", playerState.currentVideoFps.ifBlank { "—" })
                                    StreamInfoRow("Video Size", if (playerState.videoWidth > 0) "${playerState.videoWidth}x${playerState.videoHeight}" else "—")
                                    StreamInfoRow("Adaptive", if (playerState.isAdaptiveStream) "Yes" else "No")
                                    StreamInfoRow("Display Mode", playerState.aspectRatioMode.label)
                                    StreamInfoRow("Quality Mode", playerState.videoQualityMode.label)
                                    StreamInfoRow("Speed", "${playerState.playbackSpeed}x")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ImaxColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ImaxColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ImaxColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsOptionItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) ImaxColors.Primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) ImaxColors.Primary else ImaxColors.TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
            }
        }
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = ImaxColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StreamInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextPrimary)
    }
}

@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 36.dp,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isPrimary -> ImaxColors.Primary
        isFocused -> Color.White.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val borderMod = if (isFocused && !isPrimary) Modifier.border(2.dp, ImaxColors.FocusBorder, CircleShape) else Modifier

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .then(borderMod)
            .clickable(onClick = onClick)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(if (isPrimary) 12.dp else 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size)
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

private fun episodeLabel(episode: Episode): String {
    return buildString {
        append("S${episode.seasonNumber}E${episode.episodeNumber}")
        if (episode.name.isNotBlank()) {
            append(" • ")
            append(episode.name)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSwitchSheet(
    channels: List<Channel>,
    currentChannelId: Long,
    isSwitching: Boolean,
    onDismiss: () -> Unit,
    onChannelSelected: (Channel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ImaxColors.Surface,
        contentColor = ImaxColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
        ) {
            Text(
                text = stringResource(R.string.channel_list),
                style = MaterialTheme.typography.headlineSmall,
                color = ImaxColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    val isCurrent = channel.id == currentChannelId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) {
                            ImaxColors.Primary.copy(alpha = 0.14f)
                        } else {
                            ImaxColors.SurfaceVariant
                        },
                        border = BorderStroke(
                            width = if (isCurrent) 1.dp else 0.dp,
                            color = if (isCurrent) ImaxColors.Primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCurrent && !isSwitching) { onChannelSelected(channel) }
                            .alpha(if (!isCurrent && !isSwitching) 1f else 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ImaxColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (channel.groupTitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = channel.groupTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ImaxColors.TextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isCurrent) {
                                Text(
                                    text = stringResource(R.string.now_playing),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ImaxColors.Primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = ImaxColors.Primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
