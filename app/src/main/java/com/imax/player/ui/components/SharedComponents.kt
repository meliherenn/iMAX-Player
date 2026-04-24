package com.imax.player.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.imax.player.R
import androidx.compose.ui.res.stringResource
import com.imax.player.core.common.Constants
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens
import java.util.Locale

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Card Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun ImaxCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    isTv: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) { if (isTv) 1.08f else 1.05f } else 1f,
        animationSpec = tween(200), label = "cardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) ImaxColors.FocusBorder else ImaxColors.CardBorder,
        animationSpec = tween(200), label = "cardBorder"
    )
    val borderWidth = if (isFocused) { if (isTv) 3.dp else 2.dp } else 1.dp

    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ImaxColors.CardBackground),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, focusedElevation = if (isTv) 16.dp else 12.dp),
        onClick = onClick
    ) {
        Column(content = content)
    }
}

@Composable
fun ContentPosterCard(
    title: String,
    posterUrl: String,
    modifier: Modifier = Modifier,
    rating: Double = 0.0,
    year: Int = 0,
    isTv: Boolean = false,
    cardWidth: Dp? = null,
    onClick: () -> Unit = {}
) {
    val dimens = LocalImaxDimens.current
    val width = cardWidth ?: dimens.cardWidth
    var isFocused by remember(title, posterUrl, year, isTv) { mutableStateOf(false) }
    val tvFocusState = if (isTv) {
        rememberTvFocusVisualState(
            isFocused = isFocused,
            defaultSurface = ImaxColors.CardBackground,
            selectedSurface = ImaxColors.CardBackground,
            focusedSurface = ImaxColors.SurfaceElevated,
            selectedFocusedSurface = Color(0xFF5C3C2C)
        )
    } else {
        null
    }
    val scale by animateFloatAsState(
        targetValue = when {
            tvFocusState != null -> tvFocusState.scale
            isFocused -> 1.08f
            else -> 1f
        },
        animationSpec = tween(200), label = "posterScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            tvFocusState != null -> tvFocusState.borderWidth
            isFocused -> dimens.focusBorderWidth
            else -> 0.dp
        },
        animationSpec = tween(200), label = "posterBorderWidth"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (tvFocusState != null && isFocused) 0.68f else if (isFocused) 0.4f else 0f,
        animationSpec = tween(200), label = "glowAlpha"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            tvFocusState != null -> tvFocusState.backgroundColor
            isFocused -> ImaxColors.SurfaceVariant
            else -> ImaxColors.CardBackground
        },
        animationSpec = tween(200),
        label = "posterBackground"
    )
    val focusTint by animateColorAsState(
        targetValue = when {
            tvFocusState != null && isFocused -> ImaxColors.Primary.copy(alpha = 0.24f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "posterFocusTint"
    )

    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(dimens.borderRadius))
            .then(
                if (isFocused) Modifier
                    .shadow(
                        elevation = tvFocusState?.shadowElevation ?: 12.dp,
                        shape = RoundedCornerShape(dimens.borderRadius),
                        spotColor = (tvFocusState?.glowColor ?: ImaxColors.FocusGlow).copy(alpha = glowAlpha)
                    )
                    .border(
                        borderWidth,
                        tvFocusState?.borderColor ?: ImaxColors.FocusBorder,
                        RoundedCornerShape(dimens.borderRadius)
                    )
                else Modifier
            )
            .clickable(onClick = onClick)
            .focusable()
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            PosterImage(
                url = posterUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize()
            )
            if (focusTint.alpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(focusTint)
                )
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ImaxColors.CardBackground)
                        )
                    )
            )

            if (rating > 0) {
                RatingBadge(
                    rating = rating,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        isTv && isFocused -> ImaxColors.Primary.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = tvFocusState?.contentColor ?: ImaxColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (year > 0) {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = tvFocusState?.secondaryContentColor
                        ?: if (isFocused) ImaxColors.TextSecondary else ImaxColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Image & Loading Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun PosterImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasImageError by remember(url) { mutableStateOf(false) }
    var fallbackToHttp by remember(url) { mutableStateOf(false) }
    val resolvedUrl = remember(url, fallbackToHttp) {
        if (fallbackToHttp) downgradeArtworkUrlToHttp(url) else url
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(resolvedUrl.ifBlank { null })
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
                hasImageError = state is AsyncImagePainter.State.Error
                if (state is AsyncImagePainter.State.Error &&
                    !fallbackToHttp &&
                    shouldRetryArtworkOverHttp(url)
                ) {
                    fallbackToHttp = true
                    hasImageError = false
                    isLoading = true
                }
            }
        )

        if (isLoading) {
            ShimmerBox(modifier = Modifier.fillMaxSize())
        } else if (resolvedUrl.isBlank() || hasImageError) {
            PosterFallback(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PosterFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(ImaxColors.SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = ImaxColors.TextTertiary.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun shouldRetryArtworkOverHttp(url: String): Boolean {
    val parsed = url.takeIf(String::isNotBlank)?.let(Uri::parse) ?: return false
    val host = parsed.host?.lowercase().orEmpty()
    if (parsed.scheme?.lowercase() != "https") {
        return false
    }
    if (host.isBlank() || host.endsWith("tmdb.org") || host.endsWith("themoviedb.org")) {
        return false
    }

    val path = parsed.encodedPath?.lowercase().orEmpty()
    return path.contains("/logo/") ||
        path.endsWith(".png") ||
        path.endsWith(".jpg") ||
        path.endsWith(".jpeg") ||
        path.endsWith(".webp")
}

private fun downgradeArtworkUrlToHttp(url: String): String {
    val parsed = url.takeIf(String::isNotBlank)?.let(Uri::parse) ?: return url
    if (parsed.scheme?.lowercase() != "https") {
        return url
    }

    if (url.startsWith(Constants.TMDB_IMAGE_BASE_URL, ignoreCase = true)) {
        return url
    }

    return parsed.buildUpon()
        .scheme("http")
        .build()
        .toString()
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = modifier
            .background(ImaxColors.Shimmer)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ImaxColors.Shimmer,
                            ImaxColors.ShimmerHighlight,
                            ImaxColors.Shimmer
                        ),
                        start = Offset(shimmerOffset - 200, 0f),
                        end = Offset(shimmerOffset, size.height)
                    )
                )
            }
    )
}

@Composable
fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = ImaxColors.RatingStarColor.copy(alpha = 0.9f)
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%.1f", rating),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Button Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isTv: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) { if (isTv) 1.10f else 1.05f } else 1f,
        animationSpec = tween(150), label = "btnScale"
    )
    val dimens = LocalImaxDimens.current

    Button(
        onClick = onClick,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled)
            .then(
                if (isFocused) Modifier.border(if (isTv) 3.dp else 2.dp, ImaxColors.FocusBorder, RoundedCornerShape(12.dp))
                else Modifier
            ),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = ImaxColors.SurfaceVariant
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ImaxColors.GradientStart, ImaxColors.GradientEnd)
                    ),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = text, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
fun ImaxOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isTv: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) { if (isTv) 1.10f else 1.05f } else 1f,
        animationSpec = tween(150), label = "outBtnScale"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled)
            .then(
                if (isFocused) Modifier.border(if (isTv) 3.dp else 2.dp, ImaxColors.FocusBorder, RoundedCornerShape(12.dp))
                else Modifier
            ),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ImaxColors.TextPrimary,
            disabledContentColor = ImaxColors.TextSecondary
        ),
        border = BorderStroke(1.dp, if (isFocused) Color.Transparent else ImaxColors.GlassBorder),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.defaultMinSize(minHeight = 24.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Glass / Layout Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ImaxColors.GlassBackground)
            .border(1.dp, ImaxColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = ImaxColors.TextPrimary
        )
        trailing?.invoke()
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// State Screens
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ImaxColors.Background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = ImaxColors.Primary)
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ImaxColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = ImaxColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            GradientButton(text = stringResource(R.string.retry), onClick = onRetry)
        }
    }
}

@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ImaxColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "📭",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = ImaxColors.TextSecondary
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Category (untouched — TV code preserved)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun TvCategoryItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = when {
        isSelected -> ImaxColors.Primary.copy(alpha = 0.15f)
        isFocused -> ImaxColors.SurfaceVariant
        else -> Color.Transparent
    }

    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        color = when {
            isSelected && isFocused -> ImaxColors.TextPrimary
            isSelected -> ImaxColors.Primary
            isFocused -> ImaxColors.TextPrimary
            else -> ImaxColors.TextSecondary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .then(
                if (isFocused) Modifier.border(
                    width = 1.dp,
                    color = ImaxColors.FocusBorder.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MOBILE CATEGORY UX — Bottom Sheet + Quick Bar
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Quick category bar: "All" + recent categories + "Browse" button.
 * Used at the top of Movies/Series/LiveTV mobile screens.
 */
@Composable
fun QuickCategoryBar(
    categories: List<String>,
    selectedCategory: String?,
    recentCategories: List<String>,
    onCategorySelected: (String?) -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allLabel = stringResource(R.string.category_all)
    val quickList = buildList {
        add(allLabel)
        // Add recent/frequent (max 5) that are actually in the full list
        recentCategories.filter { it in categories }.take(5).forEach { add(it) }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(quickList) { cat ->
            val isAll = cat == allLabel
            val isSelected = if (isAll) selectedCategory == null else selectedCategory == cat
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(if (isAll) null else cat) },
                label = { Text(cat, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ImaxColors.Primary.copy(alpha = 0.2f),
                    selectedLabelColor = ImaxColors.Primary,
                    containerColor = ImaxColors.SurfaceVariant,
                    labelColor = ImaxColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = Color.Transparent,
                    selectedBorderColor = ImaxColors.Primary.copy(alpha = 0.4f),
                    enabled = true,
                    selected = isSelected
                )
            )
        }

        // "Browse All" button
        item {
            AssistChip(
                onClick = onBrowseAll,
                label = { Text(stringResource(R.string.action_browse_all)) },
                leadingIcon = {
                    Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = ImaxColors.SurfaceElevated,
                    labelColor = ImaxColors.TextPrimary,
                    leadingIconContentColor = ImaxColors.Primary
                ),
                border = AssistChipDefaults.assistChipBorder(
                    borderColor = ImaxColors.Primary.copy(alpha = 0.3f),
                    enabled = true
                )
            )
        }
    }
}

/**
 * Full-height category bottom sheet with search, alphabetical list,
 * pinned/recent sections, and item counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBottomSheet(
    isVisible: Boolean,
    categories: List<String>,
    categoryCounts: Map<String, Int>,
    selectedCategory: String?,
    recentCategories: List<String>,
    pinnedCategories: List<String>,
    onCategorySelected: (String?) -> Unit,
    onDismiss: () -> Unit,
    onTogglePin: (String) -> Unit
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories.sorted()
        else categories.filter { it.contains(searchQuery, ignoreCase = true) }.sorted()
    }

    // Group: pinned first, then recent, then alphabetical
    val groupedCategories = remember(filteredCategories, pinnedCategories, recentCategories) {
        val pinned = filteredCategories.filter { it in pinnedCategories }
        val recent = filteredCategories.filter { it in recentCategories && it !in pinnedCategories }.take(5)
        val rest = filteredCategories.filter { it !in pinnedCategories && it !in recentCategories }
        Triple(pinned, recent, rest)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ImaxColors.Surface,
        contentColor = ImaxColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ImaxColors.TextTertiary)
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.action_browse_all), style = MaterialTheme.typography.headlineSmall, color = ImaxColors.TextPrimary)
                Text("${categories.size} categories", style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary)
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Search categories…", color = ImaxColors.TextTertiary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = ImaxColors.TextTertiary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, null, tint = ImaxColors.TextTertiary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ImaxColors.Primary,
                    unfocusedBorderColor = ImaxColors.CardBorder,
                    focusedContainerColor = ImaxColors.SurfaceVariant,
                    unfocusedContainerColor = ImaxColors.SurfaceVariant,
                    focusedTextColor = ImaxColors.TextPrimary,
                    unfocusedTextColor = ImaxColors.TextPrimary,
                    cursorColor = ImaxColors.Primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // "All" option
            CategorySheetItem(
                name = stringResource(R.string.all_categories),
                count = categories.sumOf { categoryCounts[it] ?: 0 },
                isSelected = selectedCategory == null,
                isPinned = false,
                onClick = { onCategorySelected(null); onDismiss() },
                onTogglePin = {}
            )

            HorizontalDivider(color = ImaxColors.DividerColor, modifier = Modifier.padding(horizontal = 20.dp))

            // Category list
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                val (pinned, recent, rest) = groupedCategories

                if (pinned.isNotEmpty()) {
                    item {
                        Text("📌 Pinned", style = MaterialTheme.typography.labelMedium, color = ImaxColors.TextTertiary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(pinned) { cat ->
                        CategorySheetItem(
                            name = cat,
                            count = categoryCounts[cat] ?: 0,
                            isSelected = selectedCategory == cat,
                            isPinned = true,
                            onClick = { onCategorySelected(cat); onDismiss() },
                            onTogglePin = { onTogglePin(cat) }
                        )
                    }
                }

                if (recent.isNotEmpty()) {
                    item {
                        Text("🕐 Recent", style = MaterialTheme.typography.labelMedium, color = ImaxColors.TextTertiary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(recent) { cat ->
                        CategorySheetItem(
                            name = cat,
                            count = categoryCounts[cat] ?: 0,
                            isSelected = selectedCategory == cat,
                            isPinned = cat in pinnedCategories,
                            onClick = { onCategorySelected(cat); onDismiss() },
                            onTogglePin = { onTogglePin(cat) }
                        )
                    }
                }

                if (rest.isNotEmpty()) {
                    item {
                        Text("A–Z", style = MaterialTheme.typography.labelMedium, color = ImaxColors.TextTertiary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(rest) { cat ->
                        CategorySheetItem(
                            name = cat,
                            count = categoryCounts[cat] ?: 0,
                            isSelected = selectedCategory == cat,
                            isPinned = cat in pinnedCategories,
                            onClick = { onCategorySelected(cat); onDismiss() },
                            onTogglePin = { onTogglePin(cat) }
                        )
                    }
                }

                if (filteredCategories.isEmpty()) {
                    item {
                        Text("No categories match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImaxColors.TextTertiary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            // Clear filter button
            if (selectedCategory != null) {
                TextButton(
                    onClick = { onCategorySelected(null); onDismiss() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.ClearAll, null, tint = ImaxColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Filter", color = ImaxColors.Primary)
                }
            }
        }
    }
}

@Composable
private fun CategorySheetItem(
    name: String,
    count: Int,
    isSelected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) ImaxColors.Primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(Icons.Filled.Check, null, tint = ImaxColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) ImaxColors.Primary else ImaxColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (count > 0) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.bodySmall,
                    color = ImaxColors.TextTertiary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Filled.PushPin,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) ImaxColors.Primary else ImaxColors.TextTertiary.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
