package com.ehviewer.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.scrollbar.ScrollbarAdapter
import androidx.compose.foundation.scrollbar.ScrollbarStyle
import androidx.compose.foundation.scrollbar.VerticalScrollbar
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.util.excludeSystemGesture
import kotlinx.coroutines.flow.collectLatest

@Composable
fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    isScrollInProgress: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
    reverseLayout: Boolean = false,
) {
    val isScrolling by rememberUpdatedState(isScrollInProgress)
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scrollbarAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { isDragged || isHovered || isScrolling }.collectLatest {
            if (it) {
                scrollbarAlpha.animateTo(1f, FadeInAnimationSpec)
            } else {
                scrollbarAlpha.animateTo(0f, FadeOutAnimationSpec)
            }
        }
    }
    // 正常滚动 0.85 透明度，手指按住拖动时恢复完全不透明
    val thumbAlpha = if (isDragged) 1f else ScrollbarIdleAlpha
    val isVisible by remember { derivedStateOf { scrollbarAlpha.value > 0f } }
    val scrollbarModifier = if (isVisible && !isDragged && !isScrollInProgress) {
        modifier.excludeSystemGesture()
    } else {
        modifier
    }
    VerticalScrollbar(
        adapter = adapter,
        modifier = scrollbarModifier
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .graphicsLayer { alpha = scrollbarAlpha.value * thumbAlpha },
        reverseLayout = reverseLayout,
        interactionSource = interactionSource,
    )
}

// 滚动条样式：静息/悬停双色分离（悬停加深为桌面惯例），由桌面根节点以 Miuix 配色注入 LocalScrollbarStyle
fun scrollbarStyle(thumbColor: Color, hoverThumbColor: Color) = ScrollbarStyle(
    minimalHeight = ThumbLength,
    thickness = ThumbThickness,
    padding = ThumbPadding,
    shape = CircleShape,
    hoverDurationMillis = 300,
    unhoverColor = thumbColor,
    hoverColor = hoverThumbColor,
)

// 滑块绘制宽度 4.dp，距屏幕右边缘 3.dp 安全边距（padding 同时用于拓宽抓握热区）
private val ThumbThickness = 4.dp
private val ThumbPadding = 3.dp
private val ThumbLength = 48.dp
private const val ScrollbarIdleAlpha = 0.85f

// 开始滚动 200ms 平滑淡入；停止滚动后保持 900ms，再用 480ms 柔和缓出渐隐
private val FadeInAnimationSpec = tween<Float>(
    durationMillis = 200,
    easing = FastOutSlowInEasing,
)
private val FadeOutAnimationSpec = tween<Float>(
    durationMillis = 480,
    delayMillis = 900,
    easing = FastOutSlowInEasing,
)
