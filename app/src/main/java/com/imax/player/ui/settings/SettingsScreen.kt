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
import androidx.compose.ui.focus.onFocusChanged
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
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

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
        // E) PLAYLIST / ACCOUNT
        // ══════════════════════════════════════════════
        SettingsSection(icon = Icons.AutoMirrored.Filled.PlaylistPlay, title = stringResource(R.string.settings_playlist_account)) {
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
                onClick = { viewModel.refreshPlaylist() }
            )

            SettingsActionButton(
                icon = Icons.Filled.SwapHoriz,
                label = stringResource(R.string.action_switch_playlist),
                onClick = onBackToOnboarding
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsActionButton(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.nav_exit_playlist),
                isDanger = true,
                onClick = { showExitDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // F) APP / GENERAL
        // ══════════════════════════════════════════════
        SettingsSection(icon = Icons.Filled.Info, title = stringResource(R.string.settings_app_general)) {
            val localizedAppLangs = appLanguages.map { it.code to stringResource(it.labelResId) }
            SettingsDropdown(
                label = stringResource(R.string.settings_app_language),
                value = langLabel(settings.appLanguage, appLanguages),
                options = localizedAppLangs.map { it.second },
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
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ImaxColors.CardBackground, RoundedCornerShape(16.dp))
            .border(1.dp, ImaxColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
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
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, if (isFocused) ImaxColors.Primary else ImaxColors.CardBorder),
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
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (isFocused) Modifier.background(ImaxColors.SurfaceVariant, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = { onCheckedChange(!checked) })
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
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
    onClick: () -> Unit
) {
    val contentColor = if (isDanger) ImaxColors.Error else ImaxColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
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
