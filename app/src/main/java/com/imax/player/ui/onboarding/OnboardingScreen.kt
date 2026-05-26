package com.imax.player.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.common.Resource
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
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import androidx.compose.foundation.interaction.MutableInteractionSource
import coil.compose.AsyncImage

private const val TV_ONBOARDING_LOG_TAG = "TvOnboarding"

private const val SUPABASE_URL = "https://apkurmmvlpqsznybnxyq.supabase.co"
private const val SUPABASE_ANON_KEY = "sb_publishable_QU2LmMC06cBEpcabFqjTJg_vIrEFNUm"

@Serializable
data class SupabasePairingResponse(
    val status: String,
    val payload: JsonObject? = null
)

@Serializable
data class RemoteSetupPayload(
    val version: Int,
    val source: String,
    val pairingCode: String,
    val playlist: RemotePlaylistInfo
)

@Serializable
data class RemotePlaylistInfo(
    val name: String,
    val type: String,
    val epgUrl: String = "",
    val rememberOnStart: Boolean = true,
    val epgAutoSync: Boolean = true,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val fileContent: String = ""
)

data class OnboardingState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val isSyncing: Boolean = false,
    val syncMessage: String = "",
    val syncError: String? = null,
    val pairingCode: String? = null,
    val pairingStatus: String? = null,
    val pairingErrorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var pairingJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

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

    fun startQrPairing(onPlaylistSelected: () -> Unit) {
        pairingJob?.cancel()
        val code = (1..6).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
        _state.update {
            it.copy(
                pairingCode = code,
                pairingStatus = "pending",
                pairingErrorMessage = null
            )
        }

        pairingJob = viewModelScope.launch {
            val isInserted = insertPairingCode(code)
            if (!isInserted) {
                _state.update {
                    it.copy(
                        pairingStatus = "error",
                        pairingErrorMessage = "Sunucu bağlantısı kurulamadı."
                    )
                }
                return@launch
            }

            val startTime = System.currentTimeMillis()
            val timeout = 10 * 60 * 1000 // 10 minutes
            var isCompleted = false

            while (System.currentTimeMillis() - startTime < timeout) {
                delay(2000)
                val response = checkPairingStatus(code)
                if (response != null && response.status == "completed") {
                    isCompleted = true
                    if (response.payload != null) {
                        _state.update {
                            it.copy(
                                pairingStatus = "completed",
                                pairingCode = null
                            )
                        }
                        saveAndActivateRemotePlaylist(response.payload, onPlaylistSelected)
                    } else {
                        _state.update {
                            it.copy(
                                pairingStatus = "error",
                                pairingErrorMessage = "Sunucudan boş paket döndü."
                            )
                        }
                    }
                    break
                }
            }

            if (!isCompleted && _state.value.pairingStatus == "pending") {
                _state.update {
                    it.copy(
                        pairingStatus = "error",
                        pairingErrorMessage = "Eşleşme süresi doldu."
                    )
                }
            }
        }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _state.update {
            it.copy(
                pairingCode = null,
                pairingStatus = "idle",
                pairingErrorMessage = null
            )
        }
    }

    private suspend fun insertPairingCode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonBody = """
                {
                    "pairing_code": "$code",
                    "status": "pending",
                    "payload": {}
                }
            """.trimIndent()
            val body = jsonBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tv_pairings")
                .post(body)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert pairing code")
            false
        }
    }

    private suspend fun checkPairingStatus(code: String): SupabasePairingResponse? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tv_pairings?pairing_code=eq.$code&select=status,payload")
                .get()
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use null
                    val list = json.decodeFromString<List<SupabasePairingResponse>>(bodyString)
                    list.firstOrNull()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check pairing status")
            null
        }
    }

    private fun saveAndActivateRemotePlaylist(payloadJson: JsonObject, onPlaylistSelected: () -> Unit) {
        viewModelScope.launch {
            try {
                val payload = json.decodeFromJsonElement<RemoteSetupPayload>(payloadJson)
                val remotePlaylist = payload.playlist

                val type = when (remotePlaylist.type) {
                    "xtream" -> PlaylistType.XTREAM_CODES
                    "m3u" -> PlaylistType.M3U_URL
                    "file" -> PlaylistType.M3U_FILE
                    else -> PlaylistType.M3U_URL
                }

                var filePath = ""
                if (type == PlaylistType.M3U_FILE && remotePlaylist.fileContent.isNotEmpty()) {
                    val dir = File(context.filesDir, "playlists")
                    if (!dir.exists()) dir.mkdirs()
                    val fileName = remotePlaylist.fileName.ifBlank { "remote_${System.currentTimeMillis()}.m3u" }
                    val file = File(dir, fileName)
                    file.writeText(remotePlaylist.fileContent)
                    filePath = file.absolutePath
                }

                val playlist = Playlist(
                    name = remotePlaylist.name,
                    type = type,
                    url = if (type == PlaylistType.M3U_URL) remotePlaylist.url else "",
                    filePath = filePath,
                    serverUrl = if (type == PlaylistType.XTREAM_CODES) remotePlaylist.serverUrl else "",
                    username = if (type == PlaylistType.XTREAM_CODES) remotePlaylist.username else "",
                    password = if (type == PlaylistType.XTREAM_CODES) remotePlaylist.password else ""
                )

                val id = playlistRepository.savePlaylist(playlist)
                val savedPlaylist = playlist.copy(id = id)

                selectPlaylist(savedPlaylist, onPlaylistSelected)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save remote playlist")
                _state.update {
                    it.copy(
                        pairingStatus = "error",
                        pairingErrorMessage = "Playlist kaydedilemedi: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun showAddDialog() = _state.update { it.copy(showAddDialog = true, syncError = null) }
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
                    serverUrl = if (type == PlaylistType.XTREAM_CODES) server else "",
                    username = if (type == PlaylistType.XTREAM_CODES) username else "",
                    password = if (type == PlaylistType.XTREAM_CODES) password else ""
                )
                val id = playlistRepository.savePlaylist(playlist)
                val savedPlaylist = playlist.copy(id = id)
                _state.update { it.copy(showAddDialog = false) }
                selectPlaylist(savedPlaylist, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add playlist")
                _state.update { it.copy(syncError = e.localizedMessage ?: "Failed to add playlist") }
            }
        }
    }

    fun selectPlaylist(playlist: Playlist, onSelected: () -> Unit) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSyncing = true,
                    syncMessage = "Syncing playlist...",
                    syncError = null
                )
            }
            try {
                when (val syncResult = playlistRepository.syncPlaylist(playlist)) {
                    is Resource.Success -> {
                        playlistRepository.activatePlaylist(playlist.id)
                        _state.update { it.copy(isSyncing = false, syncMessage = "") }
                        onSelected()
                    }

                    is Resource.Error -> {
                        Timber.w(syncResult.throwable, "Playlist sync failed, checking existing content")
                        if (playlistRepository.hasSyncedContent(playlist.id)) {
                            playlistRepository.activatePlaylist(playlist.id)
                            _state.update { it.copy(isSyncing = false, syncMessage = "") }
                            onSelected()
                        } else {
                            _state.update {
                                it.copy(
                                    isSyncing = false,
                                    syncMessage = "",
                                    syncError = syncResult.message
                                )
                            }
                        }
                    }

                    Resource.Loading -> Unit
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync selected playlist")
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "",
                        syncError = e.localizedMessage ?: "Playlist sync failed"
                    )
                }
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    fun editPlaylist(
        playlist: Playlist,
        name: String,
        type: PlaylistType,
        url: String,
        server: String,
        username: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                playlistRepository.updatePlaylist(
                    playlist.copy(
                        name = name,
                        type = type,
                        url = if (type == PlaylistType.M3U_URL) url else "",
                        filePath = if (type == PlaylistType.M3U_FILE) url else "",
                        serverUrl = if (type == PlaylistType.XTREAM_CODES) server else "",
                        username = if (type == PlaylistType.XTREAM_CODES) username else "",
                        password = if (type == PlaylistType.XTREAM_CODES) password else "",
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "Failed to edit playlist")
                _state.update { it.copy(syncError = e.localizedMessage ?: "Failed to edit playlist") }
            }
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
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

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
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(34.dp))

            MobilePlaylistTopBar(
                playlistCount = state.playlists.size,
                onAdd = { viewModel.showAddDialog() }
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (state.isSyncing) {
                CircularProgressIndicator(color = ImaxColors.Primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    state.syncMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImaxColors.TextSecondary
                )
            } else {
                state.syncError?.let { error ->
                    SyncErrorPanel(message = error)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (state.playlists.isEmpty() && !state.isLoading) {
                    EmptyMobilePlaylistState(
                        onAdd = { viewModel.showAddDialog() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        "Listelerim",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ImaxColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Aktif kaynak, içerik sayıları ve hızlı işlemler tek ekranda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ImaxColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                isTv = false,
                                onSelect = { viewModel.selectPlaylist(playlist, onPlaylistSelected) },
                                onEdit = { editingPlaylist = playlist },
                                onDelete = { viewModel.deletePlaylist(playlist) }
                            )
                        }
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
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                }
            )
        }

        editingPlaylist?.let { playlist ->
            MobileEditPlaylistDialog(
                playlist = playlist,
                onDismiss = { editingPlaylist = null },
                onSave = { name, type, url, server, user, pass ->
                    viewModel.editPlaylist(
                        playlist = playlist,
                        name = name,
                        type = type,
                        url = url,
                        server = server,
                        username = user,
                        password = pass,
                        onSuccess = { editingPlaylist = null }
                    )
                },
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                }
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
    val addPlaylistFocusRequester = remember { FocusRequester() }
    val firstPlaylistFocusRequester = remember { FocusRequester() }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(state.showAddDialog, editingPlaylist, state.isSyncing, state.isLoading, state.playlists.size, state.pairingCode) {
        if (!state.showAddDialog && editingPlaylist == null && !state.isSyncing && !state.isLoading && state.pairingCode == null) {
            if (state.playlists.isNotEmpty()) {
                firstPlaylistFocusRequester.requestFocusSafely("TV onboarding first saved playlist")
            } else {
                addPlaylistFocusRequester.requestFocusSafely("TV onboarding QR pairing card")
            }
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
                .padding(horizontal = 72.dp, vertical = 40.dp)
        ) {
            TvPlaylistHeader(
                playlists = state.playlists,
                modifier = Modifier.fillMaxWidth(),
                onAddPlaylist = { viewModel.showAddDialog() }
            )
            Spacer(modifier = Modifier.height(24.dp))

            state.syncError?.let { error ->
                SyncErrorPanel(message = error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.playlists.isEmpty() && !state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TvQrPairingCard(
                        modifier = Modifier
                            .weight(1.2f)
                            .focusRequester(addPlaylistFocusRequester),
                        onClick = { viewModel.startQrPairing(onPlaylistSelected) }
                    )
                    TvAddPlaylistCard(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.showAddDialog() }
                    )
                }
            } else {
                Text(
                    "Listelerim",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ImaxColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Kumandayla liste seç, bilgileri düzenle veya yeni kaynak ekle.",
                    style = MaterialTheme.typography.titleMedium,
                    color = ImaxColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        val playlistModifier = if (playlist.id == state.playlists.firstOrNull()?.id) {
                            Modifier.focusRequester(firstPlaylistFocusRequester)
                        } else {
                            Modifier
                        }
                        TvPlaylistRow(
                            playlist = playlist,
                            modifier = playlistModifier,
                            onSelect = { viewModel.selectPlaylist(playlist, onPlaylistSelected) },
                            onEdit = { editingPlaylist = playlist },
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
                },
                onQrClick = {
                    viewModel.hideAddDialog()
                    viewModel.startQrPairing(onPlaylistSelected)
                }
            )
        }

        if (state.pairingCode != null) {
            TvQrPairingDialog(
                pairingCode = state.pairingCode,
                pairingStatus = state.pairingStatus ?: "pending",
                errorMessage = state.pairingErrorMessage,
                onDismiss = { viewModel.cancelPairing() }
            )
        }

        editingPlaylist?.let { playlist ->
            EditPlaylistDialog(
                playlist = playlist,
                onDismiss = { editingPlaylist = null },
                onSave = { name, type, url, server, user, pass ->
                    viewModel.editPlaylist(
                        playlist = playlist,
                        name = name,
                        type = type,
                        url = url,
                        server = server,
                        username = user,
                        password = pass,
                        onSuccess = { editingPlaylist = null }
                    )
                },
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                }
            )
        }
    }
}

@Composable
private fun SyncErrorPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ImaxColors.Error.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = ImaxColors.Error.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = ImaxColors.Error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = ImaxColors.Error
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
private fun MobilePlaylistTopBar(
    playlistCount: Int,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "iMAX",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ImaxColors.Primary
                )
                Text(
                    " Player",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ImaxColors.Secondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (playlistCount > 0) {
                    "$playlistCount kaynak hazır"
                } else {
                    "Kendi oynatma listelerini ekle"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ImaxColors.TextTertiary
            )
        }

        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onAdd),
            shape = RoundedCornerShape(18.dp),
            color = ImaxColors.Primary.copy(alpha = 0.16f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = ImaxColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Yeni",
                    style = MaterialTheme.typography.labelLarge,
                    color = ImaxColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun EmptyMobilePlaylistState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ImaxColors.SurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = ImaxColors.CardBorder.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ImaxColors.Primary.copy(alpha = 0.16f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = ImaxColors.Primary,
                        modifier = Modifier
                            .padding(18.dp)
                            .size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    "Henüz liste yok",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ImaxColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "M3U, dosya veya Xtream hesabını ekleyerek başla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImaxColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(22.dp))
                GradientButton(
                    text = "Liste Ekle",
                    icon = Icons.Filled.Add,
                    onClick = onAdd
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    isTv: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 176.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ImaxColors.SurfaceVariant.copy(alpha = if (isFocused) 0.98f else 0.82f),
                        ImaxColors.CardBackground.copy(alpha = 0.96f)
                    )
                )
            )
            .then(
                Modifier.border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) ImaxColors.FocusBorder else ImaxColors.CardBorder.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(26.dp)
                )
            )
            .clickable(onClick = onSelect)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ImaxColors.Primary.copy(alpha = 0.16f),
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(
                        imageVector = playlistTypeIcon(playlist.type),
                        contentDescription = null,
                        tint = ImaxColors.Primary,
                        modifier = Modifier.padding(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = ImaxColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        playlistTypeLabel(playlist.type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ImaxColors.TextSecondary
                    )
                }

                if (playlist.isActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ImaxColors.Success.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = ImaxColors.Success,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Aktif",
                                style = MaterialTheme.typography.labelMedium,
                                color = ImaxColors.Success
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlaylistStatPill(
                    label = "Kanal",
                    value = playlist.channelCount,
                    modifier = Modifier.weight(1f)
                )
                PlaylistStatPill(
                    label = "Film",
                    value = playlist.movieCount,
                    modifier = Modifier.weight(1f)
                )
                PlaylistStatPill(
                    label = "Dizi",
                    value = playlist.seriesCount,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onSelect),
                    shape = RoundedCornerShape(16.dp),
                    color = ImaxColors.Primary.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = ImaxColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Aç",
                            style = MaterialTheme.typography.labelLarge,
                            color = ImaxColors.TextPrimary
                        )
                    }
                }

                if (onEdit != null) {
                    PlaylistIconAction(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Düzenle",
                        tint = ImaxColors.TextPrimary,
                        onClick = onEdit
                    )
                }

                PlaylistIconAction(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Sil",
                    tint = ImaxColors.Error.copy(alpha = 0.86f),
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun PlaylistStatPill(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ImaxColors.Background.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = ImaxColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ImaxColors.TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlaylistIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = ImaxColors.Background.copy(alpha = 0.42f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun TvPlaylistHeader(
    playlists: List<Playlist>,
    modifier: Modifier = Modifier,
    addButtonModifier: Modifier = Modifier,
    onAddPlaylist: () -> Unit
) {
    val totalChannels = playlists.sumOf { it.channelCount }
    val totalMovies = playlists.sumOf { it.movieCount }
    val totalSeries = playlists.sumOf { it.seriesCount }
    val activePlaylist = playlists.firstOrNull { it.isActive }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = ImaxColors.CardBorder.copy(alpha = 0.75f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ImaxColors.SurfaceVariant.copy(alpha = 0.95f),
                            ImaxColors.CardBackground.copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(horizontal = 32.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "iMAX",
                                style = MaterialTheme.typography.displaySmall,
                                color = ImaxColors.Primary
                            )
                            Text(
                                " Player",
                                style = MaterialTheme.typography.displaySmall,
                                color = ImaxColors.Secondary
                            )
                        }
                        Text(
                            text = if (playlists.isEmpty()) {
                                "TV için medya kaynaklarını ekle"
                            } else {
                                "TV liste merkezi"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = ImaxColors.TextPrimary
                        )
                        Text(
                            text = activePlaylist?.let { "Aktif kaynak: ${it.name}" }
                                ?: "Kaynak ekle, test et ve kumandayla devam et.",
                            style = MaterialTheme.typography.titleMedium,
                            color = ImaxColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TvActionButton(
                        text = "Yeni liste",
                        icon = Icons.Filled.Add,
                        onClick = onAddPlaylist,
                        isSecondary = true,
                        modifier = addButtonModifier.width(198.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvDashboardMetric(
                        value = playlists.size.toString(),
                        label = "Kaynak",
                        modifier = Modifier.weight(1f)
                    )
                    TvDashboardMetric(
                        value = totalChannels.toString(),
                        label = "Kanal",
                        modifier = Modifier.weight(1f)
                    )
                    TvDashboardMetric(
                        value = totalMovies.toString(),
                        label = "Film",
                        modifier = Modifier.weight(1f)
                    )
                    TvDashboardMetric(
                        value = totalSeries.toString(),
                        label = "Dizi",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDashboardMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = ImaxColors.Background.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = ImaxColors.CardBorder.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = ImaxColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = ImaxColors.TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TvPlaylistRow(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TvFocusableCard(
            modifier = modifier.fillMaxWidth(),
            isSelected = playlist.isActive,
            onClick = onSelect
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(ImaxColors.Primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = playlistTypeIcon(playlist.type),
                            contentDescription = null,
                            tint = ImaxColors.Primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(22.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = ImaxColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playlist.isActive) {
                                TvActivePill()
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = playlistTypeLabel(playlist.type),
                            style = MaterialTheme.typography.titleMedium,
                            color = ImaxColors.TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TvInlineHint("OK ile aç")
                        if (onEdit != null) {
                            TvPlaylistMiniAction(
                                icon = Icons.Filled.Edit,
                                contentDescription = "Düzenle",
                                tint = ImaxColors.TextPrimary,
                                onClick = onEdit
                            )
                        }
                        TvPlaylistMiniAction(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Sil",
                            tint = ImaxColors.Error.copy(alpha = 0.9f),
                            onClick = onDelete
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TvPlaylistStat(
                        value = playlist.channelCount,
                        label = "Kanal",
                        modifier = Modifier.weight(1f)
                    )
                    TvPlaylistStat(
                        value = playlist.movieCount,
                        label = "Film",
                        modifier = Modifier.weight(1f)
                    )
                    TvPlaylistStat(
                        value = playlist.seriesCount,
                        label = "Dizi",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlaylistMiniAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(stiffness = 320f),
        label = "tvPlaylistMiniActionScale"
    )

    Surface(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(18.dp),
        color = if (isFocused) Color.White else ImaxColors.Background.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isFocused) Color.White else ImaxColors.CardBorder.copy(alpha = 0.55f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else tint,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun TvActivePill() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ImaxColors.Success.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = ImaxColors.Success,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Aktif",
                style = MaterialTheme.typography.labelLarge,
                color = ImaxColors.Success
            )
        }
    }
}

@Composable
private fun TvPlaylistStat(
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = ImaxColors.Background.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = ImaxColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = ImaxColors.TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TvAddPlaylistCard(
    modifier: Modifier,
    onClick: () -> Unit
) {
    TvFocusableCard(
        modifier = modifier,
        onClick = onClick
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
                    "Yeni liste ekle",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ImaxColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "M3U URL, Xtream / Portal veya yerel dosya. Kumandada OK ile TV uyumlu kurulum akışına geç.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ImaxColors.TextSecondary
                )
            }
            TvInlineHint("OK ile ekle")
        }
    }
}

@Composable
private fun AddPlaylistDialog(
    isTv: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit,
    onQrClick: (() -> Unit)? = null
) {
    if (isTv) {
        TvAddPlaylistDialog(
            onDismiss = onDismiss,
            onAdd = onAdd,
            onTest = onTest,
            onQrClick = onQrClick
        )
    } else {
        MobileAddPlaylistDialog(
            onDismiss = onDismiss,
            onAdd = onAdd,
            onTest = onTest
        )
    }
}

@Composable
private fun EditPlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (String, PlaylistType, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    TvAddPlaylistDialog(
        initialPlaylist = playlist,
        title = "Edit playlist",
        subtitle = "Update provider details, test the connection and save without leaving the TV playlist manager.",
        primaryActionText = "Save changes",
        onDismiss = onDismiss,
        onAdd = onSave,
        onTest = onTest
    )
}

@Composable
private fun MobileAddPlaylistDialog(
    initialPlaylist: Playlist? = null,
    title: String = "Add Playlist",
    primaryActionText: String = "Add",
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    var selectedType by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.type ?: PlaylistType.M3U_URL)
    }
    var name by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.name.orEmpty())
    }
    var url by remember(initialPlaylist?.id) {
        mutableStateOf(
            when (initialPlaylist?.type) {
                PlaylistType.M3U_FILE -> initialPlaylist.filePath
                else -> initialPlaylist?.url.orEmpty()
            }
        )
    }
    var server by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.serverUrl.orEmpty())
    }
    var username by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.username.orEmpty())
    }
    var password by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.password.orEmpty())
    }
    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }

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
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
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
                            imeAction = ImeAction.Done,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                }

                testMessage?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPositiveTestMessage(message)) {
                            ImaxColors.Success.copy(alpha = 0.12f)
                        } else {
                            ImaxColors.Error.copy(alpha = 0.12f)
                        }
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPositiveTestMessage(message)) {
                                ImaxColors.Success
                            } else {
                                ImaxColors.Error
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = canSave && !isTesting,
                    onClick = {
                        isTesting = true
                        onTest(name, selectedType, url, server, username, password) { result ->
                            testMessage = result
                            isTesting = false
                        }
                    }
                ) {
                    Text(
                        text = if (isTesting) "Testing..." else "Test",
                        color = if (canSave) ImaxColors.Primary else ImaxColors.TextTertiary
                    )
                }
                GradientButton(
                    text = primaryActionText,
                    enabled = canSave,
                    onClick = {
                        if (canSave) {
                            onAdd(name, selectedType, url, server, username, password)
                        }
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ImaxColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun MobileEditPlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (String, PlaylistType, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    MobileAddPlaylistDialog(
        initialPlaylist = playlist,
        title = "Edit Playlist",
        primaryActionText = "Save",
        onDismiss = onDismiss,
        onAdd = onSave,
        onTest = onTest
    )
}

@Composable
private fun TvAddPlaylistDialog(
    initialPlaylist: Playlist? = null,
    title: String = "Add playlist on TV",
    subtitle: String = "Step 1: choose a provider type. Step 2: fill only the required fields. Step 3: test and save.",
    primaryActionText: String = "Save and Continue",
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit,
    onQrClick: (() -> Unit)? = null
) {
    var selectedType by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.type ?: PlaylistType.M3U_URL)
    }
    var name by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.name.orEmpty())
    }
    var url by remember(initialPlaylist?.id) {
        mutableStateOf(
            when (initialPlaylist?.type) {
                PlaylistType.M3U_FILE -> initialPlaylist.filePath
                else -> initialPlaylist?.url.orEmpty()
            }
        )
    }
    var server by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.serverUrl.orEmpty())
    }
    var username by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.username.orEmpty())
    }
    var password by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.password.orEmpty())
    }
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
                            title,
                            style = MaterialTheme.typography.displaySmall,
                            color = ImaxColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            subtitle,
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
                        placeholder = "Living room playlist",
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
                                keyboardType = KeyboardType.Password,
                                visualTransformation = PasswordVisualTransformation()
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
                    if (onQrClick != null && initialPlaylist == null) {
                        TvActionButton(
                            text = "QR ile Uzaktan Ekle",
                            onClick = onQrClick,
                            modifier = Modifier.weight(1f),
                            isSecondary = true
                        )
                    }
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
                        text = primaryActionText,
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
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = visualTransformation,
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
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
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
            visualTransformation = visualTransformation,
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
    icon: ImageVector? = null,
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
        isSecondary -> if (isFocused) ImaxColors.SurfaceElevated else ImaxColors.SurfaceVariant
        else -> if (isFocused) Color.White else ImaxColors.SurfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = when {
        !enabled -> ImaxColors.CardBorder
        isDestructive && isFocused -> ImaxColors.Error
        isSecondary -> if (isFocused) ImaxColors.FocusBorder else ImaxColors.CardBorder
        isFocused -> ImaxColors.FocusBorder
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> ImaxColors.TextTertiary
        isFocused && !isDestructive && !isSecondary -> Color.Black
        isFocused && isSecondary -> ImaxColors.TextPrimary
        else -> ImaxColors.TextPrimary
    }

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = if (isFocused && enabled) 16.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isFocused) ImaxColors.FocusGlow else Color.Black,
                spotColor = if (isFocused) ImaxColors.FocusGlow else Color.Black
            )
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .border(if (isFocused) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
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

@Composable
private fun TvQrPairingCard(
    modifier: Modifier,
    onClick: () -> Unit
) {
    TvFocusableCard(
        modifier = modifier,
        onClick = onClick
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
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = ImaxColors.Primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "QR Kod ile Uzaktan Ekle",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ImaxColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "QR kodu telefonunuzla taratarak listenizi saniyeler içinde yükleyin.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ImaxColors.TextSecondary
                )
            }
            TvInlineHint("OK ile başlat")
        }
    }
}

@Composable
private fun TvQrPairingDialog(
    pairingCode: String,
    pairingStatus: String,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    val netlifyBaseUrl = "https://imax-player.netlify.app"
    val pairingUrl = "$netlifyBaseUrl/?code=$pairingCode"
    val qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&color=ffffff&bgcolor=12121a&data=${Uri.encode(pairingUrl)}"

    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeFocusRequester.requestFocusSafely("TV QR pairing dialog close button")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 880.dp),
            shape = RoundedCornerShape(32.dp),
            color = ImaxColors.Surface,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = ImaxColors.CardBorder.copy(alpha = 0.75f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Uzaktan Kurulum (Remote Setup)",
                        style = MaterialTheme.typography.displaySmall,
                        color = ImaxColors.TextPrimary
                    )
                    Text(
                        text = "Kumandayla uzun listeleri yazmak yerine QR kodu telefonunuzdan taratarak hızlıca kurulumu tamamlayın.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ImaxColors.TextSecondary
                    )
                }

                // Row containing QR and code details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: QR Code Box
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(ImaxColors.SurfaceVariant)
                            .border(1.dp, ImaxColors.CardBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = qrCodeUrl,
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }

                    // Right: Code displays and status
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "TV Eşleştirme Kodu",
                                style = MaterialTheme.typography.titleMedium,
                                color = ImaxColors.TextTertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pairingCode,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 42.sp,
                                    letterSpacing = 4.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                                ),
                                color = ImaxColors.Primary
                            )
                        }

                        Column {
                            Text(
                                text = "Adres",
                                style = MaterialTheme.typography.titleMedium,
                                color = ImaxColors.TextTertiary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = netlifyBaseUrl,
                                style = MaterialTheme.typography.bodyLarge,
                                color = ImaxColors.TextSecondary
                            )
                        }

                        // Connection Status panel
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when (pairingStatus) {
                                "completed" -> ImaxColors.Success.copy(alpha = 0.12f)
                                "error" -> ImaxColors.Error.copy(alpha = 0.12f)
                                else -> ImaxColors.Secondary.copy(alpha = 0.12f)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = when (pairingStatus) {
                                    "completed" -> ImaxColors.Success.copy(alpha = 0.4f)
                                    "error" -> ImaxColors.Error.copy(alpha = 0.4f)
                                    else -> ImaxColors.Secondary.copy(alpha = 0.4f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (pairingStatus == "pending" || pairingStatus == "completed") {
                                    CircularProgressIndicator(
                                        color = if (pairingStatus == "completed") ImaxColors.Success else ImaxColors.Secondary,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text(
                                    text = when (pairingStatus) {
                                        "completed" -> "Eşleşme başarılı! Listeniz TV'ye aktarılıyor..."
                                        "error" -> errorMessage ?: "Eşleşme sırasında bir hata oluştu."
                                        else -> "Oturum açık. Web formundan listeyi TV'ye gönderin."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (pairingStatus) {
                                        "completed" -> ImaxColors.Success
                                        "error" -> ImaxColors.Error
                                        else -> ImaxColors.TextPrimary
                                    }
                                )
                            }
                        }
                    }
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TvActionButton(
                        text = "İptal Et / Kapat",
                        onClick = onDismiss,
                        modifier = Modifier
                            .width(220.dp)
                            .focusRequester(closeFocusRequester),
                        isSecondary = true
                    )
                }
            }
        }
    }
}
