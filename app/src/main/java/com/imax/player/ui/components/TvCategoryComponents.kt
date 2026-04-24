package com.imax.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val itemShape = RoundedCornerShape(18.dp)

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvCategoryBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> ImaxColors.TextSecondary
        },
        animationSpec = tween(180),
        label = "tvCategoryText"
    )

    val trailingGlowColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black.copy(alpha = 0.2f)
            isSelected -> Color.White.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvCategoryGlow"
    )

    val effectiveScale by animateFloatAsState(
        targetValue = when {
            isFocused -> 1.08f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = tween(180),
        label = "tvCategoryEffectiveScale"
    )

    val effectiveBorderWidth by animateDpAsState(
        targetValue = when {
            isFocused -> 4.dp
            isSelected -> 1.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "tvCategoryEffectiveBorderWidth"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> ImaxColors.FocusBorder
            isSelected -> Color.White.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvCategoryBorderColor"
    )

    val accentWidth by animateDpAsState(
        targetValue = when {
            isFocused -> 8.dp
            isSelected -> 4.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "tvCategoryAccentWidth"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .graphicsLayer {
                scaleX = effectiveScale
                scaleY = effectiveScale
                this.shape = itemShape
                clip = false
                shadowElevation = if (isFocused) 16.dp.toPx() else 0f
                ambientShadowColor = ImaxColors.FocusGlow
                spotShadowColor = ImaxColors.FocusGlow
            }
            .clip(itemShape)
            .background(backgroundColor)
            .border(effectiveBorderWidth, borderColor, itemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(accentWidth)
                .height(36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (isFocused) ImaxColors.FocusBorder else Color.White)
        )
        Spacer(modifier = Modifier.width(if (accentWidth > 0.dp) 12.dp else 6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = when {
                isFocused || isSelected -> FontWeight.Bold
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
                    .width(8.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(trailingGlowColor)
            )
        }
    }
}
