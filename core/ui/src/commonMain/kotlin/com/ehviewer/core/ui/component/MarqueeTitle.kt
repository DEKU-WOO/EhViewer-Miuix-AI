package com.ehviewer.core.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text

/**
 * 单行跑马灯标题（短标题静止，超长标题停顿 1.2 秒后慢速平滑滚动，并在左右两端叠加柔和透明渐隐边缘）。
 */
@Composable
fun FadingMarqueeTitle(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    edgeFadeWidth: Dp = 12.dp,
    contentAlignment: Alignment = Alignment.CenterStart,
) {
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var textWidthPx by remember(text) { mutableIntStateOf(0) }
    val isOverflowing = containerWidthPx > 0 && textWidthPx > containerWidthPx

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (isOverflowing) {
                    val width = size.width
                    val fadePx = edgeFadeWidth.toPx().coerceAtMost(width * 0.25f)
                    if (width > 0f && fadePx > 0f) {
                        val stop = (fadePx / width).coerceIn(0f, 0.45f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                stop to Color.Black,
                                (1f - stop) to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
            },
        contentAlignment = contentAlignment,
    ) {
        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    iterations = 3,
                    initialDelayMillis = 1200,
                    velocity = 30.dp,
                )
                .padding(horizontal = if (isOverflowing) 4.dp else 0.dp)
                .onSizeChanged { textWidthPx = it.width },
        )
    }
}
