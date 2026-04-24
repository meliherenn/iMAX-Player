package com.imax.player.ui.player

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.imax.player.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.imax.player.core.common.StringUtils
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.*
import com.imax.player.core.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
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
    val playerReady by viewModel.playerReady.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val liveChannelSwitch by viewModel.liveChannelSwitch.collectAsStateWithLifecycle()
    val retryState by viewModel.retryState.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val currentEpgProgram by viewModel.currentEpgProgram.collectAsStateWithLifecycle()
    val nextEpgProgram by viewModel.nextEpgProgram.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChannelSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    // Screen lock — mobile only
    var isScreenLocked by remember { mutableStateOf(false) }
    // Context and activity must be declared before PiP and AudioManager references
    val context = LocalContext.current
    val activity = context as? Activity
    // PiP — read from activity
    val isPipMode by (activity as? com.imax.player.MainActivity)?.isPipMode?.collectAsStateWithLifecycle(false)
        ?: remember { mutableStateOf(false) }
    // Volume/brightness drag gesture state
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    var isDragging by remember { mutableStateOf(false) }
    var dragType by remember { mutableStateOf<String?>(null) } // "brightness" | "volume"
    var dragValue by remember { mutableStateOf(0f) }  // 0..1 for display
    var showDragIndicator by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val displayTitle = session.title.ifBlank { title }
    val isLivePlayback = contentType == ContentType.LIVE.name || session.currentChannel != null
    val isSeriesPlayback = contentType == ContentType.SERIES.name || session.currentEpisode != null
    val isChannelSwitching = liveChannelSwitch.isSwitching
    val showSwitchingOverlay = isChannelSwitching && !playerState.isPlaybackConfirmed
    val showBufferingOverlay = playerState.playbackState == PlaybackState.BUFFERING &&
        !playerState.isPlaybackConfirmed
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

    LaunchedEffect(url, contentId, isLivePlayback, settings.startFullscreenLive) {
        controlsVisible = !(isLivePlayback && settings.startFullscreenLive)
    }

    // Register player screen as active so onUserLeaveHint() triggers PiP
    DisposableEffect(Unit) {
        (activity as? com.imax.player.MainActivity)?.isPlayerScreenActive = true
        onDispose {
            (activity as? com.imax.player.MainActivity)?.isPlayerScreenActive = false
        }
    }

    val playbackActive = remember(playerState.playbackState, isChannelSwitching) {
        playerState.playbackState == PlaybackState.PLAYING ||
            playerState.playbackState == PlaybackState.BUFFERING ||
            isChannelSwitching
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

    // Load EPG when channel changes
    val epgChannelId = session.currentChannel?.epgChannelId ?: ""
    LaunchedEffect(epgChannelId) {
        if (epgChannelId.isNotBlank()) {
            viewModel.loadEpg(epgChannelId)
        }
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
                                Key.DirectionUp -> {
                                    if (!controlsVisible && isLivePlayback && !isChannelSwitching) {
                                        viewModel.playNextChannel()
                                        true
                                    } else {
                                        controlsVisible = true; true
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (!controlsVisible && isLivePlayback && !isChannelSwitching) {
                                        viewModel.playPreviousChannel()
                                        true
                                    } else {
                                        controlsVisible = true; true
                                    }
                                }
                                Key.Back, Key.Escape -> {
                                    if (showSettingsSheet) {
                                        showSettingsSheet = false; true
                                    } else if (showChannelSheet) {
                                        showChannelSheet = false; true
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
                    // Mobile: tap gestures + vertical drag for brightness/volume
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { if (!isDragging) controlsVisible = !controlsVisible },
                                onDoubleTap = { offset ->
                                    if (isDragging) return@detectTapGestures
                                    val screenWidth = size.width
                                    if (offset.x < screenWidth / 3) viewModel.seekBackward()
                                    else if (offset.x > screenWidth * 2 / 3) viewModel.seekForward()
                                    else viewModel.togglePlayPause()
                                    controlsVisible = true
                                }
                            )
                        }
                        .pointerInput(maxVolume) {
                            detectDragGestures(
                                onDragStart = { offset: androidx.compose.ui.geometry.Offset ->
                                    dragType = if (offset.x < size.width / 2f) "brightness" else "volume"
                                    isDragging = true
                                    showDragIndicator = true
                                    dragValue = when (dragType) {
                                        "brightness" -> {
                                            val lp = activity?.window?.attributes
                                            (lp?.screenBrightness ?: 0.5f).coerceIn(0f, 1f)
                                        }
                                        else -> audioManager.getStreamVolume(
                                            android.media.AudioManager.STREAM_MUSIC
                                        ).toFloat() / maxVolume.toFloat()
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    dragType = null
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragType = null
                                },
                                onDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange,
                                           dragAmount: androidx.compose.ui.geometry.Offset ->
                                    val delta = -dragAmount.y / size.height.toFloat()
                                    when (dragType) {
                                        "brightness" -> {
                                            val win = activity?.window ?: return@detectDragGestures
                                            val lp = win.attributes
                                            val newBrightness = (lp.screenBrightness + delta).coerceIn(0.01f, 1f)
                                            lp.screenBrightness = newBrightness
                                            win.attributes = lp
                                            dragValue = newBrightness
                                        }
                                        "volume" -> {
                                            val current = audioManager.getStreamVolume(
                                                android.media.AudioManager.STREAM_MUSIC
                                            )
                                            val newVol = (current.toFloat() + delta * maxVolume.toFloat())
                                                .toInt().coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(
                                                android.media.AudioManager.STREAM_MUSIC, newVol, 0
                                            )
                                            dragValue = newVol.toFloat() / maxVolume.toFloat()
                                        }
                                    }
                                }
                            )
                        }


                }
            )
    ) {
        PlayerSurfaceHost(
            modifier = Modifier.fillMaxSize(),
            playerEngine = viewModel.playerManager.getEngine(),
            playerState = playerState,
            surfaceState = PlayerSurfaceHostState(
                playerReady = playerReady,
                playbackActive = playbackActive,
                shellMode = PlayerShellMode.MOBILE
            ),
            placeholder = {
                CircularProgressIndicator(
                    color = ImaxColors.Primary,
                    modifier = Modifier.size(40.dp).align(Alignment.Center)
                )
            }
        )

        // Buffering indicator
        if (showBufferingOverlay) {
            CircularProgressIndicator(
                color = ImaxColors.Primary,
                modifier = Modifier.size(48.dp).align(Alignment.Center)
            )
        }

        // ─── Brightness / Volume drag indicator ─────────────────────────────────
        if (!isTv && showDragIndicator && dragType != null) {
            LaunchedEffect(isDragging) {
                if (!isDragging) {
                    delay(1500)
                    showDragIndicator = false
                }
            }
            val isLeft = dragType == "brightness"
            Box(
                modifier = Modifier
                    .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 32.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isLeft) Icons.Filled.Brightness6 else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "${(dragValue * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                    LinearProgressIndicator(
                        progress = { dragValue },
                        modifier = Modifier.width(60.dp).height(3.dp),
                        color = ImaxColors.Primary,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }

        // ─── Sleep Timer badge (top-right, always visible when active) ──────────
        if (!isTv && sleepTimerState.isActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 72.dp, top = 8.dp)
                    .clickable { showSleepTimerSheet = true },
                shape = RoundedCornerShape(8.dp),
                color = if (sleepTimerState.isLastMinute)
                    Color(0xFFFFB300).copy(alpha = 0.92f)
                else
                    Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = "Uyku Zamanlayıcı",
                        tint = if (sleepTimerState.isLastMinute) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = sleepTimerState.remainingFormatted,
                        color = if (sleepTimerState.isLastMinute) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Error state — with StreamRetryManager auto-retry countdown
        if (
            playerState.playbackState == PlaybackState.ERROR &&
            !isChannelSwitching
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (retryState.isRetrying) {
                        // Auto-retry in progress
                        CircularProgressIndicator(
                            color = ImaxColors.Primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = retryState.userMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (retryState.nextRetryInSeconds > 0) {
                            Text(
                                text = "${retryState.nextRetryInSeconds}s",
                                color = ImaxColors.Primary,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        // Allow manual cancel of auto-retry
                        OutlinedButton(
                            onClick = {
                                viewModel.retryManager.cancel()
                                viewModel.retryCurrent()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color.White.copy(alpha = 0.4f)
                            )
                        ) {
                            Text("Şimdi Dene")
                        }
                    } else {
                        // Manual retry controls
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = ImaxColors.Error,
                            modifier = Modifier.size(44.dp)
                        )
                        val displayError = retryState.userMessage
                            .ifBlank { playerState.errorMessage ?: "Oynatma hatası" }
                        Text(
                            text = displayError,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        OutlinedButton(
                            onClick = { viewModel.retryCurrent() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }



        AnimatedVisibility(
            visible = showSwitchingOverlay,
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

        // Controls overlay — hidden in PiP mode
        AnimatedVisibility(
            visible = controlsVisible && !isPipMode && !isChannelSwitching,
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
                            icon = Icons.AutoMirrored.Filled.List,
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
                    // EPG mini strip — live TV only, when EPG data available
                    if (isLivePlayback && currentEpgProgram != null && !isPipMode) {
                        EpgMiniStrip(
                            currentProgram = currentEpgProgram,
                            nextProgram = nextEpgProgram
                        )
                        Spacer(Modifier.height(4.dp))
                    }
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

                        if (isLivePlayback) {
                            Spacer(modifier = Modifier.width(4.dp))
                            PlayerControlButton(
                                icon = Icons.AutoMirrored.Filled.List,
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

                        // Sleep timer button — mobile only
                        if (!isTv) {
                            PlayerControlButton(
                                icon = Icons.Filled.Bedtime,
                                contentDescription = "Uyku Zamanlayıcı",
                                label = if (sleepTimerState.isActive) sleepTimerState.remainingFormatted else null,
                                onClick = { showSleepTimerSheet = true }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Screen lock button — mobile only
                        if (!isTv) {
                            PlayerControlButton(
                                icon = Icons.Filled.Lock,
                                contentDescription = "Ekranı Kilitle",
                                onClick = {
                                    isScreenLocked = true
                                    controlsVisible = false
                                }
                            )
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

    // ─── Screen Lock Overlay ────────────────────────────────────────────────────
    // Sits above all content, blocks all touches when active
    if (isScreenLocked && !isTv) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { /* consume all touches */ }
                .background(Color.Transparent),
            contentAlignment = Alignment.TopEnd
        ) {
            // Persistent unlock button — only interactive element
            Surface(
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 0.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clickable { isScreenLocked = false; controlsVisible = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = "Kilidi Aç",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Kilidi Aç",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
    // ─── Sleep Timer Bottom Sheet ────────────────────────────────────────────────
    if (showSleepTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            containerColor = ImaxColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, tint = ImaxColors.Primary)
                    Text(
                        "Uyku Zamanlayıcı",
                        style = MaterialTheme.typography.titleMedium,
                        color = ImaxColors.TextPrimary
                    )
                }
                Spacer(Modifier.height(16.dp))
                viewModel.sleepTimer.availableOptions.forEach { minutes ->
                    val isSelected = sleepTimerState.isActive && sleepTimerState.selectedMinutes == minutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) ImaxColors.Primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.setSleepTimer(minutes)
                                showSleepTimerSheet = false
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$minutes dakika",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) ImaxColors.Primary else ImaxColors.TextPrimary
                        )
                        if (isSelected) {
                            Text(
                                sleepTimerState.remainingFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = ImaxColors.Primary
                            )
                        }
                    }
                }
                if (sleepTimerState.isActive) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = ImaxColors.DividerColor)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.cancelSleepTimer()
                                showSleepTimerSheet = false
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.TimerOff, contentDescription = null, tint = ImaxColors.Error, modifier = Modifier.size(20.dp))
                        Text("Zamanlayıcıyı İptal Et", style = MaterialTheme.typography.bodyLarge, color = ImaxColors.Error)
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
            isTv = isTv,
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
            isTv = isTv,
            onDismiss = { showSettingsSheet = false },
            onSetAspectRatio = { viewModel.setAspectRatio(it) },
            onSetSpeed = { viewModel.setSpeed(it) },
            onSetQualityMode = { viewModel.setVideoQualityMode(it) },
            onSelectVideoTrack = { viewModel.selectVideoTrack(it) },
            onSelectAudio = { viewModel.selectAudio(it) },
            onSelectSubtitle = { viewModel.selectSubtitle(it) },
            onDisableSubtitles = { viewModel.disableSubtitles() }
        )
    }
}

// ─── EPG Mini Strip ─────────────────────────────────────────────────────────────
@Composable
fun EpgMiniStrip(
    currentProgram: com.imax.player.data.parser.EpgProgram?,
    nextProgram: com.imax.player.data.parser.EpgProgram?
) {
    currentProgram ?: return
    val nowMs = System.currentTimeMillis()
    val progress = ((nowMs - currentProgram.startTime).toFloat() /
        (currentProgram.endTime - currentProgram.startTime).toFloat())
        .coerceIn(0f, 1f)
    val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val startFmt = remember(currentProgram.startTime) { timeFmt.format(java.util.Date(currentProgram.startTime)) }
    val endFmt = remember(currentProgram.endTime) { timeFmt.format(java.util.Date(currentProgram.endTime)) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ImaxColors.Primary.copy(alpha = 0.85f),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text("CANLI", style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
            }
            Text(text = currentProgram.title, style = MaterialTheme.typography.labelMedium,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(text = "$startFmt – $endFmt", style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
            color = ImaxColors.Primary,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        nextProgram?.let { next ->
            Spacer(Modifier.height(2.dp))
            Text(text = "Sonraki: ${next.title}", style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Player Settings Bottom Sheet
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    playerState: PlayerState,
    isTv: Boolean,
    onDismiss: () -> Unit,
    onSetAspectRatio: (AspectRatioMode) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetQualityMode: (VideoQualityMode) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitles: () -> Unit
) {
    var activeSection by remember { mutableStateOf<String?>(null) }

    if (isTv) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(340.dp).padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = ImaxColors.Surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    PlayerSettingsContent(
                        playerState = playerState,
                        isTv = true,
                        activeSection = activeSection,
                        onSectionChange = { activeSection = it },
                        onSetAspectRatio = onSetAspectRatio,
                        onSetSpeed = onSetSpeed,
                        onSetQualityMode = onSetQualityMode,
                        onSelectVideoTrack = onSelectVideoTrack,
                        onSelectAudio = onSelectAudio,
                        onSelectSubtitle = onSelectSubtitle,
                        onDisableSubtitles = onDisableSubtitles
                    )
                }
            }
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        PlayerSettingsContent(
            playerState = playerState,
            isTv = false,
            activeSection = activeSection,
            onSectionChange = { activeSection = it },
            onSetAspectRatio = onSetAspectRatio,
            onSetSpeed = onSetSpeed,
            onSetQualityMode = onSetQualityMode,
            onSelectVideoTrack = onSelectVideoTrack,
            onSelectAudio = onSelectAudio,
            onSelectSubtitle = onSelectSubtitle,
            onDisableSubtitles = onDisableSubtitles
        )
    }
}

@Composable
private fun PlayerSettingsContent(
    playerState: PlayerState,
    isTv: Boolean,
    activeSection: String?,
    onSectionChange: (String?) -> Unit,
    onSetAspectRatio: (AspectRatioMode) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetQualityMode: (VideoQualityMode) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitles: () -> Unit
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
                            isTv = isTv,
                            onClick = { onSectionChange("aspect") }
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
                            isTv = isTv,
                            onClick = { onSectionChange("quality") }
                        )
                    }

                    // Playback Speed
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.setting_playback_speed),
                            subtitle = "${playerState.playbackSpeed}x",
                            isTv = isTv,
                            onClick = { onSectionChange("speed") }
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
                                isTv = isTv,
                                onClick = { onSectionChange("audio") }
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
                            isTv = isTv,
                            onClick = { onSectionChange("subtitle") }
                        )
                    }

                    // Stream Info
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.setting_stream_info),
                            subtitle = if (playerState.currentVideoResolution.isNotBlank()) playerState.currentVideoResolution else "—",
                            isTv = isTv,
                            onClick = { onSectionChange("info") }
                        )
                    }
                }
            } else {
                // Section back button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { onSectionChange(null) },
                        modifier = Modifier
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .focusable()
                            .background(if(isBackFocused) Color.White.copy(alpha=0.1f) else Color.Transparent, CircleShape)
                    ) {
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
                                    isTv = isTv,
                                    onClick = { onSetAspectRatio(mode); onSectionChange(null) }
                                )
                            }
                        }
                        "quality" -> {
                            // Quality modes
                            items(VideoQualityMode.entries.toList()) { mode ->
                                SettingsOptionItem(
                                    label = mode.label,
                                    isSelected = playerState.videoQualityMode == mode,
                                    isTv = isTv,
                                    onClick = { onSetQualityMode(mode); onSectionChange(null) }
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
                                        isTv = isTv,
                                        onClick = { onSelectVideoTrack(q.index); onSectionChange(null) }
                                    )
                                }
                            }
                        }
                        "speed" -> {
                            items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                                SettingsOptionItem(
                                    label = "${speed}x",
                                    isSelected = playerState.playbackSpeed == speed,
                                    isTv = isTv,
                                    onClick = { onSetSpeed(speed); onSectionChange(null) }
                                )
                            }
                        }
                        "audio" -> {
                            items(playerState.audioTracks) { track ->
                                SettingsOptionItem(
                                    label = track.name,
                                    subtitle = track.language.ifEmpty { null },
                                    isSelected = track.isSelected,
                                    isTv = isTv,
                                    onClick = { onSelectAudio(track.index); onSectionChange(null) }
                                )
                            }
                        }
                        "subtitle" -> {
                            item {
                                SettingsOptionItem(
                                    label = "Off",
                                    isSelected = playerState.selectedSubtitleTrack == -1,
                                    isTv = isTv,
                                    onClick = { onDisableSubtitles(); onSectionChange(null) }
                                )
                            }
                            items(playerState.subtitleTracks) { track ->
                                SettingsOptionItem(
                                    label = track.name,
                                    subtitle = track.language.ifEmpty { null },
                                    isSelected = track.isSelected,
                                    isTv = isTv,
                                    onClick = { onSelectSubtitle(track.index); onSectionChange(null) }
                                )
                            }
                        }
                        "info" -> {
                            item {
                                Column(modifier = Modifier.padding(20.dp)) {
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

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isTv: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (isFocused) Color.White else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (isFocused) Color.Black else ImaxColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (isFocused) Color.Black else ImaxColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (isFocused) Color.DarkGray else ImaxColors.TextTertiary)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = if (isFocused) Color.Black else ImaxColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsOptionItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isTv: Boolean = false,
    subtitle: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .background(if (isFocused) Color.White else if (isSelected) ImaxColors.Primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) Color.Black else if (isSelected) ImaxColors.Primary else ImaxColors.TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (isFocused) Color.DarkGray else ImaxColors.TextTertiary)
            }
        }
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = if (isFocused) Color.Black else ImaxColors.Primary, modifier = Modifier.size(20.dp))
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSwitchSheet(
    channels: List<Channel>,
    currentChannelId: Long,
    isSwitching: Boolean,
    isTv: Boolean,
    onDismiss: () -> Unit,
    onChannelSelected: (Channel) -> Unit
) {
    if (isTv) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(340.dp).padding(vertical = 16.dp).padding(end = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = ImaxColors.Surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.channel_list),
                            style = MaterialTheme.typography.headlineSmall,
                            color = ImaxColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(channels, key = { it.id }) { channel ->
                                val isCurrent = channel.id == currentChannelId
                                var isFocused by remember { mutableStateOf(false) }
                                
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isFocused) Color.White else if (isCurrent) ImaxColors.Primary.copy(alpha = 0.14f) else ImaxColors.SurfaceVariant,
                                    border = BorderStroke(
                                        width = if (isFocused) 0.dp else if (isCurrent) 1.dp else 0.dp,
                                        color = if (isCurrent && !isFocused) ImaxColors.Primary else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable(enabled = !isCurrent && !isSwitching)
                                        .clickable(enabled = !isCurrent && !isSwitching) { onChannelSelected(channel) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isFocused) Color.Black else ImaxColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (channel.groupTitle.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = channel.groupTitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isFocused) Color.DarkGray else ImaxColors.TextTertiary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isCurrent) {
                                            Text(
                                                text = stringResource(R.string.now_playing),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isFocused) Color.Black else ImaxColors.Primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isFocused) Color.Black else ImaxColors.Primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

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
