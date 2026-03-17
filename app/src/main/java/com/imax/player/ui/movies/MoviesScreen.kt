package com.imax.player.ui.movies

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.Movie
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

data class MoviesState(
    val movies: List<Movie> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist != null) {
                    launch {
                        contentRepository.getMovieCategories(playlist.id).collect { cats ->
                            _state.update { it.copy(categories = cats) }
                        }
                    }
                    launch {
                        contentRepository.getMovies(playlist.id).collect { movies ->
                            _state.update { it.copy(movies = movies, isLoading = false) }
                        }
                    }
                }
            }
        }
    }

    fun selectCategory(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
    }
}

@Composable
fun MoviesScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onMovieClick: (Long) -> Unit,
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        ImaxDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.MOVIES,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = { if (it == "exit") onNavigate(Routes.ONBOARDING) else onNavigate(it) }
        ) {
            if (state.isLoading) LoadingScreen()
            else TvMoviesContent(state, viewModel, onMovieClick)
        }
    } else {
        if (state.isLoading) LoadingScreen()
        else MobileMoviesContent(state, viewModel, onMovieClick)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV: Side category panel + grid
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun TvMoviesContent(
    state: MoviesState,
    viewModel: MoviesViewModel,
    onMovieClick: (Long) -> Unit
) {
    val dimens = LocalImaxDimens.current
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(dimens.categoryPanelWidth)
                .fillMaxHeight()
                .background(ImaxColors.Surface)
                .padding(vertical = 8.dp)
        ) {
            item {
                TvCategoryItem(
                    name = stringResource(R.string.category_all),
                    isSelected = state.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) }
                )
            }
            items(state.categories) { cat ->
                TvCategoryItem(
                    name = cat,
                    isSelected = state.selectedCategory == cat,
                    onClick = { viewModel.selectCategory(cat) }
                )
            }
        }

        val displayMovies = if (state.selectedCategory != null)
            state.movies.filter { it.categoryName == state.selectedCategory }
        else state.movies

        if (displayMovies.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(dimens.gridColumns),
                modifier = Modifier.weight(1f).padding(8.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(displayMovies) { movie ->
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
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile: Quick category bar + bottom sheet + grid
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MobileMoviesContent(
    state: MoviesState,
    viewModel: MoviesViewModel,
    onMovieClick: (Long) -> Unit
) {
    val dimens = LocalImaxDimens.current
    val config = LocalConfiguration.current
    val columns = if (config.screenWidthDp > 600) 4 else if (config.screenWidthDp > 400) 3 else 2
    var showCategorySheet by remember { mutableStateOf(false) }
    var recentCategories by remember { mutableStateOf(listOf<String>()) }
    var pinnedCategories by remember { mutableStateOf(listOf<String>()) }

    // Build category counts
    val categoryCounts = remember(state.movies) {
        state.movies.groupBy { it.categoryName }.mapValues { it.value.size }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        Text(
            stringResource(R.string.movies),
            style = MaterialTheme.typography.headlineMedium,
            color = ImaxColors.TextPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenPadding)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Quick category bar with "Browse All" button
        QuickCategoryBar(
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            recentCategories = recentCategories,
            onCategorySelected = { cat ->
                viewModel.selectCategory(cat)
                if (cat != null && cat !in recentCategories) {
                    recentCategories = (listOf(cat) + recentCategories).take(10)
                }
            },
            onBrowseAll = { showCategorySheet = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        val displayMovies = if (state.selectedCategory != null)
            state.movies.filter { it.categoryName == state.selectedCategory }
        else state.movies

        if (displayMovies.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(displayMovies) { movie ->
                    ContentPosterCard(
                        title = movie.name,
                        posterUrl = movie.posterUrl,
                        rating = movie.rating,
                        year = movie.year,
                        isTv = false,
                        onClick = { onMovieClick(movie.id) }
                    )
                }
            }
        }
    }

    // Category bottom sheet
    CategoryBottomSheet(
        isVisible = showCategorySheet,
        categories = state.categories,
        categoryCounts = categoryCounts,
        selectedCategory = state.selectedCategory,
        recentCategories = recentCategories,
        pinnedCategories = pinnedCategories,
        onCategorySelected = { cat ->
            viewModel.selectCategory(cat)
            if (cat != null && cat !in recentCategories) {
                recentCategories = (listOf(cat) + recentCategories).take(10)
            }
        },
        onDismiss = { showCategorySheet = false },
        onTogglePin = { cat ->
            pinnedCategories = if (cat in pinnedCategories) pinnedCategories - cat else pinnedCategories + cat
        }
    )
}
