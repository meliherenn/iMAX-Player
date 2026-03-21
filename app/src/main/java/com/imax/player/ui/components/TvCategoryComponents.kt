package com.imax.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.designsystem.theme.LocalImaxDimens

@Composable
fun TvCategoryPanel(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    val dimens = LocalImaxDimens.current
    val panelShape = RoundedCornerShape(22.dp)
    val panelBackground = Color(0xFF120D0D)
    val panelBorder = Color(0xFF5C3A2D)
    val panelInnerGlow = Color(0x26FFB47A)

    Box(
        modifier = modifier
            .width(dimens.categoryPanelWidth + 42.dp)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
                .background(panelBackground)
                .border(
                    width = 2.dp,
                    color = panelBorder,
                    shape = panelShape
                )
                .padding(horizontal = 10.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(panelInnerGlow)
                    .align(Alignment.CenterStart)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun TvRailCategoryItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val itemShape = RoundedCornerShape(18.dp)
    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        isSelected = isSelected,
        defaultSurface = Color.Transparent,
        selectedSurface = Color(0xFF2D1D16),
        focusedSurface = Color(0xFF85502B),
        selectedFocusedSurface = Color(0xFFAF6535),
        defaultContentColor = ImaxColors.TextSecondary,
        defaultSecondaryContentColor = ImaxColors.TextSecondary,
        selectedContentColor = Color(0xFFFFDFC9),
        focusedContentColor = Color(0xFFFFFCF8),
        selectedFocusedContentColor = Color(0xFFFFFCF8),
        selectedBorderColor = Color(0xFF8F6A52),
        focusedBorderColor = Color(0xFFFFD49C),
        selectedFocusedBorderColor = Color(0xFFFFE6C0),
        selectedAccentColor = Color(0xFFD69C73),
        focusedAccentColor = Color(0xFFFFDDA0),
        selectedFocusedAccentColor = Color(0xFFFFE8C8)
    )
    val trailingGlowColor by animateColorAsState(
        targetValue = when {
            isFocused && isSelected -> Color(0x60FFE7C3)
            isFocused -> Color(0x48FFD49A)
            isSelected -> Color(0x22D69C73)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvCategoryGlow"
    )
    val effectiveScale by animateFloatAsState(
        targetValue = when {
            isFocused && isSelected -> 1.10f
            isFocused -> 1.08f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = tween(180),
        label = "tvCategoryEffectiveScale"
    )
    val effectiveBorderWidth by animateDpAsState(
        targetValue = when {
            isFocused && isSelected -> 4.5.dp
            isFocused -> 3.5.dp
            isSelected -> 1.5.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "tvCategoryEffectiveBorderWidth"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .graphicsLayer {
                scaleX = effectiveScale
                scaleY = effectiveScale
                this.shape = itemShape
                clip = false
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .clip(itemShape)
            .background(focusState.backgroundColor)
            .border(effectiveBorderWidth, focusState.borderColor, itemShape)
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(focusState.accentWidth)
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(focusState.accentColor)
        )
        Spacer(modifier = Modifier.width(if (focusState.accentWidth > 0.dp) 16.dp else 10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = focusState.contentColor,
            fontWeight = when {
                isFocused && isSelected -> FontWeight.ExtraBold
                isFocused -> FontWeight.Bold
                isSelected -> FontWeight.SemiBold
                else -> FontWeight.Medium
            },
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingGlowColor != Color.Transparent) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(trailingGlowColor)
            )
        }
    }
}
