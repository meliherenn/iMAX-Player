package com.imax.player.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.R
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.Playlist
import com.imax.player.core.model.PlaylistType
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.components.GradientButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TV_ONBOARDING_LOG_TAG = "TvOnboarding"

data class OnboardingState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val isSyncing: Boolean = false,
    val syncMessage: String = ""
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                Timber.d("Starting playlist collection")
                playlistRepository.getAllPlaylists().collect { playlists ->
                    Timber.d("Collected playlists: count=%d", playlists.size)
                    _state.update { it.copy(playlists = playlists, isLoading = false) }
                }
            } catch(e: Exception) {
                Timber.e(e, "Error collecting playlists")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun showAddDialog() = _state.update { it.copy(showAddDialog = true) }
    fun hideAddDialog() = _state.update { it.copy(showAddDialog = false) }

    fun addPlaylist(
        name: String,
        type: PlaylistType,
        url: String,
        server: String,
        username: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        Timber.d("Adding playlist")
        viewModelScope.launch {
            try {
                val playlist = Playlist(
                    name = name,
                    type = type,
                    url = if (type == PlaylistType.M3U_URL) url else "",
                    filePath = if (type == PlaylistType.M3U_FILE) url else "",
                    serverUrl = server,
                    username = username,
                    password = password
                )
                val id = playlistRepository.savePlaylist(playlist)
                val savedPlaylist = playlist.copy(id = id)
                _state.update { it.copy(showAddDialog = false) }
                selectPlaylist(savedPlaylist, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add playlist")
            }
        }
    }

    fun selectPlaylist(playlist: Playlist, onSelected: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncMessage = "Syncing playlist...") }
            try {
                playlistRepository.activatePlaylist(playlist.id)
                playlistRepository.syncPlaylist(playlist)
                _state.update { it.copy(isSyncing = false, syncMessage = "") }
                onSelected()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync selected playlist")
                _state.update { it.copy(isSyncing = false, syncMessage = "") }
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    fun testDraftConnection(
        name: String,
        type: PlaylistType,
        url: String,
        server: String,
        username: String,
        password: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val draft = Playlist(
                name = name.ifBlank { "Test Playlist" },
                type = type,
                url = url,
                serverUrl = server,
                username = username,
                password = password
            )
            val result = playlistRepository.testConnection(draft)
            onResult(result.fold({ it }, { it.message ?: "Connection failed" }))
        }
    }
}

@Composable
fun OnboardingScreen(
    isTv: Boolean,
    onPlaylistSelected: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (isTv) {
        TvOnboardingContent(
            state = state,
            viewModel = viewModel,
            onPlaylistSelected = onPlaylistSelected
        )
    } else {
        MobileOnboardingContent(
            state = state,
            viewModel = viewModel,
            onPlaylistSelected = onPlaylistSelected
        )
    }
}

@Composable
private fun MobileOnboardingContent(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onPlaylistSelected: () -> Unit
) {
    val dimens = LocalImaxDimens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImaxColors.Background)
    ) {
        SharedOnboardingBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("iMAX", style = MaterialTheme.typography.displayLarge, color = ImaxColors.Primary)
                Text(" Player", style = MaterialTheme.typography.displayLarge, color = ImaxColors.Secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Your premium IPTV experience",
                style = MaterialTheme.typography.bodyMedium,
                color = ImaxColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (state.isSyncing) {
                CircularProgressIndicator(color = ImaxColors.Primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    state.syncMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImaxColors.TextSecondary
                )
            } else if (state.playlists.isEmpty() && !state.isLoading) {
                Spacer(modifier = Modifier.height(40.dp))
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = ImaxColors.TextTertiary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No playlists yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ImaxColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add a playlist to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImaxColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                GradientButton(
                    text = "Add Playlist",
                    icon = Icons.Filled.Add,
                    onClick = { viewModel.showAddDialog() }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your Playlists",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ImaxColors.TextPrimary
                    )
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = "Add",
                            tint = ImaxColors.Primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.playlists) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            isTv = false,
                            onSelect = { viewModel.selectPlaylist(playlist, onPlaylistSelected) },
                            onDelete = { viewModel.deletePlaylist(playlist) }
                        )
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddPlaylistDialog(
                isTv = false,
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { name, type, url, server, user, pass ->
                    viewModel.addPlaylist(name, type, url, server, user, pass, onPlaylistSelected)
                },
                onTest = { _, _, _, _, _, _, _ -> }
            )
        }
    }
}

@Composable
private fun TvOnboardingContent(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onPlaylistSelected: () -> Unit
) {
    val dimens = LocalImaxDimens.current
    val addPlaylistFocusRequester = remember { FocusRequester() }
    val logoPainter = painterResource(id = R.mipmap.ic_launcher_foreground)

    LaunchedEffect(state.showAddDialog, state.isSyncing) {
        if (!state.showAddDialog && !state.isSyncing) {
            addPlaylistFocusRequester.requestFocusSafely("TV onboarding add playlist card")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImaxColors.Background)
    ) {
        SharedOnboardingBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 56.dp)
        ) {
            Image(
                painter = logoPainter,
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(150.dp)
                    .height(150.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Set up your TV playlists",
                style = MaterialTheme.typography.displayMedium,
                color = ImaxColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Choose a provider type, add a playlist and continue with the remote. Focus is always highlighted and the form only shows fields you need.",
                style = MaterialTheme.typography.titleMedium,
                color = ImaxColors.TextSecondary,
                modifier = Modifier.widthIn(max = 920.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))

            TvFocusableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(addPlaylistFocusRequester),
                onClick = { viewModel.showAddDialog() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(ImaxColors.Primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = ImaxColors.Primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Add a new playlist",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ImaxColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "M3U URL, Xtream / Portal or local file. Press center to open the TV-friendly setup flow.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ImaxColors.TextSecondary
                        )
                    }
                    TvInlineHint("Press OK")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (state.playlists.isEmpty() && !state.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = ImaxColors.Surface
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "No saved playlists yet",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ImaxColors.TextPrimary
                        )
                        Text(
                            "Start with one provider type, fill in only the required fields, test the connection and save. The setup dialog opens with remote-first focus by default.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ImaxColors.TextSecondary
                        )
                    }
                }
            } else {
                Text(
                    "Saved playlists",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ImaxColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Select a playlist to sync and continue, or remove an old entry.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ImaxColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        TvPlaylistRow(
                            playlist = playlist,
                            onSelect = { viewModel.selectPlaylist(playlist, onPlaylistSelected) },
                            onDelete = { viewModel.deletePlaylist(playlist) }
                        )
                    }
                }
            }
        }

        if (state.isSyncing) {
            TvSyncingOverlay(message = state.syncMessage)
        }

        if (state.showAddDialog) {
            AddPlaylistDialog(
                isTv = true,
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { name, type, url, server, user, pass ->
                    viewModel.addPlaylist(name, type, url, server, user, pass, onPlaylistSelected)
                },
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                }
            )
        }
    }
}

@Composable
private fun SharedOnboardingBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ImaxColors.Primary.copy(alpha = 0.05f),
                        ImaxColors.Background,
                        ImaxColors.Secondary.copy(alpha = 0.03f)
                    )
                )
            )
    )
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    isTv: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    if (isTv) {
        TvPlaylistRow(
            playlist = playlist,
            onSelect = onSelect,
            onDelete = onDelete
        )
        return
    }

    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) ImaxColors.SurfaceVariant else ImaxColors.CardBackground)
            .then(
                if (isFocused) {
                    Modifier.border(1.dp, ImaxColors.FocusBorder, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onSelect)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = playlistTypeIcon(playlist.type),
            contentDescription = null,
            tint = ImaxColors.Primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = ImaxColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlist.channelCount > 0) {
                    Text(
                        "${playlist.channelCount} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = ImaxColors.TextTertiary
                    )
                }
                if (playlist.movieCount > 0) {
                    Text(
                        "${playlist.movieCount} movies",
                        style = MaterialTheme.typography.bodySmall,
                        color = ImaxColors.TextTertiary
                    )
                }
                if (playlist.seriesCount > 0) {
                    Text(
                        "${playlist.seriesCount} series",
                        style = MaterialTheme.typography.bodySmall,
                        color = ImaxColors.TextTertiary
                    )
                }
            }
        }
        if (playlist.isActive) {
            Surface(shape = RoundedCornerShape(4.dp), color = ImaxColors.Success.copy(alpha = 0.15f)) {
                Text(
                    "Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = ImaxColors.Success,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = ImaxColors.Error.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun TvPlaylistRow(
    playlist: Playlist,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvFocusableCard(
            modifier = Modifier.weight(1f),
            onClick = onSelect
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(ImaxColors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = playlistTypeIcon(playlist.type),
                        contentDescription = null,
                        tint = ImaxColors.Primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = ImaxColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playlist.isActive) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ImaxColors.Success.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = ImaxColors.Success,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ImaxColors.Success
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playlistTypeLabel(playlist.type),
                        style = MaterialTheme.typography.titleMedium,
                        color = ImaxColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlistStats(playlist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ImaxColors.TextTertiary
                    )
                }
                TvInlineHint("Continue")
            }
        }

        TvActionButton(
            text = "Remove",
            onClick = onDelete,
            isDestructive = true,
            modifier = Modifier.width(170.dp)
        )
    }
}

@Composable
private fun AddPlaylistDialog(
    isTv: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    if (isTv) {
        TvAddPlaylistDialog(
            onDismiss = onDismiss,
            onAdd = onAdd,
            onTest = onTest
        )
    } else {
        MobileAddPlaylistDialog(
            onDismiss = onDismiss,
            onAdd = onAdd
        )
    }
}

@Composable
private fun MobileAddPlaylistDialog(
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(PlaylistType.M3U_URL) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canSave = remember(selectedType, name, url, server, username, password) {
        isDraftValid(
            name = name,
            type = selectedType,
            url = url,
            server = server,
            username = username,
            password = password
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ImaxColors.Surface,
        titleContentColor = ImaxColors.TextPrimary,
        textContentColor = ImaxColors.TextSecondary,
        title = { Text("Add Playlist", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Playlist Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = ImaxColors.TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaylistType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = {
                                Text(
                                    playlistTypeLabel(type),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f),
                                selectedLabelColor = ImaxColors.Primary
                            )
                        )
                    }
                }

                DialogTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Playlist Name",
                    imeAction = ImeAction.Next
                )

                when (selectedType) {
                    PlaylistType.M3U_URL -> {
                        DialogTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = "M3U URL",
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        )
                    }

                    PlaylistType.M3U_FILE -> {
                        DialogTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = "File Path",
                            imeAction = ImeAction.Done
                        )
                    }

                    PlaylistType.XTREAM_CODES -> {
                        DialogTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = "Server URL",
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        )
                        DialogTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = "Username",
                            imeAction = ImeAction.Next
                        )
                        DialogTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                text = "Add",
                enabled = canSave,
                onClick = {
                    if (canSave) {
                        onAdd(name, selectedType, url, server, username, password)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ImaxColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun TvAddPlaylistDialog(
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    var selectedType by remember { mutableStateOf(PlaylistType.M3U_URL) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var shouldMoveFocusToForm by remember { mutableStateOf(false) }

    val typeFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }

    val canSave = remember(selectedType, name, url, server, username, password) {
        isDraftValid(
            name = name,
            type = selectedType,
            url = url,
            server = server,
            username = username,
            password = password
        )
    }

    LaunchedEffect(Unit) {
        typeFocusRequester.requestFocusSafely("TV add playlist dialog provider type")
    }

    LaunchedEffect(selectedType, shouldMoveFocusToForm) {
        if (shouldMoveFocusToForm) {
            nameFocusRequester.requestFocusSafely("TV add playlist dialog form")
            shouldMoveFocusToForm = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 1180.dp),
            shape = RoundedCornerShape(32.dp),
            color = ImaxColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Add playlist on TV",
                            style = MaterialTheme.typography.displaySmall,
                            color = ImaxColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Step 1: choose a provider type. Step 2: fill only the required fields. Step 3: test and save.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ImaxColors.TextSecondary
                        )
                    }
                    TvInlineHint("Dialog focus locked")
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Provider type",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ImaxColors.TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PlaylistType.entries.forEachIndexed { index, type ->
                            TvTypeCard(
                                modifier = if (index == 0) {
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(typeFocusRequester)
                                } else {
                                    Modifier.weight(1f)
                                },
                                type = type,
                                isSelected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    shouldMoveFocusToForm = true
                                    testMessage = null
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Playlist details",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ImaxColors.TextPrimary
                    )
                    TvDialogTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            testMessage = null
                        },
                        label = "Playlist name",
                        placeholder = "Living room IPTV",
                        focusRequester = nameFocusRequester
                    )

                    when (selectedType) {
                        PlaylistType.M3U_URL -> {
                            TvDialogTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    testMessage = null
                                },
                                label = "M3U URL",
                                placeholder = "https://provider.example.com/playlist.m3u",
                                keyboardType = KeyboardType.Uri
                            )
                        }

                        PlaylistType.M3U_FILE -> {
                            TvDialogTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    testMessage = null
                                },
                                label = "Local file path",
                                placeholder = "/storage/emulated/0/Download/playlist.m3u"
                            )
                        }

                        PlaylistType.XTREAM_CODES -> {
                            TvDialogTextField(
                                value = server,
                                onValueChange = {
                                    server = it
                                    testMessage = null
                                },
                                label = "Server URL",
                                placeholder = "https://portal.example.com",
                                keyboardType = KeyboardType.Uri
                            )
                            TvDialogTextField(
                                value = username,
                                onValueChange = {
                                    username = it
                                    testMessage = null
                                },
                                label = "Username",
                                placeholder = "Enter provider username"
                            )
                            TvDialogTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    testMessage = null
                                },
                                label = "Password",
                                placeholder = "Enter provider password",
                                keyboardType = KeyboardType.Password
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ImaxColors.Background.copy(alpha = 0.42f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = providerHelperTitle(selectedType),
                            style = MaterialTheme.typography.titleLarge,
                            color = ImaxColors.TextPrimary
                        )
                        Text(
                            text = providerHelperText(selectedType),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImaxColors.TextSecondary
                        )
                    }
                }

                if (testMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isPositiveTestMessage(testMessage)) {
                            ImaxColors.Success.copy(alpha = 0.12f)
                        } else {
                            ImaxColors.Error.copy(alpha = 0.12f)
                        }
                    ) {
                        Text(
                            text = testMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isPositiveTestMessage(testMessage)) {
                                ImaxColors.Success
                            } else {
                                ImaxColors.Error
                            },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TvActionButton(
                        text = if (isTesting) "Testing..." else "Test Connection",
                        onClick = {
                            isTesting = true
                            onTest(name, selectedType, url, server, username, password) { result ->
                                testMessage = result
                                isTesting = false
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    )
                    TvActionButton(
                        text = "Save and Continue",
                        onClick = {
                            onAdd(name, selectedType, url, server, username, password)
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    )
                    TvActionButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        isSecondary = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ImaxColors.Primary,
            unfocusedBorderColor = ImaxColors.CardBorder,
            focusedLabelColor = ImaxColors.Primary,
            cursorColor = ImaxColors.Primary,
            focusedTextColor = ImaxColors.TextPrimary,
            unfocusedTextColor = ImaxColors.TextPrimary
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction)
    )
}

@Composable
private fun TvDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(stiffness = 320f),
        label = "tvDialogFieldScale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isFocused) androidx.compose.ui.text.font.FontWeight.Bold else null,
            color = if (isFocused) ImaxColors.Primary else ImaxColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) Color.DarkGray else ImaxColors.TextTertiary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .shadow(
                    elevation = if (isFocused) 16.dp else 0.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.98f),
                unfocusedContainerColor = ImaxColors.SurfaceVariant.copy(alpha = 0.5f),
                cursorColor = ImaxColors.Primary,
                focusedTextColor = Color.Black,
                unfocusedTextColor = ImaxColors.TextPrimary
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            // EXPLICIT override so typography doesn't force white text
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = if (isFocused) Color.Black else ImaxColors.TextPrimary
            )
        )
    }
}

@Composable
private fun TvTypeCard(
    modifier: Modifier = Modifier,
    type: PlaylistType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    val icon = playlistTypeIcon(type)
    val title = playlistTypeLabel(type)
    val subtitle = when (type) {
        PlaylistType.M3U_URL -> "One playlist URL"
        PlaylistType.XTREAM_CODES -> "Server, username, password"
        PlaylistType.M3U_FILE -> "Local file path"
    }

    val isFocusedAndSelected = isFocused && isSelected
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)
    val borderWidth by animateDpAsState(if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp)
    
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color.White
            isFocused -> Color.White.copy(alpha = 0.96f)
            isSelected -> ImaxColors.Primary.copy(alpha = 0.15f)
            else -> ImaxColors.SurfaceVariant.copy(alpha = 0.4f)
        }
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> ImaxColors.Primary.copy(alpha = 0.5f)
            else -> Color.Transparent
        }
    )
    val contentColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else ImaxColors.TextPrimary
    )
    val secondaryContentColor by animateColorAsState(
        targetValue = if (isFocused) Color.DarkGray else ImaxColors.TextSecondary
    )
    val iconBgColor by animateColorAsState(
        targetValue = when {
            isFocused -> ImaxColors.Primary.copy(alpha = 0.12f)
            isSelected -> ImaxColors.Primary.copy(alpha = 0.25f)
            else -> ImaxColors.Surface
        }
    )
    val iconColor by animateColorAsState(
        targetValue = if (isFocused) ImaxColors.Primary else if (isSelected) ImaxColors.Primary else ImaxColors.TextSecondary
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(24.dp)
                clip = true
            }
            .shadow(
                elevation = if (isFocused) 16.dp else 0.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(24.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    fontWeight = if (isFocused || isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor
                )
            }
        }
    }
}

@Composable
private fun TvFocusableCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isFocusedAndSelected = isFocused && isSelected
    val scale by animateFloatAsState(
        targetValue = when {
            isFocusedAndSelected -> 1.055f
            isFocused -> 1.045f
            isSelected -> 1.012f
            else -> 1f
        },
        animationSpec = spring(stiffness = 320f),
        label = "tvFocusScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 4.dp
            isFocused -> 3.5.dp
            isSelected -> 2.dp
            else -> 1.dp
        },
        animationSpec = spring(stiffness = 320f),
        label = "tvFocusableCardBorderWidth"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFF3B2730)
            isFocused -> Color(0xFF241D28)
            isSelected -> ImaxColors.Surface.copy(alpha = 0.96f)
            else -> ImaxColors.Surface
        },
        label = "tvFocusableCardBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFFFFD7DF)
            isFocused -> Color(0xFFFFC2CF)
            isSelected -> ImaxColors.Primary.copy(alpha = 0.6f)
            else -> ImaxColors.CardBorder
        },
        label = "tvFocusableCardBorderColor"
    )
    val focusGlowColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0x88FF9DB2)
            isFocused -> Color(0x70FF87A1)
            else -> Color.Transparent
        },
        label = "tvFocusableCardGlowColor"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 12.dp
            isFocused -> 10.dp
            isSelected -> 6.dp
            else -> 0.dp
        },
        animationSpec = spring(stiffness = 320f),
        label = "tvFocusableCardIndicatorWidth"
    )
    val trailingFocusPillColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0x66FFD7DF)
            isFocused -> Color(0x4CFFB6C7)
            isSelected -> Color(0x20FF9DB2)
            else -> Color.Transparent
        },
        label = "tvFocusableCardTrailingPill"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = if (isFocused) 26.dp else 0.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = if (isFocused) focusGlowColor else ImaxColors.FocusGlow,
                spotColor = if (isFocused) focusGlowColor else ImaxColors.FocusGlow
            )
            .clip(RoundedCornerShape(28.dp))
            .background(backgroundColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (indicatorWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(indicatorWidth)
                        .height(68.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            when {
                                isFocusedAndSelected -> Color(0xFFFFDCE4)
                                isFocused -> Color(0xFFFFC9D5)
                                else -> Color(0xFFE1A5B6)
                            }
                        )
                )
            }
            if (trailingFocusPillColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(12.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(trailingFocusPillColor)
                )
            }
        }
        content()
    }
}

@Composable
private fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    isDestructive: Boolean = false
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(stiffness = 320f),
        label = "tvButtonScale"
    )

    val backgroundColor = when {
        !enabled -> ImaxColors.SurfaceVariant.copy(alpha = 0.5f)
        isDestructive -> ImaxColors.Error.copy(alpha = if (isFocused) 0.6f else 0.18f)
        isSecondary -> ImaxColors.SurfaceVariant
        else -> if (isFocused) Color.White else ImaxColors.SurfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = when {
        !enabled -> ImaxColors.CardBorder
        isDestructive && isFocused -> ImaxColors.Error
        isFocused -> Color.White
        isSecondary -> ImaxColors.CardBorder
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> ImaxColors.TextTertiary
        isFocused && !isDestructive -> Color.Black
        isFocused && isDestructive -> Color.White
        else -> ImaxColors.TextPrimary
    }

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = if (isFocused && enabled) 16.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .border(if (isFocused) 0.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isFocused) androidx.compose.ui.text.font.FontWeight.Bold else null,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvInlineHint(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ImaxColors.Secondary.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = ImaxColors.Secondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TvSyncingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ImaxColors.Surface
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = ImaxColors.Primary)
                Text(
                    text = "Preparing playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ImaxColors.TextPrimary
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ImaxColors.TextSecondary
                )
            }
        }
    }
}

private fun playlistTypeIcon(type: PlaylistType): ImageVector {
    return when (type) {
        PlaylistType.M3U_URL -> Icons.Filled.Link
        PlaylistType.M3U_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
        PlaylistType.XTREAM_CODES -> Icons.Filled.Dns
    }
}

private fun playlistTypeLabel(type: PlaylistType): String {
    return when (type) {
        PlaylistType.M3U_URL -> "M3U URL"
        PlaylistType.M3U_FILE -> "Local File"
        PlaylistType.XTREAM_CODES -> "Xtream / Portal"
    }
}

private fun playlistStats(playlist: Playlist): String {
    val stats = buildList {
        if (playlist.channelCount > 0) add("${playlist.channelCount} channels")
        if (playlist.movieCount > 0) add("${playlist.movieCount} movies")
        if (playlist.seriesCount > 0) add("${playlist.seriesCount} series")
    }
    return stats.ifEmpty { listOf("No synced content yet") }.joinToString("  •  ")
}

private fun providerHelperTitle(type: PlaylistType): String {
    return when (type) {
        PlaylistType.M3U_URL -> "Use one direct playlist link"
        PlaylistType.XTREAM_CODES -> "Use portal credentials"
        PlaylistType.M3U_FILE -> "Use a local playlist file"
    }
}

private fun providerHelperText(type: PlaylistType): String {
    return when (type) {
        PlaylistType.M3U_URL -> "Paste a direct M3U address. This is the fastest setup flow for TV remotes."
        PlaylistType.XTREAM_CODES -> "Enter server URL, username and password. Only the required fields are shown."
        PlaylistType.M3U_FILE -> "Use this only if the TV already has a local playlist file path you can access."
    }
}

private fun isDraftValid(
    name: String,
    type: PlaylistType,
    url: String,
    server: String,
    username: String,
    password: String
): Boolean {
    if (name.isBlank()) return false

    return when (type) {
        PlaylistType.M3U_URL,
        PlaylistType.M3U_FILE -> url.isNotBlank()

        PlaylistType.XTREAM_CODES -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }
}

private fun isPositiveTestMessage(message: String?): Boolean {
    if (message == null) return false
    val normalized = message.lowercase()
    return "successful" in normalized || "ready" in normalized || "active" in normalized
}

private fun FocusRequester.requestFocusSafely(reason: String) {
    runCatching { requestFocus() }
        .onFailure { error ->
            Timber.tag(TV_ONBOARDING_LOG_TAG).w(error, "Unable to request focus for %s", reason)
        }
}
