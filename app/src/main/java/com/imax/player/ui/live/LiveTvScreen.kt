package com.imax.player.ui.live

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.Channel
import com.imax.player.data.repository.ContentRepository
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.components.*
import com.imax.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.imax.player.R

data class LiveTvState(
    val channels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist != null) {
                    launch {
                        contentRepository.getChannelGroups(playlist.id).collect { groups ->
                            _state.update { it.copy(groups = groups) }
                        }
                    }
                    launch {
                        contentRepository.getChannels(playlist.id).collect { channels ->
                            _state.update { it.copy(channels = channels, isLoading = false) }
                        }
                    }
                }
            }
        }
    }

    fun selectGroup(group: String?) = _state.update { it.copy(selectedGroup = group) }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            contentRepository.toggleChannelFavorite(channel.id, !channel.isFavorite)
        }
    }
}

@Composable
fun LiveTvScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        ImaxDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.LIVE_TV,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = { if (it == "exit") onNavigate(Routes.ONBOARDING) else onNavigate(it) }
        ) {
            if (state.isLoading) LoadingScreen()
            else TvLiveTvContent(state, viewModel, onPlayChannel)
        }
    } else {
        if (state.isLoading) LoadingScreen()
        else MobileLiveTvContent(state, viewModel, onPlayChannel)
    }
}

@Composable
private fun TvLiveTvContent(
    state: LiveTvState,
    viewModel: LiveTvViewModel,
    onPlayChannel: (String, String, Long) -> Unit
) {
    val dimens = LocalImaxDimens.current
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(dimens.categoryPanelWidth).fillMaxHeight()
                .background(ImaxColors.Surface).padding(vertical = 8.dp)
        ) {
            item {
                TvCategoryItem(name = stringResource(R.string.category_all), isSelected = state.selectedGroup == null,
                    onClick = { viewModel.selectGroup(null) })
            }
            items(state.groups) { g ->
                TvCategoryItem(name = g, isSelected = state.selectedGroup == g,
                    onClick = { viewModel.selectGroup(g) })
            }
        }

        val display = if (state.selectedGroup != null)
            state.channels.filter { it.groupTitle == state.selectedGroup }
        else state.channels

        if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(8.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(display) { channel ->
                    ChannelListItem(
                        channel = channel,
                        isTv = true,
                        onClick = { onPlayChannel(channel.streamUrl, channel.name, channel.id) },
                        onFavoriteToggle = { viewModel.toggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileLiveTvContent(
    state: LiveTvState,
    viewModel: LiveTvViewModel,
    onPlayChannel: (String, String, Long) -> Unit
) {
    val dimens = LocalImaxDimens.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var recentGroups by remember { mutableStateOf(listOf<String>()) }
    var pinnedGroups by remember { mutableStateOf(listOf<String>()) }

    val groupCounts = remember(state.channels) {
        state.channels.groupBy { it.groupTitle }.mapValues { it.value.size }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        Text(stringResource(R.string.live_tv), style = MaterialTheme.typography.headlineMedium, color = ImaxColors.TextPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenPadding))
        Spacer(modifier = Modifier.height(12.dp))

        QuickCategoryBar(
            categories = state.groups,
            selectedCategory = state.selectedGroup,
            recentCategories = recentGroups,
            onCategorySelected = { group ->
                viewModel.selectGroup(group)
                if (group != null && group !in recentGroups) {
                    recentGroups = (listOf(group) + recentGroups).take(10)
                }
            },
            onBrowseAll = { showCategorySheet = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        val display = if (state.selectedGroup != null) state.channels.filter { it.groupTitle == state.selectedGroup } else state.channels

        if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(display) { channel ->
                    ChannelListItem(
                        channel = channel,
                        isTv = false,
                        onClick = { onPlayChannel(channel.streamUrl, channel.name, channel.id) },
                        onFavoriteToggle = { viewModel.toggleFavorite(channel) }
                    )
                }
            }
        }
    }

    CategoryBottomSheet(
        isVisible = showCategorySheet,
        categories = state.groups,
        categoryCounts = groupCounts,
        selectedCategory = state.selectedGroup,
        recentCategories = recentGroups,
        pinnedCategories = pinnedGroups,
        onCategorySelected = { group ->
            viewModel.selectGroup(group)
            if (group != null && group !in recentGroups) {
                recentGroups = (listOf(group) + recentGroups).take(10)
            }
        },
        onDismiss = { showCategorySheet = false },
        onTogglePin = { g ->
            pinnedGroups = if (g in pinnedGroups) pinnedGroups - g else pinnedGroups + g
        }
    )
}

@Composable
private fun ChannelListItem(
    channel: Channel,
    isTv: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = if (isFocused) ImaxColors.SurfaceVariant else ImaxColors.CardBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (isFocused) Modifier.border(1.dp, ImaxColors.FocusBorder, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterImage(
            url = channel.logoUrl,
            contentDescription = channel.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(if (isTv) 48.dp else 40.dp)
                .clip(CircleShape)
                .background(ImaxColors.Surface)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleMedium,
                color = ImaxColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.groupTitle.isNotBlank()) {
                Text(channel.groupTitle, style = MaterialTheme.typography.bodySmall,
                    color = ImaxColors.TextTertiary, maxLines = 1)
            }
        }
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (channel.isFavorite) ImaxColors.Primary else ImaxColors.TextTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.action_play),
            tint = ImaxColors.Primary,
            modifier = Modifier.size(24.dp)
        )
    }
}
