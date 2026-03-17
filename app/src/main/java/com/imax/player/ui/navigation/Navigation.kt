package com.imax.player.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.imax.player.ui.components.MobileScaffoldLayout
import com.imax.player.ui.details.DetailScreen
import com.imax.player.ui.home.HomeScreen
import com.imax.player.ui.live.LiveTvScreen
import com.imax.player.ui.movies.MoviesScreen
import com.imax.player.ui.onboarding.OnboardingScreen
import com.imax.player.ui.player.PlayerScreen
import com.imax.player.ui.search.SearchScreen
import com.imax.player.ui.series.SeriesScreen
import com.imax.player.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIVE_TV = "live_tv"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{url}?title={title}&contentId={contentId}&contentType={contentType}&startPos={startPos}"
    const val DETAIL = "detail/{contentId}/{contentType}"

    fun player(url: String, title: String = "", contentId: Long = 0, contentType: String = "", startPos: Long = 0): String {
        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
        return "player/$encoded?title=$title&contentId=$contentId&contentType=$contentType&startPos=$startPos"
    }

    fun detail(contentId: Long, contentType: String): String = "detail/$contentId/$contentType"

    // Routes that show the bottom navigation bar
    val MAIN_TABS = setOf(HOME, SEARCH, LIVE_TV, MOVIES, SERIES, SETTINGS)
}

@Composable
fun ImaxNavHost(
    isTv: Boolean,
    navController: NavHostController = rememberNavController()
) {
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route ?: Routes.ONBOARDING

    // Determine if we should show bottom nav
    val showBottomNav = !isTv && Routes.MAIN_TABS.any { currentRoute.startsWith(it) }

    // Navigation helper for main tab screens
    val navigateToTab: (String) -> Unit = { route ->
        if (route == "exit") {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate(route) {
                // Pop up to home to avoid building up a stack of tabs
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (showBottomNav) {
        MobileScaffoldLayout(
            selectedRoute = currentRoute,
            onNavigate = navigateToTab
        ) { paddingValues ->
            NavHostContent(
                navController = navController,
                isTv = isTv,
                modifier = Modifier.padding(paddingValues),
                navigateToTab = navigateToTab
            )
        }
    } else {
        NavHostContent(
            navController = navController,
            isTv = isTv,
            modifier = Modifier,
            navigateToTab = navigateToTab
        )
    }
}

@Composable
private fun NavHostContent(
    navController: NavHostController,
    isTv: Boolean,
    modifier: Modifier,
    navigateToTab: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Routes.ONBOARDING,
        modifier = modifier
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                isTv = isTv,
                onPlaylistSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                isTv = isTv,
                onNavigate = navigateToTab,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type, pos ->
                    navController.navigate(Routes.player(url, title, id, type, pos))
                }
            )
        }

        composable(Routes.LIVE_TV) {
            LiveTvScreen(
                isTv = isTv,
                onNavigate = navigateToTab,
                onPlayChannel = { url, title, id ->
                    navController.navigate(Routes.player(url, title, id, "LIVE"))
                }
            )
        }

        composable(Routes.MOVIES) {
            MoviesScreen(
                isTv = isTv,
                onNavigate = navigateToTab,
                onMovieClick = { id ->
                    navController.navigate(Routes.detail(id, "MOVIE"))
                }
            )
        }

        composable(Routes.SERIES) {
            SeriesScreen(
                isTv = isTv,
                onNavigate = navigateToTab,
                onSeriesClick = { id ->
                    navController.navigate(Routes.detail(id, "SERIES"))
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                isTv = isTv,
                onNavigate = navigateToTab,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type ->
                    navController.navigate(Routes.player(url, title, id, type))
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                isTv = isTv,
                onNavigate = navigateToTab,
                onBackToOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("contentId") { type = NavType.LongType },
                navArgument("contentType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contentId = backStackEntry.arguments?.getLong("contentId") ?: 0
            val contentType = backStackEntry.arguments?.getString("contentType") ?: ""
            DetailScreen(
                contentId = contentId,
                contentType = contentType,
                isTv = isTv,
                onBack = { navController.popBackStack() },
                onPlay = { url, title, id, type, pos ->
                    navController.navigate(Routes.player(url, title, id, type, pos))
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("contentType") { type = NavType.StringType; defaultValue = "" },
                navArgument("startPos") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val url = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("url") ?: "", "UTF-8"
            )
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val contentId = backStackEntry.arguments?.getLong("contentId") ?: 0
            val contentType = backStackEntry.arguments?.getString("contentType") ?: ""
            val startPos = backStackEntry.arguments?.getLong("startPos") ?: 0

            PlayerScreen(
                url = url,
                title = title,
                contentId = contentId,
                contentType = contentType,
                startPosition = startPos,
                isTv = isTv,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
