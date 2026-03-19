package com.imax.player.ui.tv

import android.app.Activity
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
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
import androidx.compose.runtime.key as composeKey
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.imax.player.R
import com.imax.player.core.common.StringUtils
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.model.Channel
import com.imax.player.core.model.ContentType
import com.imax.player.core.model.Episode
import com.imax.player.core.player.AspectRatioMode
import com.imax.player.core.player.EngineSwitchState
import com.imax.player.core.player.ExoPlayerEngine
import com.imax.player.core.player.PlaybackState
import com.imax.player.core.player.PlayerState
import com.imax.player.core.player.VlcPlayerEngine
import com.imax.player.ui.player.PlayerViewModel

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

private data class TvPanelOption(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@androidx.annotation.OptIn(UnstableApi::class)
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
    val switchState by viewModel.switchState.collectAsStateWithLifecycle()
    val activeEngineName by viewModel.activeEngineName.collectAsStateWithLifecycle()
    val engineReady by viewModel.engineReady.collectAsStateWithLifecycle()
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
    val displayTitle = session.title.ifBlank { title }
    val playbackActive = playerState.playbackState == PlaybackState.PLAYING ||
        playerState.playbackState == PlaybackState.BUFFERING ||
        switchState == EngineSwitchState.SWITCHING ||
        isChannelSwitching

    val rootFocusRequester = remember { FocusRequester() }
    val overlayActionRequesters = remember {
        TvOverlayAction.entries.associateWith { FocusRequester() }
    }

    var overlayVisible by rememberSaveable { mutableStateOf(true) }
    var activePanel by rememberSaveable { mutableStateOf(TvPlayerPanel.NONE) }
    var lastOverlayAction by rememberSaveable { mutableStateOf(TvOverlayAction.PLAY_PAUSE) }
    var requestedOverlayAction by rememberSaveable { mutableStateOf(TvOverlayAction.PLAY_PAUSE) }
    var interactionVersion by remember { mutableIntStateOf(0) }

    fun registerInteraction() {
        interactionVersion += 1
    }

    fun showOverlay(action: TvOverlayAction = lastOverlayAction) {
        requestedOverlayAction = action
        overlayVisible = true
        registerInteraction()
    }

    fun hideOverlay() {
        overlayVisible = false
        activePanel = TvPlayerPanel.NONE
        registerInteraction()
    }

    fun closePanel() {
        activePanel = TvPlayerPanel.NONE
        showOverlay(lastOverlayAction)
    }

    fun exitPlayer() {
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

    LaunchedEffect(url, title, contentId, contentType, startPosition, groupContext) {
        viewModel.init(url, title, startPosition, contentId, contentType, groupContext)
    }

    LaunchedEffect(overlayVisible, activePanel, requestedOverlayAction) {
        when {
            activePanel != TvPlayerPanel.NONE -> Unit
            overlayVisible -> overlayActionRequesters.getValue(requestedOverlayAction).requestFocus()
            else -> rootFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(overlayVisible, activePanel, interactionVersion, playerState.isPlaying) {
        if (overlayVisible && activePanel == TvPlayerPanel.NONE && playerState.isPlaying) {
            kotlinx.coroutines.delay(6500)
            if (overlayVisible && activePanel == TvPlayerPanel.NONE && playerState.isPlaying) {
                overlayVisible = false
                rootFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(playerState.playbackState, switchState, isChannelSwitching) {
        if (
            playerState.playbackState == PlaybackState.ERROR &&
            switchState != EngineSwitchState.SWITCHING &&
            !isChannelSwitching
        ) {
            showOverlay(TvOverlayAction.PLAY_PAUSE)
        }
    }

    BackHandler(onBack = ::handleBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.key) {
                    Key.Back,
                    Key.Escape -> handleBack()

                    Key.MediaPlayPause,
                    Key.Spacebar -> {
                        registerInteraction()
                        showOverlay(TvOverlayAction.PLAY_PAUSE)
                        viewModel.togglePlayPause()
                        true
                    }

                    Key.DirectionCenter,
                    Key.Enter -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
                            showOverlay(TvOverlayAction.PLAY_PAUSE)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp,
                    Key.DirectionDown -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
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
                                viewModel.playPreviousChannel()
                                showOverlay(TvOverlayAction.MAIN_PREVIOUS)
                            } else {
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
                                viewModel.playNextChannel()
                                showOverlay(TvOverlayAction.MAIN_NEXT)
                            } else {
                                viewModel.seekForward()
                                showOverlay(TvOverlayAction.MAIN_NEXT)
                            }
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
    ) {
        TvPlayerVideoSurface(
            modifier = Modifier.fillMaxSize(),
            engineReady = engineReady,
            viewModel = viewModel,
            playerState = playerState,
            activeEngineName = activeEngineName,
            playbackActive = playbackActive,
            switchState = switchState
        )

        if (!engineReady || playerState.playbackState == PlaybackState.BUFFERING || isChannelSwitching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (playerState.playbackState == PlaybackState.BUFFERING || isChannelSwitching) {
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
                            isChannelSwitching -> liveChannelSwitch.targetTitle.ifBlank {
                                stringResource(R.string.channel_list)
                            }

                            !engineReady -> stringResource(R.string.loading)
                            else -> stringResource(R.string.buffering)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = switchState == EngineSwitchState.SWITCHING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ImaxColors.Primary)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.switching_engine),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        if (
            playerState.playbackState == PlaybackState.ERROR &&
            switchState != EngineSwitchState.SWITCHING &&
            !isChannelSwitching
        ) {
            TvErrorState(
                message = playerState.errorMessage ?: stringResource(R.string.playback_error),
                onRetry = {
                    showOverlay(TvOverlayAction.PLAY_PAUSE)
                    viewModel.retryCurrent()
                },
                onSwitchEngine = {
                    showOverlay(TvOverlayAction.PLAY_PAUSE)
                    viewModel.switchEngine()
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
                engineName = activeEngineName,
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
                activeEngineName = activeEngineName,
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
                },
                onSwitchEngine = {
                    viewModel.switchEngine()
                    closePanel()
                }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun TvPlayerVideoSurface(
    modifier: Modifier,
    engineReady: Boolean,
    viewModel: PlayerViewModel,
    playerState: PlayerState,
    activeEngineName: String,
    playbackActive: Boolean,
    switchState: EngineSwitchState
) {
    val engine = remember(activeEngineName, engineReady) {
        if (engineReady) viewModel.playerManager.getEngine() else null
    }

    if (!engineReady || engine == null) {
        return
    }

    if (switchState == EngineSwitchState.SWITCHING) {
        Spacer(modifier = modifier)
        return
    }

    composeKey(engine.engineName) {
        when (engine) {
            is ExoPlayerEngine -> {
                val exoPlayer = engine.getExoPlayer()

                if (exoPlayer != null) {
                    BoxWithConstraints(
                        modifier = modifier,
                        contentAlignment = Alignment.Center
                    ) {
                        val viewportAspectRatio = if (maxHeight > 0.dp) {
                            maxWidth.value / maxHeight.value
                        } else {
                            null
                        }

                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    controllerHideOnTouch = false
                                    controllerAutoShow = false
                                    setKeepContentOnPlayerReset(true)
                                    keepScreenOn = playbackActive
                                    isFocusable = false
                                    isFocusableInTouchMode = false
                                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    engine.setPlayerView(this)
                                }
                            },
                            update = { playerView ->
                                playerView.player = exoPlayer
                                playerView.keepScreenOn = playbackActive
                                playerView.resizeMode = engine.getResizeModeFor(playerState.aspectRatioMode)
                                playerView.videoSurfaceView?.keepScreenOn = playbackActive
                                playerView.videoSurfaceView?.isFocusable = false
                                playerView.videoSurfaceView?.isFocusableInTouchMode = false
                            },
                            modifier = playerViewportModifier(
                                targetAspectRatio = engine.getViewportAspectRatio(playerState.aspectRatioMode),
                                viewportAspectRatio = viewportAspectRatio
                            )
                        )
                    }

                    DisposableEffect(engine.engineName) {
                        onDispose { engine.clearPlayerView() }
                    }
                }
            }

            is VlcPlayerEngine -> {
                BoxWithConstraints(
                    modifier = modifier,
                    contentAlignment = Alignment.Center
                ) {
                    val viewportAspectRatio = if (maxHeight > 0.dp) {
                        maxWidth.value / maxHeight.value
                    } else {
                        null
                    }

                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                keepScreenOn = playbackActive
                                isFocusable = false
                                isFocusableInTouchMode = false
                                setZOrderMediaOverlay(false)
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }.also(engine::setSurface)
                        },
                        update = { surfaceView ->
                            surfaceView.keepScreenOn = playbackActive
                            if (surfaceView.width > 0 && surfaceView.height > 0) {
                                engine.updateSurfaceSize(surfaceView.width, surfaceView.height)
                            }
                        },
                        modifier = playerViewportModifier(
                            targetAspectRatio = forcedViewportAspectRatio(
                                mode = playerState.aspectRatioMode,
                                videoWidth = playerState.videoWidth,
                                videoHeight = playerState.videoHeight
                            ),
                            viewportAspectRatio = viewportAspectRatio
                        )
                    )
                }

                DisposableEffect(engine.engineName) {
                    onDispose { engine.detachSurface() }
                }
            }
        }
    }
}

@Composable
private fun TvPlaybackOverlay(
    title: String,
    subtitle: String,
    engineName: String,
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
                    TvStatusBadge(label = engineName)
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = {
                                if (playerState.duration > 0L) {
                                    (playerState.currentPosition.toFloat() / playerState.duration.toFloat())
                                        .coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = ImaxColors.Primary,
                            trackColor = Color.White.copy(alpha = 0.18f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = StringUtils.formatDuration(playerState.currentPosition),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = StringUtils.formatDuration(playerState.duration),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
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
                        onFocused = { onActionFocused(TvOverlayAction.MAIN_PREVIOUS) },
                        onClick = onPrevious
                    )
                    TvActionButton(
                        icon = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (playerState.isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                        enabled = true,
                        primary = true,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                        onFocused = { onActionFocused(TvOverlayAction.PLAY_PAUSE) },
                        onClick = onPlayPause
                    )
                    TvActionButton(
                        icon = if (isLivePlayback) Icons.Filled.SkipNext else Icons.Filled.Forward10,
                        label = if (isLivePlayback) stringResource(R.string.next_channel) else "10s",
                        enabled = if (isLivePlayback) hasNextChannel else true,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.MAIN_NEXT),
                        onFocused = { onActionFocused(TvOverlayAction.MAIN_NEXT) },
                        onClick = onNext
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLivePlayback) {
                        TvActionChip(
                            icon = Icons.AutoMirrored.Filled.List,
                            label = stringResource(R.string.channel_list),
                            focusRequester = overlayActionRequesters.getValue(TvOverlayAction.CHANNELS),
                            onFocused = { onActionFocused(TvOverlayAction.CHANNELS) },
                            onClick = onOpenChannels
                        )
                    }

                    if (isSeriesPlayback && hasPreviousEpisode) {
                        TvActionChip(
                            icon = Icons.Filled.SkipPrevious,
                            label = stringResource(R.string.previous_episode),
                            focusRequester = overlayActionRequesters.getValue(TvOverlayAction.PREVIOUS_EPISODE),
                            onFocused = { onActionFocused(TvOverlayAction.PREVIOUS_EPISODE) },
                            onClick = onPreviousEpisode
                        )
                    }

                    if (isSeriesPlayback && hasNextEpisode) {
                        TvActionChip(
                            icon = Icons.Filled.SkipNext,
                            label = stringResource(R.string.next_episode),
                            focusRequester = overlayActionRequesters.getValue(TvOverlayAction.NEXT_EPISODE),
                            onFocused = { onActionFocused(TvOverlayAction.NEXT_EPISODE) },
                            onClick = onNextEpisode
                        )
                    }

                    TvActionChip(
                        icon = Icons.Filled.Audiotrack,
                        label = stringResource(R.string.setting_audio_track),
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.AUDIO),
                        onFocused = { onActionFocused(TvOverlayAction.AUDIO) },
                        onClick = onOpenAudio
                    )
                    TvActionChip(
                        icon = Icons.Filled.Subtitles,
                        label = stringResource(R.string.setting_subtitles),
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.SUBTITLE),
                        onFocused = { onActionFocused(TvOverlayAction.SUBTITLE) },
                        onClick = onOpenSubtitle
                    )
                    TvActionChip(
                        icon = Icons.Filled.AspectRatio,
                        label = stringResource(R.string.setting_display_mode),
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.SCREEN_MODE),
                        onFocused = { onActionFocused(TvOverlayAction.SCREEN_MODE) },
                        onClick = onOpenScreenMode
                    )
                    TvActionChip(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.nav_settings),
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.SETTINGS),
                        onFocused = { onActionFocused(TvOverlayAction.SETTINGS) },
                        onClick = onOpenSettings
                    )
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
    activeEngineName: String,
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
    onRetry: () -> Unit,
    onSwitchEngine: () -> Unit
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
                            subtitle = playerState.currentVideoResolution.ifBlank { activeEngineName },
                            onClick = onOpenStreamInfoPanel
                        ),
                        TvPanelOption(
                            title = stringResource(R.string.retry),
                            onClick = onRetry
                        ),
                        TvPanelOption(
                            title = stringResource(R.string.setting_player_engine),
                            subtitle = activeEngineName,
                            onClick = onSwitchEngine
                        )
                    )
                )
            }

            TvPlayerPanel.STREAM_INFO -> {
                TvInfoPanel(
                    title = stringResource(R.string.setting_stream_info),
                    rows = listOf(
                        stringResource(R.string.setting_player_engine) to activeEngineName,
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
    val firstRequester = remember(title, items.size) { FocusRequester() }

    LaunchedEffect(title, items.size) {
        firstRequester.requestFocus()
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
                itemsIndexed(items) { index, item ->
                    TvPanelOptionRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        selected = item.selected,
                        enabled = item.enabled,
                        focusRequester = if (index == 0) firstRequester else null,
                        onClick = item.onClick
                    )
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
        closeRequester.requestFocus()
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
    onSwitchEngine: () -> Unit,
    onExit: () -> Unit
) {
    val retryRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        retryRequester.requestFocus()
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
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.setting_player_engine),
                        onFocused = {},
                        onClick = onSwitchEngine
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
private fun TvActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.42f)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(enabled = enabled),
        shape = RoundedCornerShape(28.dp),
        color = when {
            primary && isFocused -> ImaxColors.PrimaryVariant
            primary -> ImaxColors.Primary
            isFocused -> Color.White.copy(alpha = 0.18f)
            else -> Color.Black.copy(alpha = 0.36f)
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) ImaxColors.FocusBorder else Color.White.copy(alpha = 0.08f)
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
                tint = Color.White,
                modifier = Modifier.size(if (primary) 42.dp else 34.dp)
            )
            Text(
                text = label,
                style = if (primary) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TvActionChip(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(),
        shape = RoundedCornerShape(999.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.34f),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) ImaxColors.FocusBorder else Color.White.copy(alpha = 0.1f)
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
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun TvPanelOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.52f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled),
        shape = RoundedCornerShape(18.dp),
        color = when {
            selected -> ImaxColors.Primary.copy(alpha = 0.18f)
            isFocused -> ImaxColors.SurfaceVariant
            else -> Color.Transparent
        },
        border = BorderStroke(
            width = if (selected || isFocused) 1.dp else 0.dp,
            color = when {
                selected -> ImaxColors.Primary
                isFocused -> ImaxColors.FocusBorder.copy(alpha = 0.72f)
                else -> Color.Transparent
            }
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
                    color = Color.White
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ImaxColors.TextSecondary,
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
