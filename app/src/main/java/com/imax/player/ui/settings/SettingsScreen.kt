package com.imax.player.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.R
import com.imax.player.core.datastore.AppSettings
import com.imax.player.core.datastore.SettingsDataStore
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.PlayerEngineType
import com.imax.player.core.player.AspectRatioMode
import com.imax.player.core.player.LiveLatencyMode
import com.imax.player.core.player.VideoQualityMode
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.data.repository.EpgRepository
import com.imax.player.ui.components.*
import com.imax.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ViewModel
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val playlistRepository: PlaylistRepository,
    private val epgRepository: EpgRepository,
    private val mpvPlayerEngine: com.imax.player.core.player.MpvPlayerEngine,
    private val vlcPlayerEngine: com.imax.player.core.player.VlcPlayerEngine,
    val parentalControlManager: com.imax.player.core.security.ParentalControlManager
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // Engine availability durumları — başlangıçta kontrol edilir
    val isMpvAvailable: Boolean by lazy { mpvPlayerEngine.isAvailable() }
    val isVlcAvailable: Boolean by lazy { vlcPlayerEngine.isAvailable() }

    val activePlaylist = playlistRepository.getActivePlaylist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ━━━ App General ━━━
    fun updateAppLanguage(lang: String) = viewModelScope.launch { settingsDataStore.updateAppLanguage(lang) }

    // ━━━ Player ━━━
    fun updatePlayerEngine(engine: PlayerEngineType) = viewModelScope.launch { settingsDataStore.updatePlayerEngine(engine) }
    fun updateDisplayMode(mode: String) = viewModelScope.launch { settingsDataStore.updateDefaultDisplayMode(mode) }
    fun updateDefaultSpeed(speed: Float) = viewModelScope.launch { settingsDataStore.updateDefaultPlaybackSpeed(speed) }
    fun updateSeekForward(ms: Long) = viewModelScope.launch { settingsDataStore.updateSeekForward(ms) }
    fun updateSeekBackward(ms: Long) = viewModelScope.launch { settingsDataStore.updateSeekBackward(ms) }
    fun updateAutoResume(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoResume(v) }
    fun updateContinueWatching(v: Boolean) = viewModelScope.launch { settingsDataStore.updateContinueWatching(v) }
    fun updateAutoPlayNext(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoPlayNext(v) }

    // ━━━ Audio & Subtitle ━━━
    fun updateSubtitleLang(lang: String) = viewModelScope.launch { settingsDataStore.updateSubtitleLanguage(lang) }
    fun updateAudioLang(lang: String) = viewModelScope.launch { settingsDataStore.updateAudioLanguage(lang) }
    fun updateAutoEnableSubtitles(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoEnableSubtitles(v) }
    fun updateSubtitleSize(size: Int) = viewModelScope.launch { settingsDataStore.updateSubtitleSize(size) }
    fun updateRememberTrack(v: Boolean) = viewModelScope.launch { settingsDataStore.updateRememberTrackPerContent(v) }

    // ━━━ Quality ━━━
    fun updateQualityMode(mode: String) = viewModelScope.launch { settingsDataStore.updateVideoQualityMode(mode) }
    fun updatePreferHw(v: Boolean) = viewModelScope.launch { settingsDataStore.updatePreferHwDecoding(v) }
    fun updateAllowFallback(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAllowQualityFallback(v) }

    // ━━━ Live TV ━━━
    fun updateLiveMode(mode: String) = viewModelScope.launch { settingsDataStore.updateLiveLatencyMode(mode) }
    fun updateLiveReconnect(v: Boolean) = viewModelScope.launch { settingsDataStore.updateLiveReconnect(v) }
    fun updateRememberChannel(v: Boolean) = viewModelScope.launch { settingsDataStore.updateRememberLastChannel(v) }
    fun updateStartFullscreen(v: Boolean) = viewModelScope.launch { settingsDataStore.updateStartFullscreenLive(v) }

    // ━━━ Playlist / Account ━━━
    fun exitPlaylist() = viewModelScope.launch {
        val active = activePlaylist.value ?: return@launch
        playlistRepository.deletePlaylist(active)
    }

    fun refreshPlaylist() = viewModelScope.launch {
        val active = activePlaylist.value ?: return@launch
        playlistRepository.syncPlaylist(active)
    }

    // ━━━ EPG ━━━
    fun updateEpgUrl(url: String) {
        viewModelScope.launch { settingsDataStore.updateEpgUrl(url) }
    }
    fun updateEpgAutoSync(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateEpgAutoSync(enabled) }
    }
    fun syncEpgNow() {
        viewModelScope.launch {
            val url = settingsDataStore.settings.first().epgUrl
            if (url.isNotBlank()) epgRepository.syncNow(url)
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Screen entry point
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun SettingsScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onBackToOnboarding: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        ImaxDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.SETTINGS,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = { route ->
                if (route == "exit") onBackToOnboarding()
                else onNavigate(route)
            }
        ) {
            SettingsContent(settings, viewModel, isTv = true, onBackToOnboarding)
        }
    } else {
        SettingsContent(settings, viewModel, isTv = false, onBackToOnboarding)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Language helpers
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private data class LanguageOption(val code: String, val labelResId: Int)

private val appLanguages = listOf(
    LanguageOption("system", R.string.language_system),
    LanguageOption("tr", R.string.language_turkish),
    LanguageOption("en", R.string.language_english)
)

@Composable
private fun langLabel(code: String, options: List<LanguageOption>): String {
    val resId = options.find { it.code == code }?.labelResId ?: return code
    return stringResource(resId)
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Main content
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun SettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    isTv: Boolean,
    onBackToOnboarding: () -> Unit
) {
    val dimens = LocalImaxDimens.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = dimens.screenPadding, vertical = dimens.screenPadding)
    ) {
        if (!isTv) {
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, color = ImaxColors.TextPrimary)
            Spacer(modifier = Modifier.height(20.dp))
        }


        // ══════════════════════════════════════════════
        // A) PLAYER
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.PlayCircle,
            title = stringResource(R.string.settings_player),
            isTv = isTv
        ) {
            // Engine selection — with availability checks
            val engineEntries = PlayerEngineType.entries.map { engineType ->
                val isAvailable = when (engineType) {
                    PlayerEngineType.EXOPLAYER -> true
                    PlayerEngineType.MPV -> viewModel.isMpvAvailable
                    PlayerEngineType.VLC -> viewModel.isVlcAvailable
                }
                val suffix = if (!isAvailable) " (desteklenmiyor)" else ""
                engineType to (engineType.name + suffix)
            }
            val availableEngineOptions = engineEntries.map { it.second }
            val currentEngineLabel = engineEntries.find { it.first == settings.playerEngine }?.second
                ?: settings.playerEngine.name
            SettingsDropdown(
                label = stringResource(R.string.setting_player_engine),
                value = currentEngineLabel,
                options = availableEngineOptions,
                isTv = isTv,
                onSelect = { name ->
                    val selectedEntry = engineEntries.find { it.second == name }
                    val type = selectedEntry?.first ?: PlayerEngineType.EXOPLAYER
                    val isAvailable = when (type) {
                        PlayerEngineType.EXOPLAYER -> true
                        PlayerEngineType.MPV -> viewModel.isMpvAvailable
                        PlayerEngineType.VLC -> viewModel.isVlcAvailable
                    }
                    if (isAvailable) {
                        viewModel.updatePlayerEngine(type)
                    }
                }
            )

            // Display mode
            val displayModes = AspectRatioMode.entries.map { it.name to it.label }
            SettingsDropdown(
                label = stringResource(R.string.setting_display_mode),
                value = displayModes.find { it.first.equals(settings.defaultDisplayMode, ignoreCase = true) }?.second ?: "Fit to Screen",
                options = displayModes.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val mode = displayModes.find { it.second == label }?.first ?: "FIT"
                    viewModel.updateDisplayMode(mode)
                }
            )

            // Playback speed
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            SettingsDropdown(
                label = stringResource(R.string.setting_playback_speed),
                value = "${settings.defaultPlaybackSpeed}x",
                options = speeds.map { "${it}x" },
                isTv = isTv,
                onSelect = { label ->
                    val speed = label.removeSuffix("x").toFloatOrNull() ?: 1f
                    viewModel.updateDefaultSpeed(speed)
                }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_auto_resume),
                checked = settings.autoResumePlayback,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAutoResume(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_continue_watching),
                checked = settings.continueWatching,
                isTv = isTv,
                onCheckedChange = { viewModel.updateContinueWatching(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_auto_play_next),
                checked = settings.autoPlayNextEpisode,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAutoPlayNext(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // B) AUDIO & SUBTITLES
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.Subtitles,
            title = stringResource(R.string.settings_audio_subtitle),
            isTv = isTv
        ) {
            val subtitleLangs = listOf("off", "tr", "en", "de", "fr", "ar", "system")
            SettingsDropdown(
                label = stringResource(R.string.setting_subtitles),
                value = settings.defaultSubtitleLanguage,
                options = subtitleLangs,
                isTv = isTv,
                onSelect = { viewModel.updateSubtitleLang(it) }
            )

            val audioLangs = listOf("system", "tr", "en", "de", "fr", "ar")
            SettingsDropdown(
                label = stringResource(R.string.setting_audio_track),
                value = settings.defaultAudioLanguage,
                options = audioLangs,
                isTv = isTv,
                onSelect = { viewModel.updateAudioLang(it) }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_auto_enable_subtitles),
                checked = settings.autoEnableSubtitles,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAutoEnableSubtitles(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // C) VIDEO QUALITY
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.HighQuality,
            title = stringResource(R.string.settings_video_quality),
            isTv = isTv
        ) {
            val qualityModes = VideoQualityMode.entries.map { it.name to it.label }
            SettingsDropdown(
                label = stringResource(R.string.setting_video_quality),
                value = qualityModes.find { it.first.equals(settings.videoQualityMode, ignoreCase = true) }?.second ?: "Auto",
                options = qualityModes.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val mode = qualityModes.find { it.second == label }?.first ?: "AUTO"
                    viewModel.updateQualityMode(mode)
                }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_prefer_hw_decoding),
                checked = settings.preferHwDecoding,
                isTv = isTv,
                onCheckedChange = { viewModel.updatePreferHw(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_allow_quality_fallback),
                checked = settings.allowQualityFallback,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAllowFallback(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // D) LIVE TV
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.LiveTv,
            title = stringResource(R.string.settings_live_tv),
            isTv = isTv
        ) {
            val latencyModes = LiveLatencyMode.entries.map { it.name to it.label }
            SettingsDropdown(
                label = stringResource(R.string.settings_live_latency),
                value = latencyModes.find { it.first.equals(settings.liveLatencyMode, ignoreCase = true) }?.second ?: "Balanced",
                options = latencyModes.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val mode = latencyModes.find { it.second == label }?.first ?: "BALANCED"
                    viewModel.updateLiveMode(mode)
                }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_live_reconnect),
                checked = settings.liveReconnectOnFailure,
                isTv = isTv,
                onCheckedChange = { viewModel.updateLiveReconnect(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_remember_channel),
                checked = settings.rememberLastChannel,
                isTv = isTv,
                onCheckedChange = { viewModel.updateRememberChannel(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_start_fullscreen),
                checked = settings.startFullscreenLive,
                isTv = isTv,
                onCheckedChange = { viewModel.updateStartFullscreen(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // D-2) EPG (Program Rehberi)
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.Tv,
            title = stringResource(R.string.settings_epg),
            isTv = isTv
        ) {
            // EPG URL
            androidx.compose.material3.OutlinedTextField(
                value = settings.epgUrl,
                onValueChange = { viewModel.updateEpgUrl(it) },
                label = { Text(stringResource(R.string.epg_url_hint)) },
                placeholder = { Text("http://provider.com/epg.xml") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ImaxColors.Primary,
                    unfocusedBorderColor = ImaxColors.CardBorder,
                    focusedLabelColor = ImaxColors.Primary,
                    cursorColor = ImaxColors.Primary,
                    focusedTextColor = ImaxColors.TextPrimary,
                    unfocusedTextColor = ImaxColors.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Auto sync toggle
            SettingsSwitch(
                label = stringResource(R.string.settings_epg_auto_sync),
                checked = settings.epgAutoSync,
                isTv = isTv,
                onCheckedChange = { viewModel.updateEpgAutoSync(it) }
            )

            // Manual sync button
            if (settings.epgUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsActionButton(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(R.string.epg_sync_now),
                    isTv = isTv,
                    onClick = { viewModel.syncEpgNow() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // E) PARENTAL CONTROL
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.FamilyRestroom,
            title = "Ebeveyn Denetimi",
            isTv = isTv
        ) {
            ParentalControlSection(
                parentalControlManager = viewModel.parentalControlManager,
                isTv = isTv
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // F) PLAYLIST / ACCOUNT
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            title = stringResource(R.string.settings_playlist_account),
            isTv = isTv
        ) {
            // Current playlist info
            if (activePlaylist != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(ImaxColors.Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null,
                            tint = ImaxColors.Primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activePlaylist!!.name, style = MaterialTheme.typography.bodyLarge, color = ImaxColors.TextPrimary)
                        Text(
                            when (activePlaylist!!.type) {
                                com.imax.player.core.model.PlaylistType.XTREAM_CODES -> "Xtream Codes"
                                com.imax.player.core.model.PlaylistType.M3U_URL -> "M3U URL"
                                com.imax.player.core.model.PlaylistType.M3U_FILE -> "M3U File"
                            },
                            style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary
                        )
                    }
                }
                HorizontalDivider(color = ImaxColors.DividerColor, modifier = Modifier.padding(vertical = 4.dp))
            }

            SettingsActionButton(
                icon = Icons.Filled.Refresh,
                label = stringResource(R.string.action_refresh_playlist),
                isTv = isTv,
                onClick = { viewModel.refreshPlaylist() }
            )

            SettingsActionButton(
                icon = Icons.Filled.SwapHoriz,
                label = stringResource(R.string.action_switch_playlist),
                isTv = isTv,
                onClick = onBackToOnboarding
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsActionButton(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.nav_exit_playlist),
                isDanger = true,
                isTv = isTv,
                onClick = { showExitDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // F) APP / GENERAL
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.settings_app_general),
            isTv = isTv
        ) {
            val localizedAppLangs = appLanguages.map { it.code to stringResource(it.labelResId) }
            SettingsDropdown(
                label = stringResource(R.string.settings_app_language),
                value = langLabel(settings.appLanguage, appLanguages),
                options = localizedAppLangs.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val langCode = localizedAppLangs.find { it.second == label }?.first ?: "system"
                    viewModel.updateAppLanguage(langCode)
                    
                    val localeList = if (langCode == "system") {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(langCode)
                    }
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsInfoRow(stringResource(R.string.app_version), "iMAX Player v1.0.0")
            SettingsInfoRow("Player Engine", settings.playerEngine.name)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.labelSmall, color = ImaxColors.TextTertiary)
            Text(stringResource(R.string.disclaimer_text),
                style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Exit playlist confirm dialog
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.nav_exit_playlist)) },
            text = {
                Text(
                    "Mevcut listeyi silmek ve çıkış yapmak istediğinize emin misiniz?",
                    style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.exitPlaylist()
                        onBackToOnboarding()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ImaxColors.Error)
                ) { Text("Onayla") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("İptal") }
            },
            containerColor = ImaxColors.Surface,
            titleContentColor = ImaxColors.TextPrimary,
            textContentColor = ImaxColors.TextSecondary
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Reusable settings composables
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    isTv: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (isTv) ImaxColors.GlassBorder else ImaxColors.CardBorder
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ImaxColors.CardBackground, RoundedCornerShape(16.dp))
            .border(if (isTv) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(if (isTv) 18.dp else 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = ImaxColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = ImaxColors.Primary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    isTv: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val scale = if (isTv && isFocused) 1.02f else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(
                        if (isTv && isFocused) {
                            Modifier.shadow(14.dp, RoundedCornerShape(10.dp), spotColor = ImaxColors.FocusGlow)
                        } else {
                            Modifier
                        }
                    ),
                border = BorderStroke(
                    width = if (isTv && isFocused) 3.dp else if (isFocused) 2.dp else 1.dp,
                    color = when {
                        isFocused -> ImaxColors.FocusBorder
                        else -> ImaxColors.CardBorder
                    }
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ImaxColors.TextPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(option, modifier = Modifier.weight(1f))
                                if (option == value) {
                                    Icon(Icons.Filled.Check, contentDescription = null,
                                        tint = ImaxColors.Primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    isTv: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isTv && isFocused -> ImaxColors.SurfaceElevated
        isFocused -> ImaxColors.SurfaceVariant
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = { onCheckedChange(!checked) })
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                when {
                    isTv && isFocused -> Modifier
                        .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = ImaxColors.FocusGlow)
                        .border(3.dp, ImaxColors.FocusBorder, RoundedCornerShape(10.dp))

                    isFocused -> Modifier.border(2.dp, ImaxColors.FocusBorder, RoundedCornerShape(10.dp))
                    else -> Modifier
                }
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ImaxColors.Primary,
                checkedTrackColor = ImaxColors.Primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingsActionButton(
    icon: ImageVector,
    label: String,
    isDanger: Boolean = false,
    isTv: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDanger) ImaxColors.Error else ImaxColors.TextPrimary
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isTv && isFocused -> ImaxColors.SurfaceElevated
                    isFocused -> ImaxColors.SurfaceVariant
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                when {
                    isTv && isFocused -> Modifier
                        .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = ImaxColors.FocusGlow)
                        .border(3.dp, ImaxColors.FocusBorder, RoundedCornerShape(10.dp))

                    isFocused -> Modifier.border(2.dp, ImaxColors.FocusBorder, RoundedCornerShape(10.dp))
                    else -> Modifier
                }
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = contentColor)
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextPrimary)
    }
}
