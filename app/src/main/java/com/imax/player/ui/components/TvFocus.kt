package com.imax.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imax.player.core.designsystem.theme.ImaxColors

@Immutable
data class TvFocusVisualState(
    val scale: Float,
    val shadowElevation: Dp,
    val borderWidth: Dp,
    val backgroundColor: Color,
    val borderColor: Color,
    val glowColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val accentColor: Color,
    val accentWidth: Dp
) {
    val elevation: Dp
        get() = shadowElevation
}

@Composable
fun rememberTvFocusVisualState(
    isFocused: Boolean,
    isSelected: Boolean = false,
    defaultSurface: Color = Color.Transparent,
    selectedSurface: Color = ImaxColors.Primary.copy(alpha = 0.14f),
    focusedSurface: Color = ImaxColors.SurfaceElevated,
    selectedFocusedSurface: Color = Color(0xFF4A1C27),
    defaultContentColor: Color = ImaxColors.TextPrimary,
    defaultSecondaryContentColor: Color = ImaxColors.TextSecondary,
    selectedContentColor: Color = Color(0xFFFFE3D1),
    focusedContentColor: Color = Color.White,
    selectedFocusedContentColor: Color = Color.White,
    selectedBorderColor: Color = ImaxColors.Primary.copy(alpha = 0.58f),
    focusedBorderColor: Color = ImaxColors.FocusBorder,
    selectedFocusedBorderColor: Color = ImaxColors.FocusBorder,
    selectedAccentColor: Color = ImaxColors.Primary.copy(alpha = 0.78f),
    focusedAccentColor: Color = ImaxColors.FocusBorder,
    selectedFocusedAccentColor: Color = ImaxColors.FocusBorder
): TvFocusVisualState {
    val isFocusedAndSelected = isFocused && isSelected
    val animationSpec = tween<Dp>(180)

    val scale by animateFloatAsState(
        targetValue = when {
            isFocusedAndSelected -> 1.045f
            isFocused -> 1.035f
            isSelected -> 1.01f
            else -> 1f
        },
        animationSpec = tween(160),
        label = "tvFocusScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 30.dp
            isFocused -> 28.dp
            isSelected -> 0.dp
            else -> 0.dp
        },
        animationSpec = animationSpec,
        label = "tvFocusShadow"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 5.dp
            isFocused -> 4.dp
            isSelected -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(160),
        label = "tvFocusBorderWidth"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedSurface
            isFocused -> focusedSurface
            isSelected -> selectedSurface
            else -> defaultSurface
        },
        animationSpec = tween(180),
        label = "tvFocusBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedBorderColor
            isFocused -> focusedBorderColor
            isSelected -> selectedBorderColor
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvFocusBorderColor"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedContentColor
            isFocused -> focusedContentColor
            isSelected -> selectedContentColor
            else -> defaultContentColor
        },
        animationSpec = tween(180),
        label = "tvFocusContentColor"
    )
    val secondaryContentColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedContentColor.copy(alpha = 0.92f)
            isFocused -> focusedContentColor.copy(alpha = 0.88f)
            isSelected -> selectedContentColor.copy(alpha = 0.84f)
            else -> defaultSecondaryContentColor
        },
        animationSpec = tween(180),
        label = "tvFocusSecondaryColor"
    )
    val accentColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedAccentColor
            isFocused -> focusedAccentColor
            isSelected -> selectedAccentColor
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvFocusAccentColor"
    )
    val accentWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 12.dp
            isFocused -> 10.dp
            isSelected -> 5.dp
            else -> 0.dp
        },
        animationSpec = animationSpec,
        label = "tvFocusAccentWidth"
    )

    return TvFocusVisualState(
        scale = scale,
        shadowElevation = shadowElevation,
        borderWidth = borderWidth,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        glowColor = when {
            isFocusedAndSelected -> selectedFocusedBorderColor.copy(alpha = 0.78f)
            isFocused -> focusedBorderColor.copy(alpha = 0.72f)
            isSelected -> selectedBorderColor.copy(alpha = 0.38f)
            else -> Color.Transparent
        },
        contentColor = contentColor,
        secondaryContentColor = secondaryContentColor,
        accentColor = accentColor,
        accentWidth = accentWidth
    )
}
