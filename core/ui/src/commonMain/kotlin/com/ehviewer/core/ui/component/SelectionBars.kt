package com.ehviewer.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 多选模式下底部操作胶囊中的单个按钮描述。 */
data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * 澎湃 OS 4 风格多选顶栏：左侧独立正圆退出按钮 + 居中计数 + 右侧独立正圆全选按钮。
 * 材质与底部悬浮导航栏同源（[LiquidGlassSurface] + CircleShape），浅色/深色自动自适应。
 */
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onExit: () -> Unit,
    onToggleSelectAll: () -> Unit,
    countText: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    var lockedStatusBarTop by remember { mutableStateOf(0.dp) }
    val currentStatusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (currentStatusBarTop > 0.dp) {
        lockedStatusBarTop = currentStatusBarTop
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(maxOf(currentStatusBarTop, lockedStatusBarTop)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            SelectionCircleButton(
                icon = MiuixIcons.Close,
                contentDescription = null,
                onClick = onExit,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = countText,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 56.dp),
            )
            SelectionCircleButton(
                icon = MiuixIcons.SelectAll,
                contentDescription = null,
                onClick = onToggleSelectAll,
                backdrop = backdrop,
                tint = if (allSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun SelectionCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onSurface,
) {
    LiquidGlassSurface(
        shape = CircleShape,
        elevation = 4.dp,
        refractionHeight = 14.dp,
        refractionAmount = 14.dp,
        highlight = LiquidGlassNavHighlight,
        containerColor = MiuixTheme.colorScheme.surface,
        modifier = modifier.size(LiquidGlassCircleButtonSize),
        backdrop = backdrop,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(LiquidGlassCircleButtonIconSize),
            )
        }
    }
}

/**
 * 多选模式下底部悬浮操作胶囊：与主导航栏同高、同全圆角、同毛玻璃材质，
 * 内部按数量均分「图标 + 文字」，禁用项降低透明度但保持主题前景色。
 */
@Composable
fun SelectionActionBar(
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    // 与底部悬浮导航栏完全一致的落位：左右 24dp 外边距 + 同一底部安全区偏移
    val navBarBottomPadding = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomPaddingValue = if (navBarBottomPadding != 0.dp) 8.dp + navBarBottomPadding else 20.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPaddingValue, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        LiquidGlassSurface(
            shape = CircleShape,
            elevation = 6.dp,
            refractionHeight = 24.dp,
            refractionAmount = 24.dp,
            highlight = LiquidGlassNavHighlight,
            containerColor = MiuixTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            backdrop = backdrop,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    val alpha = if (action.enabled) 1f else 0.35f
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = action.enabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = action.onClick,
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = action.label,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
