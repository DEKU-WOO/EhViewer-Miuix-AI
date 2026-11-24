package com.ehviewer.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.liquid.lens
import com.ehviewer.core.ui.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 澎湃 OS 风格的弹窗微光描边（Rim Highlight）。
 *
 * 暗色模式下沿容器内缘绘制 0.8.dp、12%~18% 白色渐变高光，
 * 让纯黑背景上的弹窗呈现玻璃悬浮边缘。
 *
 * @param cornerRadius 容器圆角，需与弹窗自身圆角一致。
 * @param topOnly 底部弹窗只描顶部边缘，避免在屏幕底部画出多余圆角。
 */
@Composable
fun Modifier.glassRim(cornerRadius: Dp = 32.dp, topOnly: Boolean = false): Modifier {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    if (!isDark) return this
    return drawWithContent {
        drawContent()
        val stroke = 0.8.dp.toPx()
        val radiusPx = cornerRadius.toPx()
        val rimSize = Size(size.width - stroke, size.height - stroke)
        val brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.18f),
            0.55f to Color.White.copy(alpha = 0.10f),
            1f to Color.White.copy(alpha = 0.06f),
        )
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val radius = CornerRadius(radiusPx)
        if (topOnly) {
            clipRect(0f, 0f, size.width, radiusPx * 1.5f) {
                drawRoundRect(brush, topLeft, rimSize, radius, style = Stroke(stroke))
            }
        } else {
            drawRoundRect(brush, topLeft, rimSize, radius, style = Stroke(stroke))
        }
    }
}

/**
 * 通用液态玻璃材质容器 (Liquid Glass Surface)。
 * 结合超椭圆曲率、物理透镜折射、高光边缘渲染与多级优雅降级。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(24.dp),
    backdrop: Backdrop? = LocalBackdrop.current,
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    elevation: Dp = 8.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 16.dp,
    highlight: Highlight? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val shaderSupported = isRuntimeShaderSupported() && backdrop != null

    // 高光随当前主题取明/暗预设，与 LiquidGlass、FloatingBottomBar 保持一致
    val specularHighlight = if (isDark) LiquidGlassHighlightDark else LiquidGlassHighlightLight

    val finalContainerColor = if (shaderSupported) {
        containerColor.copy(alpha = if (isDark) 0.5f else 0.65f)
    } else if (backdrop != null) {
        containerColor.copy(alpha = if (isDark) 0.7f else 0.85f)
    } else {
        containerColor
    }

    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.dropShadow(
                        shape = shape,
                        shadow = Shadow(
                            radius = elevation,
                            color = Color.Black,
                            alpha = if (isDark) 0.25f else 0.1f,
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (backdrop != null && shaderSupported) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            padding = maxOf(padding, 32.dp.toPx())
                            vibrancy()
                            blur(4.dp.toPx(), 4.dp.toPx())
                            lens(
                                refractionHeight = refractionHeight.toPx(),
                                refractionAmount = refractionAmount.toPx(),
                            )
                        },
                        highlight = { highlight ?: specularHighlight },
                        onDrawSurface = { drawRect(finalContainerColor) },
                    )
                } else if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            blur(25.dp.toPx(), 25.dp.toPx())
                        },
                        onDrawSurface = { drawRect(finalContainerColor) },
                    )
                } else {
                    Modifier
                        .background(finalContainerColor, shape)
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
                            shape = shape,
                        )
                },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
