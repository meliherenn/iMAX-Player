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
    val panelShape = RoundedCornerShape(18.dp)
    val panelBackground = Color(0xFF171210)
    val panelBorder = Color(0xFF3C2C24)

    Box(
        modifier = modifier
            .width(dimens.categoryPanelWidth + 36.dp)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
                .background(panelBackground)
                .border(
                    width = 1.5.dp,
                    color = panelBorder,
                    shape = panelShape
                )
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
    val itemShape = RoundedCornerShape(14.dp)
    val isFocusedAndSelected = isSelected && isFocused
    val scale by animateFloatAsState(
        targetValue = when {
            isFocusedAndSelected -> 1.065f
            isFocused -> 1.055f
            isSelected -> 1.01f
            else -> 1f
        },
        animationSpec = tween(180),
        label = "tvCategoryScale"
    )
    val focusShadowElevation by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 18.dp
            isFocused -> 14.dp
            isSelected -> 4.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "tvCategoryShadow"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFF795238)
            isFocused -> Color(0xFF5C3F2E)
            isSelected -> Color(0xFF2F241E)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvCategoryBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFFFFD9BB)
            isFocused -> Color(0xFFFFC08F)
            isSelected -> Color(0xFF8D6C57)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvCategoryBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 4.dp
            isFocused -> 3.5.dp
            isSelected -> 1.5.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "tvCategoryBorderWidth"
    )
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> Color(0xFFFFE1C8)
            isFocused -> Color(0xFFFFC89B)
            isSelected -> Color(0xFFD8A787)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "tvCategoryIndicator"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected -> 12.dp
            isFocused -> 10.dp
            isSelected -> 5.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "tvCategoryIndicatorWidth"
    )
    val textColor = when {
        isFocusedAndSelected -> Color(0xFFFFFCF8)
        isFocused -> Color(0xFFFFF7F0)
        isSelected -> Color(0xFFFFDEC8)
        else -> ImaxColors.TextSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = itemShape
                clip = false
                shadowElevation = focusShadowElevation.toPx()
            }
            .clip(itemShape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, itemShape)
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(if (indicatorWidth > 0.dp) 12.dp else 6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = when {
                isFocusedAndSelected -> FontWeight.Bold
                isFocused -> FontWeight.Bold
                isSelected -> FontWeight.SemiBold
                else -> FontWeight.Medium
            },
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
