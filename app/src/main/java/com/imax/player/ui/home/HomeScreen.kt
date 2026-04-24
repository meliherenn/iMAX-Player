package com.imax.player.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import com.imax.player.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imax.player.core.common.StringUtils
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.*
import com.imax.player.ui.components.*
import com.imax.player.ui.navigation.Routes
import java.util.Locale

@Composable
fun HomeScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (isTv) {
        ImaxDrawer(
            isExpanded = false,
            selectedRoute = Routes.HOME,
            isTv = true,
            onToggle = {},
            onNavigate = { route ->
                if (route == "exit") onNavigate(Routes.ONBOARDING) else onNavigate(route)
            }
        ) {
            TvHomeContent(
                state = state,
                isLoading = state.isLoading,
                onContentClick = onContentClick,
                onPlayContent = onPlayContent
            ) { viewModel.selectContent(it) }
        }
    } else {
        if (state.isLoading) {
            LoadingScreen()
        } else {
            MobileHomeContent(state, onContentClick, onPlayContent) { viewModel.selectContent(it) }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Home — rail-based landscape layout
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun TvHomeContent(
    state: HomeState,
    isLoading: Boolean,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit,
    onFocusChange: (Any?) -> Unit
) {
    val dimens = LocalImaxDimens.current
    val latestMovies = remember(state.recentMovies, state.allMovies) {
        state.recentMovies
            .ifEmpty { state.allMovies }
            .take(18)
    }
    val latestSeries = remember(state.allSeries) {
        state.allSeries
            .take(18)
    }
    val favoriteMovies = remember(state.favoriteMovies) { state.favoriteMovies.take(18) }
    val recentChannels = remember(state.recentChannels) { state.recentChannels.take(16) }
    val hasVisibleContent = remember(
        state.continueWatching,
        latestMovies,
        latestSeries,
        recentChannels,
        favoriteMovies
    ) {
        state.continueWatching.isNotEmpty() ||
            latestMovies.isNotEmpty() ||
            latestSeries.isNotEmpty() ||
            recentChannels.isNotEmpty() ||
            favoriteMovies.isNotEmpty()
    }
    val backdropUrl = remember(state.selectedContent, latestMovies) {
        when (val selected = state.selectedContent) {
            is Movie -> selected.backdropUrl.ifBlank { selected.posterUrl }
            is Series -> selected.backdropUrl.ifBlank { selected.posterUrl }
            else -> latestMovies.firstOrNull()?.backdropUrl
                ?.ifBlank { latestMovies.firstOrNull()?.posterUrl.orEmpty() }
                .orEmpty()
        }
    }
    var showBackdrop by remember(backdropUrl) { mutableStateOf(false) }

    LaunchedEffect(backdropUrl) {
        showBackdrop = false
        if (backdropUrl.isNotBlank()) {
            withFrameNanos {
                showBackdrop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showBackdrop && backdropUrl.isNotBlank()) {
            PosterImage(
                url = backdropUrl,
                contentDescription = "Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .blur(14.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Brush.verticalGradient(listOf(ImaxColors.OverlayDark, ImaxColors.Background)))
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading && !hasVisibleContent) {
                item(key = "tv-home-loading") {
                    TvHomeLoadingState()
                }
            }

            if (state.continueWatching.isNotEmpty()) {
                item(key = "tv-home-continue-watching") {
                    ContentRail(stringResource(R.string.section_continue_watching), dimens.screenPadding) {
                        items(
                            items = state.continueWatching,
                            key = { "${it.contentType.name}-${it.id}" }
                        ) { item ->
                            ContinueWatchingCard(item, true,
                                onClick = { onPlayContent(item.streamUrl, item.title, item.contentId, item.contentType.name, item.position) },
                                onFocus = { onFocusChange(item) })
                        }
                    }
                }
            }
            if (latestMovies.isNotEmpty()) {
                item(key = "tv-home-movies") {
                    ContentRail(stringResource(R.string.nav_movies), dimens.screenPadding) {
                        items(latestMovies, key = { it.id }) { movie ->
                            ContentPosterCard(title = movie.name, posterUrl = movie.posterUrl, rating = movie.rating, year = movie.year, isTv = true,
                                onClick = { onContentClick(movie.id, "MOVIE") })
                        }
                    }
                }
            }
            if (latestSeries.isNotEmpty()) {
                item(key = "tv-home-series") {
                    ContentRail(stringResource(R.string.nav_series), dimens.screenPadding) {
                        items(latestSeries, key = { it.id }) { series ->
                            ContentPosterCard(title = series.name, posterUrl = series.posterUrl, rating = series.rating, year = series.year, isTv = true,
                                onClick = { onContentClick(series.id, "SERIES") })
                        }
                    }
                }
            }
            if (recentChannels.isNotEmpty()) {
                item(key = "tv-home-recent-channels") {
                    ContentRail(stringResource(R.string.section_recent_channels), dimens.screenPadding) {
                        items(recentChannels, key = { it.id }) { ch ->
                            ChannelCard(ch, true,
                                onClick = { onPlayContent(ch.streamUrl, ch.name, ch.id, "LIVE", 0) },
                                onFocus = { onFocusChange(ch) })
                        }
                    }
                }
            }
            if (favoriteMovies.isNotEmpty()) {
                item(key = "tv-home-favorites") {
                    ContentRail(stringResource(R.string.section_favorites), dimens.screenPadding) {
                        items(favoriteMovies, key = { it.id }) { movie ->
                            ContentPosterCard(title = movie.name, posterUrl = movie.posterUrl, rating = movie.rating, year = movie.year, isTv = true,
                                onClick = { onContentClick(movie.id, "MOVIE") })
                        }
                    }
                }
            }
            item(key = "tv-home-footer-space") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TvHomeLoadingState() {
    val dimens = LocalImaxDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_home),
            style = MaterialTheme.typography.headlineMedium,
            color = ImaxColors.TextPrimary
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = ImaxColors.Primary,
            trackColor = ImaxColors.SurfaceVariant
        )
        repeat(2) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile Home — portrait-first vertical layout
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MobileHomeContent(
    state: HomeState,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit,
    onFocusChange: (Any?) -> Unit
) {
    val dimens = LocalImaxDimens.current
    val scrollState = rememberScrollState()
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp

    Box(modifier = Modifier.fillMaxSize()) {
        // Dynamic backdrop
        val backdropUrl = when (val selected = state.selectedContent) {
            is Movie -> selected.backdropUrl.ifBlank { selected.posterUrl }
            is Series -> selected.backdropUrl.ifBlank { selected.posterUrl }
            else -> state.allMovies.firstOrNull()?.posterUrl ?: ""
        }
        if (backdropUrl.isNotBlank()) {
            PosterImage(
                url = backdropUrl,
                contentDescription = "Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(300.dp).blur(20.dp)
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(300.dp)
                    .background(Brush.verticalGradient(listOf(ImaxColors.OverlayDark, ImaxColors.Background)))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
                .padding(top = dimens.screenPadding, bottom = 8.dp)
        ) {
            // Mobile hero banner — stacked layout for portrait
            MobileHeroBanner(state, isLandscape, onPlayContent, onContentClick)
            Spacer(modifier = Modifier.height(dimens.sectionSpacing))

            if (state.continueWatching.isNotEmpty()) {
                ContentRail(stringResource(R.string.section_continue_watching), dimens.screenPadding) {
                    items(state.continueWatching) { item ->
                        ContinueWatchingCard(item, false,
                            onClick = { onPlayContent(item.streamUrl, item.title, item.contentId, item.contentType.name, item.position) },
                            onFocus = { onFocusChange(item) })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (state.recentChannels.isNotEmpty()) {
                ContentRail(stringResource(R.string.section_recent_channels), dimens.screenPadding) {
                    items(state.recentChannels) { ch ->
                        ChannelCard(ch, false,
                            onClick = { onPlayContent(ch.streamUrl, ch.name, ch.id, "LIVE", 0) },
                            onFocus = { onFocusChange(ch) })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (state.allMovies.isNotEmpty()) {
                ContentRail(stringResource(R.string.nav_movies), dimens.screenPadding) {
                    items(state.allMovies) { movie ->
                        ContentPosterCard(title = movie.name, posterUrl = movie.posterUrl, rating = movie.rating, year = movie.year, isTv = false,
                            onClick = { onContentClick(movie.id, "MOVIE") })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (state.favoriteMovies.isNotEmpty()) {
                ContentRail(stringResource(R.string.section_favorites), dimens.screenPadding) {
                    items(state.favoriteMovies) { movie ->
                        ContentPosterCard(title = movie.name, posterUrl = movie.posterUrl, rating = movie.rating, year = movie.year, isTv = false,
                            onClick = { onContentClick(movie.id, "MOVIE") })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (state.allSeries.isNotEmpty()) {
                ContentRail(stringResource(R.string.nav_series), dimens.screenPadding) {
                    items(state.allSeries) { series ->
                        ContentPosterCard(title = series.name, posterUrl = series.posterUrl, rating = series.rating, year = series.year, isTv = false,
                            onClick = { onContentClick(series.id, "SERIES") })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Hero Banner — horizontal Row layout (landscape-first)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun TvHeroBanner(
    state: HomeState,
    onPlay: (String, String, Long, String, Long) -> Unit,
    onDetail: (Long, String) -> Unit
) {
    val featured = state.allMovies.firstOrNull() ?: return
    val dimens = LocalImaxDimens.current

    Box(
        modifier = Modifier.fillMaxWidth().height(dimens.bannerHeight)
            .padding(horizontal = dimens.screenPadding)
    ) {
        GlassCard(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                PosterImage(
                    url = featured.posterUrl,
                    contentDescription = featured.name,
                    modifier = Modifier.width(dimens.posterWidth).fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                )
                Column(
                    modifier = Modifier.weight(1f).padding(start = 16.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = featured.name, style = MaterialTheme.typography.headlineMedium,
                        color = ImaxColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (featured.year > 0) Text(featured.year.toString(), style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextSecondary)
                        if (featured.rating > 0) Text("⭐ ${String.format(Locale.getDefault(), "%.1f", featured.rating)}", style = MaterialTheme.typography.bodyMedium, color = ImaxColors.RatingStarColor)
                        if (featured.genre.isNotBlank()) Text(featured.genre.take(30), style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextSecondary)
                    }
                    if (featured.plot.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(featured.plot, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradientButton(text = stringResource(R.string.action_play), icon = Icons.Filled.PlayArrow,
                            onClick = { onPlay(featured.streamUrl, featured.name, featured.id, "MOVIE", featured.lastPosition) })
                        ImaxOutlinedButton(
                            text = stringResource(R.string.action_details),
                            icon = Icons.Filled.Info,
                            onClick = { onDetail(featured.id, "MOVIE") }
                        )
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile Hero Banner — stacked Column for portrait, Row for landscape
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MobileHeroBanner(
    state: HomeState,
    isLandscape: Boolean,
    onPlay: (String, String, Long, String, Long) -> Unit,
    onDetail: (Long, String) -> Unit
) {
    val featured = state.allMovies.firstOrNull() ?: return
    val dimens = LocalImaxDimens.current

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            if (isLandscape) {
                // Landscape: horizontal layout
                Row(modifier = Modifier.fillMaxWidth()) {
                    PosterImage(
                        url = featured.posterUrl,
                        contentDescription = featured.name,
                        modifier = Modifier.width(140.dp).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    MobileHeroBannerInfo(featured, onPlay, onDetail)
                }
            } else {
                // Portrait: stacked vertical layout
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Backdrop image
                    PosterImage(
                        url = featured.backdropUrl.ifBlank { featured.posterUrl },
                        contentDescription = featured.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MobileHeroBannerInfo(featured, onPlay, onDetail)
                }
            }
        }
    }
}

@Composable
private fun MobileHeroBannerInfo(
    featured: Movie,
    onPlay: (String, String, Long, String, Long) -> Unit,
    onDetail: (Long, String) -> Unit
) {
    Column {
        Text(text = featured.name, style = MaterialTheme.typography.titleLarge,
            color = ImaxColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (featured.year > 0) Text(featured.year.toString(), style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextSecondary)
            if (featured.rating > 0) Text("⭐ ${String.format(Locale.getDefault(), "%.1f", featured.rating)}", style = MaterialTheme.typography.bodySmall, color = ImaxColors.RatingStarColor)
        }
        if (featured.plot.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(featured.plot, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(text = stringResource(R.string.action_play), icon = Icons.Filled.PlayArrow,
                onClick = { onPlay(featured.streamUrl, featured.name, featured.id, "MOVIE", featured.lastPosition) })
            OutlinedButton(
                onClick = { onDetail(featured.id, "MOVIE") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ImaxColors.TextPrimary),
                border = BorderStroke(1.dp, ImaxColors.GlassBorder)
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_details), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Shared composables
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun ContentRail(
    title: String,
    horizontalPad: androidx.compose.ui.unit.Dp,
    itemContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val dimens = LocalImaxDimens.current
    SectionHeader(title = title, modifier = Modifier.padding(horizontal = horizontalPad))
    LazyRow(
        modifier = Modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = horizontalPad),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
        content = { itemContent() }
    )
}

@Composable
private fun ContinueWatchingCard(
    item: WatchHistoryItem,
    isTv: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val dimens = LocalImaxDimens.current
    val interactionSource = remember(item.id, item.contentId, item.contentType) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused, item.id, item.contentId, item.contentType) {
        if (isFocused) onFocus()
    }
    val width = if (isTv) 240.dp else 184.dp
    val title = remember(item) { item.seriesName.ifBlank { item.title } }
    val subtitle = remember(item) { continueWatchingSubtitle(item) }
    val progressLabel = remember(item) { continueWatchingProgressLabel(item) }
    val progress = item.progress.coerceIn(0f, 1f)
    
    val tvFocusState = if (isTv) {
        rememberTvFocusVisualState(
            isFocused = isFocused,
            defaultSurface = ImaxColors.CardBackground,
            selectedSurface = ImaxColors.CardBackground,
            focusedSurface = ImaxColors.SurfaceElevated,
            selectedFocusedSurface = Color(0xFF5C3C2C)
        )
    } else null

    val scale by animateFloatAsState(
        targetValue = tvFocusState?.scale ?: if (isFocused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "continueWatchingScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = tvFocusState?.borderWidth ?: if (isFocused) dimens.focusBorderWidth else 0.dp,
        animationSpec = tween(180),
        label = "continueWatchingBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = tvFocusState?.backgroundColor ?: if (isFocused) ImaxColors.SurfaceVariant else ImaxColors.CardBackground,
        animationSpec = tween(180),
        label = "continueWatchingBackground"
    )
    val borderColor = tvFocusState?.borderColor ?: ImaxColors.FocusBorder

    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(dimens.borderRadius))
            .background(backgroundColor)
            .then(
                if (isTv && isFocused && tvFocusState != null) {
                    Modifier.shadow(
                        elevation = tvFocusState.shadowElevation,
                        shape = RoundedCornerShape(dimens.borderRadius),
                        spotColor = tvFocusState.glowColor
                    )
                } else {
                    Modifier
                }
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(dimens.borderRadius)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTv) null else LocalIndication.current,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            PosterImage(url = item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )
            if (item.contentType == ContentType.SERIES && item.seasonNumber > 0) {
                Surface(
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "S${item.seasonNumber}:E${item.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Surface(
                modifier = Modifier.padding(8.dp).align(Alignment.BottomStart),
                shape = RoundedCornerShape(999.dp),
                color = ImaxColors.Primary.copy(alpha = 0.92f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
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
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .align(Alignment.BottomCenter).background(ImaxColors.SurfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(ImaxColors.Primary))
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = ImaxColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ImaxColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ImaxColors.TextSecondary
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ImaxColors.Primary
                )
            }
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

@Composable
private fun ChannelCard(
    channel: Channel,
    isTv: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val dimens = LocalImaxDimens.current
    val interactionSource = remember(channel.id) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused, channel.id) {
        if (isFocused) onFocus()
    }

    val tvFocusState = if (isTv) {
        rememberTvFocusVisualState(
            isFocused = isFocused,
            defaultSurface = ImaxColors.CardBackground,
            selectedSurface = ImaxColors.CardBackground,
            focusedSurface = ImaxColors.SurfaceElevated,
            selectedFocusedSurface = Color(0xFF5C3C2C)
        )
    } else null

    val scale by animateFloatAsState(
        targetValue = tvFocusState?.scale ?: if (isFocused) 1.02f else 1f,
        animationSpec = tween(180),
        label = "channelScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = tvFocusState?.borderWidth ?: if (isFocused) dimens.focusBorderWidth else 0.dp,
        animationSpec = tween(180),
        label = "channelBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = tvFocusState?.backgroundColor ?: if (isFocused) ImaxColors.SurfaceVariant else ImaxColors.CardBackground,
        animationSpec = tween(180),
        label = "channelBackground"
    )
    val borderColor = tvFocusState?.borderColor ?: ImaxColors.FocusBorder

    Box(
        modifier = Modifier
            .width(if (isTv) 160.dp else 110.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.borderRadius))
                .background(backgroundColor)
                .then(
                    if (isTv && isFocused && tvFocusState != null) {
                        Modifier.shadow(
                            elevation = tvFocusState.shadowElevation,
                            shape = RoundedCornerShape(dimens.borderRadius),
                            spotColor = ImaxColors.Primary.copy(alpha = 0.75f)
                        )
                    } else {
                        Modifier
                    }
                )
                .border(
                    width = if (isTv && isFocused) 4.dp else borderWidth,
                    color = if (isTv && isFocused) ImaxColors.Primary else borderColor,
                    shape = RoundedCornerShape(dimens.borderRadius)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = if (isTv) null else LocalIndication.current,
                    onClick = onClick
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PosterImage(
                url = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(if (isTv) 64.dp else 48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isTv && isFocused) Color.White else ImaxColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (isTv && isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .width(7.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ImaxColors.Primary)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ImaxColors.Primary)
            )
        }
    }
}
