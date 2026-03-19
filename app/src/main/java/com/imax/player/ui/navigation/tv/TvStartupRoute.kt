package com.imax.player.ui.navigation.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.model.Playlist
import com.imax.player.core.model.PlaylistType
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.components.LoadingScreen
import com.imax.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class TvStartupState(
    val isLoading: Boolean = true,
    val targetRoute: String? = null
)

@HiltViewModel
class TvStartupViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TvStartupState())
    val state: StateFlow<TvStartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collectLatest { playlists ->
                val activePlaylist = playlists.firstOrNull { it.isActive && it.isUsableForTvStartup() }
                val fallbackPlaylist = playlists.firstOrNull { it.isUsableForTvStartup() }

                val targetRoute = when {
                    activePlaylist != null -> Routes.HOME
                    fallbackPlaylist != null -> {
                        if (!fallbackPlaylist.isActive) {
                            playlistRepository.activatePlaylist(fallbackPlaylist.id)
                        }
                        Routes.HOME
                    }

                    else -> Routes.PLAYLISTS
                }

                _state.value = TvStartupState(
                    isLoading = false,
                    targetRoute = targetRoute
                )
            }
        }
    }
}

@Composable
fun TvStartupRoute(
    onReady: (String) -> Unit,
    viewModel: TvStartupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasNavigated by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(state.isLoading, state.targetRoute, hasNavigated) {
        val targetRoute = state.targetRoute ?: return@LaunchedEffect
        if (!state.isLoading && !hasNavigated) {
            hasNavigated = true
            onReady(targetRoute)
        }
    }

    LoadingScreen()
}

private fun Playlist.isUsableForTvStartup(): Boolean {
    val hasSource = when (type) {
        PlaylistType.M3U_URL -> url.isNotBlank()
        PlaylistType.M3U_FILE -> filePath.isNotBlank()
        PlaylistType.XTREAM_CODES -> serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    val hasPlayableCatalog = channelCount > 0 || movieCount > 0 || seriesCount > 0
    return hasSource && hasPlayableCatalog
}
