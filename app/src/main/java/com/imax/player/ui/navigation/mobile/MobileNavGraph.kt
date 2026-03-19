package com.imax.player.ui.navigation.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.imax.player.ui.components.MobileScaffoldLayout
import com.imax.player.ui.mobile.MobileDetailScreen
import com.imax.player.ui.mobile.MobileHomeScreen
import com.imax.player.ui.mobile.MobileLiveTvScreen
import com.imax.player.ui.mobile.MobileMoviesScreen
import com.imax.player.ui.mobile.MobilePlayerScreen
import com.imax.player.ui.mobile.MobilePlaylistScreen
import com.imax.player.ui.mobile.MobileSearchScreen
import com.imax.player.ui.mobile.MobileSeriesScreen
import com.imax.player.ui.mobile.MobileSettingsScreen
import com.imax.player.ui.navigation.Routes

@Composable
fun MobileNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route ?: Routes.PLAYLISTS
    val showBottomNav = Routes.MOBILE_MAIN_TABS.any { currentRoute.startsWith(it) }

    val navigateToTopLevel: (String) -> Unit = { route ->
        if (route == Routes.EXIT) {
            navController.navigate(Routes.PLAYLISTS) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (showBottomNav) {
        MobileScaffoldLayout(
            selectedRoute = currentRoute,
            onNavigate = navigateToTopLevel
        ) { paddingValues ->
            MobileNavHostContent(
                navController = navController,
                modifier = modifier.padding(paddingValues),
                onNavigate = navigateToTopLevel
            )
        }
    } else {
        MobileNavHostContent(
            navController = navController,
            modifier = modifier,
            onNavigate = navigateToTopLevel
        )
    }
}

@Composable
private fun MobileNavHostContent(
    navController: NavHostController,
    modifier: Modifier,
    onNavigate: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Routes.PLAYLISTS,
        modifier = modifier
    ) {
        composable(Routes.PLAYLISTS) {
            MobilePlaylistScreen(
                onPlaylistSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PLAYLISTS) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            MobileHomeScreen(
                onNavigate = onNavigate,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type, startPos ->
                    navController.navigate(Routes.player(url, title, id, type, startPos))
                }
            )
        }

        composable(Routes.LIVE_TV) {
            MobileLiveTvScreen(
                onNavigate = onNavigate,
                onPlayChannel = { url, title, id, group ->
                    navController.navigate(
                        Routes.player(
                            url = url,
                            title = title,
                            contentId = id,
                            contentType = "LIVE",
                            group = group.orEmpty()
                        )
                    )
                }
            )
        }

        composable(Routes.MOVIES) {
            MobileMoviesScreen(
                onNavigate = onNavigate,
                onMovieClick = { id ->
                    navController.navigate(Routes.detail(id, "MOVIE"))
                }
            )
        }

        composable(Routes.SERIES) {
            MobileSeriesScreen(
                onNavigate = onNavigate,
                onSeriesClick = { id ->
                    navController.navigate(Routes.detail(id, "SERIES"))
                }
            )
        }

        composable(Routes.SEARCH) {
            MobileSearchScreen(
                onNavigate = onNavigate,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type ->
                    navController.navigate(Routes.player(url, title, id, type))
                }
            )
        }

        composable(Routes.SETTINGS) {
            MobileSettingsScreen(
                onNavigate = onNavigate,
                onBackToPlaylists = {
                    navController.navigate(Routes.PLAYLISTS) {
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
            MobileDetailScreen(
                contentId = backStackEntry.arguments?.getLong("contentId") ?: 0L,
                contentType = backStackEntry.arguments?.getString("contentType").orEmpty(),
                onBack = { navController.popBackStack() },
                onPlay = { url, title, id, type, startPos ->
                    navController.navigate(Routes.player(url, title, id, type, startPos))
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
                navArgument("startPos") { type = NavType.LongType; defaultValue = 0L },
                navArgument("group") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            MobilePlayerScreen(
                url = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("url").orEmpty(),
                    "UTF-8"
                ),
                title = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("title").orEmpty(),
                    "UTF-8"
                ),
                contentId = backStackEntry.arguments?.getLong("contentId") ?: 0L,
                contentType = backStackEntry.arguments?.getString("contentType").orEmpty(),
                startPosition = backStackEntry.arguments?.getLong("startPos") ?: 0L,
                groupContext = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("group").orEmpty(),
                    "UTF-8"
                ),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
