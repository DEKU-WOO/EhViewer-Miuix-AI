package com.hippo.ehviewer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.component.SquircleShape
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CheckableItem(
    checked: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (MutableInteractionSource) -> Unit,
) {
    val src = controlledInteractionSource(enabled = checked)
    Box(modifier) {
        content(src)
        if (checked) {
            // 选中态高亮遮罩：半透明主题色覆盖整张卡片，仅绘制不拦截触摸，卡片仍可正常点击
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(SquircleShape(12.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
            )
            com.ehviewer.core.ui.component.CheckIndicator(
                selected = true,
                role = Role.Checkbox,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}

@Composable
@Stable
fun collectDetailSizeAsState(): State<Dp> {
    val long = dimensionResource(R.dimen.gallery_list_column_width_long)
    val short = dimensionResource(R.dimen.gallery_list_column_width_short)
    return Settings.detailSize.collectAsState {
        when (it) {
            0 -> long
            1 -> short
            else -> error("Unexpected value: $it")
        }
    }
}

@Composable
@Stable
fun collectListThumbSizeAsState(): State<Dp> {
    val density = LocalDensity.current
    return Settings.listThumbSize.collectAsState {
        with(density) {
            // 全局画廊列表卡片高度缩减为原高度的 80%（0.8f 倍），左侧封面按比例同步缩小
            ((it.toDp() * 9).coerceAtLeast(120.dp) * 0.8f).coerceAtLeast(96.dp)
        }
    }
}

@Composable
private fun controlledInteractionSource(enabled: Boolean): MutableInteractionSource {
    val update by rememberUpdatedState(newValue = enabled)
    return remember {
        val impl = MutableInteractionSource()
        val controlled = snapshotFlow { update }.map { enable ->
            if (enable) enter else exit
        }
        object : MutableInteractionSource by impl {
            override val interactions = merge(controlled, impl.interactions)
        }
    }
}

private val enter = HoverInteraction.Enter()
private val exit = HoverInteraction.Exit(enter)
