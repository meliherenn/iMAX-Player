package com.imax.player.ui.live

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.imax.player.R
import com.imax.player.core.datastore.SettingsDataStore

data class LiveTvState(
    val channels: List<Channel> = emptyList(),
    val mobileChannels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val mobileGroups: List<String> = emptyList(),
    val groupCounts: Map<String, Int> = emptyMap(),
    val selectedGroup: String? = null,
    val isLoading: Boolean = true,
    val epgPrograms: Map<String, com.imax.player.data.parser.EpgProgram> = emptyMap()
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository,
    private val epgRepository: com.imax.player.data.repository.EpgRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist == null) {
                    _state.value = LiveTvState()
                } else {
                    _state.update {
                        it.copy(
                            channels = emptyList(),
                            mobileChannels = emptyList(),
                            groups = emptyList(),
                            mobileGroups = emptyList(),
                            groupCounts = emptyMap(),
                            selectedGroup = null,
                            isLoading = true
                        )
                    }

                    contentRepository.getChannels(playlist.id).collectLatest { channels ->
                        val settings = settingsDataStore.settings.first()
                        val orderedChannels = if (settings.rememberLastChannel) {
                            channels.sortedWith(
                                compareByDescending<Channel> { it.lastWatched }
                                    .thenBy { it.sortOrder }
                                    .thenBy { it.name.lowercase() }
                            )
                        } else {
                            channels
                        }
                        val processed = withContext(Dispatchers.Default) {
                            val groups = orderedChannels.distinctGroupsInOrder()
                            val mobileGroups = prioritizeGroupsForMobile(groups)
                            val mobileChannels = rankChannelsForMobile(orderedChannels)
                            val groupCounts = orderedChannels
                                .groupBy { it.groupTitle }
                                .mapValues { it.value.size }

                            ProcessedLiveTvContent(
                                groups = groups,
                                mobileGroups = mobileGroups,
                                mobileChannels = mobileChannels,
                                groupCounts = groupCounts
                            )
                        }

                        _state.update { currentState ->
                            val selectedGroup = currentState.selectedGroup
                                ?.takeIf { it in processed.groups }

                            currentState.copy(
                                channels = orderedChannels,
                                mobileChannels = processed.mobileChannels,
                                groups = processed.groups,
                                mobileGroups = processed.mobileGroups,
                                groupCounts = processed.groupCounts,
                                selectedGroup = selectedGroup,
                                isLoading = false
                            )
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

    fun loadEpgForVisibleChannels(channelIds: List<String>) {
        viewModelScope.launch {
            val programs = epgRepository.getCurrentProgramsForChannels(channelIds)
            _state.update { it.copy(epgPrograms = programs) }
        }
    }
}

@Composable
fun LiveTvScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit,
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
        MobileLiveTvContent(state, viewModel, onPlayChannel)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvLiveTvContent(
    state: LiveTvState,
    viewModel: LiveTvViewModel,
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    val allCategoryLabel = stringResource(R.string.category_all)
    val displayCategories = remember(state.groups, allCategoryLabel) {
        listOf(
            TvCategoryRailItem(
                id = TV_ALL_CATEGORY_KEY,
                title = allCategoryLabel,
                group = null
            )
        ) + state.groups.map { group ->
            TvCategoryRailItem(
                id = group,
                title = group,
                group = group
            )
        }
    }
    val categoryFocusRequesters = remember(displayCategories) {
        displayCategories.associate { it.id to FocusRequester() }
    }
    Row(modifier = Modifier.fillMaxSize()) {
        val display = if (state.selectedGroup != null)
            state.channels.filter { it.groupTitle == state.selectedGroup }
        else state.channels
        val channelFocusRequesters = remember(display.map(Channel::id)) {
            display.associate { it.id to FocusRequester() }
        }
        val selectedCategoryRequester = categoryFocusRequesters[state.selectedGroup ?: TV_ALL_CATEGORY_KEY]
        val firstChannelRequester = display.firstOrNull()
            ?.let { channel -> channelFocusRequesters.getValue(channel.id) }

        TvCategoryPanel {
            itemsIndexed(displayCategories, key = { _, category -> category.id }) { index, category ->
                TvRailCategoryItem(
                    name = category.title,
                    isSelected = state.selectedGroup == category.group,
                    modifier = Modifier
                        .focusRequester(categoryFocusRequesters.getValue(category.id))
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = firstChannelRequester ?: FocusRequester.Cancel
                            // Prevent wrap-around: block UP on first item, DOWN on last item
                            if (index == 0) up = FocusRequester.Cancel
                            if (index == displayCategories.lastIndex) down = FocusRequester.Cancel
                        },
                    onClick = { viewModel.selectGroup(category.group) }
                )
            }
        }

        if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 18.dp, end = 20.dp, bottom = 18.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(display, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelListItem(
                        channel = channel,
                        isTv = true,
                        epgProgram = state.epgPrograms[channel.epgChannelId],
                        modifier = Modifier
                            .focusRequester(channelFocusRequesters.getValue(channel.id))
                            .focusProperties {
                                left = selectedCategoryRequester ?: FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                        onClick = {
                            onPlayChannel(
                                channel.streamUrl,
                                channel.name,
                                channel.id,
                                state.selectedGroup ?: channel.groupTitle
                            )
                        },
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
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    val dimens = LocalImaxDimens.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var recentGroups by remember { mutableStateOf(listOf<String>()) }
    var pinnedGroups by remember { mutableStateOf(listOf<String>()) }
    val rankedChannels = state.mobileChannels
    val mobileGroups = state.mobileGroups

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        Text(stringResource(R.string.live_tv), style = MaterialTheme.typography.headlineMedium, color = ImaxColors.TextPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenPadding))
        Spacer(modifier = Modifier.height(12.dp))

        QuickCategoryBar(
            categories = mobileGroups,
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

        val display = if (state.selectedGroup != null) {
            rankedChannels.filter { it.groupTitle == state.selectedGroup }
        } else {
            rankedChannels
        }

        if (state.isLoading && display.isEmpty()) {
            ChannelListLoadingPlaceholder()
        } else if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(display, key = { it.id }) { channel ->
                    ChannelListItem(
                        channel = channel,
                        isTv = false,
                        epgProgram = state.epgPrograms[channel.epgChannelId],
                        onClick = { onPlayChannel(channel.streamUrl, channel.name, channel.id, state.selectedGroup) },
                        onFavoriteToggle = { viewModel.toggleFavorite(channel) }
                    )
                }
            }
        }
    }

    CategoryBottomSheet(
        isVisible = showCategorySheet,
        categories = mobileGroups,
        categoryCounts = state.groupCounts,
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

private data class ProcessedLiveTvContent(
    val groups: List<String>,
    val mobileGroups: List<String>,
    val mobileChannels: List<Channel>,
    val groupCounts: Map<String, Int>
)

private fun List<Channel>.distinctGroupsInOrder(): List<String> {
    if (isEmpty()) return emptyList()

    val seen = LinkedHashSet<String>(size)
    forEach { channel ->
        val group = channel.groupTitle
        if (group.isNotBlank()) {
            seen += group
        }
    }
    return seen.toList()
}

@Composable
private fun ChannelListLoadingPlaceholder() {
    val dimens = LocalImaxDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ImaxColors.CardBackground)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ShimmerBox(modifier = Modifier.size(24.dp).clip(CircleShape))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ChannelListItem(
    channel: Channel,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    epgProgram: com.imax.player.data.parser.EpgProgram? = null,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val itemShape = RoundedCornerShape(12.dp)

    val targetBackgroundColor = if (isTv) {
        if (isFocused) ImaxColors.SurfaceElevated else ImaxColors.CardBackground
    } else {
        if (isFocused) ImaxColors.SurfaceVariant else ImaxColors.CardBackground
    }
    val backgroundColor by animateColorAsState(targetBackgroundColor, tween(150), label = "channelBg")

    val targetContentColor = ImaxColors.TextPrimary
    val contentColor by animateColorAsState(targetContentColor, tween(150), label = "channelText")

    val targetSecondaryColor = if (isTv && isFocused) ImaxColors.TextSecondary else ImaxColors.TextTertiary
    val secondaryContentColor by animateColorAsState(targetSecondaryColor, tween(150), label = "channelSecondaryText")

    val targetScale = if (isTv && isFocused) 1.035f else 1f
    val scale by animateFloatAsState(targetScale, tween(150), label = "channelScale")

    val borderWidth = when {
        isTv && isFocused -> 4.dp
        !isTv && isFocused -> 1.dp
        else -> 0.dp
    }
    val borderColor = if (isFocused) ImaxColors.FocusBorder else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isTv && isFocused) 18.dp.toPx() else 0f
                ambientShadowColor = ImaxColors.FocusGlow
                spotShadowColor = ImaxColors.FocusGlow
                this.shape = itemShape
                clip = false
            }
            .clip(itemShape)
            .background(backgroundColor, itemShape)
            .border(borderWidth, borderColor, itemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTv) null else androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
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
                .background(if (isTv && isFocused) ImaxColors.Primary.copy(alpha = 0.14f) else ImaxColors.Surface)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = if (isTv && isFocused) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (channel.groupTitle.isNotBlank()) {
                Text(channel.groupTitle, style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor,
                    maxLines = 1)
            }
            // EPG program info
            if (epgProgram != null) {
                Text(
                    text = epgProgram.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isTv && isFocused) ImaxColors.TextSecondary else ImaxColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = { epgProgram.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 2.dp),
                    color = ImaxColors.Primary,
                    trackColor = if (isTv && isFocused) ImaxColors.CardBorder else ImaxColors.Surface
                )
            }
        }
        IconButton(
            onClick = onFavoriteToggle,
            modifier = if (isTv) {
                Modifier.focusProperties { canFocus = false }
            } else {
                Modifier
            }
        ) {
            Icon(
                imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (channel.isFavorite) ImaxColors.Primary else if (isTv && isFocused) ImaxColors.TextSecondary else ImaxColors.TextTertiary,
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

private data class TvCategoryRailItem(
    val id: String,
    val title: String,
    val group: String?
)

private const val TV_ALL_CATEGORY_KEY = "__tv_all__"
