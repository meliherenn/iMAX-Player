package com.imax.player.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.imax.player.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.*
import com.imax.player.data.repository.ContentRepository
import com.imax.player.metadata.MetadataResult
import com.imax.player.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val movie: Movie? = null,
    val series: Series? = null,
    val episodes: List<Episode> = emptyList(),
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val isLoadingEpisodes: Boolean = false,
    val isEnrichingMetadata: Boolean = false,
    val metadataEnriched: Boolean = false,
    val episodeError: String? = null,
    // Enriched metadata fields (available after TMDB fetch)
    val tagline: String = "",
    val trailerUrl: String = ""
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun loadContent(id: Long, type: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (type) {
                "MOVIE" -> {
                    val movie = contentRepository.getMovie(id)
                    _state.update { it.copy(movie = movie, isFavorite = movie?.isFavorite == true, isLoading = false) }
                    if (movie != null && needsMetadataEnrichment(movie)) {
                        enrichMovieMetadata(movie)
                    }
                }
                "SERIES" -> {
                    val series = contentRepository.getSeriesById(id)
                    _state.update { it.copy(series = series, isFavorite = series?.isFavorite == true) }
                    if (series != null) {
                        fetchSeriesEpisodes(series)
                        if (needsSeriesMetadataEnrichment(series)) {
                            enrichSeriesMetadata(series)
                        }
                        contentRepository.getSeasons(series.id).collect { seasons ->
                            _state.update { it.copy(seasons = seasons, isLoading = false) }
                            if (seasons.isNotEmpty()) loadEpisodes(series.id, seasons.first())
                        }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    private fun needsMetadataEnrichment(movie: Movie): Boolean {
        return movie.plot.isBlank() || movie.cast.isBlank() || movie.backdropUrl.isBlank()
    }

    private fun needsSeriesMetadataEnrichment(series: Series): Boolean {
        return series.plot.isBlank() || series.cast.isBlank() || series.backdropUrl.isBlank()
    }

    private fun enrichMovieMetadata(movie: Movie) {
        viewModelScope.launch {
            _state.update { it.copy(isEnrichingMetadata = true) }
            val metadata = contentRepository.enrichMetadata(movie.name, movie.year, ContentType.MOVIE)
            if (metadata != null) {
                contentRepository.updateMovieWithMetadata(movie.id, metadata)
                val updated = contentRepository.getMovie(movie.id)
                _state.update { 
                    it.copy(
                        movie = updated, 
                        isEnrichingMetadata = false, 
                        metadataEnriched = true,
                        tagline = metadata.tagline,
                        trailerUrl = metadata.trailerUrl
                    ) 
                }
            } else {
                _state.update { it.copy(isEnrichingMetadata = false) }
            }
        }
    }

    private fun enrichSeriesMetadata(series: Series) {
        viewModelScope.launch {
            _state.update { it.copy(isEnrichingMetadata = true) }
            val metadata = contentRepository.enrichMetadata(series.name, series.year, ContentType.SERIES)
            if (metadata != null) {
                contentRepository.updateSeriesWithMetadata(series.id, metadata)
                val updated = contentRepository.getSeriesById(series.id)
                _state.update { 
                    it.copy(
                        series = updated, 
                        isEnrichingMetadata = false, 
                        metadataEnriched = true,
                        tagline = metadata.tagline,
                        trailerUrl = metadata.trailerUrl
                    ) 
                }
            } else {
                _state.update { it.copy(isEnrichingMetadata = false) }
            }
        }
    }

    private fun fetchSeriesEpisodes(series: Series) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingEpisodes = true, episodeError = null) }
            try {
                contentRepository.syncSeriesEpisodes(series)
                _state.update { it.copy(isLoadingEpisodes = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingEpisodes = false, episodeError = "Failed to load episodes: ${e.localizedMessage}") }
            }
        }
    }

    fun retryEpisodes() {
        val series = _state.value.series ?: return
        fetchSeriesEpisodes(series)
    }

    fun selectSeason(season: Int) {
        _state.update { it.copy(selectedSeason = season) }
        val seriesId = _state.value.series?.id ?: return
        loadEpisodes(seriesId, season)
    }

    private fun loadEpisodes(seriesId: Long, season: Int) {
        viewModelScope.launch {
            contentRepository.getEpisodesBySeason(seriesId, season).collect { eps ->
                _state.update { it.copy(episodes = eps) }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val s = _state.value
            val newFav = !s.isFavorite
            _state.update { it.copy(isFavorite = newFav) }
            when {
                s.movie != null -> contentRepository.toggleMovieFavorite(s.movie.id, newFav)
                s.series != null -> contentRepository.toggleSeriesFavorite(s.series.id, newFav)
            }
        }
    }
}

@Composable
fun DetailScreen(
    contentId: Long,
    contentType: String,
    isTv: Boolean,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(contentId, contentType) {
        viewModel.loadContent(contentId, contentType)
    }

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    if (isTv) {
        TvDetailContent(state, viewModel, onBack, onPlay)
    } else {
        MobileDetailContent(state, viewModel, onBack, onPlay)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Detail — horizontal layout (UNTOUCHED)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun TvDetailContent(
    state: DetailState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit
) {
    val movie = state.movie
    val series = state.series
    val title = movie?.name ?: series?.name ?: ""
    val posterUrl = movie?.posterUrl ?: series?.posterUrl ?: ""
    val backdropUrl = movie?.backdropUrl ?: series?.backdropUrl ?: ""
    val plot = movie?.plot ?: series?.plot ?: ""
    val year = movie?.year ?: series?.year ?: 0
    val rating = movie?.rating ?: series?.rating ?: 0.0
    val genre = movie?.genre ?: series?.genre ?: ""
    val cast = movie?.cast ?: series?.cast ?: ""
    val director = movie?.director ?: series?.director ?: ""
    val dimens = LocalImaxDimens.current
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop
        if (backdropUrl.isNotBlank()) {
            PosterImage(url = backdropUrl, contentDescription = title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(400.dp))
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)
                .background(Brush.verticalGradient(listOf(ImaxColors.OverlayDark, ImaxColors.Background))))
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(dimens.screenPadding)) {
            // Back button
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ImaxColors.TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                PosterImage(url = posterUrl, contentDescription = title,
                    modifier = Modifier.width(dimens.posterWidth).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)))
                Spacer(modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.displaySmall, color = ImaxColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailMetaRow(year, rating, genre, movie?.duration ?: series?.episodeCount?.takeIf { it > 0 } ?: 0)
                    
                    if (plot.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(plot, style = MaterialTheme.typography.bodyLarge, color = ImaxColors.TextSecondary, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    } else if (state.isEnrichingMetadata) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.loading_details), style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextTertiary)
                    }

                    // Metadata loading indicator
                    if (state.isEnrichingMetadata) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ImaxColors.TextTertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.loading_details), style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailActionButtons(state, viewModel, onPlay)
                    if (cast.isNotBlank()) { Spacer(modifier = Modifier.height(12.dp)); DetailInfoSection(stringResource(R.string.cast), cast) }
                    if (director.isNotBlank()) { Spacer(modifier = Modifier.height(8.dp)); DetailInfoSection(stringResource(R.string.director), director) }
                }
            }

            // Episodes section for series
            if (series != null) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = ImaxColors.DividerColor)
                Spacer(modifier = Modifier.height(16.dp))

                if (state.isLoadingEpisodes) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ImaxColors.Primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.loading_episodes), style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextSecondary)
                    }
                } else if (state.seasons.isNotEmpty()) {
                    SeasonSelector(state.seasons, state.selectedSeason) { viewModel.selectSeason(it) }
                    Spacer(modifier = Modifier.height(12.dp))
                    state.episodes.forEach { ep ->
                        EpisodeRow(ep, true) { onPlay(ep.streamUrl, "${series.name} S${ep.seasonNumber}E${ep.episodeNumber}", ep.id, "SERIES", ep.lastPosition) }
                    }
                } else {
                    Column {
                        Text(state.episodeError ?: stringResource(R.string.no_episodes_available),
                            style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextTertiary)
                        if (state.episodeError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.retryEpisodes() }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile Detail — ENHANCED stacked vertical layout
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MobileDetailContent(
    state: DetailState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit
) {
    val movie = state.movie
    val series = state.series
    val title = movie?.name ?: series?.name ?: ""
    val posterUrl = movie?.posterUrl ?: series?.posterUrl ?: ""
    val backdropUrl = movie?.backdropUrl ?: series?.backdropUrl ?: ""
    val plot = movie?.plot ?: series?.plot ?: ""
    val year = movie?.year ?: series?.year ?: 0
    val rating = movie?.rating ?: series?.rating ?: 0.0
    val genre = movie?.genre ?: series?.genre ?: ""
    val cast = movie?.cast ?: series?.cast ?: ""
    val director = movie?.director ?: series?.director ?: ""
    val tagline = state.tagline
    val trailerUrl = state.trailerUrl
    val dimens = LocalImaxDimens.current
    val scrollState = rememberScrollState()
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp

    Box(modifier = Modifier.fillMaxSize()) {
        // ─── Backdrop with multi-stop gradient ───
        if (backdropUrl.isNotBlank()) {
            PosterImage(
                url = backdropUrl, 
                contentDescription = title, 
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(320.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.3f to ImaxColors.Background.copy(alpha = 0.3f),
                            0.6f to ImaxColors.Background.copy(alpha = 0.7f),
                            1.0f to ImaxColors.Background
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // ─── Top bar ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ImaxColors.TextPrimary)
                }
            }

            if (isLandscape) {
                // ─── Landscape: poster + info side by side ───
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding)) {
                    PosterImage(url = posterUrl, contentDescription = title,
                        modifier = Modifier.width(160.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium, color = ImaxColors.TextPrimary)
                        if (tagline.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(tagline, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), 
                                color = ImaxColors.TextTertiary, maxLines = 2)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailMetaRow(year, rating, genre, movie?.duration ?: series?.episodeCount?.takeIf { it > 0 } ?: 0)
                        // Overview
                        if (plot.isNotBlank()) { 
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpandableOverview(plot)
                        } else if (state.isEnrichingMetadata) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MetadataLoadingPlaceholder()
                        }
                        MetadataLoadingIndicator(state.isEnrichingMetadata)
                        Spacer(modifier = Modifier.height(12.dp))
                        DetailActionButtons(state, viewModel, onPlay, trailerUrl)
                    }
                }
            } else {
                // ─── Portrait: hero header ───
                Spacer(modifier = Modifier.height(if (backdropUrl.isNotBlank()) 160.dp else 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.screenPadding),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PosterImage(
                        url = posterUrl, 
                        contentDescription = title,
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title, 
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), 
                            color = ImaxColors.TextPrimary, 
                            maxLines = 3, 
                            overflow = TextOverflow.Ellipsis
                        )
                        // Tagline
                        if (tagline.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                tagline,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = ImaxColors.TextTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        DetailMetaRow(year, rating, genre, movie?.duration ?: series?.episodeCount?.takeIf { it > 0 } ?: 0)
                    }
                }

                // ─── Action Row ───
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.padding(horizontal = dimens.screenPadding)) {
                    DetailActionButtons(state, viewModel, onPlay, trailerUrl)
                }

                // ─── Overview / Description ───
                if (plot.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ExpandableOverview(
                        text = plot,
                        modifier = Modifier.padding(horizontal = dimens.screenPadding)
                    )
                } else if (state.isEnrichingMetadata) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MetadataLoadingPlaceholder(Modifier.padding(horizontal = dimens.screenPadding))
                }
                MetadataLoadingIndicator(state.isEnrichingMetadata, Modifier.padding(horizontal = dimens.screenPadding))
            }

            // ─── Metadata info sections ───
            // Always show these sections when data is available, regardless of other fields
            val sectionPadding = Modifier.padding(horizontal = dimens.screenPadding)

            // Genre chips (if not already shown in meta row, show as chips for better visibility)
            if (genre.isNotBlank() && genre.contains(",")) {
                Spacer(modifier = Modifier.height(16.dp))
                GenreChipsRow(genre, sectionPadding)
            }

            // Cast
            if (cast.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                DetailInfoSection(
                    label = stringResource(R.string.cast),
                    value = cast,
                    modifier = sectionPadding
                )
            }

            // Director / Creator
            if (director.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                DetailInfoSection(
                    label = stringResource(R.string.director),
                    value = director,
                    modifier = sectionPadding
                )
            }

            // ─── Rating display (when available and substantial) ───
            if (rating > 0 && plot.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                RatingBar(rating, sectionPadding)
            }

            // ─── Episodes section for series ───
            if (series != null) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = ImaxColors.DividerColor, modifier = sectionPadding)
                Spacer(modifier = Modifier.height(12.dp))

                if (state.isLoadingEpisodes) {
                    Row(
                        modifier = sectionPadding,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ImaxColors.Primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.loading_episodes), style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextSecondary)
                    }
                } else if (state.seasons.isNotEmpty()) {
                    SeasonSelector(state.seasons, state.selectedSeason, sectionPadding) { viewModel.selectSeason(it) }
                    Spacer(modifier = Modifier.height(8.dp))
                    state.episodes.forEach { ep ->
                        EpisodeRow(ep, false) { onPlay(ep.streamUrl, "${series.name} S${ep.seasonNumber}E${ep.episodeNumber}", ep.id, "SERIES", ep.lastPosition) }
                    }
                } else {
                    Column(modifier = sectionPadding) {
                        Text(state.episodeError ?: stringResource(R.string.no_episodes_available),
                            style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextTertiary)
                        if (state.episodeError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.retryEpisodes() }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Enhanced shared detail composables
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun ExpandableOverview(text: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val maxLines = if (expanded) Int.MAX_VALUE else 4
    
    Column(modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = ImaxColors.TextSecondary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        // Show expand/collapse only if text is likely longer than 4 lines
        if (text.length > 200) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) "Daralt ▲" else "Devamını Göster ▼",
                style = MaterialTheme.typography.labelMedium,
                color = ImaxColors.Primary,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}

@Composable
private fun MetadataLoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (it == 2) 0.6f else 1f)
                    .height(14.dp)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ImaxColors.SurfaceVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MetadataLoadingIndicator(isLoading: Boolean, modifier: Modifier = Modifier) {
    if (isLoading) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ImaxColors.TextTertiary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.loading_details), style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
        }
    }
}

@Composable
private fun DetailMetaRow(year: Int, rating: Double, genre: String, runtime: Int = 0) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp), 
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        if (year > 0) {
            Surface(shape = RoundedCornerShape(6.dp), color = ImaxColors.Primary.copy(alpha = 0.15f)) {
                Text(year.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
                    color = ImaxColors.Primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        if (rating > 0) {
            Surface(shape = RoundedCornerShape(6.dp), color = ImaxColors.RatingStarColor.copy(alpha = 0.15f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Filled.Star, 
                        contentDescription = null, 
                        tint = ImaxColors.RatingStarColor, 
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        String.format(java.util.Locale.US, "%.1f", rating), 
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
                        color = ImaxColors.RatingStarColor
                    )
                }
            }
        }
        if (runtime > 0) {
            Surface(shape = RoundedCornerShape(6.dp), color = ImaxColors.SurfaceVariant) {
                Text(
                    com.imax.player.core.common.StringUtils.formatDurationMinutes(runtime), 
                    style = MaterialTheme.typography.labelMedium, 
                    color = ImaxColors.TextSecondary, 
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        if (genre.isNotBlank()) {
            val shortGenre = genre.split(",").take(2).joinToString(", ") { it.trim() }
            Text(
                shortGenre, 
                style = MaterialTheme.typography.bodySmall, 
                color = ImaxColors.TextTertiary, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GenreChipsRow(genre: String, modifier: Modifier = Modifier) {
    val genres = genre.split(",").map { it.trim() }.filter { it.isNotBlank() }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(genres) { g ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ImaxColors.SurfaceVariant,
                border = BorderStroke(0.5.dp, ImaxColors.DividerColor)
            ) {
                Text(
                    g,
                    style = MaterialTheme.typography.labelSmall,
                    color = ImaxColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingBar(rating: Double, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val filledStars = (rating / 2).toInt()
        val halfStar = (rating / 2) - filledStars >= 0.5
        repeat(filledStars) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = ImaxColors.RatingStarColor, modifier = Modifier.size(18.dp))
        }
        if (halfStar) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.StarHalf, contentDescription = null, tint = ImaxColors.RatingStarColor, modifier = Modifier.size(18.dp))
        }
        repeat(5 - filledStars - if (halfStar) 1 else 0) {
            Icon(Icons.Filled.StarBorder, contentDescription = null, tint = ImaxColors.TextTertiary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            String.format(java.util.Locale.US, "%.1f / 10", rating),
            style = MaterialTheme.typography.labelMedium,
            color = ImaxColors.TextTertiary
        )
    }
}

@Composable
private fun DetailActionButtons(
    state: DetailState,
    viewModel: DetailViewModel,
    onPlay: (String, String, Long, String, Long) -> Unit,
    trailerUrl: String = ""
) {
    val movie = state.movie
    val series = state.series
    val context = LocalContext.current

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (movie != null) {
            GradientButton(
                text = if (movie.lastPosition > 0) stringResource(R.string.action_resume) else stringResource(R.string.action_play),
                icon = Icons.Filled.PlayArrow,
                onClick = { onPlay(movie.streamUrl, movie.name, movie.id, "MOVIE", movie.lastPosition) }
            )
        } else if (series != null && state.episodes.isNotEmpty()) {
            val ep = state.episodes.first()
            GradientButton(text = stringResource(R.string.action_play_episode, ep.seasonNumber, ep.episodeNumber), icon = Icons.Filled.PlayArrow,
                onClick = { onPlay(ep.streamUrl, "${series.name} S${ep.seasonNumber}E${ep.episodeNumber}", ep.id, "SERIES", ep.lastPosition) })
        }

        // Favorite button
        IconButton(onClick = { viewModel.toggleFavorite() }) {
            Icon(
                imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(if (state.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                tint = if (state.isFavorite) ImaxColors.Primary else ImaxColors.TextSecondary
            )
        }

        // Trailer button (when available)
        if (trailerUrl.isNotBlank()) {
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl))
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "Fragman",
                    tint = ImaxColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DetailInfoSection(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label, 
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
            color = ImaxColors.TextTertiary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value, 
            style = MaterialTheme.typography.bodyMedium, 
            color = ImaxColors.TextSecondary, 
            maxLines = 4, 
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SeasonSelector(seasons: List<Int>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(seasons) { season ->
            FilterChip(
                selected = season == selected,
                onClick = { onSelect(season) },
                label = { Text(stringResource(R.string.season_format, season)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f),
                    selectedLabelColor = ImaxColors.Primary
                )
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    isTv: Boolean,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val dimens = LocalImaxDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) ImaxColors.SurfaceVariant else ImaxColors.CardBackground)
            .then(if (isFocused) Modifier.border(1.dp, ImaxColors.FocusBorder, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onPlay)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.episode_format, episode.episodeNumber, episode.name),
                style = MaterialTheme.typography.titleSmall,
                color = ImaxColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (episode.plot.isNotBlank()) {
                Text(episode.plot, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (episode.duration > 0) {
                Text(com.imax.player.core.common.StringUtils.formatDurationMinutes(episode.duration),
                    style = MaterialTheme.typography.labelSmall, color = ImaxColors.TextTertiary)
            }
        }
        if (episode.lastPosition > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(4.dp), color = ImaxColors.Primary.copy(alpha = 0.2f)) {
                Text(stringResource(R.string.action_resume), style = MaterialTheme.typography.labelSmall, color = ImaxColors.Primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Filled.PlayCircle, stringResource(R.string.action_play), tint = ImaxColors.Primary, modifier = Modifier.size(28.dp))
    }
}
