package com.ehviewer.core.shell.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ehviewer.core.ui.component.DropdownFilterChip
import com.ehviewer.core.ui.component.LiquidGlassSurface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 下拉选择 chip 数据面（语言/最低评分一类：固定候选项 + 选中下标）
data class FilterDropdownSpec(
    val label: String,
    val items: List<String>,
    val selectedIndex: Int,
    val onSelectIndex: (Int) -> Unit,
)

// 开关 chip 数据面（高级搜索 on/off 项）
data class FilterToggleSpec(
    val label: String,
    val selected: Boolean,
    val onToggle: () -> Unit,
)

// 动作 chip 数据面（如页数区间：点击唤起宿主编辑对话框）
data class FilterActionSpec(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

private val FilterRowHorizontalGap = 8.dp
private val FilterRowVerticalGap = 8.dp
private val FilterChipMinHorizontalPadding = 8.dp
private val FilterChipVerticalPadding = 7.dp

/**
 * 固定外部间隙 + 剩余宽度均分到同行胶囊内部左右内边距的自适应行布局。
 *
 * 先通过 `maxIntrinsicWidth` 获取行内 N 个胶囊的固有宽度之和，再将扣除固定间隙后的剩余可用宽度
 * 均分给每个胶囊（文字居中即自动转化为对称左右内边距），保证所有行的左右两端 1:1 严丝合缝对齐；
 * 极端长文本超出整行宽度时优先收缩超长项并单行省略号截断，保留最小内边距保护。
 */
@Composable
private fun EqualInnerPaddingChipRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = FilterRowHorizontalGap,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val count = measurables.size
        if (count == 0) {
            return@Layout layout(constraints.minWidth, 0) {}
        }
        val gapPx = horizontalGap.roundToPx()
        val rowWidth = constraints.maxWidth
        val totalGaps = gapPx * (count - 1)
        val availableWidth = (rowWidth - totalGaps).coerceAtLeast(0)

        val intrinsics = IntArray(count) { i ->
            measurables[i].maxIntrinsicWidth(Constraints.Infinity).coerceAtLeast(0)
        }
        val totalIntrinsic = intrinsics.sum()
        val targetWidths = IntArray(count)

        if (totalIntrinsic <= availableWidth) {
            // 正常情况：将剩余空白均分给每个胶囊，余数像素均匀补齐以保证整行左右边缘严格齐平
            val delta = availableWidth - totalIntrinsic
            val baseExtra = delta / count
            val remainder = delta % count
            for (i in 0 until count) {
                targetWidths[i] = intrinsics[i] + baseExtra + if (i < remainder) 1 else 0
            }
        } else {
            // 极端长文本保护：不超过均分宽度的短项保留固有宽度，超长项按比例分摊剩余宽度并单行省略
            val fairShare = availableWidth / count
            var fixedSum = 0
            var overflowSum = 0
            for (i in 0 until count) {
                if (intrinsics[i] <= fairShare) {
                    fixedSum += intrinsics[i]
                } else {
                    overflowSum += intrinsics[i]
                }
            }
            val remainingForOverflow = (availableWidth - fixedSum).coerceAtLeast(0)
            var assigned = 0
            val overflowIndices = mutableListOf<Int>()
            for (i in 0 until count) {
                if (intrinsics[i] <= fairShare) {
                    targetWidths[i] = intrinsics[i]
                    assigned += targetWidths[i]
                } else {
                    overflowIndices.add(i)
                    val w = if (overflowSum > 0) {
                        (remainingForOverflow.toLong() * intrinsics[i] / overflowSum).toInt()
                    } else {
                        fairShare
                    }
                    targetWidths[i] = w.coerceAtLeast(0)
                    assigned += targetWidths[i]
                }
            }
            var leftover = availableWidth - assigned
            var idx = 0
            while (leftover > 0 && overflowIndices.isNotEmpty()) {
                val targetIndex = overflowIndices[idx % overflowIndices.size]
                targetWidths[targetIndex] += 1
                leftover -= 1
                idx += 1
            }
        }

        val placeables = List(count) { i ->
            val w = targetWidths[i].coerceAtLeast(0)
            measurables[i].measure(
                Constraints(
                    minWidth = w,
                    maxWidth = w,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                ),
            )
        }
        val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
        layout(rowWidth, rowHeight) {
            var x = 0
            for (placeable in placeables) {
                val y = (rowHeight - placeable.height) / 2
                placeable.placeRelative(x, y)
                x += placeable.width + gapPx
            }
        }
    }
}

/**
 * 全局筛选面板（同时承载于全局筛选悬浮窗与搜索输入展开页筛选区）：
 * 取消 FlowRow 自动折行，严格按固定 7 行排列，每行使用 [EqualInnerPaddingChipRow]
 * 实现固定外部间隙、同行对称均分内边距与 7 行左右外边缘严格对齐。
 */
@Composable
fun SearchFilterPanel(
    categories: List<CategoryChipSpec>,
    selectedCategory: Int,
    allCategoriesLabel: String,
    onCategoryChange: (Int) -> Unit,
    dropdowns: List<FilterDropdownSpec>,
    toggles: List<FilterToggleSpec>,
    actions: List<FilterActionSpec>,
    modifier: Modifier = Modifier,
) {
    val isAllSelected = selectedCategory == CategoryChipFlags.ALL || selectedCategory <= 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(FilterRowVerticalGap),
    ) {
        // 第 1 行：全部、DOUJINSHI、MANGA
        EqualInnerPaddingChipRow {
            CategoryAllPill(
                selectedCategory = selectedCategory,
                allLabel = allCategoriesLabel,
                onSelectCategory = onCategoryChange,
                horizontalPadding = FilterChipMinHorizontalPadding,
                verticalPadding = FilterChipVerticalPadding,
            )
            categories.drop(0).take(2).forEach { spec ->
                CategoryPill(
                    spec = spec,
                    selected = selectedCategory > 0 && selectedCategory and spec.flag != 0,
                    isAllSelected = isAllSelected,
                    currentSelection = selectedCategory,
                    onSelectCategory = onCategoryChange,
                    horizontalPadding = FilterChipMinHorizontalPadding,
                    verticalPadding = FilterChipVerticalPadding,
                )
            }
        }

        // 第 2 行：ARTIST CG、GAME CG、COSPLAY
        val row2 = categories.drop(2).take(3)
        if (row2.isNotEmpty()) {
            EqualInnerPaddingChipRow {
                row2.forEach { spec ->
                    CategoryPill(
                        spec = spec,
                        selected = selectedCategory > 0 && selectedCategory and spec.flag != 0,
                        isAllSelected = isAllSelected,
                        currentSelection = selectedCategory,
                        onSelectCategory = onCategoryChange,
                        horizontalPadding = FilterChipMinHorizontalPadding,
                        verticalPadding = FilterChipVerticalPadding,
                    )
                }
            }
        }

        // 第 3 行：WESTERN、NON-H、IMAGE SET
        val row3 = categories.drop(5).take(3)
        if (row3.isNotEmpty()) {
            EqualInnerPaddingChipRow {
                row3.forEach { spec ->
                    CategoryPill(
                        spec = spec,
                        selected = selectedCategory > 0 && selectedCategory and spec.flag != 0,
                        isAllSelected = isAllSelected,
                        currentSelection = selectedCategory,
                        onSelectCategory = onCategoryChange,
                        horizontalPadding = FilterChipMinHorizontalPadding,
                        verticalPadding = FilterChipVerticalPadding,
                    )
                }
            }
        }

        // 第 4 行：ASIAN PORN、MISC
        val row4 = categories.drop(8)
        if (row4.isNotEmpty()) {
            EqualInnerPaddingChipRow {
                row4.forEach { spec ->
                    CategoryPill(
                        spec = spec,
                        selected = selectedCategory > 0 && selectedCategory and spec.flag != 0,
                        isAllSelected = isAllSelected,
                        currentSelection = selectedCategory,
                        onSelectCategory = onCategoryChange,
                        horizontalPadding = FilterChipMinHorizontalPadding,
                        verticalPadding = FilterChipVerticalPadding,
                    )
                }
            }
        }

        // 第 5 行：语言、星标、页数（动态显示所选语言、星级与页数范围）
        if (dropdowns.isNotEmpty() || actions.isNotEmpty()) {
            EqualInnerPaddingChipRow {
                dropdowns.forEach { spec ->
                    DropdownFilterChip(
                        label = spec.label,
                        menuItems = spec.items,
                        selectedItemIndex = spec.selectedIndex,
                        onSelectedItemIndexChange = spec.onSelectIndex,
                        fillWidth = true,
                    )
                }
                actions.forEach { spec ->
                    FilterChipPill(
                        label = spec.label,
                        selected = spec.selected,
                        role = Role.Button,
                        onClick = spec.onClick,
                    )
                }
            }
        }

        // 第 6 行：已删除、有种子、禁用排除项：语言
        val row6 = toggles.take(3)
        if (row6.isNotEmpty()) {
            EqualInnerPaddingChipRow {
                row6.forEach { spec ->
                    FilterChipPill(
                        label = spec.label,
                        selected = spec.selected,
                        role = Role.Checkbox,
                        onClick = spec.onToggle,
                    )
                }
            }
        }

        // 第 7 行：禁用排除项：上传者、禁用排除项：标签
        val row7 = toggles.drop(3)
        if (row7.isNotEmpty()) {
            EqualInnerPaddingChipRow {
                row7.forEach { spec ->
                    FilterChipPill(
                        label = spec.label,
                        selected = spec.selected,
                        role = Role.Checkbox,
                        onClick = spec.onToggle,
                    )
                }
            }
        }
    }
}

// 筛选 chip 胶囊：选中态统一实心主题强调色底色 + 纯白加粗高对比文字，未选中态保持常规玻璃胶囊样式
@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    role: Role,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val textColor = if (selected) Color.White else MiuixTheme.colorScheme.onSurface
    val handleClick = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
    if (selected) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary, CircleShape)
                .clickable(role = role, onClick = handleClick)
                .padding(horizontal = FilterChipMinHorizontalPadding, vertical = FilterChipVerticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        LiquidGlassSurface(
            shape = CircleShape,
            containerColor = MiuixTheme.colorScheme.surfaceContainerHigh,
            elevation = 1.dp,
            refractionHeight = 8.dp,
            refractionAmount = 8.dp,
            modifier = modifier
                .clip(CircleShape)
                .clickable(role = role, onClick = handleClick),
        ) {
            Box(
                modifier = Modifier.padding(
                    horizontal = FilterChipMinHorizontalPadding,
                    vertical = FilterChipVerticalPadding,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
