package com.imax.player.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.Playlist
import com.imax.player.core.model.PlaylistType
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.components.GradientButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            playlistRepository.getAllPlaylists().collect { playlists ->
                _state.update { it.copy(playlists = playlists, isLoading = false) }
            }
        }
    }

    fun showAddDialog() = _state.update { it.copy(showAddDialog = true) }
    fun hideAddDialog() = _state.update { it.copy(showAddDialog = false) }

    fun addPlaylist(name: String, type: PlaylistType, url: String, server: String, username: String, password: String) {
        viewModelScope.launch {
            val playlist = Playlist(
                name = name,
                type = type,
                url = url,
                serverUrl = server,
                username = username,
                password = password
            )
            val id = playlistRepository.savePlaylist(playlist)
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun selectPlaylist(playlist: Playlist, onSelected: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncMessage = "Syncing playlist...") }
            playlistRepository.activatePlaylist(playlist.id)
            val result = playlistRepository.syncPlaylist(playlist)
            _state.update { it.copy(isSyncing = false, syncMessage = "") }
            onSelected()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlist) }
    }

    fun testConnection(playlist: Playlist, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = playlistRepository.testConnection(playlist)
            onResult(result.fold({ it }, { it.message ?: "Failed" }))
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
    val dimens = LocalImaxDimens.current

    Box(modifier = Modifier.fillMaxSize().background(ImaxColors.Background)) {
        // Gradient background
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(ImaxColors.Primary.copy(alpha = 0.05f), ImaxColors.Background, ImaxColors.Secondary.copy(alpha = 0.03f))
                )
            )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(dimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(if (isTv) 40.dp else 60.dp))

            // App logo/title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("iMAX", style = MaterialTheme.typography.displayLarge, color = ImaxColors.Primary)
                Text(" Player", style = MaterialTheme.typography.displayLarge, color = ImaxColors.Secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Your premium IPTV experience", style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextTertiary)

            Spacer(modifier = Modifier.height(32.dp))

            if (state.isSyncing) {
                CircularProgressIndicator(color = ImaxColors.Primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(state.syncMessage, style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextSecondary)
            } else if (state.playlists.isEmpty() && !state.isLoading) {
                // Empty state
                Spacer(modifier = Modifier.height(40.dp))
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null,
                    tint = ImaxColors.TextTertiary, modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No playlists yet", style = MaterialTheme.typography.headlineSmall, color = ImaxColors.TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Add a playlist to get started", style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextSecondary)
                Spacer(modifier = Modifier.height(24.dp))
                GradientButton(text = "Add Playlist", icon = Icons.Filled.Add,
                    onClick = { viewModel.showAddDialog() })
            } else {
                // Playlist list
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Playlists", style = MaterialTheme.typography.headlineSmall, color = ImaxColors.TextPrimary)
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "Add", tint = ImaxColors.Primary, modifier = Modifier.size(32.dp))
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
                            isTv = isTv,
                            onSelect = { viewModel.selectPlaylist(playlist, onPlaylistSelected) },
                            onDelete = { viewModel.deletePlaylist(playlist) }
                        )
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddPlaylistDialog(
                isTv = isTv,
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { name, type, url, server, user, pass ->
                    viewModel.addPlaylist(name, type, url, server, user, pass)
                }
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    isTv: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) ImaxColors.SurfaceVariant else ImaxColors.CardBackground)
            .then(if (isFocused) Modifier.border(1.dp, ImaxColors.FocusBorder, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onSelect)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (playlist.type) {
                PlaylistType.M3U_URL -> Icons.Filled.Link
                PlaylistType.M3U_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
                PlaylistType.XTREAM_CODES -> Icons.Filled.Dns
            },
            contentDescription = null,
            tint = ImaxColors.Primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = ImaxColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlist.channelCount > 0) Text("${playlist.channelCount} channels", style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
                if (playlist.movieCount > 0) Text("${playlist.movieCount} movies", style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
                if (playlist.seriesCount > 0) Text("${playlist.seriesCount} series", style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
            }
        }
        if (playlist.isActive) {
            Surface(shape = RoundedCornerShape(4.dp), color = ImaxColors.Success.copy(alpha = 0.15f)) {
                Text("Active", style = MaterialTheme.typography.labelSmall, color = ImaxColors.Success,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = ImaxColors.Error.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AddPlaylistDialog(
    isTv: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(PlaylistType.M3U_URL) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ImaxColors.Surface,
        titleContentColor = ImaxColors.TextPrimary,
        textContentColor = ImaxColors.TextSecondary,
        title = { Text("Add Playlist", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type selection
                Text("Playlist Type", style = MaterialTheme.typography.labelLarge, color = ImaxColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaylistType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(when (type) {
                                PlaylistType.M3U_URL -> "M3U URL"
                                PlaylistType.M3U_FILE -> "File"
                                PlaylistType.XTREAM_CODES -> "Xtream"
                            }, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f),
                                selectedLabelColor = ImaxColors.Primary
                            )
                        )
                    }
                }

                DialogTextField(value = name, onValueChange = { name = it }, label = "Playlist Name", imeAction = ImeAction.Next)

                when (selectedType) {
                    PlaylistType.M3U_URL -> {
                        DialogTextField(value = url, onValueChange = { url = it }, label = "M3U URL",
                            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                    }
                    PlaylistType.M3U_FILE -> {
                        DialogTextField(value = url, onValueChange = { url = it }, label = "File Path",
                            imeAction = ImeAction.Done)
                    }
                    PlaylistType.XTREAM_CODES -> {
                        DialogTextField(value = server, onValueChange = { server = it }, label = "Server URL",
                            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                        DialogTextField(value = username, onValueChange = { username = it }, label = "Username",
                            imeAction = ImeAction.Next)
                        DialogTextField(value = password, onValueChange = { password = it }, label = "Password",
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                text = "Add",
                onClick = {
                    if (name.isNotBlank()) {
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
