package com.imax.player.core.designsystem.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Premium Dark Color Palette
object ImaxColors {
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF12121A)
    val SurfaceVariant = Color(0xFF1A1A2E)
    val SurfaceElevated = Color(0xFF1E1E30)
    val CardBackground = Color(0xFF161625)
    val CardBorder = Color(0xFF2A2A3E)

    val Primary = Color(0xFFFF1744) // Neon Red
    val PrimaryVariant = Color(0xFFFF5252)
    val Secondary = Color(0xFF2979FF) // Neon Blue
    val SecondaryVariant = Color(0xFF448AFF)
    val Accent = Color(0xFF7C4DFF) // Purple accent

    val GradientStart = Color(0xFFFF1744)
    val GradientEnd = Color(0xFF2979FF)
    val GradientPurple = Color(0xFF7C4DFF)

    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB0B0C0)
    val TextTertiary = Color(0xFF707088)
    val TextOnPrimary = Color(0xFFFFFFFF)

    val DividerColor = Color(0xFF2A2A3E)
    val Shimmer = Color(0xFF2A2A3E)
    val ShimmerHighlight = Color(0xFF3A3A50)

    val Success = Color(0xFF00E676)
    val Warning = Color(0xFFFFAB00)
    val Error = Color(0xFFFF5252)
    val Info = Color(0xFF40C4FF)

    val FocusBorder = Color(0xFFFF1744)
    val FocusGlow = Color(0x60FF1744)

    val GlassBackground = Color(0x1AFFFFFF)
    val GlassBorder = Color(0x33FFFFFF)

    val RatingStarColor = Color(0xFFFFD600)
    val OverlayDark = Color(0xCC000000)
    val OverlayGradient = Color(0x00000000)
}

// Platform-aware dimensions
data class ImaxDimens(
    val screenPadding: Dp,
    val contentPadding: Dp,
    val cardWidth: Dp,
    val cardSpacing: Dp,
    val bannerHeight: Dp,
    val posterWidth: Dp,
    val sectionSpacing: Dp,
    val iconSize: Dp,
    val buttonHeight: Dp,
    val drawerWidth: Dp,
    val drawerCollapsedWidth: Dp,
    val categoryPanelWidth: Dp,
    val gridColumns: Int,
    val borderRadius: Dp,
    val focusBorderWidth: Dp,
    val touchTargetMin: Dp,
    val playerControlSize: Dp,
    val playerControlSpacing: Dp
)

val TvDimens = ImaxDimens(
    screenPadding = 48.dp,
    contentPadding = 24.dp,
    cardWidth = 180.dp,
    cardSpacing = 16.dp,
    bannerHeight = 320.dp,
    posterWidth = 200.dp,
    sectionSpacing = 24.dp,
    iconSize = 28.dp,
    buttonHeight = 52.dp,
    drawerWidth = 220.dp,
    drawerCollapsedWidth = 72.dp,
    categoryPanelWidth = 200.dp,
    gridColumns = 5,
    borderRadius = 14.dp,
    focusBorderWidth = 3.dp,
    touchTargetMin = 48.dp,
    playerControlSize = 72.dp,
    playerControlSpacing = 40.dp
)

val MobileDimens = ImaxDimens(
    screenPadding = 16.dp,
    contentPadding = 12.dp,
    cardWidth = 110.dp,
    cardSpacing = 10.dp,
    bannerHeight = 220.dp,
    posterWidth = 100.dp,
    sectionSpacing = 16.dp,
    iconSize = 24.dp,
    buttonHeight = 44.dp,
    drawerWidth = 0.dp, // no drawer on mobile
    drawerCollapsedWidth = 0.dp,
    categoryPanelWidth = 0.dp, // no side panel on mobile portrait
    gridColumns = 3,
    borderRadius = 12.dp,
    focusBorderWidth = 2.dp,
    touchTargetMin = 44.dp,
    playerControlSize = 56.dp,
    playerControlSpacing = 28.dp
)

val TabletDimens = ImaxDimens(
    screenPadding = 24.dp,
    contentPadding = 16.dp,
    cardWidth = 160.dp,
    cardSpacing = 12.dp,
    bannerHeight = 280.dp,
    posterWidth = 160.dp,
    sectionSpacing = 20.dp,
    iconSize = 26.dp,
    buttonHeight = 48.dp,
    drawerWidth = 200.dp,
    drawerCollapsedWidth = 72.dp,
    categoryPanelWidth = 180.dp,
    gridColumns = 4,
    borderRadius = 12.dp,
    focusBorderWidth = 2.dp,
    touchTargetMin = 44.dp,
    playerControlSize = 64.dp,
    playerControlSpacing = 32.dp
)

val LocalImaxDimens = compositionLocalOf { MobileDimens }

private val ImaxDarkColorScheme = darkColorScheme(
    primary = ImaxColors.Primary,
    onPrimary = ImaxColors.TextOnPrimary,
    primaryContainer = ImaxColors.PrimaryVariant,
    secondary = ImaxColors.Secondary,
    onSecondary = ImaxColors.TextOnPrimary,
    secondaryContainer = ImaxColors.SecondaryVariant,
    tertiary = ImaxColors.Accent,
    background = ImaxColors.Background,
    onBackground = ImaxColors.TextPrimary,
    surface = ImaxColors.Surface,
    onSurface = ImaxColors.TextPrimary,
    surfaceVariant = ImaxColors.SurfaceVariant,
    onSurfaceVariant = ImaxColors.TextSecondary,
    error = ImaxColors.Error,
    onError = ImaxColors.TextOnPrimary,
    outline = ImaxColors.CardBorder,
    outlineVariant = ImaxColors.DividerColor
)

val ImaxTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        color = ImaxColors.TextPrimary
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = ImaxColors.TextPrimary
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = ImaxColors.TextPrimary
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = ImaxColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = ImaxColors.TextPrimary
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = ImaxColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = ImaxColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = ImaxColors.TextPrimary
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = ImaxColors.TextSecondary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = ImaxColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = ImaxColors.TextSecondary
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = ImaxColors.TextTertiary
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = ImaxColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = ImaxColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = ImaxColors.TextTertiary
    )
)

@Composable
fun ImaxTheme(
    isTv: Boolean = false,
    content: @Composable () -> Unit
) {
    val config = LocalConfiguration.current
    val dimens = remember(isTv, config.screenWidthDp) {
        when {
            isTv -> TvDimens
            config.smallestScreenWidthDp >= 600 -> TabletDimens
            else -> MobileDimens
        }
    }

    CompositionLocalProvider(LocalImaxDimens provides dimens) {
        MaterialTheme(
            colorScheme = ImaxDarkColorScheme,
            typography = ImaxTypography,
            content = content
        )
    }
}
