package com.imax.player.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.R
import com.imax.player.core.common.StringUtils
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.Channel
import com.imax.player.core.model.ContentType
import com.imax.player.core.model.Movie
import com.imax.player.core.model.Series
import com.imax.player.core.model.WatchHistoryItem
import com.imax.player.data.repository.ContentRepository
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.components.ContentPosterCard
import com.imax.player.ui.components.EmptyScreen
import com.imax.player.ui.components.GradientButton
import com.imax.player.ui.components.ImaxDrawer
import com.imax.player.ui.components.ImaxOutlinedButton
import com.imax.player.ui.components.LoadingScreen
import com.imax.player.ui.components.PosterImage
import com.imax.player.ui.components.rememberTvFocusVisualState
import com.imax.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TvContinueWatchingState(
    val items: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TvContinueWatchingViewModel @Inject constructor(
    contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TvContinueWatchingState())
    val state: StateFlow<TvContinueWatchingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            contentRepository.getContinueWatching().collectLatest { items ->
                _state.value = TvContinueWatchingState(
                    items = items,
                    isLoading = false
                )
            }
        }
    }
}

data class TvFavoritesState(
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TvFavoritesViewModel @Inject constructor(
    playlistRepository: PlaylistRepository,
    contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TvFavoritesState())
    val state: StateFlow<TvFavoritesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist == null) {
                    _state.value = TvFavoritesState(isLoading = false)
                } else {
                    launch {
                        contentRepository.getFavoriteMovies(playlist.id).collectLatest { movies ->
                            _state.value = _state.value.copy(
                                movies = movies,
                                isLoading = false
                            )
                        }
                    }
                    launch {
                        contentRepository.getFavoriteSeries(playlist.id).collectLatest { series ->
                            _state.value = _state.value.copy(
                                series = series,
                                isLoading = false
                            )
                        }
                    }
                    launch {
                        contentRepository.getFavoriteChannels(playlist.id).collectLatest { channels ->
                            _state.value = _state.value.copy(
                                channels = channels,
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvContinueWatchingScreen(
    onNavigate: (String) -> Unit,
    onResume: (WatchHistoryItem) -> Unit,
    onOpenDetail: (Long, String) -> Unit,
    viewModel: TvContinueWatchingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    ImaxDrawer(
        isExpanded = isDrawerExpanded,
        selectedRoute = Routes.CONTINUE_WATCHING,
        isTv = true,
        onToggle = { isDrawerExpanded = !isDrawerExpanded },
        onNavigate = onNavigate
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.items.isEmpty() -> EmptyScreen(message = stringResource(R.string.no_content))
            else -> TvContinueWatchingContent(
                state = state,
                onResume = onResume,
                onOpenDetail = onOpenDetail
            )
        }
    }
}

@Composable
private fun TvContinueWatchingContent(
    state: TvContinueWatchingState,
    onResume: (WatchHistoryItem) -> Unit,
    onOpenDetail: (Long, String) -> Unit
) {
    val dimens = LocalImaxDimens.current
    val featured = state.items.firstOrNull()
    val movies = state.items.filter { it.contentType == ContentType.MOVIE }
    val series = state.items.filter { it.contentType == ContentType.SERIES }
    val live = state.items.filter { it.contentType == ContentType.LIVE }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = dimens.screenPadding)
    ) {
        if (featured != null) {
            TvContinueWatchingHero(
                item = featured,
                onResume = { onResume(featured) },
                onOpenDetail = {
                    if (featured.contentType != ContentType.LIVE) {
                        onOpenDetail(featured.contentId, featured.contentType.name)
                    } else {
                        onResume(featured)
                    }
                }
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        TvHistoryRail(
            title = stringResource(R.string.nav_series),
            items = series,
            onResume = onResume,
            onOpenDetail = onOpenDetail
        )
        TvHistoryRail(
            title = stringResource(R.string.nav_movies),
            items = movies,
            onResume = onResume,
            onOpenDetail = onOpenDetail
        )
        TvHistoryRail(
            title = stringResource(R.string.nav_live_tv),
            items = live,
            onResume = onResume,
            onOpenDetail = onOpenDetail
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TvContinueWatchingHero(
    item: WatchHistoryItem,
    onResume: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val dimens = LocalImaxDimens.current
    val title = item.seriesName.ifBlank { item.title }
    val subtitle = continueWatchingSubtitle(item)
    val progress = item.progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(horizontal = dimens.screenPadding)
            .clip(RoundedCornerShape(28.dp))
            .background(ImaxColors.Surface)
    ) {
        PosterImage(
            url = item.posterUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ImaxColors.Background.copy(alpha = 0.94f),
                            ImaxColors.Background.copy(alpha = 0.62f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 40.dp, vertical = 32.dp)
                .width(500.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ImaxColors.Primary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = stringResource(R.string.section_continue_watching),
                    style = MaterialTheme.typography.labelLarge,
                    color = ImaxColors.Primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = ImaxColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = ImaxColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = continueWatchingProgressLabel(item),
                style = MaterialTheme.typography.bodyLarge,
                color = ImaxColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ImaxColors.SurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxSize()
                        .background(ImaxColors.Primary)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GradientButton(
                    text = stringResource(R.string.action_resume),
                    icon = Icons.Filled.PlayArrow,
                    onClick = onResume,
                    isTv = true
                )
                if (item.contentType != ContentType.LIVE) {
                    ImaxOutlinedButton(
                        text = stringResource(R.string.action_details),
                        onClick = onOpenDetail,
                        isTv = true
                    )
                }
            }
        }
    }
}

@Composable
private fun TvHistoryRail(
    title: String,
    items: List<WatchHistoryItem>,
    onResume: (WatchHistoryItem) -> Unit,
    onOpenDetail: (Long, String) -> Unit
) {
    if (items.isEmpty()) return

    val dimens = LocalImaxDimens.current

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = ImaxColors.TextPrimary,
        modifier = Modifier.padding(horizontal = dimens.screenPadding)
    )
    Spacer(modifier = Modifier.height(12.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        items(items, key = { "${it.contentType.name}-${it.id}" }) { item ->
            TvHistoryCard(
                item = item,
                onResume = { onResume(item) },
                onSecondaryAction = {
                    if (item.contentType == ContentType.LIVE) {
                        onResume(item)
                    } else {
                        onOpenDetail(item.contentId, item.contentType.name)
                    }
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun TvHistoryCard(
    item: WatchHistoryItem,
    onResume: () -> Unit,
    onSecondaryAction: () -> Unit
) {
    val dimens = LocalImaxDimens.current
    var isFocused by remember { mutableStateOf(false) }
    val progress = item.progress.coerceIn(0f, 1f)
    val title = item.seriesName.ifBlank { item.title }

    val tvFocusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        defaultSurface = ImaxColors.CardBackground,
        selectedSurface = ImaxColors.CardBackground,
        focusedSurface = ImaxColors.SurfaceElevated,
        selectedFocusedSurface = Color(0xFF5C3C2C)
    )

    val scale by animateFloatAsState(targetValue = tvFocusState.scale, animationSpec = tween(180), label = "tcScale")
    val borderWidth by animateDpAsState(targetValue = tvFocusState.borderWidth.coerceAtLeast(if (isFocused) dimens.focusBorderWidth else 0.dp), animationSpec = tween(180), label = "tcBorderWidth")
    val backgroundColor by animateColorAsState(targetValue = tvFocusState.backgroundColor, animationSpec = tween(180), label = "tcBackground")

    Column(
        modifier = Modifier
            .width(240.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = tvFocusState.shadowElevation,
                        shape = RoundedCornerShape(22.dp),
                        spotColor = tvFocusState.glowColor
                    )
                } else Modifier
            )
            .border(
                width = borderWidth,
                color = tvFocusState.borderColor.takeIf { isFocused } ?: Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onResume)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            PosterImage(
                url = item.posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ImaxColors.Primary.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_resume),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(ImaxColors.SurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxSize()
                    .background(ImaxColors.Primary)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ImaxColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = continueWatchingSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = ImaxColors.TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            ImaxOutlinedButton(
                text = if (item.contentType == ContentType.LIVE) stringResource(R.string.action_play) else stringResource(R.string.action_details),
                onClick = onSecondaryAction,
                isTv = true
            )
        }
    }
}

@Composable
fun TvFavoritesScreen(
    onNavigate: (String) -> Unit,
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit,
    viewModel: TvFavoritesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    ImaxDrawer(
        isExpanded = isDrawerExpanded,
        selectedRoute = Routes.FAVORITES,
        isTv = true,
        onToggle = { isDrawerExpanded = !isDrawerExpanded },
        onNavigate = onNavigate
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.movies.isEmpty() && state.series.isEmpty() && state.channels.isEmpty() -> {
                EmptyScreen(message = stringResource(R.string.no_content))
            }

            else -> TvFavoritesContent(
                state = state,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onPlayChannel = onPlayChannel
            )
        }
    }
}

@Composable
private fun TvFavoritesContent(
    state: TvFavoritesState,
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    val dimens = LocalImaxDimens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = dimens.screenPadding)
    ) {
        TvFavoritesHeader(state = state)
        Spacer(modifier = Modifier.height(28.dp))

        if (state.movies.isNotEmpty()) {
            TvMovieRail(
                title = stringResource(R.string.nav_movies),
                movies = state.movies,
                onMovieClick = onMovieClick
            )
        }

        if (state.series.isNotEmpty()) {
            TvSeriesRail(
                title = stringResource(R.string.nav_series),
                series = state.series,
                onSeriesClick = onSeriesClick
            )
        }

        if (state.channels.isNotEmpty()) {
            Text(
                text = stringResource(R.string.nav_live_tv),
                style = MaterialTheme.typography.headlineMedium,
                color = ImaxColors.TextPrimary,
                modifier = Modifier.padding(horizontal = dimens.screenPadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(state.channels, key = { it.id }) { channel ->
                    TvFavoriteChannelCard(
                        channel = channel,
                        onClick = {
                            onPlayChannel(
                                channel.streamUrl,
                                channel.name,
                                channel.id,
                                channel.groupTitle
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TvFavoritesHeader(
    state: TvFavoritesState
) {
    val dimens = LocalImaxDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TvStatCard(
            icon = Icons.Filled.Movie,
            label = stringResource(R.string.nav_movies),
            value = state.movies.size.toString()
        )
        TvStatCard(
            icon = Icons.Filled.Tv,
            label = stringResource(R.string.nav_series),
            value = state.series.size.toString()
        )
        TvStatCard(
            icon = Icons.Filled.LiveTv,
            label = stringResource(R.string.nav_live_tv),
            value = state.channels.size.toString()
        )
        TvStatCard(
            icon = Icons.Filled.Favorite,
            label = stringResource(R.string.favorites),
            value = (state.movies.size + state.series.size + state.channels.size).toString()
        )
    }
}

@Composable
private fun TvStatCard(
    icon: ImageVector,
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(24.dp),
        color = ImaxColors.Surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ImaxColors.Primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ImaxColors.Primary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ImaxColors.TextPrimary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = ImaxColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TvMovieRail(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Long) -> Unit
) {
    val dimens = LocalImaxDimens.current

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = ImaxColors.TextPrimary,
        modifier = Modifier.padding(horizontal = dimens.screenPadding)
    )
    Spacer(modifier = Modifier.height(12.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        items(movies, key = { it.id }) { movie ->
            ContentPosterCard(
                title = movie.name,
                posterUrl = movie.posterUrl,
                rating = movie.rating,
                year = movie.year,
                isTv = true,
                onClick = { onMovieClick(movie.id) }
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun TvSeriesRail(
    title: String,
    series: List<Series>,
    onSeriesClick: (Long) -> Unit
) {
    val dimens = LocalImaxDimens.current

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = ImaxColors.TextPrimary,
        modifier = Modifier.padding(horizontal = dimens.screenPadding)
    )
    Spacer(modifier = Modifier.height(12.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        items(series, key = { it.id }) { item ->
            ContentPosterCard(
                title = item.name,
                posterUrl = item.posterUrl,
                rating = item.rating,
                year = item.year,
                isTv = true,
                onClick = { onSeriesClick(item.id) }
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun TvFavoriteChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    val dimens = LocalImaxDimens.current
    var isFocused by remember { mutableStateOf(false) }

    val tvFocusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        defaultSurface = ImaxColors.CardBackground,
        selectedSurface = ImaxColors.CardBackground,
        focusedSurface = ImaxColors.SurfaceElevated,
        selectedFocusedSurface = Color(0xFF5C3C2C)
    )

    val scale by animateFloatAsState(targetValue = tvFocusState.scale, animationSpec = tween(180), label = "fcScale")
    val borderWidth by animateDpAsState(targetValue = tvFocusState.borderWidth.coerceAtLeast(if (isFocused) dimens.focusBorderWidth else 0.dp), animationSpec = tween(180), label = "fcBorder")
    val backgroundColor by animateColorAsState(targetValue = tvFocusState.backgroundColor, animationSpec = tween(180), label = "fcBackground")

    Column(
        modifier = Modifier
            .width(170.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = tvFocusState.shadowElevation,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = tvFocusState.glowColor
                    )
                } else Modifier
            )
            .border(
                width = borderWidth,
                color = tvFocusState.borderColor.takeIf { isFocused } ?: Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PosterImage(
            url = channel.logoUrl,
            contentDescription = channel.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ImaxColors.Surface)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            color = ImaxColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (channel.groupTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = channel.groupTitle,
                style = MaterialTheme.typography.bodySmall,
                color = ImaxColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun continueWatchingSubtitle(item: WatchHistoryItem): String {
    return when {
        item.contentType == ContentType.SERIES && item.seasonNumber > 0 -> {
            val episodeLabel = "S${item.seasonNumber}:E${item.episodeNumber}"
            if (item.title.isNotBlank() && item.title != item.seriesName) {
                "$episodeLabel  •  ${item.title}"
            } else {
                episodeLabel
            }
        }

        item.title.isNotBlank() -> item.title
        else -> ""
    }
}

private fun continueWatchingProgressLabel(item: WatchHistoryItem): String {
    return if (item.totalDuration > 0L) {
        "${StringUtils.formatDuration(item.position)} / ${StringUtils.formatDuration(item.totalDuration)}"
    } else if (item.position > 0L) {
        StringUtils.formatDuration(item.position)
    } else {
        ""
    }
}
