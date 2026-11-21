package com.hippo.ehviewer.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.component.glassRim
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 澎湃 OS 风格的锚点弹簧下拉菜单。
 *
 * 与 [top.yukonga.miuix.kmp.preference.OverlayDropdownPreference] 的触发条外观保持一致，
 * 但菜单使用自绘的 [Popup]：从触发条目右侧的锚点出发，通过 spring 阻尼曲线驱动
 * 宽高缩放与圆角连续形变，并在暗色模式下叠加 [glassRim] 微光玻璃描边。
 */
@Composable
fun AnchorSpringDropdown(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    showValue: Boolean = true,
    enabled: Boolean = true,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    val itemsNotEmpty = items.isNotEmpty()
    val actualEnabled = enabled && itemsNotEmpty
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    BasicComponent(
        modifier = modifier.onGloballyPositioned { anchorBounds = it.boundsInWindow() },
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        summary = summary,
        endActions = {
            if (showValue) {
                val text = items.getOrNull(selectedIndex)
                if (!text.isNullOrEmpty()) {
                    Text(
                        text = text,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .align(Alignment.CenterVertically)
                            .weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End,
                    )
                }
            }
            DropdownArrowEndAction(actionColor = actionColor)
        },
        onClick = {
            if (actualEnabled && anchorBounds != null) {
                expanded = !expanded
                if (expanded) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        },
        role = Role.DropdownList,
        holdDownState = expanded,
        enabled = actualEnabled,
    )

    anchorBounds?.let { anchor ->
        SpringDropdownPopup(
            show = expanded,
            anchorRect = anchor,
            items = items,
            selectedIndex = selectedIndex,
            onDismiss = { expanded = false },
            onSelected = { index ->
                if (index != selectedIndex) onSelectedIndexChange(index)
                expanded = false
            },
        )
    }
}

@Composable
private fun SpringDropdownPopup(
    show: Boolean,
    anchorRect: Rect,
    items: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val topInset = WindowInsets.statusBars.getTop(density)
    val bottomInset = WindowInsets.navigationBars.getBottom(density)
    val marginPx = with(density) { 8.dp.roundToPx() }
    val sideMarginPx = with(density) { 12.dp.roundToPx() }

    val progress = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(show) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(show) {
        if (show) {
            mounted = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } else if (mounted) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 1f,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            mounted = false
        }
    }

    if (!mounted) return

    val positionProvider = remember(
        anchorRect,
        windowWidth,
        windowHeight,
        topInset,
        bottomInset,
        marginPx,
        sideMarginPx,
        layoutDirection,
    ) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = resolveDropdownOffset(
                anchor = anchorRect,
                windowWidth = windowWidth,
                windowHeight = windowHeight,
                contentWidth = popupContentSize.width,
                contentHeight = popupContentSize.height,
                marginPx = marginPx,
                sideMarginPx = sideMarginPx,
                topInset = topInset,
                bottomInset = bottomInset,
            )
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val menuShape = remember {
            MorphCornerShape(progress = { progress.value }, fromRadius = 26.dp, toRadius = 16.dp)
        }
        val anchorWidth = anchorRect.width
        val anchorHeight = anchorRect.height
        val startScaleX = if (menuSize.width > 0) {
            (anchorWidth / menuSize.width).coerceIn(0.25f, 0.9f)
        } else {
            0.35f
        }
        val startScaleY = if (menuSize.height > 0) {
            (anchorHeight / menuSize.height).coerceIn(0.08f, 0.6f)
        } else {
            0.15f
        }
        val safeBottom = windowHeight - bottomInset
        val fitsBelow = anchorRect.bottom + marginPx + menuSize.height <= safeBottom
        val fitsAbove = anchorRect.top - marginPx - menuSize.height >= topInset
        val showBelow = menuSize == IntSize.Zero || fitsBelow || !fitsAbove

        Column(
            modifier = Modifier
                .widthIn(min = 200.dp, max = 288.dp)
                .onSizeChanged { menuSize = it }
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1.02f)
                    alpha = progress.value.coerceIn(0f, 1f)
                    scaleX = startScaleX + (1f - startScaleX) * p
                    scaleY = startScaleY + (1f - startScaleY) * p
                    transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = if (showBelow) 0f else 1f)
                }
                .glassRim(cornerRadius = 16.dp)
                .background(color = MiuixTheme.colorScheme.surfaceContainer, shape = menuShape)
                .clip(menuShape)
                .padding(6.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .clip(SquircleShape(10.dp))
                            .background(
                                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else MiuixTheme.colorScheme.surfaceContainer,
                            )
                            .clickable { onSelected(index) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item,
                            style = MiuixTheme.textStyles.body1,
                            color = if (selected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceContainer
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = MiuixIcons.Basic.Check,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 计算锚点下方/上方的菜单位置：优先贴住锚点条目，空间不足时向上翻转，并夹取在安全区内。
 */
private fun resolveDropdownOffset(
    anchor: Rect,
    windowWidth: Int,
    windowHeight: Int,
    contentWidth: Int,
    contentHeight: Int,
    marginPx: Int,
    sideMarginPx: Int,
    topInset: Int,
    bottomInset: Int,
): IntOffset {
    val anchorRight = anchor.right.roundToInt()
    val anchorTop = anchor.top.roundToInt()
    val anchorBottom = anchor.bottom.roundToInt()
    val safeBottom = windowHeight - bottomInset

    val fitsBelow = anchorBottom + marginPx + contentHeight <= safeBottom
    val fitsAbove = anchorTop - marginPx - contentHeight >= topInset
    val y = when {
        fitsBelow || !fitsAbove -> anchorBottom + marginPx
        else -> anchorTop - marginPx - contentHeight
    }.coerceIn(
        topInset,
        (safeBottom - contentHeight).coerceAtLeast(topInset),
    )

    val x = (anchorRight - contentWidth).coerceIn(
        sideMarginPx,
        (windowWidth - contentWidth - sideMarginPx).coerceAtLeast(sideMarginPx),
    )
    return IntOffset(x, y)
}

/**
 * 圆角随弹簧进度从 [fromRadius] 连续插值到 [toRadius] 的 Shape，避免逐帧重组。
 */
private class MorphCornerShape(
    private val progress: () -> Float,
    private val fromRadius: Dp,
    private val toRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = lerp(fromRadius, toRadius, progress().coerceIn(0f, 1f))
        val radiusPx = with(density) { radius.toPx() }
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radiusPx),
            ),
        )
    }
}
