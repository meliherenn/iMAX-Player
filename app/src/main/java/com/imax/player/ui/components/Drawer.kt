package com.imax.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import com.imax.player.ui.navigation.Routes

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
@OptIn(ExperimentalComposeUiApi::class)
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
    val exitItem = remember {
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
    val toggleFocusRequester = remember { FocusRequester() }
    val firstMenuRoute = menuRoutes.firstOrNull()
    val selectedMenuRoute = selectedRoute.takeIf { it in itemFocusRequesters } ?: firstMenuRoute
    var lastFocusedRoute by rememberSaveable {
        mutableStateOf(selectedMenuRoute ?: "")
    }
    var focusedIndex by rememberSaveable {
        mutableStateOf(menuRoutes.indexOf(selectedMenuRoute).takeIf { it >= 0 } ?: 0)
    }

    LaunchedEffect(menuRoutes, selectedRoute) {
        val fallbackRoute = selectedRoute.takeIf { it in itemFocusRequesters } ?: firstMenuRoute ?: ""
        if (lastFocusedRoute !in itemFocusRequesters) {
            lastFocusedRoute = fallbackRoute
        }
        if (focusedIndex !in menuRoutes.indices) {
            focusedIndex = menuRoutes.indexOf(lastFocusedRoute).takeIf { it >= 0 } ?: 0
        }
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            val targetRoute = when {
                itemFocusRequesters.containsKey(lastFocusedRoute) -> lastFocusedRoute
                itemFocusRequesters.containsKey(selectedRoute) -> selectedRoute
                else -> firstMenuRoute
            }
            targetRoute?.let { route ->
                focusedIndex = menuRoutes.indexOf(route).takeIf { it >= 0 } ?: focusedIndex
                itemFocusRequesters.getValue(route).requestFocus()
            }
        } else {
            toggleFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isExpanded, focusedIndex) {
        if (!isExpanded || focusedIndex !in menuRoutes.indices) return@LaunchedEffect

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@LaunchedEffect

        val firstVisibleIndex = visibleItems.first().index
        val lastVisibleIndex = visibleItems.last().index
        if (focusedIndex < firstVisibleIndex || focusedIndex > lastVisibleIndex) {
            listState.scrollToItem(focusedIndex)
        }
    }

    Row(modifier = modifier.fillMaxSize().background(ImaxColors.Background)) {
        val width = if (isExpanded) dimens.drawerWidth else dimens.drawerCollapsedWidth

        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .background(ImaxColors.Surface)
                .padding(vertical = 16.dp, horizontal = 10.dp)
        ) {
            // App Logo/Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ImaxColors.SurfaceVariant.copy(alpha = if (isExpanded) 0.36f else 0.22f))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .clickable(onClick = onToggle)
                    .focusRequester(toggleFocusRequester)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = firstMenuRoute
                            ?.let { route -> itemFocusRequesters.getValue(route) }
                            ?: FocusRequester.Cancel
                    }
                    .focusable(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                    contentDescription = "Menu",
                    tint = ImaxColors.Primary,
                    modifier = Modifier.size(28.dp)
                )
                if (isExpanded) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "iMAX",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ImaxColors.Primary
                    )
                    Text(
                        text = " Player",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ImaxColors.Secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ImaxColors.DividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

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
                        isExpanded = isExpanded,
                        focusRequester = itemFocusRequesters.getValue(item.route),
                        previousFocusRequester = items.getOrNull(index - 1)?.route
                            ?.let { route -> itemFocusRequesters.getValue(route) },
                        nextFocusRequester = items.getOrNull(index + 1)?.route
                            ?.let { route -> itemFocusRequesters.getValue(route) }
                            ?: itemFocusRequesters.getValue(Routes.EXIT),
                        onFocused = {
                            lastFocusedRoute = item.route
                            focusedIndex = index
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
                        isExpanded = isExpanded,
                        focusRequester = itemFocusRequesters.getValue(Routes.EXIT),
                        previousFocusRequester = items.lastOrNull()?.route
                            ?.let { route -> itemFocusRequesters.getValue(route) },
                        nextFocusRequester = null,
                        onFocused = {
                            lastFocusedRoute = Routes.EXIT
                            focusedIndex = items.lastIndex + 1
                        },
                        onClick = { onNavigate(Routes.EXIT) }
                    )
                }
            }
        }

        // Main content
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
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
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val itemShape = RoundedCornerShape(14.dp)
    val isFocusedAndSelected = isSelected && isFocused
    val scale by animateFloatAsState(
        targetValue = when {
            isFocusedAndSelected -> 1.07f
            isFocused -> 1.055f
            isSelected -> 1.01f
            else -> 1f
        },
        animationSpec = tween(180),
        label = "drawerItemScale"
    )
    val focusShadowElevation by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 20.dp
            isFocused -> 16.dp
            isSelected -> 4.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "drawerItemShadow"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFF6E3927)
            isFocused -> Color(0xFF58333D)
            isSelected -> Color(0xFF2A211D)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "drawerItemBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFFFFD7B4)
            isFocused -> Color(0xFFFFB196)
            isSelected -> Color(0xFF8C6D5C)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "drawerItemBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 4.dp
            isFocused -> 3.5.dp
            isSelected -> 1.5.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "drawerItemBorderWidth"
    )
    val accentColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFFFFE0C3)
            isFocused -> Color(0xFFFFC1A7)
            isSelected -> Color(0xFFD4A485)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "drawerItemAccent"
    )
    val accentWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 12.dp
            isFocused -> 10.dp
            isSelected -> 5.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "drawerItemAccentWidth"
    )
    val iconTint = when {
        isFocusedAndSelected -> Color(0xFFFFFCFA)
        isFocused -> Color(0xFFFFF8F2)
        isSelected -> Color(0xFFFFD9C0)
        else -> ImaxColors.TextSecondary
    }
    val textColor = when {
        isFocusedAndSelected -> Color(0xFFFFFCFA)
        isFocused -> Color(0xFFFFF8F2)
        isSelected -> Color(0xFFFFD9C0)
        else -> ImaxColors.TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = itemShape
                clip = false
                shadowElevation = focusShadowElevation.toPx()
            }
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(itemShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .focusRequester(focusRequester)
            .focusProperties {
                up = previousFocusRequester ?: FocusRequester.Cancel
                down = nextFocusRequester ?: FocusRequester.Cancel
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .border(borderWidth, borderColor, itemShape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(if (isExpanded) accentWidth else 0.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor)
        )
        if (isExpanded) {
            Spacer(modifier = Modifier.width(if (accentWidth > 0.dp) 14.dp else 8.dp))
        }
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
        if (isExpanded) {
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = when {
                    isFocusedAndSelected -> FontWeight.Bold
                    isFocused -> FontWeight.Bold
                    isSelected -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
