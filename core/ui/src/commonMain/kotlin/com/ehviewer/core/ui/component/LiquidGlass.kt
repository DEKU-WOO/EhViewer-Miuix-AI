package com.ehviewer.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.liquid.lens
import com.ehviewer.core.ui.liquid.vibrancy
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupVisuals
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.material.EdgeLightSource
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DefaultBlurRadius = 20.dp
private val DefaultCornerRadius = 24.dp
private const val DEFAULT_REFRACTION_RATIO = 0.12f
private const val DEFAULT_CHROMATIC_ABERRATION = 0.15f
private const val PRESSED_SCALE = 0.96f

val LiquidGlassHighlightLight = EdgeStroke(
    width = 1.dp,
    bevel = 3.0.dp,
    color = Color.White.copy(alpha = 0.10f),
    // 左上主高光 + 右下副反射，与底部导航栏保持同一斜向光源
    primary = EdgeLightSource(0.25f, 0.3f, -0.5f, intensity = 0.75f),
    secondary = EdgeLightSource(0.8f, 0.85f, -0.5f, intensity = 0.32f),
    peak = EdgePeak.Dual,
)

val LiquidGlassHighlightDark = EdgeStroke(
    width = 0.9.dp,
    bevel = 2.4.dp,
    color = Color.White.copy(alpha = 0.16f),
    primary = EdgeLightSource(0.25f, 0.3f, -0.5f, intensity = 1f),
    secondary = EdgeLightSource(0.8f, 0.85f, -0.45f, intensity = 0.42f),
    peak = EdgePeak.Dual,
)

/**
 * 底部悬浮导航胶囊使用的高光规范；搜索栏、多选圆钮等需要与导航栏保持同一轮廓与描边粗细。
 */
val LiquidGlassNavHighlight = EdgeStroke(
    width = 1.dp,
    bevel = 2.2.dp,
    color = Color.White.copy(alpha = 0.16f),
    primary = EdgeLightSource(0.25f, 0.3f, -0.05f, intensity = 1.1f),
    secondary = EdgeLightSource(0.8f, 0.85f, -0.5f, intensity = 0.48f),
    peak = EdgePeak.Dual,
)

/** 弹层统一使用更清晰的斜向双峰高光，仍保持细腻而非白色硬边。 */
val LiquidGlassOverlayHighlight = EdgeStroke(
    width = 1.dp,
    bevel = 2.8.dp,
    color = Color.White.copy(alpha = 0.18f),
    primary = EdgeLightSource(0.25f, 0.3f, -0.5f, intensity = 1.05f),
    secondary = EdgeLightSource(0.8f, 0.85f, -0.5f, intensity = 0.46f),
    peak = EdgePeak.Dual,
)

/**
 * 弹窗/菜单统一的高通透玻璃材质。
 *
 * 最后一层使用 SrcOver 直接控制表面遮罩强度：深色 62%、浅色 68%，剩余部分实时透出
 * 背后的模糊色彩；前两层保留 MIUIX 的明暗与饱和度处理，避免封面过亮时文字失去对比度。
 */
@Composable
fun liquidGlassOverlayMaterial(dialog: Boolean = false): GlassMaterial {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val base = if (dialog) GlassMaterials.dialogGlass(isDark) else GlassMaterials.popupViewGlass(isDark)
    val surface = if (isDark) MiuixTheme.colorScheme.surfaceContainer else Color.White
    return base.copy(
        blurRadius = 48.dp,
        third = GlassColorLayer(
            color = surface.copy(alpha = if (isDark) 0.62f else 0.68f),
            mode = GlassColorBlendMode.SrcOver,
        ),
    )
}

@Composable
fun liquidGlassPopupVisuals(): GlassPopupVisuals = GlassPopupDefaults.visuals(
    material = liquidGlassOverlayMaterial(),
    stroke = LiquidGlassOverlayHighlight,
)

/** 全应用对话框统一入口：仅定制玻璃表面，标题、内容与交互仍由调用方原样提供。 */
@Composable
fun LiquidGlassDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    maxWidth: Dp = DialogDefaults.MaxWidth,
    cornerRadius: Dp? = null,
    backgroundColor: Color = GlassDialogDefaults.containerColor(),
    enableWindowDim: Boolean = true,
    content: @Composable () -> Unit,
) = GlassDialog(
    show = show,
    onDismissRequest = onDismissRequest,
    backdrop = LocalBackdrop.current,
    modifier = modifier,
    enableWindowDim = enableWindowDim,
    title = title,
    summary = summary,
    maxWidth = maxWidth,
    cornerRadius = cornerRadius,
    material = liquidGlassOverlayMaterial(dialog = true),
    stroke = LiquidGlassOverlayHighlight,
    containerColor = backgroundColor,
    content = content,
)

/** 全应用底部抽屉统一入口；沿用原 WindowBottomSheet 的内容边距，避免改变页内布局。 */
@Composable
fun LiquidGlassBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    enableWindowDim: Boolean = true,
    content: @Composable () -> Unit,
) = GlassBottomSheet(
    show = show,
    onDismissRequest = onDismissRequest,
    backdrop = LocalBackdrop.current,
    modifier = modifier,
    enableWindowDim = enableWindowDim,
    progressiveBlur = false,
    insideMargin = BottomSheetDefaults.insideMargin,
    material = liquidGlassOverlayMaterial(dialog = true),
    stroke = LiquidGlassOverlayHighlight,
) {
    content()
}

val LocalBackdrop = compositionLocalOf<LayerBackdrop?> { null }

fun Modifier.blurBackdropSource(backdrop: LayerBackdrop?): Modifier = if (backdrop != null) this.layerBackdrop(backdrop) else this

fun Modifier.backdropBlur(
    backdrop: Backdrop?,
    shape: Shape = RoundedCornerShape(0.dp),
    blurRadius: Dp = 20.dp,
    tintColor: Color = Color.Unspecified,
    tintAlpha: Float = 0.96f,
): Modifier = if (backdrop != null) {
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(blurRadius.toPx(), blurRadius.toPx())
        },
        onDrawSurface = {
            val color = if (tintColor != Color.Unspecified) tintColor else Color.White
            drawRect(color.copy(alpha = tintAlpha))
        },
    )
} else {
    val color = if (tintColor != Color.Unspecified) tintColor else Color.White
    this.background(color.copy(alpha = tintAlpha), shape)
}

@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop?,
    shape: Shape = SquircleShape(32.dp),
    blurRadius: Dp = 20.dp,
    tintColor: Color = Color.Unspecified,
    tintAlpha: Float = 0.45f,
): Modifier {
    // 高光随当前主题取明/暗预设，与 LiquidGlass、LiquidGlassSurface 保持一致
    val highlight = if (MiuixTheme.colorScheme.surface.luminance() < 0.5f) {
        LiquidGlassHighlightDark
    } else {
        LiquidGlassHighlightLight
    }
    return if (backdrop != null && isRuntimeShaderSupported()) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                padding = maxOf(padding, 40.dp.toPx())
                vibrancy()
                blur(blurRadius.toPx(), blurRadius.toPx())
                lens(16.dp.toPx(), 20.dp.toPx())
            },
            onDrawSurface = {
                val color = if (tintColor != Color.Unspecified) tintColor else Color.White
                drawRect(color.copy(alpha = tintAlpha))
            },
        ).edgeStroke(shape = shape, stroke = highlight)
    } else if (backdrop != null) {
        this.backdropBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            tintColor = tintColor,
            tintAlpha = tintAlpha,
        )
    } else {
        val color = if (tintColor != Color.Unspecified) tintColor else Color.White
        this.background(color.copy(alpha = tintAlpha), shape)
    }
}

@Composable
fun LiquidGlass(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = DefaultCornerRadius,
    blurRadius: Dp = DefaultBlurRadius,
    refractionRatio: Float = DEFAULT_REFRACTION_RATIO,
    chromaticAberration: Float = DEFAULT_CHROMATIC_ABERRATION,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    if (backdrop == null || !isRuntimeShaderSupported() || !enabled) {
        Box(modifier = modifier.clip(shape), content = content)
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        pressScale.animateTo(
            targetValue = if (isPressed) PRESSED_SCALE else 1f,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = 400f,
            ),
        )
    }

    val highlight = remember(isDark) {
        if (isDark) LiquidGlassHighlightDark else LiquidGlassHighlightLight
    }

    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val refractionAmountPx = blurRadiusPx * refractionRatio.coerceIn(0.08f, 0.15f)

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    padding = maxOf(padding, 40.dp.toPx())
                    vibrancy()
                    blur(blurRadiusPx, blurRadiusPx)
                    lens(
                        refractionHeight = blurRadiusPx * 0.9f,
                        refractionAmount = refractionAmountPx,
                        depthEffect = true,
                        chromaticAberration = chromaticAberration,
                    )
                },
                onDrawSurface = {
                    drawRect(
                        color = if (isDark) {
                            Color.White.copy(alpha = 0.03f)
                        } else {
                            Color.White.copy(alpha = 0.06f)
                        },
                    )
                },
            )
            .edgeStroke(shape = shape, stroke = highlight),
        content = content,
    )
}

@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
    tint: Color = MiuixTheme.colorScheme.onSurface,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(
                backdrop = backdrop,
                shape = shape,
                tintColor = surfaceColor,
                tintAlpha = 0.50f,
            )
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
