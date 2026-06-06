package com.imax.player.ui.search

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.common.Constants
import com.imax.player.core.common.SearchMatcher
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.*
import com.imax.player.data.repository.ContentRepository
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.components.*
import com.imax.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.imax.player.R

private const val MIN_SEARCH_QUERY_LENGTH = 1
private const val MAX_COMBINED_SEARCH_RESULTS = 120

private data class RankedSearchResult(
    val result: SearchResult,
    val score: Double
)

private data class SearchRequest(
    val query: String,
    val filter: ContentType?,
    val playlistId: Long?
)

data class SearchState(
    val query: String = "",
    val contentTypeFilter: ContentType? = null,
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _queryFlow = MutableStateFlow("")
    private val _filterFlow = MutableStateFlow<ContentType?>(null)
    private val activePlaylist = playlistRepository.getActivePlaylist()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            combine(
                _queryFlow
                    .debounce(Constants.SEARCH_DEBOUNCE_MS)
                    .map { it.trim() }
                    .distinctUntilChanged(),
                _filterFlow,
                activePlaylist
            ) { query, filter, playlist ->
                SearchRequest(query = query, filter = filter, playlistId = playlist?.id)
            }
                .collectLatest { request ->
                    if (request.query.length >= MIN_SEARCH_QUERY_LENGTH && request.playlistId != null) {
                        performSearch(request.query, request.filter, request.playlistId)
                    } else {
                        _state.update { it.copy(results = emptyList(), isSearching = false) }
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        val isSearchable = query.trim().length >= MIN_SEARCH_QUERY_LENGTH
        _state.update {
            it.copy(
                query = query,
                results = if (isSearchable) it.results else emptyList(),
                isSearching = isSearchable
            )
        }
        _queryFlow.value = query
    }

    fun setFilter(type: ContentType?) {
        if (_state.value.contentTypeFilter == type) return
        _filterFlow.value = type
        _state.update {
            it.copy(
                contentTypeFilter = type,
                isSearching = it.query.trim().length >= MIN_SEARCH_QUERY_LENGTH
            )
        }
    }

    private suspend fun performSearch(query: String, filter: ContentType?, playlistId: Long) = coroutineScope {
        val startedAt = SystemClock.elapsedRealtime()
        val results = mutableListOf<RankedSearchResult>()
        val allowFullScanFallback = filter != null

        if (filter == null || filter == ContentType.LIVE) {
            addChannelResults(
                results = results,
                channels = contentRepository.searchChannelsNow(
                    playlistId = playlistId,
                    query = query,
                    allowFullScanFallback = allowFullScanFallback
                ),
                query = query
            )
            if (filter == null && results.isNotEmpty()) {
                publishSearchResults(results, isSearching = true)
            }
        }

        val movies = if (filter == null || filter == ContentType.MOVIE) {
            async {
                contentRepository.searchMoviesNow(
                    playlistId = playlistId,
                    query = query,
                    allowFullScanFallback = allowFullScanFallback
                )
            }
        } else {
            null
        }
        val series = if (filter == null || filter == ContentType.SERIES) {
            async {
                contentRepository.searchSeriesNow(
                    playlistId = playlistId,
                    query = query,
                    allowFullScanFallback = allowFullScanFallback
                )
            }
        } else {
            null
        }

        movies?.await()?.let { addMovieResults(results, it, query) }
        series?.await()?.let { addSeriesResults(results, it, query) }

        val sortedResults = publishSearchResults(results, isSearching = false)
        Timber.d(
            "Search completed length=${query.length}, filter=$filter, results=${sortedResults.size}, elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
        )
    }

    private fun publishSearchResults(
        results: List<RankedSearchResult>,
        isSearching: Boolean
    ): List<SearchResult> {
        val sortedResults = results
            .sortedWith(
                compareByDescending<RankedSearchResult> { it.score }
                    .thenBy { it.result.title.lowercase(Locale.ENGLISH) }
            )
            .take(MAX_COMBINED_SEARCH_RESULTS)
            .map { it.result }

        _state.update { it.copy(results = sortedResults, isSearching = isSearching) }
        return sortedResults
    }

    private fun addChannelResults(
        results: MutableList<RankedSearchResult>,
        channels: List<Channel>,
        query: String
    ) {
        channels.mapTo(results) {
            RankedSearchResult(
                result = SearchResult(it.id, it.name, it.logoUrl, ContentType.LIVE, it.groupTitle, streamUrl = it.streamUrl),
                score = SearchMatcher.score(query, it.name, listOf(it.groupTitle, it.epgChannelId))
            )
        }
    }

    private fun addMovieResults(
        results: MutableList<RankedSearchResult>,
        movies: List<Movie>,
        query: String
    ) {
        movies.mapTo(results) {
            RankedSearchResult(
                result = SearchResult(it.id, it.name, it.posterUrl, ContentType.MOVIE, it.genre, it.year, it.rating, it.streamUrl),
                score = SearchMatcher.score(query, it.name, listOf(it.categoryName, it.genre, it.releaseDate), it.year)
            )
        }
    }

    private fun addSeriesResults(
        results: MutableList<RankedSearchResult>,
        series: List<Series>,
        query: String
    ) {
        series.mapTo(results) {
            RankedSearchResult(
                result = SearchResult(it.id, it.name, it.posterUrl, ContentType.SERIES, it.genre, it.year, it.rating),
                score = SearchMatcher.score(query, it.name, listOf(it.categoryName, it.genre, it.releaseDate), it.year)
            )
        }
    }
}

@Composable
fun SearchScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        ImaxDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.SEARCH,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = onNavigate
        ) {
            TvSearchContent(state, viewModel, onContentClick, onPlayContent)
        }
    } else {
        MobileSearchContent(state, viewModel, onContentClick, onPlayContent)
    }
}

@Composable
private fun TvSearchContent(
    state: SearchState,
    viewModel: SearchViewModel,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    val dimens = LocalImaxDimens.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { searchFocusRequester.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(dimens.screenPadding)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.updateQuery(it) },
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(onClick = { viewModel.updateQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ImaxColors.Primary,
                unfocusedBorderColor = ImaxColors.CardBorder,
                focusedLabelColor = ImaxColors.Primary,
                cursorColor = ImaxColors.Primary,
                focusedTextColor = ImaxColors.TextPrimary,
                unfocusedTextColor = ImaxColors.TextPrimary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Spacer(modifier = Modifier.height(12.dp))
        SearchFilterChips(state, viewModel)
        Spacer(modifier = Modifier.height(12.dp))
        SearchResults(state, dimens.gridColumns, true, onContentClick, onPlayContent)
    }
}

@Composable
private fun MobileSearchContent(
    state: SearchState,
    viewModel: SearchViewModel,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    val dimens = LocalImaxDimens.current
    val config = LocalConfiguration.current
    val columns = if (config.screenWidthDp > 600) 4 else if (config.screenWidthDp > 400) 3 else 2
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineMedium, color = ImaxColors.TextPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenPadding))
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.updateQuery(it) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(onClick = { viewModel.updateQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding)
                .focusRequester(searchFocusRequester),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ImaxColors.Primary,
                unfocusedBorderColor = ImaxColors.CardBorder,
                focusedTextColor = ImaxColors.TextPrimary,
                unfocusedTextColor = ImaxColors.TextPrimary,
                cursorColor = ImaxColors.Primary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
        Spacer(modifier = Modifier.height(10.dp))
        SearchFilterChips(state, viewModel, Modifier.padding(horizontal = dimens.screenPadding))
        Spacer(modifier = Modifier.height(8.dp))
        SearchResults(state, columns, false, onContentClick, onPlayContent)
    }
}

@Composable
private fun SearchFilterChips(
    state: SearchState,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = state.contentTypeFilter == null, onClick = { viewModel.setFilter(null) },
                label = { Text(stringResource(R.string.category_all)) }, colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f), selectedLabelColor = ImaxColors.Primary))
        }
        item {
            FilterChip(selected = state.contentTypeFilter == ContentType.LIVE, onClick = { viewModel.setFilter(ContentType.LIVE) },
                label = { Text(stringResource(R.string.live_tv)) }, colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f), selectedLabelColor = ImaxColors.Primary))
        }
        item {
            FilterChip(selected = state.contentTypeFilter == ContentType.MOVIE, onClick = { viewModel.setFilter(ContentType.MOVIE) },
                label = { Text(stringResource(R.string.movies)) }, colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f), selectedLabelColor = ImaxColors.Primary))
        }
        item {
            FilterChip(selected = state.contentTypeFilter == ContentType.SERIES, onClick = { viewModel.setFilter(ContentType.SERIES) },
                label = { Text(stringResource(R.string.series)) }, colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f), selectedLabelColor = ImaxColors.Primary))
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    columns: Int,
    isTv: Boolean,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    val dimens = LocalImaxDimens.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isSearching && state.results.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding),
                color = ImaxColors.Primary,
                trackColor = ImaxColors.SurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        when {
            state.isSearching && state.results.isEmpty() -> LoadingScreen()
            state.query.isNotBlank() && state.results.isEmpty() -> EmptyScreen(stringResource(R.string.no_results))
            state.query.isBlank() -> EmptyScreen(stringResource(R.string.search_hint))
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                    verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
                ) {
                    items(state.results, key = { "${it.contentType.name}-${it.id}" }) { result ->
                        ContentPosterCard(
                            title = result.title,
                            posterUrl = result.posterUrl,
                            rating = result.rating,
                            year = result.year,
                            isTv = isTv,
                            onClick = {
                                when (result.contentType) {
                                    ContentType.LIVE -> onPlayContent(result.streamUrl, result.title, result.id, "LIVE")
                                    else -> onContentClick(result.id, result.contentType.name)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
