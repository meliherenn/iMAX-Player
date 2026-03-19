package com.imax.player.ui.navigation.tv

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.imax.player.ui.navigation.Routes
import com.imax.player.ui.tv.TvContinueWatchingScreen
import com.imax.player.ui.tv.TvDetailScreen
import com.imax.player.ui.tv.TvFavoritesScreen
import com.imax.player.ui.tv.TvHomeScreen
import com.imax.player.ui.tv.TvLiveTvScreen
import com.imax.player.ui.tv.TvMoviesScreen
import com.imax.player.ui.tv.TvPlayerScreen
import com.imax.player.ui.tv.TvPlaylistsScreen
import com.imax.player.ui.tv.TvSearchScreen
import com.imax.player.ui.tv.TvSeriesScreen
import com.imax.player.ui.tv.TvSettingsScreen

private const val TV_STARTUP = "tv_startup"

@Composable
fun TvNavGraph(
    navController: NavHostController
) {
    val navigateToTopLevel: (String) -> Unit = { route ->
        when (route) {
            Routes.EXIT,
            Routes.PLAYLISTS -> {
                navController.navigate(Routes.PLAYLISTS) {
                    popUpTo(0) { inclusive = true }
                }
            }

            else -> {
                navController.navigate(route) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = TV_STARTUP
    ) {
        composable(TV_STARTUP) {
            TvStartupRoute(
                onReady = { route ->
                    navController.navigate(route) {
                        popUpTo(TV_STARTUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PLAYLISTS) {
            TvPlaylistsScreen(
                onNavigate = navigateToTopLevel,
                onPlaylistSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PLAYLISTS) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            TvHomeScreen(
                onNavigate = navigateToTopLevel,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type, startPos ->
                    navController.navigate(Routes.player(url, title, id, type, startPos))
                }
            )
        }

        composable(Routes.LIVE_TV) {
            TvLiveTvScreen(
                onNavigate = navigateToTopLevel,
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
            TvMoviesScreen(
                onNavigate = navigateToTopLevel,
                onMovieClick = { id ->
                    navController.navigate(Routes.detail(id, "MOVIE"))
                }
            )
        }

        composable(Routes.SERIES) {
            TvSeriesScreen(
                onNavigate = navigateToTopLevel,
                onSeriesClick = { id ->
                    navController.navigate(Routes.detail(id, "SERIES"))
                }
            )
        }

        composable(Routes.SEARCH) {
            TvSearchScreen(
                onNavigate = navigateToTopLevel,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type ->
                    navController.navigate(Routes.player(url, title, id, type))
                }
            )
        }

        composable(Routes.CONTINUE_WATCHING) {
            TvContinueWatchingScreen(
                onNavigate = navigateToTopLevel,
                onResume = { item ->
                    navController.navigate(
                        Routes.player(
                            url = item.streamUrl,
                            title = item.title,
                            contentId = item.contentId,
                            contentType = item.contentType.name,
                            startPos = item.position
                        )
                    )
                },
                onOpenDetail = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                }
            )
        }

        composable(Routes.FAVORITES) {
            TvFavoritesScreen(
                onNavigate = navigateToTopLevel,
                onMovieClick = { id -> navController.navigate(Routes.detail(id, "MOVIE")) },
                onSeriesClick = { id -> navController.navigate(Routes.detail(id, "SERIES")) },
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

        composable(Routes.SETTINGS) {
            TvSettingsScreen(
                onNavigate = navigateToTopLevel,
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
            TvDetailScreen(
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
            TvPlayerScreen(
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
