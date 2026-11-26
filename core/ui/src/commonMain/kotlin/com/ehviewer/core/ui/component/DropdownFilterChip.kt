package com.ehviewer.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DropdownFilterChip(
    label: String,
    menuItems: List<String>,
    selectedItemIndex: Int,
    onSelectedItemIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    val isSelected = selectedItemIndex != 0
    val chipText = if (selectedItemIndex == 0) label else menuItems.getOrElse(selectedItemIndex) { label }

    val backgroundColor = if (isSelected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (isSelected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    Box(modifier = modifier) {
        LiquidGlassSurface(
            shape = CircleShape,
            containerColor = backgroundColor,
            elevation = if (isSelected) 2.dp else 1.dp,
            refractionHeight = 8.dp,
            refractionAmount = 8.dp,
            modifier = Modifier
                .clip(CircleShape)
                .glassPopupAnchor(anchor, cornerRadius = 24.dp)
                .clickable(role = Role.Button) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chipText,
                    color = contentColor,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        if (expanded) {
            // 承载在独立 Popup 窗口中，保证 FlowRow/对话框内的 chip 也能获得整屏锚点形变菜单
            Popup(
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(Modifier.fillMaxSize()) {
                    GlassTransformPopup(
                        show = expanded,
                        onDismissRequest = { expanded = false },
                        anchor = anchor,
                        backdrop = LocalBackdrop.current,
                        visuals = liquidGlassPopupVisuals(),
                        anchorContent = {
                            Text(
                                text = chipText,
                                color = contentColor,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        sizing = GlassPopupSizing(minWidth = 160.dp, maxWidth = 280.dp),
                    ) {
                        menuItems.forEachIndexed { index, item ->
                            GlassPopupItem(
                                text = item,
                                selected = index == selectedItemIndex,
                                onClick = {
                                    onSelectedItemIndexChange(index)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
