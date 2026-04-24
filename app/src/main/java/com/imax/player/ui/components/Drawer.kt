package com.imax.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.imax.player.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.ui.navigation.Routes
import timber.log.Timber
import kotlinx.coroutines.delay

private const val TV_DRAWER_LOG_TAG = "TvDrawer"
private const val TV_DRAWER_COLLAPSE_DELAY_MS = 180L

data class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val tvDrawerItems @Composable get() = listOf(
    DrawerItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home, Icons.Filled.Home),
    DrawerItem(Routes.SEARCH, stringResource(R.string.nav_search), Icons.Outlined.Search, Icons.Filled.Search),
    DrawerItem(Routes.LIVE_TV, stringResource(R.string.nav_live_tv), Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    DrawerItem(Routes.MOVIES, stringResource(R.string.nav_movies), Icons.Outlined.Movie, Icons.Filled.Movie),
    DrawerItem(Routes.SERIES, stringResource(R.string.nav_series), Icons.Outlined.Tv, Icons.Filled.Tv),
    DrawerItem(Routes.CONTINUE_WATCHING, stringResource(R.string.nav_continue_watching), Icons.Filled.History, Icons.Filled.History),
    DrawerItem(Routes.FAVORITES, stringResource(R.string.favorites), Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    DrawerItem(Routes.PLAYLISTS, stringResource(R.string.playlists), Icons.AutoMirrored.Filled.PlaylistPlay, Icons.AutoMirrored.Filled.PlaylistPlay),
    DrawerItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings, Icons.Filled.Settings)
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV-specific side drawer
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun TvDrawerLayout(
    isExpanded: Boolean,
    selectedRoute: String,
    onToggle: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dimens = LocalImaxDimens.current
    val items = tvDrawerItems
    val exitLabel = stringResource(R.string.nav_exit_playlist)
    val logoPainter = painterResource(id = R.mipmap.ic_launcher_foreground)
    val exitItem = remember(exitLabel) {
        DrawerItem(
            Routes.EXIT,
            exitLabel,
            Icons.AutoMirrored.Outlined.ExitToApp,
            Icons.AutoMirrored.Filled.ExitToApp
        )
    }
    val menuItems = remember(items, exitItem) {
        items + exitItem
    }
    val menuRoutes = remember(menuItems) { menuItems.map { it.route } }
    val listState = rememberLazyListState()
    val itemFocusRequesters = remember(menuRoutes) {
        menuRoutes.associateWith { FocusRequester() }
    }
    val firstMenuRoute = menuRoutes.firstOrNull()
    val selectedMenuRoute = selectedRoute.takeIf { it in itemFocusRequesters } ?: firstMenuRoute
    var lastFocusedRoute by rememberSaveable {
        mutableStateOf(selectedMenuRoute ?: "")
    }
    var focusedIndex by rememberSaveable {
        mutableStateOf(menuRoutes.indexOf(selectedMenuRoute).takeIf { it >= 0 } ?: 0)
    }
    var focusedDrawerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var drawerExpanded by rememberSaveable { mutableStateOf(isExpanded) }
    val drawerWidth by animateDpAsState(
        targetValue = if (drawerExpanded) dimens.drawerWidth else dimens.drawerCollapsedWidth,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "tvDrawerWidth"
    )

    LaunchedEffect(menuRoutes, selectedRoute) {
        val fallbackRoute = selectedRoute.takeIf { it in itemFocusRequesters } ?: firstMenuRoute ?: ""
        if (lastFocusedRoute !in itemFocusRequesters) {
            lastFocusedRoute = fallbackRoute
        }
        if (focusedDrawerTarget == null) {
            lastFocusedRoute = fallbackRoute
        }
        if (focusedIndex !in menuRoutes.indices) {
            focusedIndex = menuRoutes.indexOf(lastFocusedRoute).takeIf { it >= 0 } ?: 0
        }
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            drawerExpanded = true
        } else {
            delay(TV_DRAWER_COLLAPSE_DELAY_MS)
            if (!isExpanded) {
                drawerExpanded = false
            }
        }
    }

    LaunchedEffect(drawerExpanded, focusedIndex) {
        if (!drawerExpanded || focusedIndex !in menuRoutes.indices) return@LaunchedEffect

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@LaunchedEffect

        val firstVisibleIndex = visibleItems.first().index
        val lastVisibleIndex = visibleItems.last().index
        if (focusedIndex < firstVisibleIndex || focusedIndex > lastVisibleIndex) {
            listState.scrollToItem(focusedIndex)
        }
    }

    Row(modifier = modifier.fillMaxSize().background(ImaxColors.Background)) {
        Column(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .background(ImaxColors.Surface)
                .padding(
                    vertical = if (drawerExpanded) 16.dp else 18.dp,
                    horizontal = if (drawerExpanded) 10.dp else 8.dp
                )
        ) {
            TvDrawerBrand(
                isExpanded = drawerExpanded,
                logoPainter = logoPainter
            )

            Spacer(modifier = Modifier.height(if (drawerExpanded) 16.dp else 12.dp))
            HorizontalDivider(color = ImaxColors.DividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(if (drawerExpanded) 10.dp else 12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    TvDrawerItemRow(
                        item = item,
                        isSelected = selectedRoute == item.route,
                        isExpanded = drawerExpanded,
                        focusRequester = itemFocusRequesters.getValue(item.route),
                        previousFocusRequester = items.getOrNull(index - 1)?.route
                            ?.let { route -> itemFocusRequesters.getValue(route) },
                        nextFocusRequester = items.getOrNull(index + 1)?.route
                            ?.let { route -> itemFocusRequesters.getValue(route) }
                            ?: itemFocusRequesters.getValue(Routes.EXIT),
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusedDrawerTarget = item.route
                                lastFocusedRoute = item.route
                                focusedIndex = index
                            } else if (focusedDrawerTarget == item.route) {
                                focusedDrawerTarget = null
                            }
                        },
                        onClick = { onNavigate(item.route) }
                    )
                }

                item(key = Routes.EXIT) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = ImaxColors.DividerColor, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TvDrawerItemRow(
                        item = exitItem,
                        isSelected = false,
                        isExpanded = drawerExpanded,
                        focusRequester = itemFocusRequesters.getValue(Routes.EXIT),
                        previousFocusRequester = items.lastOrNull()?.route
                            ?.let { route -> itemFocusRequesters.getValue(route) },
                        nextFocusRequester = null,
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusedDrawerTarget = Routes.EXIT
                                lastFocusedRoute = Routes.EXIT
                                focusedIndex = items.lastIndex + 1
                            } else if (focusedDrawerTarget == Routes.EXIT) {
                                focusedDrawerTarget = null
                            }
                        },
                        onClick = { onNavigate(Routes.EXIT) }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}

@Composable
private fun TvDrawerBrand(
    isExpanded: Boolean,
    logoPainter: androidx.compose.ui.graphics.painter.Painter
) {
    val containerColor by animateColorAsState(
        targetValue = if (isExpanded) {
            ImaxColors.SurfaceVariant.copy(alpha = 0.36f)
        } else {
            ImaxColors.SurfaceVariant.copy(alpha = 0.24f)
        },
        animationSpec = tween(180),
        label = "tvDrawerBrandBackground"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .padding(
                horizontal = if (isExpanded) 14.dp else 10.dp,
                vertical = if (isExpanded) 14.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
    ) {
        Image(
            painter = logoPainter,
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(if (isExpanded) 62.dp else 36.dp)
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(160)) + expandHorizontally(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally(animationSpec = tween(120))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = ImaxColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun FocusRequester.requestFocusSafely(reason: String) {
    runCatching { requestFocus() }
        .onFailure { error ->
            Timber.tag(TV_DRAWER_LOG_TAG).w(error, "Unable to request focus for %s", reason)
        }
}

@Composable
private fun CollapsedDrawerPlate(
    item: DrawerItem,
    isSelected: Boolean,
    isFocused: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isFocused && isSelected -> 1.15f
            isFocused -> 1.12f
            else -> 1f
        },
        animationSpec = tween(150),
        label = "collapseScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && isSelected -> ImaxColors.Primary
            isFocused -> ImaxColors.SurfaceVariant
            isSelected -> ImaxColors.Primary.copy(alpha = 0.20f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "collapseBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused && isSelected -> Color.White
            isFocused -> Color.White
            isSelected -> ImaxColors.Primary
            else -> ImaxColors.TextSecondary
        },
        animationSpec = tween(150),
        label = "collapseContent"
    )

    val itemShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isFocused) 12.dp.toPx() else 0f
                shape = itemShape
                clip = false
            }
            .clip(itemShape)
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Transparent,
                shape = itemShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ExpandedDrawerPlate(
    item: DrawerItem,
    isSelected: Boolean,
    isFocused: Boolean
) {
    val plateShape = RoundedCornerShape(12.dp)

    val scale by animateFloatAsState(
        targetValue = when {
            isFocused && isSelected -> 1.08f
            isFocused -> 1.06f
            else -> 1f
        },
        animationSpec = tween(150),
        label = "expandScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && isSelected -> Color.White
            isFocused -> Color.White.copy(alpha = 0.95f)
            isSelected -> ImaxColors.Primary.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "expandBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused && isSelected -> ImaxColors.Primary
            isFocused -> ImaxColors.Background
            isSelected -> ImaxColors.Primary
            else -> ImaxColors.TextSecondary
        },
        animationSpec = tween(150),
        label = "expandContent"
    )

    val accentWidth by animateDpAsState(
        targetValue = if (isFocused || isSelected) 5.dp else 0.dp,
        animationSpec = tween(150),
        label = "expandAccentWidth"
    )

    val accentColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else ImaxColors.Primary,
        animationSpec = tween(150),
        label = "expandAccentColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isFocused) 12.dp.toPx() else 0f
                shape = plateShape
                clip = false
            }
            .clip(plateShape)
            .background(bgColor)
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                shape = plateShape
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(accentWidth)
                .fillMaxHeight()
                .background(accentColor)
        )

        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .padding(start = if (accentWidth > 0.dp) 14.dp else 19.dp, end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(16.dp))
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvDrawerItemRow(
    item: DrawerItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    focusRequester: FocusRequester,
    previousFocusRequester: FocusRequester?,
    nextFocusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    // Invisible shell that securely connects DPAD interaction states without enforcing layout shape limitations.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusProperties {
                left = FocusRequester.Cancel
                up = previousFocusRequester ?: FocusRequester.Cancel
                down = nextFocusRequester ?: FocusRequester.Cancel
            }
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
    ) {
        if (isExpanded) {
            ExpandedDrawerPlate(
                item = item,
                isSelected = isSelected,
                isFocused = isFocused
            )
        } else {
            CollapsedDrawerPlate(
                item = item,
                isSelected = isSelected,
                isFocused = isFocused
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile BottomNavigation 
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

val bottomNavItems @Composable get() = listOf(
    DrawerItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home, Icons.Filled.Home),
    DrawerItem(Routes.SEARCH, stringResource(R.string.nav_search), Icons.Outlined.Search, Icons.Filled.Search),
    DrawerItem(Routes.LIVE_TV, stringResource(R.string.nav_live_tv), Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    DrawerItem(Routes.MOVIES, stringResource(R.string.nav_movies), Icons.Outlined.Movie, Icons.Filled.Movie),
    DrawerItem(Routes.SERIES, stringResource(R.string.nav_series), Icons.Outlined.Tv, Icons.Filled.Tv),
    DrawerItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings, Icons.Filled.Settings)
)

@Composable
fun MobileScaffoldLayout(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ImaxColors.Background,
        bottomBar = {
            NavigationBar(
                containerColor = ImaxColors.Surface,
                contentColor = ImaxColors.TextPrimary,
                tonalElevation = 0.dp
            ) {
                bottomNavItems.forEach { item ->
                    val isSelected = selectedRoute == item.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onNavigate(item.route) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ImaxColors.Primary,
                            selectedTextColor = ImaxColors.Primary,
                            unselectedIconColor = ImaxColors.TextTertiary,
                            unselectedTextColor = ImaxColors.TextTertiary,
                            indicatorColor = ImaxColors.Primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Backward-compatible wrapper 
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Unified wrapper: TV gets side drawer, Mobile gets bottom navigation.
 * Called by all screens that need the standard shell.
 */
@Composable
fun ImaxDrawer(
    isExpanded: Boolean,
    selectedRoute: String,
    isTv: Boolean,
    onToggle: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isTv) {
        TvDrawerLayout(
            isExpanded = isExpanded,
            selectedRoute = selectedRoute,
            onToggle = onToggle,
            onNavigate = onNavigate,
            modifier = modifier,
            content = content
        )
    } else {
        // For mobile, this just provides content directly.
        // The BottomNavigation is handled by the Navigation scaffold.
        // Individual screens still call ImaxDrawer but on mobile it's a pass-through.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ImaxColors.Background)
        ) {
            content()
        }
    }
}
