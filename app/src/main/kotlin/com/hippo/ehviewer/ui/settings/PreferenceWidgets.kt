package com.hippo.ehviewer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.component.LocalBackdrop
import com.ehviewer.core.ui.util.LocalBottomBarContentPadding
import com.ehviewer.core.ui.util.LocalWindowLayout
import com.ehviewer.core.ui.util.readableWidth
import com.hippo.ehviewer.ui.openBrowser
import com.hippo.ehviewer.util.ProgressDialog
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 卡片自身的外边距（与各页 Card 的 padding(horizontal = 12.dp, vertical = 4.dp) 保持一致）。 */
private val CardVerticalPadding = 4.dp

/** Miuix SmallTitle 默认的纵向内边距（SmallTitleDefaults.InsideMargin 的 vertical 值）。 */
private val SmallTitleDefaultVerticalPadding = 8.dp

/** 非首个分组标题距上一张白底卡片的目标间距。 */
internal val SmallTitleTopGap = 20.dp

/** 分组标题距其所属白底卡片上边缘的目标间距。 */
internal val SmallTitleBottomGap = 8.dp

/** 大标题（顶栏）与下方第一个白底卡片之间的目标间距。 */
internal val SettingsContentTopGap = 8.dp

/**
 * 设置页统一的分组小标题。
 *
 * 通过调整 Miuix SmallTitle 的 insideMargin 与外部 padding，统一「上一卡片 → 小标题 → 所属卡片」的
 * 视觉间距，避免各页使用不一致的魔数：
 * - 非首个分组：距上一张卡片 [SmallTitleTopGap]（20.dp）；
 * - 首个分组：紧接大标题下方 [SmallTitleDefaultVerticalPadding]（8.dp）；
 * - 距所属卡片上边缘统一为 [SmallTitleBottomGap]（8.dp，已抵扣卡片自身 4.dp 外边距）。
 *
 * @param first 是否为页面第一个分组（直接位于大标题下方）
 */
@Composable
internal fun SettingsSmallTitle(text: String, first: Boolean = false, modifier: Modifier = Modifier) {
    // 非首个分组额外上移量：目标间距 - 默认上内边距 - 上一卡片的外边距
    // 下间距沿用 MIUIX 默认 8.dp（已在 6~8.dp 规范内），因此无需覆盖 insideMargin
    val extraTopPadding = if (first) 0.dp else SmallTitleTopGap - SmallTitleDefaultVerticalPadding - CardVerticalPadding
    SmallTitle(
        text = text,
        modifier = modifier.padding(top = extraTopPadding),
    )
}

@Composable
fun PreferenceHeader(
    icon: ImageVector,
    @StringRes title: Int,
    childRoute: DirectionDestinationSpec,
    navigator: DestinationsNavigator,
) {
    ArrowPreference(
        title = stringResource(title),
        startAction = {
            // 固定图标占位尺寸并居中，保证不同图标库里 intrinsic 尺寸不同的图标
            // 也能让右侧标题起始横坐标与上下条目严格左对齐
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
        onClick = { navigator.navigate(childRoute) },
    )
}

@Composable
fun Preference(title: String, summary: String? = null, onClick: () -> Unit = {}) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = onClick,
    )
}

@Composable
fun SwitchPreference(title: String, summary: String? = null, state: MutableState<Boolean>, enabled: Boolean = true) {
    SwitchPreference(
        checked = state.value,
        onCheckedChange = { state.value = it },
        title = title,
        summary = summary,
        enabled = enabled,
    )
}

@Composable
fun IntSliderPreference(
    maxValue: Int,
    minValue: Int = 0,
    step: Int = maxValue - minValue - 1,
    title: String,
    summary: String? = null,
    state: MutableState<Int>,
    enabled: Boolean = true,
    display: (Int) -> Int = { it },
) {
    // 拖动过程中只更新本地状态，松手时才写回偏好设置，避免每帧写一次 DataStore
    var sliderValue by remember { mutableFloatStateOf(state.value.toFloat()) }
    LaunchedEffect(state.value) { sliderValue = state.value.toFloat() }
    SliderPreference(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { state.value = sliderValue.toInt() },
        title = title,
        summary = summary,
        valueRange = minValue.toFloat()..maxValue.toFloat(),
        steps = step,
        valueText = "${display(sliderValue.toInt())}",
        enabled = enabled,
    )
}

@Composable
fun UrlPreference(title: String, url: String) = with(LocalContext.current) {
    ArrowPreference(
        title = title,
        summary = url,
        onClick = { openBrowser(url) },
    )
}

@Composable
fun HtmlPreference(title: String, summary: AnnotatedString? = null, onClick: () -> Unit = {}) {
    BasicComponent(
        title = title,
        summary = summary?.text,
        onClick = onClick,
    )
}

@Composable
fun SimpleMenuPreferenceInt(
    title: String,
    summary: String? = null,
    @ArrayRes entry: Int,
    @ArrayRes entryValueRes: Int,
    state: MutableState<Int>,
) {
    val entryArray = stringArrayResource(id = entry)
    val valuesArray = integerArrayResource(id = entryValueRes)
    check(entryArray.size == valuesArray.size)
    val selectedIndex = valuesArray.indexOf(state.value).coerceAtLeast(0)
    GlassDropdownPreference(
        title = title,
        summary = summary,
        items = entryArray.toList(),
        selectedIndex = selectedIndex,
        backdrop = LocalBackdrop.current,
        // 未显式给出说明时必须展示当前选中项，否则用户看不到自己选了什么
        showValue = true,
        onSelectedIndexChange = { index ->
            if (index in valuesArray.indices) {
                state.value = valuesArray[index]
            }
        },
    )
}

@Composable
fun WorkPreference(title: String, summary: String? = null, work: suspend CoroutineScope.() -> Unit) {
    val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
    var completed by remember { mutableStateOf(true) }
    if (!completed) {
        ProgressDialog()
    }
    BasicComponent(
        title = title,
        summary = summary,
        onClick = {
            completed = false
            coroutineScope.launch(block = work).invokeOnCompletion { completed = true }
        },
    )
}

@Composable
fun <I, O> LauncherPreference(
    title: String,
    summary: String? = null,
    contract: ActivityResultContract<I, O>,
    key: I,
    work: suspend CoroutineScope.(O) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
    val callback = remember { AtomicReference<(O) -> Unit> {} }
    val launcher = rememberLauncherForActivityResult(contract = contract) { callback.exchange { }.invoke(it) }
    var completed by remember { mutableStateOf(true) }
    if (!completed) {
        ProgressDialog()
    }
    BasicComponent(
        title = title,
        summary = summary,
        onClick = {
            coroutineScope.launch {
                val o = suspendCoroutine { cont ->
                    callback.store { cont.resume(it) }
                    launcher.launch(key)
                }
                completed = false
                work(o)
            }.invokeOnCompletion { completed = true }
        },
    )
}

/**
 * 设置页统一的纵向滚动容器。
 *
 * 滚动节点铺满整屏，因此大屏留白区域也可以上下滑动；内部内容再按可读宽度居中限宽，
 * 避免列表项/卡片被拉伸到接近整屏宽。
 */
@Composable
internal fun SettingsScrollColumn(
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .readableWidth()
                .fillMaxWidth(),
            content = content,
        )
        // 设置页及其二级子页始终展示底部悬浮导航栏，预留内容避让高度
        Spacer(modifier = Modifier.height(LocalBottomBarContentPadding.current))
    }
}

/**
 * 设置页统一的 LazyColumn 容器。
 *
 * LazyColumn 本身铺满整屏以接管两侧留白处的滑动手势，再通过对称的横向 contentPadding
 * 让列表项保持居中限宽。
 */
@Composable
internal fun SettingsLazyColumn(
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val maxContentWidth = LocalWindowLayout.current.contentMaxWidth
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = if (maxContentWidth != null) {
            ((maxWidth - maxContentWidth) / 2f).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        val layoutDirection = LocalLayoutDirection.current
        val bottomBarPadding = LocalBottomBarContentPadding.current
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + sidePadding,
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection) + sidePadding,
                bottom = contentPadding.calculateBottomPadding() + bottomBarPadding,
            ),
            content = content,
        )
    }
}
