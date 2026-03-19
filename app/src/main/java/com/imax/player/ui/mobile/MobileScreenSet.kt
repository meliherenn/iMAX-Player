package com.imax.player.ui.mobile

import androidx.compose.runtime.Composable
import com.imax.player.ui.details.DetailScreen
import com.imax.player.ui.home.HomeScreen
import com.imax.player.ui.live.LiveTvScreen
import com.imax.player.ui.movies.MoviesScreen
import com.imax.player.ui.onboarding.OnboardingScreen
import com.imax.player.ui.player.PlayerScreen
import com.imax.player.ui.search.SearchScreen
import com.imax.player.ui.series.SeriesScreen
import com.imax.player.ui.settings.SettingsScreen

@Composable
fun MobilePlaylistScreen(
    onPlaylistSelected: () -> Unit
) {
    OnboardingScreen(
        isTv = false,
        onPlaylistSelected = onPlaylistSelected
    )
}

@Composable
fun MobileHomeScreen(
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit
) {
    HomeScreen(
        isTv = false,
        onNavigate = onNavigate,
        onContentClick = onContentClick,
        onPlayContent = onPlayContent
    )
}

@Composable
fun MobileLiveTvScreen(
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    LiveTvScreen(
        isTv = false,
        onNavigate = onNavigate,
        onPlayChannel = onPlayChannel
    )
}

@Composable
fun MobileMoviesScreen(
    onNavigate: (String) -> Unit,
    onMovieClick: (Long) -> Unit
) {
    MoviesScreen(
        isTv = false,
        onNavigate = onNavigate,
        onMovieClick = onMovieClick
    )
}

@Composable
fun MobileSeriesScreen(
    onNavigate: (String) -> Unit,
    onSeriesClick: (Long) -> Unit
) {
    SeriesScreen(
        isTv = false,
        onNavigate = onNavigate,
        onSeriesClick = onSeriesClick
    )
}

@Composable
fun MobileSearchScreen(
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    SearchScreen(
        isTv = false,
        onNavigate = onNavigate,
        onContentClick = onContentClick,
        onPlayContent = onPlayContent
    )
}

@Composable
fun MobileSettingsScreen(
    onNavigate: (String) -> Unit,
    onBackToPlaylists: () -> Unit
) {
    SettingsScreen(
        isTv = false,
        onNavigate = onNavigate,
        onBackToOnboarding = onBackToPlaylists
    )
}

@Composable
fun MobileDetailScreen(
    contentId: Long,
    contentType: String,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit
) {
    DetailScreen(
        contentId = contentId,
        contentType = contentType,
        isTv = false,
        onBack = onBack,
        onPlay = onPlay
    )
}

@Composable
fun MobilePlayerScreen(
    url: String,
    title: String,
    contentId: Long,
    contentType: String,
    startPosition: Long,
    groupContext: String,
    onBack: () -> Unit
) {
    PlayerScreen(
        url = url,
        title = title,
        contentId = contentId,
        contentType = contentType,
        startPosition = startPosition,
        groupContext = groupContext,
        isTv = false,
        onBack = onBack
    )
}
