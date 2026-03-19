package com.imax.player.ui.series

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.core.model.Series
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

data class SeriesState(
    val series: List<Series> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist != null) {
                    launch {
                        contentRepository.getSeriesCategories(playlist.id).collect { cats ->
                            _state.update { it.copy(categories = cats) }
                        }
                    }
                    launch {
                        contentRepository.getSeries(playlist.id).collect { series ->
                            _state.update { it.copy(series = series, isLoading = false) }
                        }
                    }
                }
            }
        }
    }

    fun selectCategory(cat: String?) = _state.update { it.copy(selectedCategory = cat) }
}

@Composable
fun SeriesScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onSeriesClick: (Long) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        ImaxDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.SERIES,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = { if (it == "exit") onNavigate(Routes.ONBOARDING) else onNavigate(it) }
        ) {
            if (state.isLoading) LoadingScreen()
            else TvSeriesContent(state, viewModel, onSeriesClick)
        }
    } else {
        if (state.isLoading) LoadingScreen()
        else MobileSeriesContent(state, viewModel, onSeriesClick)
    }
}

@Composable
private fun TvSeriesContent(
    state: SeriesState,
    viewModel: SeriesViewModel,
    onSeriesClick: (Long) -> Unit
) {
    val dimens = LocalImaxDimens.current
    Row(modifier = Modifier.fillMaxSize()) {
        TvCategoryPanel {
            item {
                TvRailCategoryItem(name = stringResource(R.string.category_all), isSelected = state.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) })
            }
            items(state.categories) { cat ->
                TvRailCategoryItem(name = cat, isSelected = state.selectedCategory == cat,
                    onClick = { viewModel.selectCategory(cat) })
            }
        }

        val display = if (state.selectedCategory != null) state.series.filter { it.categoryName == state.selectedCategory } else state.series

        if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(dimens.gridColumns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 18.dp, end = 20.dp, bottom = 18.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(display) { s ->
                    ContentPosterCard(title = s.name, posterUrl = s.posterUrl, rating = s.rating, year = s.year, isTv = true,
                        onClick = { onSeriesClick(s.id) })
                }
            }
        }
    }
}

@Composable
private fun MobileSeriesContent(
    state: SeriesState,
    viewModel: SeriesViewModel,
    onSeriesClick: (Long) -> Unit
) {
    val dimens = LocalImaxDimens.current
    val config = LocalConfiguration.current
    val columns = if (config.screenWidthDp > 600) 4 else if (config.screenWidthDp > 400) 3 else 2
    var showCategorySheet by remember { mutableStateOf(false) }
    var recentCategories by remember { mutableStateOf(listOf<String>()) }
    var pinnedCategories by remember { mutableStateOf(listOf<String>()) }

    val categoryCounts = remember(state.series) {
        state.series.groupBy { it.categoryName }.mapValues { it.value.size }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        Text(stringResource(R.string.series), style = MaterialTheme.typography.headlineMedium, color = ImaxColors.TextPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenPadding))
        Spacer(modifier = Modifier.height(12.dp))

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

        val display = if (state.selectedCategory != null) state.series.filter { it.categoryName == state.selectedCategory } else state.series

        if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(display) { s ->
                    ContentPosterCard(title = s.name, posterUrl = s.posterUrl, rating = s.rating, year = s.year, isTv = false,
                        onClick = { onSeriesClick(s.id) })
                }
            }
        }
    }

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
