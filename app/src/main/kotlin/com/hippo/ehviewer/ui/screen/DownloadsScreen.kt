package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.TagNamespace
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ehviewer.core.ui.util.LocalIsPageVisible
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.component.FastScrollLazyVerticalGrid
import com.ehviewer.core.ui.component.FastScrollLazyVerticalStaggeredGrid
import com.ehviewer.core.ui.component.SelectionAction
import com.ehviewer.core.ui.component.SelectionActionBar
import com.ehviewer.core.ui.component.SelectionTopBar
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.Download
import com.ehviewer.core.ui.util.WindowLayout
import com.ehviewer.core.ui.util.rememberInVM
import com.ehviewer.core.ui.util.snackBarPadding
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.launchIO
import com.ehviewer.core.util.mapToLongArray
import com.ehviewer.core.util.withNonCancellableContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.DownloadService
import com.hippo.ehviewer.download.DownloadsFilterMode
import com.hippo.ehviewer.download.SortMode
import com.hippo.ehviewer.ui.BottomBarScrollState
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.main.DownloadsTabSession
import com.hippo.ehviewer.ui.main.MainTabsViewModel
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.SelectionModeController
import com.hippo.ehviewer.ui.collectConfiguredThumbColumns
import com.hippo.ehviewer.ui.confirmRemoveDownloadRange
import com.hippo.ehviewer.ui.main.DownloadCard
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.navToReader
import com.hippo.ehviewer.ui.showMoveDownloadLabelList
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitInputText
import com.hippo.ehviewer.ui.tools.awaitSelectAction
import com.hippo.ehviewer.ui.tools.showNoButton
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import moe.tarsin.navigate
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.DownloadsScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val tabsViewModel: MainTabsViewModel = viewModel(contextOf<MainActivity>())
    DownloadsTabContent(session = tabsViewModel.downloadsSession, ownerKey = "route_downloads")
}

@Composable
context(
    _: MainActivity,
    _: SnackbarHostState,
    _: DialogState,
    _: SharedTransitionScope,
    _: TransitionsVisibilityScope,
    _: DestinationsNavigator,
    _: CoroutineScope,
)
fun DownloadsTabContent(
    session: DownloadsTabSession,
    ownerKey: String = "tab_downloads",
) {
    val isPageVisible = LocalIsPageVisible.current
    var gridView by Settings.gridView.asMutableState()
    val filterMode by Settings.downloadFilterMode.collectAsState { DownloadsFilterMode.from(it) }
    val showProgress by Settings.showReadingProgress.collectAsState()
    var filterState by rememberSerializable { mutableStateOf(DownloadsFilterState(filterMode, Settings.recentDownloadLabel.value)) }
    var invalidateKey by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(!DownloadManager.isInitialized) }
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by session.searchBarOffsetY
    val animateItems by Settings.animateItems.collectAsState()
    // 两种视图的滚动状态提升到页面级，便于从阅读器返回时判断是否仍在顶部
    val detailGridState = session.detailGridState
    val staggeredGridState = session.staggeredGridState
    // 全屏阅读器返回后系统状态栏重新展开：列表位于顶部时复位搜索栏偏移，
    // 避免首张卡片直接顶在搜索栏下方；不在顶部则保留原有滚动位置
    LifecycleResumeEffect(Unit) {
        val atTop = if (gridView) {
            staggeredGridState.firstVisibleItemIndex == 0 && staggeredGridState.firstVisibleItemScrollOffset <= 1
        } else {
            detailGridState.firstVisibleItemIndex == 0 && detailGridState.firstVisibleItemScrollOffset <= 1
        }
        if (atTop) searchBarOffsetY = 0
        onPauseOrDispose { }
    }

    val checkedInfoMap = remember { mutableStateMapOf<Long, DownloadInfo>() }
    val selectMode = checkedInfoMap.isNotEmpty()
    val backdrop = rememberBlurBackdrop()
    // ⋮ 菜单提升到页面级：菜单本体渲染在 Screen 根 Box 内，获得整屏实时模糊与锚点形变
    val downloadMenuAnchor = rememberGlassPopupAnchor()
    var downloadMenuShow by remember { mutableStateOf(false) }
    var downloadMenuEntries by remember { mutableStateOf<List<DropdownEntry>>(emptyList()) }
    DrawerHandle(isPageVisible && !selectMode && !searchBarExpanded)
    // 选中态同步给主界面（暂停左右滑动切页），退出页面时兜底复位
    LaunchedEffect(ownerKey, isPageVisible, selectMode) { SelectionModeController.update(ownerKey, isPageVisible && selectMode) }
    DisposableEffect(ownerKey) { onDispose { SelectionModeController.clearOwner(ownerKey) } }
    // 侧滑/返回键退出多选；系统返回优先由本页消费
    BackHandler(enabled = isPageVisible && selectMode) { checkedInfoMap.clear() }

    val density = LocalDensity.current
    val canTranslate = Settings.showTagTranslations.value && EhTagDatabase.translatable && EhTagDatabase.initialized
    val ehTags = EhTagDatabase.takeIf { canTranslate }
    fun getTranslation(tag: String) = ehTags?.run {
        getTranslation(TagNamespace.Artist.prefix, tag) ?: getTranslation(TagNamespace.Cosplayer.prefix, tag)
    } ?: tag
    val allName = stringResource(R.string.download_all)
    val defaultName = stringResource(R.string.default_download_label_name)
    val unknownName = stringResource(R.string.unknown_artists)
    val emptyLabelName = when (filterMode) {
        DownloadsFilterMode.ARTIST -> unknownName
        DownloadsFilterMode.CUSTOM -> defaultName
    }
    val title = stringResource(
        R.string.scene_download_title,
        with(filterState) {
            when (label) {
                "" -> allName
                null -> emptyLabelName
                else -> if (mode == DownloadsFilterMode.ARTIST) getTranslation(label) else label
            }
        },
    )
    val hint = stringResource(R.string.search_bar_hint, title)
    val downloadListVersion by DownloadManager.downloadListVersion.collectAsState()
    val list = if (DownloadManager.isInitialized) {
        remember(filterState, invalidateKey, downloadListVersion) {
            DownloadManager.downloadInfoList.filterTo(mutableStateListOf()) { info ->
                filterState.take(info)
            }.also {
                launch {
                    // delay removed for instant tab rendering
                    isLoading = false
                }
            }
        }
    } else {
        remember { mutableStateListOf() }
    }

    val newLabel = stringResource(R.string.new_label_title)
    val renameLabel = stringResource(R.string.rename_label_title)
    val labelsStr = stringResource(R.string.download_labels)
    val labelEmpty = stringResource(R.string.label_text_is_empty)
    val defaultInvalid = stringResource(R.string.label_text_is_invalid)
    val labelExists = stringResource(R.string.label_text_exist)
    val downloadsCountGroupByArtist by rememberInVM { EhDB.downloadsCountByArtist }.collectAsState(emptyMap())
    val downloadsCountGroupByLabel by rememberInVM { EhDB.downloadsCountByLabel }.collectAsState(emptyMap())
    val downloadsCount = when (filterMode) {
        DownloadsFilterMode.CUSTOM -> downloadsCountGroupByLabel
        DownloadsFilterMode.ARTIST -> downloadsCountGroupByArtist
    }
    val artistList = remember(downloadsCountGroupByArtist) {
        downloadsCountGroupByArtist.keys.mapNotNull { artist -> artist?.let { it to it } }
    }
    val labelList by remember {
        derivedStateOf {
            DownloadManager.labelList.map { it.id!! to it.label }
        }
    }
    val groupList = when (filterMode) {
        DownloadsFilterMode.CUSTOM -> labelList
        DownloadsFilterMode.ARTIST -> artistList
    }
    // 面板里展示「名称 [数量]」，名称按分组模式决定是否翻译
    fun groupName(label: String) = if (filterMode == DownloadsFilterMode.ARTIST) getTranslation(label) else label
    val groupEntries = groupList.map { (_, label) ->
        DownloadLabelEntry(
            displayName = groupName(label),
            label = label,
            count = downloadsCount.getOrDefault(label, 0),
        )
    }
    val totalCount = downloadsCount.values.sum()

    fun switchLabel(label: String?) {
        Settings.recentDownloadLabel.value = label
        filterState = filterState.copy(label = label)
    }

    // 「下载标签」二级锚点菜单：完整保留标签数量、新建与管理交互
    val groupingCustomStr = stringResource(R.string.select_grouping_mode_custom)
    val groupingArtistStr = stringResource(R.string.select_grouping_mode_artist)
    val letMeSelectStr = stringResource(R.string.let_me_select)
    val labelChildren = buildList {
        add(
            DropdownItem(
                text = allName,
                summary = totalCount.toString(),
                selected = filterState.label == "",
                onClick = { switchLabel("") },
            ),
        )
        add(
            DropdownItem(
                text = emptyLabelName,
                summary = downloadsCount.getOrDefault(null, 0).toString(),
                selected = filterState.label == null,
                onClick = { switchLabel(null) },
            ),
        )
        groupEntries.forEach { entry ->
            if (entry.label != null) {
                add(
                    DropdownItem(
                        text = entry.displayName,
                        summary = entry.count.toString(),
                        selected = filterState.label == entry.label,
                        onClick = { switchLabel(entry.label) },
                    ),
                )
            }
        }
        if (filterMode == DownloadsFilterMode.CUSTOM) {
            add(
                DropdownItem(
                    text = "+ 新建标签",
                    onClick = {
                        launch {
                            val text = awaitInputText(title = newLabel, hint = labelsStr) { text ->
                                when {
                                    text.isBlank() -> raise(labelEmpty)
                                    text == emptyLabelName -> raise(defaultInvalid)
                                    DownloadManager.containLabel(text) -> raise(labelExists)
                                }
                            }
                            DownloadManager.addLabel(text)
                        }
                    },
                ),
            )
            add(
                DropdownItem(
                    text = "管理标签",
                    onClick = {
                        launch {
                            while (true) {
                                val current = DownloadManager.labelList.map { it.label }
                                when (val action = awaitManageDownloadLabels(renameLabel, current)) {
                                    is DownloadLabelManageResult.Rename -> {
                                        val old = action.label
                                        val text = awaitInputText(initial = old, title = renameLabel, hint = labelsStr) { text ->
                                            when {
                                                text.isBlank() -> raise(labelEmpty)
                                                text == emptyLabelName -> raise(defaultInvalid)
                                                text != old && DownloadManager.containLabel(text) -> raise(labelExists)
                                            }
                                        }
                                        DownloadManager.renameLabel(old, text)
                                        if (filterState.label == old) switchLabel(text)
                                    }
                                    is DownloadLabelManageResult.Delete -> {
                                        awaitConfirmationOrCancel(confirmText = R.string.delete) {
                                            Text(text = stringResource(R.string.delete_label, action.label))
                                        }
                                        DownloadManager.deleteLabel(action.label)
                                        if (filterState.label == action.label) switchLabel("")
                                    }
                                    DownloadLabelManageResult.Dismiss -> break
                                }
                            }
                        }
                    },
                ),
            )
            add(
                DropdownItem(
                    text = "默认下载标签",
                    onClick = {
                        launch {
                            val selected = if (!Settings.hasDefaultDownloadLabel) {
                                0
                            } else {
                                DownloadManager.labelList.indexOfFirst { it.label == Settings.defaultDownloadLabel } + 2
                            }
                            awaitSelectAction(R.string.default_download_label, selected) {
                                onSelect(letMeSelectStr) {
                                    Settings.hasDefaultDownloadLabel = false
                                }
                                onSelect(defaultName) {
                                    Settings.hasDefaultDownloadLabel = true
                                    Settings.defaultDownloadLabel = null
                                }
                                DownloadManager.labelList.forEach { (label) ->
                                    onSelect(label) {
                                        Settings.hasDefaultDownloadLabel = true
                                        Settings.defaultDownloadLabel = label
                                    }
                                }
                            }()
                        }
                    },
                ),
            )
        }
        add(
            DropdownItem(
                text = "分组方式",
                onClick = {
                    launch {
                        awaitSelectAction(R.string.select_grouping_mode) {
                            val select = { mode: DownloadsFilterMode ->
                                filterState = filterState.copy(mode = mode, label = "")
                                Settings.downloadFilterMode.value = mode.flag
                                Settings.recentDownloadLabel.value = ""
                            }
                            onSelect(groupingCustomStr) { select(DownloadsFilterMode.CUSTOM) }
                            onSelect(groupingArtistStr) { select(DownloadsFilterMode.ARTIST) }
                        }()
                    }
                },
            ),
        )
    }

    LaunchedEffect(filterState) {
        searchBarOffsetY = 0
    }

    // 日常浏览仅保留默认的「添加时间降序」，不再提供排序/筛选面板
    LaunchedEffect(Unit) {
        if (DownloadManager.isInitialized && Settings.downloadSortMode.value != SortMode.Default.flag) {
            Settings.downloadSortMode.value = SortMode.Default.flag
            DownloadManager.sortDownloads(SortMode.Default)
            invalidateKey = !invalidateKey
        }
    }

    val searchFieldState = session.searchFieldState
    class DownloadLabelSuggestion(private val label: String) : Suggestion() {
        override val keyword = LABEL_PREFIX + label
        override fun onClick() {
            searchFieldState.setTextAndPlaceCursorAtEnd(keyword)
            searchBarExpanded = false
            switchLabel(label)
        }
    }
    SearchBarScreen(
        onApplySearch = { filterState = filterState.copy(keyword = it) },
        expanded = searchBarExpanded,
        onExpandedChange = {
            searchBarExpanded = it
            if (it) checkedInfoMap.clear()
        },
        title = title,
        searchFieldHint = hint,
        searchFieldState = searchFieldState,
        suggestionProvider = { query ->
            val label = query.substringAfter(LABEL_PREFIX, "")
            if (label.isEmpty()) {
                emptyList()
            } else {
                EhDB.searchDownloadLabel(label, 10).map(::DownloadLabelSuggestion)
            }
        },
        searchBarOffsetY = { searchBarOffsetY },
        backdrop = backdrop,
        hideSearchBar = selectMode,
        ownerKey = ownerKey,
        trailingIcon = {
            SearchBarCircleAction(onClick = { gridView = !gridView }) {
                val icon = if (gridView) MiuixIcons.ListView else MiuixIcons.GridView
                val desc = stringResource(if (gridView) R.string.settings_eh_list_mode else R.string.settings_eh_list_mode_thumb)
                Icon(imageVector = icon, contentDescription = desc, modifier = Modifier.size(SearchBarIconSize))
            }
            val startAllStr = stringResource(id = R.string.download_start_all)
            val stopAllStr = stringResource(id = R.string.download_stop_all)
            val resetProgressStr = stringResource(id = R.string.download_reset_reading_progress)
            val resetProgressMsg = stringResource(id = R.string.reset_reading_progress_message)
            val startAllReversedStr = stringResource(id = R.string.download_start_all_reversed)
            val moveLabelStr = stringResource(id = R.string.download_move_dialog_title)
            val groupingCustomStr = stringResource(R.string.select_grouping_mode_custom)
            val groupingArtistStr = stringResource(R.string.select_grouping_mode_artist)
            val letMeSelectStr = stringResource(R.string.let_me_select)

            val menuEntry = DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = labelsStr,
                        children = labelChildren,
                        onClick = {
                            launch {
                                // 「下载标签」改为就地弹出的卡片面板，替代原来的全高侧边抽屉
                                var keepPanel = true
                                while (keepPanel) {
                                    when (
                                        val result = awaitDownloadLabelPanel(
                                            title = labelsStr,
                                            allName = allName,
                                            allCount = totalCount,
                                            defaultName = emptyLabelName,
                                            defaultCount = downloadsCount.getOrDefault(null, 0),
                                            entries = groupEntries,
                                            selectedLabel = filterState.label,
                                            labelMode = filterMode == DownloadsFilterMode.CUSTOM,
                                        )
                                    ) {
                                        is DownloadLabelPanelResult.Select -> {
                                            switchLabel(result.label)
                                            keepPanel = false
                                        }
                                        DownloadLabelPanelResult.Create -> {
                                            val text = awaitInputText(title = newLabel, hint = labelsStr) { text ->
                                                when {
                                                    text.isBlank() -> raise(labelEmpty)
                                                    text == defaultName -> raise(defaultInvalid)
                                                    DownloadManager.containLabel(text) -> raise(labelExists)
                                                }
                                            }
                                            DownloadManager.addLabel(text)
                                            keepPanel = false
                                        }
                                        DownloadLabelPanelResult.Manage -> {
                                            while (true) {
                                                val current = DownloadManager.labelList.map { it.label }
                                                when (val action = awaitManageDownloadLabels(renameLabel, current)) {
                                                    is DownloadLabelManageResult.Rename -> {
                                                        val old = action.label
                                                        val text = awaitInputText(initial = old, title = renameLabel, hint = labelsStr) { text ->
                                                            when {
                                                                text.isBlank() -> raise(labelEmpty)
                                                                text == defaultName -> raise(defaultInvalid)
                                                                text != old && DownloadManager.containLabel(text) -> raise(labelExists)
                                                            }
                                                        }
                                                        DownloadManager.renameLabel(old, text)
                                                        if (filterState.label == old) switchLabel(text)
                                                    }
                                                    is DownloadLabelManageResult.Delete -> {
                                                        awaitConfirmationOrCancel(confirmText = R.string.delete) {
                                                            Text(text = stringResource(R.string.delete_label, action.label))
                                                        }
                                                        DownloadManager.deleteLabel(action.label)
                                                        if (filterState.label == action.label) switchLabel("")
                                                    }
                                                    DownloadLabelManageResult.Dismiss -> break
                                                }
                                            }
                                            keepPanel = false
                                        }
                                        DownloadLabelPanelResult.GroupingMode -> {
                                            awaitSelectAction(R.string.select_grouping_mode) {
                                                val select = { mode: DownloadsFilterMode ->
                                                    filterState = filterState.copy(mode = mode, label = "")
                                                    Settings.downloadFilterMode.value = mode.flag
                                                    Settings.recentDownloadLabel.value = ""
                                                }
                                                onSelect(groupingCustomStr) { select(DownloadsFilterMode.CUSTOM) }
                                                onSelect(groupingArtistStr) { select(DownloadsFilterMode.ARTIST) }
                                            }()
                                            keepPanel = false
                                        }
                                        DownloadLabelPanelResult.DefaultLabel -> {
                                            val selected = if (!Settings.hasDefaultDownloadLabel) {
                                                0
                                            } else {
                                                DownloadManager.labelList.indexOfFirst { it.label == Settings.defaultDownloadLabel } + 2
                                            }
                                            awaitSelectAction(R.string.default_download_label, selected) {
                                                onSelect(letMeSelectStr) {
                                                    Settings.hasDefaultDownloadLabel = false
                                                }
                                                onSelect(defaultName) {
                                                    Settings.hasDefaultDownloadLabel = true
                                                    Settings.defaultDownloadLabel = null
                                                }
                                                DownloadManager.labelList.forEach { (label) ->
                                                    onSelect(label) {
                                                        Settings.hasDefaultDownloadLabel = true
                                                        Settings.defaultDownloadLabel = label
                                                    }
                                                }
                                            }()
                                            keepPanel = false
                                        }
                                        DownloadLabelPanelResult.Dismiss -> keepPanel = false
                                    }
                                }
                            }
                        },
                    ),
                    DropdownItem(
                        text = startAllStr,
                        onClick = { DownloadService.startService(DownloadService.ACTION_START_ALL) },
                    ),
                    DropdownItem(
                        text = stopAllStr,
                        onClick = { launchIO { DownloadManager.stopAllDownload() } },
                    ),
                    DropdownItem(
                        text = resetProgressStr,
                        onClick = {
                            launchIO {
                                awaitConfirmationOrCancel(
                                    confirmText = android.R.string.ok,
                                    dismissText = android.R.string.cancel,
                                ) {
                                    Text(text = resetProgressMsg)
                                }
                                withNonCancellableContext {
                                    DownloadManager.resetAllReadingProgress()
                                }
                            }
                        },
                    ),
                    DropdownItem(
                        text = startAllReversedStr,
                        onClick = {
                            val gidList = list.filter { it.state != DownloadInfo.STATE_FINISH }.asReversed().mapToLongArray(DownloadInfo::gid)
                            DownloadService.startRangeDownload(gidList)
                        },
                    ),
                    // 原多选悬浮组里的「移动标签」入口，改为对当前列表整体移动
                    DropdownItem(
                        text = moveLabelStr,
                        onClick = {
                            val infoList = list.toList()
                            val targetIds = infoList.map { it.gid }
                            launch {
                                val toLabel = showMoveDownloadLabelList(infoList)
                                with(filterState) {
                                    if (label != "" && label != toLabel) {
                                        list.removeAll { it.gid in targetIds }
                                    }
                                }
                            }
                        },
                    ),
                ),
            )
            SearchBarCircleAction(
                onClick = {
                    downloadMenuEntries = listOf(menuEntry)
                    downloadMenuShow = true
                },
                modifier = Modifier.glassPopupAnchor(downloadMenuAnchor, cornerRadius = SearchBarActionCornerRadius, floating = true),
            ) {
                Icon(imageVector = MiuixIcons.More, contentDescription = stringResource(R.string.more_actions), modifier = Modifier.size(SearchBarIconSize))
            }
        },
    ) { contentPadding ->
        val height by collectListThumbSizeAsState()
        val marginH = dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_list_margin_h)
        val marginV = dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_list_margin_v)
        // 从隐藏状态栏的全屏阅读器返回时，Scaffold 首帧可能沿用旧 inset 导致顶部内边距变矮；
        // 这里按「状态栏 + 搜索栏 + 8.dp」兜底，保证首张卡片始终在搜索栏下方
        val statusBarTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
        val expectedTop = statusBarTop + SearchBarDefaults.InputFieldMinHeight + 16.dp + 8.dp
        val layoutDirection = LocalLayoutDirection.current
        val realPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + marginH,
            top = maxOf(contentPadding.calculateTopPadding(), expectedTop) + marginV,
            end = contentPadding.calculateEndPadding(layoutDirection) + marginH,
            bottom = contentPadding.calculateBottomPadding() + marginV,
        )
        // 原地旋转时 paddingValues/density 会变化，连接体内惰性读取最新值（remember 无 key 不重建）
        val currentContentPadding by rememberUpdatedState(contentPadding)
        val currentDensity by rememberUpdatedState(density)
        val currentVisible by rememberUpdatedState(isPageVisible)
        val searchBarConnection = remember {
            val slop = ViewConfiguration.get(contextOf<Context>()).scaledTouchSlop
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = -consumed.y
                    if (currentVisible && dy >= slop) {
                        BottomBarScrollState.hidden = true
                    } else if (currentVisible && dy <= -slop / 2) {
                        BottomBarScrollState.hidden = false
                    }
                    val topPaddingPx = with(currentDensity) { currentContentPadding.calculateTopPadding().roundToPx() }
                    searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                    return Offset.Zero // We never consume it
                }
            }
        }

        fun onItemClick(info: DownloadInfo) {
            launchIO { EhDB.putHistoryInfo(info) }
            navToReader(info.galleryInfo)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 列宽按网格自身可用宽度算：窗口宽度会把侧边导航栏与外边距也算进去，宽屏下列数偏多
            val availableWidthDp = (maxWidth - marginH * 2).value.roundToInt().coerceAtLeast(1)
            Crossfade(targetState = gridView, label = "Downloads") { showGridView ->
                if (showGridView) {
                    val gridInterval = dimensionResource(com.hippo.ehviewer.R.dimen.gallery_grid_interval)
                    val configuredThumbColumns = collectConfiguredThumbColumns()
                    // 横屏/宽屏下按可用宽度补足列数，避免固定列数把缩略图拉得过宽
                    val thumbColumns = WindowLayout.thumbGridColumns(availableWidthDp, configuredThumbColumns)
                    FastScrollLazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(thumbColumns),
                        modifier = Modifier.nestedScroll(searchBarConnection).fillMaxSize(),
                        state = staggeredGridState,
                        contentPadding = realPadding,
                        verticalItemSpacing = gridInterval,
                        horizontalArrangement = Arrangement.spacedBy(gridInterval),
                    ) {
                        items(list, key = { it.gid }) { info ->
                            val checked = info.gid in checkedInfoMap
                            CheckableItem(
                                checked = checked,
                                modifier = Modifier.thenIf(animateItems) { animateItem() },
                            ) { interactionSource ->
                                GalleryInfoGridItem(
                                    onClick = {
                                        if (selectMode) {
                                            if (checked) {
                                                checkedInfoMap.remove(info.gid)
                                            } else {
                                                checkedInfoMap[info.gid] = info
                                            }
                                        } else {
                                            onItemClick(info)
                                        }
                                    },
                                    onLongClick = {
                                        checkedInfoMap[info.gid] = info
                                    },
                                    info = info,
                                    onThumbClick = if (selectMode) {
                                        null
                                    } else {
                                        { navigate(info.galleryInfo.asDst()) }
                                    },
                                    showLanguage = false,
                                    showProgress = showProgress,
                                    interactionSource = interactionSource,
                                )
                            }
                        }
                    }
                } else {
                    val listSpacing = dimensionResource(com.hippo.ehviewer.R.dimen.gallery_list_interval)
                    val configuredColumnWidth by collectDetailSizeAsState()
                    // 宽屏下压最小列宽，保证下载列表至少两列而不是把卡片拉伸成满宽
                    val columnWidth = WindowLayout.detailMinColumnWidth(
                        availableWidthDp = availableWidthDp,
                        configuredMinWidthDp = configuredColumnWidth.value.toInt(),
                        spacingDp = listSpacing.value.roundToInt(),
                    ).dp
                    FastScrollLazyVerticalGrid(
                        columns = GridCells.Adaptive(columnWidth),
                        modifier = Modifier.nestedScroll(searchBarConnection).fillMaxSize(),
                        state = detailGridState,
                        contentPadding = realPadding,
                        verticalArrangement = Arrangement.spacedBy(listSpacing),
                        horizontalArrangement = Arrangement.spacedBy(listSpacing),
                    ) {
                        items(list, key = { it.gid }) { info ->
                            val checked = info.gid in checkedInfoMap
                            CheckableItem(
                                checked = checked,
                                modifier = Modifier.thenIf(animateItems) { animateItem() },
                            ) { interactionSource ->
                                DownloadCard(
                                    onClick = {
                                        if (selectMode) {
                                            if (checked) {
                                                checkedInfoMap.remove(info.gid)
                                            } else {
                                                checkedInfoMap[info.gid] = info
                                            }
                                        } else {
                                            onItemClick(info)
                                        }
                                    },
                                    onThumbClick = {
                                        navigate(info.galleryInfo.asDst())
                                    },
                                    onLongClick = {
                                        checkedInfoMap[info.gid] = info
                                    },
                                    onStart = {
                                        DownloadService.startDownload(info.galleryInfo)
                                    },
                                    onStop = { launchIO { DownloadManager.stopDownload(info.gid) } },
                                    info = info,
                                    thumbHeight = height,
                                    selectMode = selectMode,
                                    showProgress = showProgress,
                                    interactionSource = interactionSource,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else if (list.isEmpty()) {
            Column(
                modifier = Modifier.padding(realPadding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = EhIcons.Big.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).size(120.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(id = R.string.no_download_info),
                    style = MiuixTheme.textStyles.title2,
                )
            }
        }
    }

    // 选择态顶栏直接切换搜索栏，不额外叠加背景遮罩
    AnimatedVisibility(
        visible = selectMode,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        SelectionTopBar(
            selectedCount = checkedInfoMap.size,
            allSelected = list.isNotEmpty() && checkedInfoMap.size == list.size,
            onExit = { checkedInfoMap.clear() },
            onToggleSelectAll = {
                if (list.isNotEmpty() && checkedInfoMap.size == list.size) {
                    checkedInfoMap.clear()
                } else {
                    checkedInfoMap.putAll(list.associateBy { it.gid })
                }
            },
            countText = "已选择 ${checkedInfoMap.size} 项",
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // 多选底部操作胶囊：4 个均分按钮，进入/退出时整体升起/落下
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = selectMode,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            fun selectedGids() = checkedInfoMap.keys.toLongArray()
            SelectionActionBar(
                actions = listOf(
                    SelectionAction(icon = MiuixIcons.Play, label = "开始") {
                        val ids = selectedGids()
                        checkedInfoMap.clear()
                        DownloadService.startRangeDownload(ids)
                    },
                    SelectionAction(icon = MiuixIcons.Pause, label = "暂停") {
                        val ids = selectedGids()
                        checkedInfoMap.clear()
                        launch { DownloadManager.stopRangeDownload(ids) }
                    },
                    SelectionAction(icon = MiuixIcons.Refresh, label = "重下") {
                        val ids = selectedGids()
                        checkedInfoMap.clear()
                        // 先停掉在跑的任务，再重新入队；ensureDownload 会重置进度
                        launch {
                            DownloadManager.stopRangeDownload(ids)
                            DownloadService.startRangeDownload(ids)
                        }
                    },
                    SelectionAction(icon = MiuixIcons.Delete, label = "删除") {
                        val targets = checkedInfoMap.values.toList()
                        val targetIds = targets.map { it.gid }
                        checkedInfoMap.clear()
                        launch {
                            confirmRemoveDownloadRange(targets)
                            list.removeAll { it.gid in targetIds }
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
                backdrop = backdrop,
            )
        }
    }
    // ⋮ 菜单：液态玻璃锚点菜单（「下载标签」通过 GlassSecondaryPopup 展开二级菜单）
    Box(Modifier.fillMaxSize()) {
    GlassIconDropdownMenu(
        show = downloadMenuShow,
        onDismissRequest = { downloadMenuShow = false },
        anchor = downloadMenuAnchor,
        anchorContent = { Icon(imageVector = MiuixIcons.More, contentDescription = null, modifier = Modifier.size(SearchBarIconSize)) },
        entries = downloadMenuEntries,
        backdrop = backdrop,
        visuals = liquidGlassPopupVisuals(),
    )
    }
}

private const val LABEL_PREFIX = "label:"

/** 标签面板的一行：展示名（可能已翻译）、原始标签值与数量。 */
private data class DownloadLabelEntry(
    val displayName: String,
    val label: String,
    val count: Int,
)

/** 标签面板的返回结果：选中某个标签，或触发底部动作。 */
private sealed interface DownloadLabelPanelResult {
    data class Select(val label: String?) : DownloadLabelPanelResult
    data object Create : DownloadLabelPanelResult
    data object Manage : DownloadLabelPanelResult
    data object GroupingMode : DownloadLabelPanelResult
    data object DefaultLabel : DownloadLabelPanelResult
    data object Dismiss : DownloadLabelPanelResult
}

/** 标签管理面板的返回结果。 */
private sealed interface DownloadLabelManageResult {
    data class Rename(val label: String) : DownloadLabelManageResult
    data class Delete(val label: String) : DownloadLabelManageResult
    data object Dismiss : DownloadLabelManageResult
}

context(_: DialogState)
private suspend fun awaitDownloadLabelPanel(
    title: String,
    allName: String,
    allCount: Int,
    defaultName: String,
    defaultCount: Int,
    entries: List<DownloadLabelEntry>,
    selectedLabel: String?,
    labelMode: Boolean,
): DownloadLabelPanelResult = showNoButton(respectDefaultWidth = false) {
    DownloadLabelPanel(
        title = title,
        allName = allName,
        allCount = allCount,
        defaultName = defaultName,
        defaultCount = defaultCount,
        entries = entries,
        selectedLabel = selectedLabel,
        labelMode = labelMode,
        onSelect = { resume(DownloadLabelPanelResult.Select(it)) },
        onCreate = { resume(DownloadLabelPanelResult.Create) },
        onManage = { resume(DownloadLabelPanelResult.Manage) },
        onGroupingMode = { resume(DownloadLabelPanelResult.GroupingMode) },
        onDefaultLabel = { resume(DownloadLabelPanelResult.DefaultLabel) },
        onDismiss = { resume(DownloadLabelPanelResult.Dismiss) },
    )
}

context(_: DialogState)
private suspend fun awaitManageDownloadLabels(
    title: String,
    labels: List<String>,
): DownloadLabelManageResult = showNoButton(respectDefaultWidth = false) {
    DownloadLabelManagePanel(
        title = title,
        labels = labels,
        onRename = { resume(DownloadLabelManageResult.Rename(it)) },
        onDelete = { resume(DownloadLabelManageResult.Delete(it)) },
        onDismiss = { resume(DownloadLabelManageResult.Dismiss) },
    )
}

@Composable
private fun DownloadLabelPanel(
    title: String,
    allName: String,
    allCount: Int,
    defaultName: String,
    defaultCount: Int,
    entries: List<DownloadLabelEntry>,
    selectedLabel: String?,
    labelMode: Boolean,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onManage: () -> Unit,
    onGroupingMode: () -> Unit,
    onDefaultLabel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MiuixTheme.textStyles.title4,
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            item {
                DownloadLabelPanelRow(
                    name = allName,
                    count = allCount,
                    selected = selectedLabel == "",
                    onClick = { onSelect("") },
                )
            }
            item {
                DownloadLabelPanelRow(
                    name = defaultName,
                    count = defaultCount,
                    selected = selectedLabel == null,
                    onClick = { onSelect(null) },
                )
            }
            items(entries) { entry ->
                DownloadLabelPanelRow(
                    name = entry.displayName,
                    count = entry.count,
                    selected = selectedLabel == entry.label,
                    onClick = { onSelect(entry.label) },
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (labelMode) {
            DownloadLabelPanelAction(text = "+ 新建标签", onClick = onCreate)
            DownloadLabelPanelAction(text = "管理标签", onClick = onManage)
            DownloadLabelPanelAction(text = "默认下载标签", onClick = onDefaultLabel)
        }
        DownloadLabelPanelAction(text = "分组方式", onClick = onGroupingMode)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(text = "关闭", onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DownloadLabelPanelRow(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(SquircleShape(10.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$name [$count]",
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DownloadLabelPanelAction(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MiuixTheme.textStyles.body1)
    }
}

@Composable
private fun DownloadLabelManagePanel(
    title: String,
    labels: List<String>,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MiuixTheme.textStyles.title4,
        )
        if (labels.isEmpty()) {
            Text(
                text = "暂无自定义标签",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(labels) { label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(SquircleShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.body1,
                        )
                        IconButton(onClick = { onRename(label) }) {
                            Icon(
                                imageVector = MiuixIcons.Edit,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                        IconButton(onClick = { onDelete(label) }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(text = "完成", onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
