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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.imax.player.core.common.StringUtils
import com.imax.player.core.datastore.AppSettings
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.ContentType
import com.imax.player.core.player.*
import com.imax.player.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository
) : ViewModel() {
    val state: StateFlow<PlayerState> = playerManager.state
    val switchState: StateFlow<EngineSwitchState> = playerManager.switchState
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private var currentUrl = ""
    private var currentContentId = 0L
    private var currentContentType = ""

    fun init(url: String, startPos: Long, contentId: Long, contentType: String) {
        currentUrl = url
        currentContentId = contentId
        currentContentType = contentType
        viewModelScope.launch {
            playerManager.initializeWithSettings()
            playerManager.play(url, startPos)
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
        viewModelScope.launch {
            val s = state.value
            if (currentContentId > 0 && s.currentPosition > 0) {
                when (currentContentType) {
                    "MOVIE" -> contentRepository.updateMovieProgress(currentContentId, s.currentPosition, s.duration)
                    "SERIES" -> contentRepository.updateEpisodeProgress(currentContentId, s.currentPosition, s.duration)
                }
            }
        }
    }

    /**
     * Optimized exit: save progress async, navigate back immediately,
     * then release player on IO dispatcher to avoid main-thread jank.
     */
    fun exitPlayer(onBack: () -> Unit) {
        // Save progress on IO — don't block UI
        viewModelScope.launch(Dispatchers.IO) {
            val s = state.value
            if (currentContentId > 0 && s.currentPosition > 0) {
                when (currentContentType) {
                    "MOVIE" -> contentRepository.updateMovieProgress(currentContentId, s.currentPosition, s.duration)
                    "SERIES" -> contentRepository.updateEpisodeProgress(currentContentId, s.currentPosition, s.duration)
                }
            }
        }
        // Navigate back instantly on main thread
        onBack()
        // Release player 
        playerManager.release()
    }

    override fun onCleared() {
        super.onCleared()
        saveProgress()
        playerManager.release()
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
    isTv: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.state.collectAsStateWithLifecycle()
    val switchState by viewModel.switchState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }

    // Get activity for window flags
    val context = LocalContext.current
    val activity = context as? Activity

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // KEEP SCREEN ON — prevents screen from turning off during playback
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    DisposableEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
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
    DisposableEffect(Unit) {
        insetsController?.let { controller ->
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // Restore system bars when leaving player
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Sync system bars with controls visibility
    LaunchedEffect(controlsVisible) {
        insetsController?.let { controller ->
            if (controlsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Initialize player
    LaunchedEffect(url) {
        viewModel.init(url, startPosition, contentId, contentType)
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
        viewModel.exitPlayer(onBack)
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
        val engine = viewModel.playerManager.getEngine()
        if (switchState == EngineSwitchState.SWITCHING) {
            Spacer(modifier = Modifier.fillMaxSize())
        } else {
            key(engine.engineName) {
                when (engine) {
                is ExoPlayerEngine -> {
                    val exoPlayer = engine.getExoPlayer()
                    val currentAspectMode = playerState.aspectRatioMode
                    if (exoPlayer != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    keepScreenOn = true
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
                                // Apply resizeMode on every recomposition when aspect mode changes
                                val resizeMode = when (currentAspectMode) {
                                    AspectRatioMode.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    AspectRatioMode.FORCE_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                    AspectRatioMode.FORCE_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                }
                                pv.resizeMode = resizeMode
                            },
                            modifier = Modifier.fillMaxSize()
                        )
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
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Ensure surface is on top (VLC needs this for proper z-order)
                                setZOrderMediaOverlay(false)
                            }.also { surfaceView ->
                                // Attach surface to VLC — this triggers pending playback if queued
                                engine.setSurface(surfaceView)
                            }
                        },
                        update = { surfaceView ->
                            // Update surface size on recomposition/layout change
                            if (surfaceView.width > 0 && surfaceView.height > 0) {
                                engine.updateSurfaceSize(surfaceView.width, surfaceView.height)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
        if (playerState.playbackState == PlaybackState.ERROR && switchState != EngineSwitchState.SWITCHING) {
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
                        onClick = { viewModel.init(url, playerState.currentPosition, contentId, contentType) },
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
                    Text("Switched to ${viewModel.playerManager.engineName}",
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

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible && switchState != EngineSwitchState.SWITCHING,
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
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    // Engine badge
                    Surface(shape = RoundedCornerShape(4.dp), color = ImaxColors.SurfaceVariant.copy(alpha = 0.6f)) {
                        Text(viewModel.playerManager.engineName,
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
                    PlayerControlButton(icon = Icons.Filled.Replay10, contentDescription = "Rewind",
                        size = if (isTv) 48.dp else 40.dp,
                        onClick = { viewModel.seekBackward() })

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

                    PlayerControlButton(icon = Icons.Filled.Forward10, contentDescription = "Forward",
                        size = if (isTv) 48.dp else 40.dp,
                        onClick = { viewModel.seekForward() })
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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SETTINGS BOTTOM SHEET
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    if (showSettingsSheet) {
        PlayerSettingsSheet(
            playerState = playerState,
            engineName = viewModel.playerManager.engineName,
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
