package com.imax.player.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.imax.player.core.common.DeviceUiMode
import com.imax.player.ui.navigation.mobile.MobileNavGraph
import com.imax.player.ui.navigation.tv.TvNavGraph

object Routes {
    const val PLAYLISTS = "playlists"
    const val ONBOARDING = PLAYLISTS
    const val HOME = "home"
    const val LIVE_TV = "live_tv"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val SEARCH = "search"
    const val CONTINUE_WATCHING = "continue_watching"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val EXIT = "exit"
    const val PLAYER = "player?url={url}&title={title}&contentId={contentId}&contentType={contentType}&startPos={startPos}&group={group}"
    const val DETAIL = "detail/{contentId}/{contentType}"

    fun player(
        url: String,
        title: String = "",
        contentId: Long = 0,
        contentType: String = "",
        startPos: Long = 0,
        group: String = ""
    ): String {
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val encodedGroup = java.net.URLEncoder.encode(group, "UTF-8")
        return "player?url=$encodedUrl&title=$encodedTitle&contentId=$contentId&contentType=$contentType&startPos=$startPos&group=$encodedGroup"
    }

    fun detail(contentId: Long, contentType: String): String = "detail/$contentId/$contentType"

    val MOBILE_MAIN_TABS = setOf(HOME, SEARCH, LIVE_TV, MOVIES, SERIES, SETTINGS)
    val TV_TOP_LEVEL_DESTINATIONS = setOf(
        HOME,
        SEARCH,
        LIVE_TV,
        MOVIES,
        SERIES,
        CONTINUE_WATCHING,
        FAVORITES,
        PLAYLISTS,
        SETTINGS
    )
}

@Composable
fun ImaxNavHost(
    deviceUiMode: DeviceUiMode,
    navController: NavHostController = rememberNavController()
) {
    if (deviceUiMode.isTv) {
        TvNavGraph(navController = navController)
    } else {
        MobileNavGraph(navController = navController)
    }
}
