package com.imax.player.ui.tv

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imax.player.R
import com.imax.player.core.common.StringUtils
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.model.Channel
import com.imax.player.core.model.ContentType
import com.imax.player.core.model.Episode
import com.imax.player.core.player.AspectRatioMode
import com.imax.player.core.player.PlaybackState
import com.imax.player.core.player.PlayerState

import com.imax.player.ui.components.rememberTvFocusVisualState
import com.imax.player.ui.player.PlayerShellMode
import com.imax.player.ui.player.PlayerSurfaceHost
import com.imax.player.ui.player.PlayerSurfaceHostState
import com.imax.player.ui.player.PlayerViewModel
import timber.log.Timber

private enum class TvPlayerPanel {
    NONE,
    CHANNELS,
    AUDIO,
    SUBTITLE,
    SCREEN_MODE,
    SPEED,
    SETTINGS,
    STREAM_INFO
}

private enum class TvOverlayAction {
    BACK,
    MAIN_PREVIOUS,
    PLAY_PAUSE,
    MAIN_NEXT,
    CHANNELS,
    PREVIOUS_EPISODE,
    NEXT_EPISODE,
    AUDIO,
    SUBTITLE,
    SCREEN_MODE,
    SETTINGS
}

private const val TV_PLAYER_LOG_TAG = "TvPlayerScreen"

private data class TvPanelOption(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun TvPlayerScreen(
    url: String,
    title: String,
    contentId: Long,
    contentType: String,
    startPosition: Long,
    groupContext: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.state.collectAsStateWithLifecycle()
    val playerReady by viewModel.playerReady.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val liveChannelSwitch by viewModel.liveChannelSwitch.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as? Activity
    val insetsController = remember(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
        }
    }

    val isLivePlayback = contentType == ContentType.LIVE.name || session.currentChannel != null
    val isSeriesPlayback = contentType == ContentType.SERIES.name || session.currentEpisode != null
    val isChannelSwitching = liveChannelSwitch.isSwitching
    val showSwitchingOverlay = isChannelSwitching && !playerState.isPlaybackConfirmed
    val showBlockingPlaybackOverlay = !playerReady ||
        (playerState.playbackState == PlaybackState.BUFFERING && !playerState.isPlaybackConfirmed) ||
        showSwitchingOverlay
    val displayTitle = session.title.ifBlank { title }
    val playbackActive = playerState.playbackState == PlaybackState.PLAYING ||
        playerState.playbackState == PlaybackState.BUFFERING ||
        isChannelSwitching
    val availableOverlayActions = remember(
        isLivePlayback,
        isSeriesPlayback,
        session.previousEpisode?.id,
        session.nextEpisode?.id
    ) {
        buildSet {
            add(TvOverlayAction.BACK)
            add(TvOverlayAction.MAIN_PREVIOUS)
            add(TvOverlayAction.PLAY_PAUSE)
            add(TvOverlayAction.MAIN_NEXT)
            if (isLivePlayback) {
                add(TvOverlayAction.CHANNELS)
            }
            if (isSeriesPlayback && session.previousEpisode != null) {
                add(TvOverlayAction.PREVIOUS_EPISODE)
            }
            if (isSeriesPlayback && session.nextEpisode != null) {
                add(TvOverlayAction.NEXT_EPISODE)
            }
            add(TvOverlayAction.AUDIO)
            add(TvOverlayAction.SUBTITLE)
            add(TvOverlayAction.SCREEN_MODE)
            add(TvOverlayAction.SETTINGS)
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val overlayActionRequesters = remember {
        TvOverlayAction.entries.associateWith { FocusRequester() }
    }

    var overlayVisible by rememberSaveable { mutableStateOf(true) }
    var activePanel by rememberSaveable { mutableStateOf(TvPlayerPanel.NONE) }
    var lastOverlayAction by rememberSaveable { mutableStateOf(TvOverlayAction.PLAY_PAUSE) }
    var requestedOverlayAction by rememberSaveable { mutableStateOf(TvOverlayAction.PLAY_PAUSE) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var isExiting by rememberSaveable { mutableStateOf(false) }
    var channelNumberInput by remember { mutableStateOf("") }
    var transientZapAction by rememberSaveable { mutableStateOf<TvOverlayAction?>(null) }
    var transientZapTitle by rememberSaveable { mutableStateOf("") }
    var transientZapVersion by remember { mutableIntStateOf(0) }

    fun registerInteraction() {
        interactionVersion += 1
    }

    LaunchedEffect(url, contentId, isLivePlayback, settings.startFullscreenLive) {
        overlayVisible = !(isLivePlayback && settings.startFullscreenLive)
        if (!overlayVisible) {
            activePanel = TvPlayerPanel.NONE
        }
        transientZapAction = null
        transientZapTitle = ""
    }

    fun showOverlay(action: TvOverlayAction = lastOverlayAction) {
        Timber.tag(TV_PLAYER_LOG_TAG).d(
            "showOverlay action=%s panel=%s visible=%s",
            action,
            activePanel,
            overlayVisible
        )
        requestedOverlayAction = action
        overlayVisible = true
        registerInteraction()
    }

    fun hideOverlay() {
        Timber.tag(TV_PLAYER_LOG_TAG).d("hideOverlay panel=%s", activePanel)
        overlayVisible = false
        activePanel = TvPlayerPanel.NONE
        registerInteraction()
    }

    fun closePanel() {
        activePanel = TvPlayerPanel.NONE
        showOverlay(lastOverlayAction)
    }

    fun showTransientZapFeedback(action: TvOverlayAction, title: String) {
        transientZapAction = action
        transientZapTitle = title
        transientZapVersion += 1
        Timber.tag(TV_PLAYER_LOG_TAG).d(
            "showTransientZapFeedback action=%s title=%s",
            action,
            title
        )
    }

    fun clearTransientZapFeedback() {
        transientZapAction = null
        transientZapTitle = ""
    }

    fun resolvedOverlayAction(action: TvOverlayAction): TvOverlayAction {
        return when {
            action in availableOverlayActions -> action
            lastOverlayAction in availableOverlayActions -> lastOverlayAction
            else -> TvOverlayAction.PLAY_PAUSE
        }
    }

    fun exitPlayer() {
        if (isExiting) return
        isExiting = true
        hideOverlay()
        viewModel.exitPlayer(onBack)
    }

    fun handleBack(): Boolean {
        return when {
            activePanel != TvPlayerPanel.NONE -> {
                closePanel()
                true
            }

            overlayVisible -> {
                hideOverlay()
                true
            }

            else -> {
                exitPlayer()
                true
            }
        }
    }

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

    DisposableEffect(activity) {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        insetsController?.let { controller ->
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(insetsController, overlayVisible, activePanel, playbackActive) {
        if (playbackActive) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
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

    DisposableEffect(viewModel) {
        viewModel.configureTvPlayback(true)
        onDispose {
            viewModel.configureTvPlayback(false)
        }
    }

    LaunchedEffect(url, title, contentId, contentType, startPosition, groupContext) {
        viewModel.init(url, title, startPosition, contentId, contentType, groupContext)
    }

    LaunchedEffect(overlayVisible, activePanel, requestedOverlayAction) {
        val targetAction = resolvedOverlayAction(requestedOverlayAction)
        if (requestedOverlayAction != targetAction) {
            requestedOverlayAction = targetAction
        }
        if (lastOverlayAction !in availableOverlayActions) {
            lastOverlayAction = targetAction
        }

        when {
            activePanel != TvPlayerPanel.NONE -> Unit
            overlayVisible -> {
                Timber.tag(TV_PLAYER_LOG_TAG).d(
                    "requestOverlayFocus action=%s panel=%s",
                    targetAction,
                    activePanel
                )
                overlayActionRequesters
                    .getValue(targetAction)
                    .requestFocusSafely("player overlay action $targetAction")
            }
            else -> rootFocusRequester.requestFocusSafely("player root surface")
        }
    }

    LaunchedEffect(overlayVisible, activePanel, interactionVersion, playerState.isPlaying) {
        if (overlayVisible && activePanel == TvPlayerPanel.NONE && playerState.isPlaying) {
            kotlinx.coroutines.delay(6500)
            if (overlayVisible && activePanel == TvPlayerPanel.NONE && playerState.isPlaying) {
                overlayVisible = false
                rootFocusRequester.requestFocusSafely("player root after overlay timeout")
            }
        }
    }

    LaunchedEffect(playerState.playbackState, isChannelSwitching) {
        if (
            playerState.playbackState == PlaybackState.ERROR &&
            !isChannelSwitching
        ) {
            showOverlay(TvOverlayAction.PLAY_PAUSE)
        }
    }

    LaunchedEffect(liveChannelSwitch.errorMessage) {
        if (!liveChannelSwitch.errorMessage.isNullOrBlank()) {
            showOverlay(TvOverlayAction.CHANNELS)
        }
    }

    LaunchedEffect(transientZapVersion) {
        if (transientZapAction == null) return@LaunchedEffect
        val version = transientZapVersion
        kotlinx.coroutines.delay(900)
        if (transientZapVersion == version) {
            clearTransientZapFeedback()
        }
    }

    BackHandler(enabled = !isExiting, onBack = ::handleBack)

    // Channel number input timeout — navigate after 2 seconds
    LaunchedEffect(channelNumberInput) {
        if (channelNumberInput.isNotBlank() && isLivePlayback) {
            kotlinx.coroutines.delay(2000)
            val targetNumber = channelNumberInput.toIntOrNull()
            channelNumberInput = ""
            if (targetNumber != null && targetNumber > 0) {
                val channels = session.availableChannels
                val targetIndex = targetNumber - 1
                if (targetIndex in channels.indices) {
                    viewModel.playChannel(channels[targetIndex])
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (isExiting) {
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.key) {
                    Key.Back,
                    Key.Escape -> handleBack()

                    Key.MediaPlayPause,
                    Key.Spacebar -> {
                        registerInteraction()
                        clearTransientZapFeedback()
                        showOverlay(TvOverlayAction.PLAY_PAUSE)
                        viewModel.togglePlayPause()
                        true
                    }

                    Key.DirectionCenter,
                    Key.Enter -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
                            clearTransientZapFeedback()
                            Timber.tag(TV_PLAYER_LOG_TAG).d("remote center opens overlay")
                            showOverlay(TvOverlayAction.PLAY_PAUSE)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp,
                    Key.DirectionDown -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
                            clearTransientZapFeedback()
                            Timber.tag(TV_PLAYER_LOG_TAG).d("remote %s opens overlay", event.key)
                            showOverlay(TvOverlayAction.PLAY_PAUSE)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionLeft -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
                            registerInteraction()
                            if (isLivePlayback) {
                                val targetChannel = session.previousChannel
                                Timber.tag(TV_PLAYER_LOG_TAG).d(
                                    "remote left live zap previous current=%s target=%s switching=%s",
                                    session.currentChannel?.name,
                                    targetChannel?.name,
                                    isChannelSwitching
                                )
                                if (targetChannel != null) {
                                    showTransientZapFeedback(TvOverlayAction.MAIN_PREVIOUS, targetChannel.name)
                                }
                                viewModel.playPreviousChannel()
                            } else {
                                clearTransientZapFeedback()
                                viewModel.seekBackward()
                                showOverlay(TvOverlayAction.MAIN_PREVIOUS)
                            }
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
                            registerInteraction()
                            if (isLivePlayback) {
                                val targetChannel = session.nextChannel
                                Timber.tag(TV_PLAYER_LOG_TAG).d(
                                    "remote right live zap next current=%s target=%s switching=%s",
                                    session.currentChannel?.name,
                                    targetChannel?.name,
                                    isChannelSwitching
                                )
                                if (targetChannel != null) {
                                    showTransientZapFeedback(TvOverlayAction.MAIN_NEXT, targetChannel.name)
                                }
                                viewModel.playNextChannel()
                            } else {
                                clearTransientZapFeedback()
                                viewModel.seekForward()
                                showOverlay(TvOverlayAction.MAIN_NEXT)
                            }
                            true
                        } else {
                            false
                        }
                    }

                    else -> {
                        // Digit keys for channel number input (TV remote)
                        if (isLivePlayback && activePanel == TvPlayerPanel.NONE) {
                            val digit = when (event.key) {
                                Key.Zero, Key(7) -> "0"
                                Key.One, Key(8) -> "1"
                                Key.Two, Key(9) -> "2"
                                Key.Three, Key(10) -> "3"
                                Key.Four, Key(11) -> "4"
                                Key.Five, Key(12) -> "5"
                                Key.Six, Key(13) -> "6"
                                Key.Seven, Key(14) -> "7"
                                Key.Eight, Key(15) -> "8"
                                Key.Nine, Key(16) -> "9"
                                else -> null
                            }
                            if (digit != null && channelNumberInput.length < 4) {
                                clearTransientZapFeedback()
                                channelNumberInput += digit
                                registerInteraction()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                }
            }
    ) {
        PlayerSurfaceHost(
            modifier = Modifier.fillMaxSize(),
            playerEngine = viewModel.playerManager.getEngine(),
            playerState = playerState,
            surfaceState = PlayerSurfaceHostState(
                playerReady = playerReady,
                playbackActive = playbackActive,
                shellMode = PlayerShellMode.TV
            )
        )

        if (showBlockingPlaybackOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (showSwitchingOverlay || playerState.playbackState == PlaybackState.BUFFERING) {
                            Color.Black.copy(alpha = 0.18f)
                        } else {
                            Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(54.dp),
                        color = ImaxColors.Primary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = when {
                            showSwitchingOverlay -> liveChannelSwitch.targetTitle.ifBlank {
                                stringResource(R.string.channel_list)
                            }

                            !playerReady -> stringResource(R.string.loading)
                            else -> stringResource(R.string.buffering)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        if (
            (playerState.playbackState == PlaybackState.ERROR || !liveChannelSwitch.errorMessage.isNullOrBlank()) &&
            !isChannelSwitching
        ) {
            TvErrorState(
                message = liveChannelSwitch.errorMessage
                    ?: playerState.errorMessage
                    ?: stringResource(R.string.playback_error),
                onRetry = {
                    showOverlay(TvOverlayAction.PLAY_PAUSE)
                    viewModel.clearLiveChannelSwitchError()
                    viewModel.retryCurrent()
                },
                onExit = ::exitPlayer
            )
        }

        AnimatedVisibility(
            visible = overlayVisible && activePanel == TvPlayerPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            TvPlaybackOverlay(
                title = displayTitle,
                subtitle = tvPlaybackSubtitle(
                    isLivePlayback = isLivePlayback,
                    isSeriesPlayback = isSeriesPlayback,
                    sessionEpisode = session.currentEpisode,
                    currentChannel = session.currentChannel,
                    liveGroup = session.liveGroup
                ),
                playerState = playerState,
                isLivePlayback = isLivePlayback,
                isSeriesPlayback = isSeriesPlayback,
                isChannelSwitching = isChannelSwitching,
                hasPreviousChannel = session.previousChannel != null,
                hasNextChannel = session.nextChannel != null,
                hasPreviousEpisode = session.previousEpisode != null,
                hasNextEpisode = session.nextEpisode != null,
                overlayActionRequesters = overlayActionRequesters,
                onActionFocused = { action ->
                    lastOverlayAction = action
                    requestedOverlayAction = action
                    registerInteraction()
                },
                onBack = ::exitPlayer,
                onPrevious = {
                    registerInteraction()
                    if (isLivePlayback) {
                        viewModel.playPreviousChannel()
                    } else {
                        viewModel.seekBackward()
                    }
                },
                onPlayPause = {
                    registerInteraction()
                    viewModel.togglePlayPause()
                },
                onNext = {
                    registerInteraction()
                    if (isLivePlayback) {
                        viewModel.playNextChannel()
                    } else {
                        viewModel.seekForward()
                    }
                },
                onOpenChannels = {
                    registerInteraction()
                    activePanel = TvPlayerPanel.CHANNELS
                    overlayVisible = true
                },
                onOpenAudio = {
                    registerInteraction()
                    activePanel = TvPlayerPanel.AUDIO
                    overlayVisible = true
                },
                onOpenSubtitle = {
                    registerInteraction()
                    activePanel = TvPlayerPanel.SUBTITLE
                    overlayVisible = true
                },
                onOpenScreenMode = {
                    registerInteraction()
                    activePanel = TvPlayerPanel.SCREEN_MODE
                    overlayVisible = true
                },
                onOpenSettings = {
                    registerInteraction()
                    activePanel = TvPlayerPanel.SETTINGS
                    overlayVisible = true
                },
                onPreviousEpisode = {
                    registerInteraction()
                    viewModel.playPreviousEpisode()
                },
                onNextEpisode = {
                    registerInteraction()
                    viewModel.playNextEpisode()
                }
            )
        }

        if (activePanel != TvPlayerPanel.NONE) {
            TvPlayerPanelHost(
                modifier = Modifier.fillMaxSize(),
                activePanel = activePanel,
                playerState = playerState,
                channels = session.availableChannels,
                currentChannelId = liveChannelSwitch.targetChannelId ?: session.currentChannel?.id ?: contentId,
                isChannelSwitching = isChannelSwitching,
                onDismiss = ::closePanel,
                onSelectChannel = { channel ->
                    if (!isChannelSwitching) {
                        viewModel.playChannel(channel)
                        closePanel()
                    }
                },
                onSelectAudio = {
                    viewModel.selectAudio(it)
                    closePanel()
                },
                onSelectSubtitle = {
                    viewModel.selectSubtitle(it)
                    closePanel()
                },
                onDisableSubtitles = {
                    viewModel.disableSubtitles()
                    closePanel()
                },
                onSelectAspectRatio = {
                    viewModel.setAspectRatio(it)
                    closePanel()
                },
                onSelectSpeed = {
                    viewModel.setSpeed(it)
                    closePanel()
                },
                onOpenSpeedPanel = { activePanel = TvPlayerPanel.SPEED },
                onOpenStreamInfoPanel = { activePanel = TvPlayerPanel.STREAM_INFO },
                onRetry = {
                    viewModel.retryCurrent()
                    closePanel()
                }
            )
        }

        // Channel number overlay
        AnimatedVisibility(
            visible = channelNumberInput.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(40.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, ImaxColors.Primary)
            ) {
                Text(
                    text = channelNumberInput,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = transientZapAction != null && !overlayVisible && activePanel == TvPlayerPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, ImaxColors.Primary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (transientZapAction == TvOverlayAction.MAIN_PREVIOUS) {
                            Icons.Filled.SkipPrevious
                        } else {
                            Icons.Filled.SkipNext
                        },
                        contentDescription = null,
                        tint = ImaxColors.Primary
                    )
                    Column {
                        Text(
                            text = if (transientZapAction == TvOverlayAction.MAIN_PREVIOUS) {
                                stringResource(R.string.previous_channel)
                            } else {
                                stringResource(R.string.next_channel)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = ImaxColors.TextSecondary
                        )
                        Text(
                            text = transientZapTitle.ifBlank { displayTitle },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvPlaybackOverlay(
    title: String,
    subtitle: String,
    playerState: PlayerState,
    isLivePlayback: Boolean,
    isSeriesPlayback: Boolean,
    isChannelSwitching: Boolean,
    hasPreviousChannel: Boolean,
    hasNextChannel: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    overlayActionRequesters: Map<TvOverlayAction, FocusRequester>,
    onActionFocused: (TvOverlayAction) -> Unit,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenScreenMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit
) {
    val shouldShowProgress = !isLivePlayback && playerState.duration > 0L
    val utilityActions = remember(
        isLivePlayback,
        isSeriesPlayback,
        hasPreviousEpisode,
        hasNextEpisode
    ) {
        buildList {
            if (isLivePlayback) {
                add(TvOverlayAction.CHANNELS)
            }
            if (isSeriesPlayback && hasPreviousEpisode) {
                add(TvOverlayAction.PREVIOUS_EPISODE)
            }
            if (isSeriesPlayback && hasNextEpisode) {
                add(TvOverlayAction.NEXT_EPISODE)
            }
            add(TvOverlayAction.AUDIO)
            add(TvOverlayAction.SUBTITLE)
            add(TvOverlayAction.SCREEN_MODE)
            add(TvOverlayAction.SETTINGS)
        }
    }

    fun actionRequester(action: TvOverlayAction?): FocusRequester {
        return action?.let { overlayActionRequesters.getValue(it) } ?: FocusRequester.Cancel
    }

    fun utilityActionForMain(slot: Int): TvOverlayAction? {
        if (utilityActions.isEmpty()) return null

        return when (slot) {
            0 -> utilityActions.first()
            1 -> utilityActions[utilityActions.lastIndex / 2]
            else -> utilityActions.last()
        }
    }

    fun mainActionForUtility(index: Int): TvOverlayAction {
        if (utilityActions.size <= 1) {
            return TvOverlayAction.PLAY_PAUSE
        }

        return when {
            index == 0 -> TvOverlayAction.MAIN_PREVIOUS
            index == utilityActions.lastIndex -> TvOverlayAction.MAIN_NEXT
            else -> TvOverlayAction.PLAY_PAUSE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ImaxColors.Background.copy(alpha = 0.92f),
                        Color.Transparent,
                        Color.Transparent,
                        ImaxColors.Background.copy(alpha = 0.92f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 54.dp, vertical = 38.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvActionChip(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = stringResource(R.string.cancel),
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.BACK),
                        focusProperties = {
                            down = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE)
                        },
                        onFocused = { onActionFocused(TvOverlayAction.BACK) },
                        onClick = onBack
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(modifier = Modifier.widthIn(max = 900.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = ImaxColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playerState.currentVideoResolution.isNotBlank()) {
                        TvStatusBadge(label = playerState.currentVideoResolution)
                    }
                    if (isChannelSwitching) {
                        TvStatusBadge(
                            label = stringResource(R.string.buffering),
                            background = ImaxColors.Warning.copy(alpha = 0.22f),
                            contentColor = ImaxColors.Warning
                        )
                    } else if (playerState.playbackState == PlaybackState.BUFFERING) {
                        TvStatusBadge(
                            label = stringResource(R.string.buffering),
                            background = ImaxColors.Secondary.copy(alpha = 0.22f),
                            contentColor = ImaxColors.Secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                if (shouldShowProgress) {
                    TvSeekBar(
                        currentPosition = playerState.currentPosition,
                        duration = playerState.duration,
                        onSeekBackward = onPrevious,   // reuse seek callbacks
                        onSeekForward = onNext,
                        onFocused = { onActionFocused(TvOverlayAction.PLAY_PAUSE) }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvActionButton(
                        icon = if (isLivePlayback) Icons.Filled.SkipPrevious else Icons.Filled.Replay10,
                        label = if (isLivePlayback) stringResource(R.string.previous_channel) else "10s",
                        enabled = if (isLivePlayback) hasPreviousChannel else true,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.MAIN_PREVIOUS),
                        focusProperties = {
                            up = overlayActionRequesters.getValue(TvOverlayAction.BACK)
                            right = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE)
                            down = actionRequester(utilityActionForMain(0))
                        },
                        onFocused = { onActionFocused(TvOverlayAction.MAIN_PREVIOUS) },
                        onClick = onPrevious
                    )
                    TvActionButton(
                        icon = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (playerState.isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                        enabled = true,
                        primary = true,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                        focusProperties = {
                            up = overlayActionRequesters.getValue(TvOverlayAction.BACK)
                            left = overlayActionRequesters.getValue(TvOverlayAction.MAIN_PREVIOUS)
                            right = overlayActionRequesters.getValue(TvOverlayAction.MAIN_NEXT)
                            down = actionRequester(utilityActionForMain(1))
                        },
                        onFocused = { onActionFocused(TvOverlayAction.PLAY_PAUSE) },
                        onClick = onPlayPause
                    )
                    TvActionButton(
                        icon = if (isLivePlayback) Icons.Filled.SkipNext else Icons.Filled.Forward10,
                        label = if (isLivePlayback) stringResource(R.string.next_channel) else "10s",
                        enabled = if (isLivePlayback) hasNextChannel else true,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.MAIN_NEXT),
                        focusProperties = {
                            up = overlayActionRequesters.getValue(TvOverlayAction.BACK)
                            left = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE)
                            down = actionRequester(utilityActionForMain(2))
                        },
                        onFocused = { onActionFocused(TvOverlayAction.MAIN_NEXT) },
                        onClick = onNext
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    utilityActions.forEachIndexed { index, action ->
                        when (action) {
                            TvOverlayAction.CHANNELS -> TvActionChip(
                                icon = Icons.AutoMirrored.Filled.List,
                                label = stringResource(R.string.channel_list),
                                focusRequester = overlayActionRequesters.getValue(action),
                                focusProperties = {
                                    left = actionRequester(utilityActions.getOrNull(index - 1))
                                    right = actionRequester(utilityActions.getOrNull(index + 1))
                                    up = overlayActionRequesters.getValue(mainActionForUtility(index))
                                },
                                onFocused = { onActionFocused(action) },
                                onClick = onOpenChannels
                            )

                            TvOverlayAction.PREVIOUS_EPISODE -> TvActionChip(
                                icon = Icons.Filled.SkipPrevious,
                                label = stringResource(R.string.previous_episode),
                                focusRequester = overlayActionRequesters.getValue(action),
                                focusProperties = {
                                    left = actionRequester(utilityActions.getOrNull(index - 1))
                                    right = actionRequester(utilityActions.getOrNull(index + 1))
                                    up = overlayActionRequesters.getValue(mainActionForUtility(index))
                                },
                                onFocused = { onActionFocused(action) },
                                onClick = onPreviousEpisode
                            )

                            TvOverlayAction.NEXT_EPISODE -> TvActionChip(
                                icon = Icons.Filled.SkipNext,
                                label = stringResource(R.string.next_episode),
                                focusRequester = overlayActionRequesters.getValue(action),
                                focusProperties = {
                                    left = actionRequester(utilityActions.getOrNull(index - 1))
                                    right = actionRequester(utilityActions.getOrNull(index + 1))
                                    up = overlayActionRequesters.getValue(mainActionForUtility(index))
                                },
                                onFocused = { onActionFocused(action) },
                                onClick = onNextEpisode
                            )

                            TvOverlayAction.AUDIO -> TvActionChip(
                                icon = Icons.Filled.Audiotrack,
                                label = stringResource(R.string.setting_audio_track),
                                focusRequester = overlayActionRequesters.getValue(action),
                                focusProperties = {
                                    left = actionRequester(utilityActions.getOrNull(index - 1))
                                    right = actionRequester(utilityActions.getOrNull(index + 1))
                                    up = overlayActionRequesters.getValue(mainActionForUtility(index))
                                },
                                onFocused = { onActionFocused(action) },
                                onClick = onOpenAudio
                            )

                            TvOverlayAction.SUBTITLE -> TvActionChip(
                                icon = Icons.Filled.Subtitles,
                                label = stringResource(R.string.setting_subtitles),
                                focusRequester = overlayActionRequesters.getValue(action),
                                focusProperties = {
                                    left = actionRequester(utilityActions.getOrNull(index - 1))
                                    right = actionRequester(utilityActions.getOrNull(index + 1))
                                    up = overlayActionRequesters.getValue(mainActionForUtility(index))
                                },
                                onFocused = { onActionFocused(action) },
                                onClick = onOpenSubtitle
                            )

                            TvOverlayAction.SCREEN_MODE -> TvActionChip(
                                icon = Icons.Filled.AspectRatio,
                                label = stringResource(R.string.setting_display_mode),
                                focusRequester = overlayActionRequesters.getValue(action),
                                focusProperties = {
                                    left = actionRequester(utilityActions.getOrNull(index - 1))
                                    right = actionRequester(utilityActions.getOrNull(index + 1))
                                    up = overlayActionRequesters.getValue(mainActionForUtility(index))
                                },
                                onFocused = { onActionFocused(action) },
                                onClick = onOpenScreenMode
                            )

                            TvOverlayAction.SETTINGS -> TvActionChip(
                                icon = Icons.Filled.Settings,
                                label = stringResource(R.string.nav_settings),
                                focusRequester = overlayActionRequesters.getValue(action),
                                focusProperties = {
                                    left = actionRequester(utilityActions.getOrNull(index - 1))
                                    right = actionRequester(utilityActions.getOrNull(index + 1))
                                    up = overlayActionRequesters.getValue(mainActionForUtility(index))
                                },
                                onFocused = { onActionFocused(action) },
                                onClick = onOpenSettings
                            )

                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPlayerPanelHost(
    modifier: Modifier,
    activePanel: TvPlayerPanel,
    playerState: PlayerState,
    channels: List<Channel>,
    currentChannelId: Long,
    isChannelSwitching: Boolean,
    onDismiss: () -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onSelectAspectRatio: (AspectRatioMode) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onOpenSpeedPanel: () -> Unit,
    onOpenStreamInfoPanel: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.44f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd
    ) {
        when (activePanel) {
            TvPlayerPanel.NONE -> Unit

            TvPlayerPanel.CHANNELS -> {
                TvOptionPanel(
                    title = stringResource(R.string.channel_list),
                    modifier = Modifier.clickable(enabled = false) {},
                    items = channels.map { channel ->
                        TvPanelOption(
                            title = channel.name,
                            subtitle = channel.groupTitle.ifBlank { null },
                            selected = channel.id == currentChannelId,
                            enabled = channel.id != currentChannelId && !isChannelSwitching,
                            onClick = { onSelectChannel(channel) }
                        )
                    }
                )
            }

            TvPlayerPanel.AUDIO -> {
                val options = if (playerState.audioTracks.isNotEmpty()) {
                    playerState.audioTracks.map { track ->
                        TvPanelOption(
                            title = track.name,
                            subtitle = track.language.ifBlank { null },
                            selected = track.isSelected,
                            onClick = { onSelectAudio(track.index) }
                        )
                    }
                } else {
                    listOf(
                        TvPanelOption(
                            title = stringResource(R.string.no_content),
                            enabled = false,
                            onClick = {}
                        )
                    )
                }

                TvOptionPanel(
                    title = stringResource(R.string.setting_audio_track),
                    modifier = Modifier.clickable(enabled = false) {},
                    items = options
                )
            }

            TvPlayerPanel.SUBTITLE -> {
                val options = buildList {
                    add(
                        TvPanelOption(
                            title = stringResource(R.string.language_off),
                            selected = playerState.selectedSubtitleTrack == -1,
                            onClick = onDisableSubtitles
                        )
                    )
                    addAll(
                        playerState.subtitleTracks.map { track ->
                            TvPanelOption(
                                title = track.name,
                                subtitle = track.language.ifBlank { null },
                                selected = track.isSelected,
                                onClick = { onSelectSubtitle(track.index) }
                            )
                        }
                    )
                }

                TvOptionPanel(
                    title = stringResource(R.string.setting_subtitles),
                    modifier = Modifier.clickable(enabled = false) {},
                    items = options
                )
            }

            TvPlayerPanel.SCREEN_MODE -> {
                TvOptionPanel(
                    title = stringResource(R.string.setting_display_mode),
                    modifier = Modifier.clickable(enabled = false) {},
                    items = AspectRatioMode.entries.map { mode ->
                        TvPanelOption(
                            title = mode.label,
                            selected = playerState.aspectRatioMode == mode,
                            onClick = { onSelectAspectRatio(mode) }
                        )
                    }
                )
            }

            TvPlayerPanel.SPEED -> {
                TvOptionPanel(
                    title = stringResource(R.string.setting_playback_speed),
                    modifier = Modifier.clickable(enabled = false) {},
                    items = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { speed ->
                        TvPanelOption(
                            title = "${speed}x",
                            selected = playerState.playbackSpeed == speed,
                            onClick = { onSelectSpeed(speed) }
                        )
                    }
                )
            }

            TvPlayerPanel.SETTINGS -> {
                TvOptionPanel(
                    title = stringResource(R.string.nav_settings),
                    modifier = Modifier.clickable(enabled = false) {},
                    items = listOf(
                        TvPanelOption(
                            title = stringResource(R.string.setting_playback_speed),
                            subtitle = "${playerState.playbackSpeed}x",
                            onClick = onOpenSpeedPanel
                        ),
                        TvPanelOption(
                            title = stringResource(R.string.setting_stream_info),
                            subtitle = playerState.currentVideoResolution.ifBlank { "—" },
                            onClick = onOpenStreamInfoPanel
                        ),
                        TvPanelOption(
                            title = stringResource(R.string.retry),
                            onClick = onRetry
                        )
                    )
                )
            }

            TvPlayerPanel.STREAM_INFO -> {
                TvInfoPanel(
                    title = stringResource(R.string.setting_stream_info),
                    rows = listOf(
                        "Resolution" to playerState.currentVideoResolution.ifBlank { "—" },
                        "Bitrate" to playerState.currentVideoBitrate.ifBlank { "—" },
                        "Codec" to playerState.currentVideoCodec.ifBlank { "—" },
                        "FPS" to playerState.currentVideoFps.ifBlank { "—" },
                        "Display" to playerState.aspectRatioMode.label
                    ),
                    onClose = onDismiss
                )
            }
        }
    }
}

@Composable
private fun TvOptionPanel(
    title: String,
    items: List<TvPanelOption>,
    modifier: Modifier = Modifier
) {
    val optionKeys = remember(title, items) {
        items.mapIndexed { index, item ->
            "$index|${item.title}|${item.subtitle.orEmpty()}|${item.selected}|${item.enabled}"
        }
    }
    val optionRequesters = remember(optionKeys) {
        optionKeys.map { FocusRequester() }
    }
    val initialFocusIndex = remember(items) {
        items.indexOfFirst { it.selected && it.enabled }
            .takeIf { it >= 0 }
            ?: items.indexOfFirst { it.enabled }.takeIf { it >= 0 }
    }

    LaunchedEffect(title, optionKeys, initialFocusIndex) {
        if (initialFocusIndex != null) {
            optionRequesters
                .getOrNull(initialFocusIndex)
                ?.requestFocusSafely("player option panel $title")
        }
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 54.dp, vertical = 44.dp)
            .width(460.dp)
            .fillMaxHeight(0.78f),
        shape = RoundedCornerShape(26.dp),
        color = ImaxColors.Surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, ImaxColors.CardBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            HorizontalDivider(color = ImaxColors.DividerColor)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_content),
                            style = MaterialTheme.typography.bodyLarge,
                            color = ImaxColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        items = items,
                        key = { index, item -> optionKeys[index] }
                    ) { index, item ->
                        TvPanelOptionRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            selected = item.selected,
                            enabled = item.enabled,
                            focusRequester = optionRequesters.getOrNull(index),
                            previousFocusRequester = optionRequesters.getOrNull(index - 1),
                            nextFocusRequester = optionRequesters.getOrNull(index + 1),
                            onClick = item.onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvInfoPanel(
    title: String,
    rows: List<Pair<String, String>>,
    onClose: () -> Unit
) {
    val closeRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeRequester.requestFocusSafely("player info panel close action")
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 54.dp, vertical = 44.dp)
            .width(460.dp)
            .fillMaxHeight(0.58f),
        shape = RoundedCornerShape(26.dp),
        color = ImaxColors.Surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, ImaxColors.CardBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            HorizontalDivider(color = ImaxColors.DividerColor)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TvActionChip(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = stringResource(R.string.cancel),
                    focusRequester = closeRequester,
                    onFocused = {},
                    onClick = onClose
                )
                rows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImaxColors.TextSecondary
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvErrorState(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val retryRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        retryRequester.requestFocusSafely("player error retry action")
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 740.dp),
            shape = RoundedCornerShape(28.dp),
            color = ImaxColors.Surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, ImaxColors.Error.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = ImaxColors.Error,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvActionChip(
                        icon = Icons.Filled.PlayArrow,
                        label = stringResource(R.string.retry),
                        focusRequester = retryRequester,
                        onFocused = {},
                        onClick = onRetry
                    )
                    TvActionChip(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = stringResource(R.string.cancel),
                        onFocused = {},
                        onClick = onExit
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    focusProperties: (androidx.compose.ui.focus.FocusProperties.() -> Unit)? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    var isFocused by remember(label, enabled, primary) { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        isSelected = primary,
        defaultSurface = Color.Black.copy(alpha = 0.36f),
        selectedSurface = ImaxColors.Primary,
        focusedSurface = Color(0xFF2D2D34),
        selectedFocusedSurface = ImaxColors.PrimaryVariant,
        defaultContentColor = Color.White,
        defaultSecondaryContentColor = Color.White.copy(alpha = 0.82f),
        selectedContentColor = Color.White,
        focusedContentColor = Color.White,
        selectedFocusedContentColor = Color.White,
        selectedBorderColor = Color.White.copy(alpha = 0.12f),
        focusedBorderColor = ImaxColors.FocusBorder,
        selectedFocusedBorderColor = Color(0xFFFFE1C8),
        selectedAccentColor = Color.Transparent,
        focusedAccentColor = Color.Transparent,
        selectedFocusedAccentColor = Color.Transparent
    )

    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.42f)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .focusRequester(focusRequester)
            .then(
                if (focusProperties != null) {
                    Modifier.focusProperties { focusProperties() }
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(enabled = enabled),
        shape = RoundedCornerShape(28.dp),
        color = focusState.backgroundColor,
        border = BorderStroke(
            width = focusState.borderWidth.coerceAtLeast(1.dp),
            color = if (focusState.borderWidth > 0.dp) {
                focusState.borderColor
            } else {
                Color.White.copy(alpha = 0.08f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = focusState.contentColor,
                modifier = Modifier.size(if (primary) 42.dp else 34.dp)
            )
            Text(
                text = label,
                style = if (primary) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = focusState.contentColor,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvActionChip(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    focusProperties: (androidx.compose.ui.focus.FocusProperties.() -> Unit)? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember(label) { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        defaultSurface = Color.Black.copy(alpha = 0.34f),
        selectedSurface = Color.Black.copy(alpha = 0.34f),
        focusedSurface = Color(0xFF2D2D34),
        selectedFocusedSurface = Color(0xFF2D2D34),
        defaultContentColor = Color.White,
        defaultSecondaryContentColor = Color.White.copy(alpha = 0.82f),
        selectedContentColor = Color.White,
        focusedContentColor = Color.White,
        selectedFocusedContentColor = Color.White,
        selectedBorderColor = Color.White.copy(alpha = 0.1f),
        focusedBorderColor = ImaxColors.FocusBorder,
        selectedFocusedBorderColor = ImaxColors.FocusBorder,
        selectedAccentColor = Color.Transparent,
        focusedAccentColor = Color.Transparent,
        selectedFocusedAccentColor = Color.Transparent
    )

    Surface(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .then(
                if (focusProperties != null) {
                    Modifier.focusProperties { focusProperties() }
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(),
        shape = RoundedCornerShape(999.dp),
        color = focusState.backgroundColor,
        border = BorderStroke(
            width = focusState.borderWidth.coerceAtLeast(1.dp),
            color = if (focusState.borderWidth > 0.dp) {
                focusState.borderColor
            } else {
                Color.White.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = focusState.contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = focusState.contentColor
            )
        }
    }
}

private fun FocusRequester.requestFocusSafely(reason: String) {
    runCatching { requestFocus() }
        .onFailure { error ->
            Timber.tag(TV_PLAYER_LOG_TAG).w(error, "Unable to request focus for %s", reason)
        }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvPanelOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    previousFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null
) {
    var isFocused by remember(title, subtitle, selected, enabled) { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        isSelected = selected,
        defaultSurface = Color.Transparent,
        selectedSurface = ImaxColors.Primary.copy(alpha = 0.16f),
        focusedSurface = ImaxColors.SurfaceVariant,
        selectedFocusedSurface = Color(0xFF5A4035),
        defaultContentColor = Color.White,
        defaultSecondaryContentColor = ImaxColors.TextSecondary,
        selectedContentColor = Color(0xFFFFE4D3),
        focusedContentColor = Color.White,
        selectedFocusedContentColor = Color.White
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.52f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .focusProperties {
                up = previousFocusRequester ?: FocusRequester.Cancel
                down = nextFocusRequester ?: FocusRequester.Cancel
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled),
        shape = RoundedCornerShape(18.dp),
        color = focusState.backgroundColor,
        border = BorderStroke(
            width = focusState.borderWidth,
            color = focusState.borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = focusState.contentColor
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = focusState.secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ImaxColors.Primary
                )
            }
        }
    }
}

@Composable
private fun TvStatusBadge(
    label: String,
    background: Color = Color.White.copy(alpha = 0.08f),
    contentColor: Color = Color.White
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private fun tvPlaybackSubtitle(
    isLivePlayback: Boolean,
    isSeriesPlayback: Boolean,
    sessionEpisode: Episode?,
    currentChannel: Channel?,
    liveGroup: String
): String {
    return when {
        isLivePlayback -> {
            listOfNotNull(
                currentChannel?.groupTitle?.takeIf { it.isNotBlank() },
                liveGroup.takeIf { it.isNotBlank() }
            ).distinct().joinToString(" • ")
        }

        isSeriesPlayback && sessionEpisode != null -> {
            buildString {
                append("S")
                append(sessionEpisode.seasonNumber)
                append("E")
                append(sessionEpisode.episodeNumber)
                if (sessionEpisode.name.isNotBlank()) {
                    append(" • ")
                    append(sessionEpisode.name)
                }
            }
        }

        else -> ""
    }
}


// ─── TV D-pad Seek Bar ─────────────────────────────────────────────────────────
// Intercepts Left / Right D-pad key presses to seek ±10s.
// Highlights in primary color when TV focus is on it.
@Composable
private fun TvSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onFocused: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var scrubPosition by remember(currentPosition) { mutableStateOf(currentPosition) }

    val progress = if (duration > 0L) {
        (scrubPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                    scrubPosition = currentPosition
                }
            }
            .onPreviewKeyEvent { event ->
                if (!isFocused) return@onPreviewKeyEvent false
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        scrubPosition = maxOf(0L, scrubPosition - 10_000L)
                        onSeekBackward()
                        true
                    }
                    Key.DirectionRight -> {
                        scrubPosition = minOf(duration, scrubPosition + 10_000L)
                        onSeekForward()
                        true
                    }
                    else -> false
                }
            }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 10.dp else 6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (isFocused) ImaxColors.Primary else Color.White.copy(alpha = 0.85f),
            trackColor = Color.White.copy(alpha = if (isFocused) 0.28f else 0.18f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = StringUtils.formatDuration(scrubPosition),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) ImaxColors.Primary else Color.White
            )
            if (isFocused) {
                Text(
                    text = "◄ 10s  |  YÖNLÜ TUŞLAR  |  10s ►",
                    style = MaterialTheme.typography.labelSmall,
                    color = ImaxColors.Primary.copy(alpha = 0.8f)
                )
            }
            Text(
                text = StringUtils.formatDuration(duration),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}
