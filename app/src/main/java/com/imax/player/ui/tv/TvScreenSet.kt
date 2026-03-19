package com.imax.player.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.imax.player.ui.components.ImaxDrawer
import com.imax.player.ui.details.DetailScreen
import com.imax.player.ui.home.HomeScreen
import com.imax.player.ui.live.LiveTvScreen
import com.imax.player.ui.movies.MoviesScreen
import com.imax.player.ui.onboarding.OnboardingScreen
import com.imax.player.ui.search.SearchScreen
import com.imax.player.ui.series.SeriesScreen
import com.imax.player.ui.settings.SettingsScreen
import com.imax.player.ui.navigation.Routes

@Composable
fun TvPlaylistsScreen(
    onNavigate: (String) -> Unit,
    onPlaylistSelected: () -> Unit
) {
    var isDrawerExpanded by remember { mutableStateOf(true) }

    ImaxDrawer(
        isExpanded = isDrawerExpanded,
        selectedRoute = Routes.PLAYLISTS,
        isTv = true,
        onToggle = { isDrawerExpanded = !isDrawerExpanded },
        onNavigate = onNavigate
    ) {
        OnboardingScreen(
            isTv = true,
            onPlaylistSelected = onPlaylistSelected
        )
    }
}

@Composable
fun TvHomeScreen(
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit
) {
    HomeScreen(
        isTv = true,
        onNavigate = onNavigate,
        onContentClick = onContentClick,
        onPlayContent = onPlayContent
    )
}

@Composable
fun TvLiveTvScreen(
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    LiveTvScreen(
        isTv = true,
        onNavigate = onNavigate,
        onPlayChannel = onPlayChannel
    )
}

@Composable
fun TvMoviesScreen(
    onNavigate: (String) -> Unit,
    onMovieClick: (Long) -> Unit
) {
    MoviesScreen(
        isTv = true,
        onNavigate = onNavigate,
        onMovieClick = onMovieClick
    )
}

@Composable
fun TvSeriesScreen(
    onNavigate: (String) -> Unit,
    onSeriesClick: (Long) -> Unit
) {
    SeriesScreen(
        isTv = true,
        onNavigate = onNavigate,
        onSeriesClick = onSeriesClick
    )
}

@Composable
fun TvSearchScreen(
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    SearchScreen(
        isTv = true,
        onNavigate = onNavigate,
        onContentClick = onContentClick,
        onPlayContent = onPlayContent
    )
}

@Composable
fun TvSettingsScreen(
    onNavigate: (String) -> Unit,
    onBackToPlaylists: () -> Unit
) {
    SettingsScreen(
        isTv = true,
        onNavigate = onNavigate,
        onBackToOnboarding = onBackToPlaylists
    )
}

@Composable
fun TvDetailScreen(
    contentId: Long,
    contentType: String,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit
) {
    DetailScreen(
        contentId = contentId,
        contentType = contentType,
        isTv = true,
        onBack = onBack,
        onPlay = onPlay
    )
}
