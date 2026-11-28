package com.hippo.ehviewer.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.component.LocalBackdrop
import com.ehviewer.core.ui.component.Slider
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 阅读器毛玻璃弹窗内部卡片配色：柔和低不透明度自适应紫调容器，区分层级且保留背景通透感。 */
@Composable
fun readerGlassCardColors(): CardColors {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    return CardDefaults.defaultColors(
        color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.38f else 0.50f),
        contentColor = MiuixTheme.colorScheme.onSurface,
    )
}

@Composable
fun SpinnerChoice(title: String, entries: Array<String>, values: List<Int>, field: MutableState<Int>) {
    var value by field
    val items = remember(entries) { entries.toList() }
    val selectedIndex = values.indexOf(value).coerceAtLeast(0)
    GlassDropdownPreference(
        title = title,
        items = items,
        selectedIndex = selectedIndex,
        backdrop = LocalBackdrop.current,
        visuals = liquidGlassPopupVisuals(),
        showValue = true,
        onSelectedIndexChange = { index ->
            if (index in values.indices) {
                value = values[index]
            }
        },
    )
}

@Composable
fun SwitchChoice(title: String, field: MutableState<Boolean>) {
    SwitchPreference(
        checked = field.value,
        onCheckedChange = { field.value = it },
        title = title,
    )
}

@Composable
fun SliderChoice(
    startSlot: @Composable () -> Unit,
    endSlot: @Composable () -> Unit,
    range: IntRange,
    field: MutableState<Int>,
) {
    var value by field
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        startSlot()
        Slider(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.weight(1f).padding(8.dp),
            valueRange = range,
        )
        endSlot()
    }
}
