package com.ehviewer.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    fillWidth: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    val isSelected = selectedItemIndex != 0
    val chipText = if (selectedItemIndex == 0) label else menuItems.getOrElse(selectedItemIndex) { label }

    // 选中态统一采用实心主题强调色底色 + 纯白高对比度文字，与上方分类胶囊视觉一致
    val contentColor = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
    val widthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    val horizontalPad = if (fillWidth) 8.dp else 12.dp

    val chipContent: @Composable () -> Unit = {
        Row(
            modifier = widthModifier.padding(horizontal = horizontalPad, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chipText,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = if (isSelected) 0.9f else 0.7f),
                modifier = Modifier.size(12.dp),
            )
        }
    }

    Box(modifier = modifier) {
        if (isSelected) {
            Box(
                modifier = widthModifier
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary, CircleShape)
                    .glassPopupAnchor(anchor, cornerRadius = 24.dp)
                    .clickable(role = Role.Button) { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                chipContent()
            }
        } else {
            LiquidGlassSurface(
                shape = CircleShape,
                containerColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                elevation = 1.dp,
                refractionHeight = 8.dp,
                refractionAmount = 8.dp,
                modifier = widthModifier
                    .clip(CircleShape)
                    .glassPopupAnchor(anchor, cornerRadius = 24.dp)
                    .clickable(role = Role.Button) { expanded = true },
            ) {
                chipContent()
            }
        }

        if (expanded) {
            // 承载在独立 Popup 窗口中，保证固定行/对话框内的 chip 也能获得整屏锚点形变菜单
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
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
