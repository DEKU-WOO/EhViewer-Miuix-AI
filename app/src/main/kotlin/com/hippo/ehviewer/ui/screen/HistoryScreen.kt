package com.hippo.ehviewer.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.paging.map
import com.ehviewer.core.database.model.GalleryEntity
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FastScrollLazyColumn
import com.ehviewer.core.ui.component.SelectionAction
import com.ehviewer.core.ui.component.SelectionActionBar
import com.ehviewer.core.ui.component.SelectionTopBar
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.History
import com.ehviewer.core.ui.icons.filled.MenuBook
import com.ehviewer.core.ui.util.Await
import com.ehviewer.core.ui.util.LocalBottomBarContentPadding
import com.ehviewer.core.ui.util.readableWidth
import com.ehviewer.core.ui.util.rememberInVM
import com.ehviewer.core.ui.util.takeAndClear
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.sync.VpsHistorySyncManager
import com.hippo.ehviewer.ui.BottomBarScrollState
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.SelectionModeController
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.SelectableGalleryItem
import com.hippo.ehviewer.ui.modifyFavorites
import com.hippo.ehviewer.ui.startDownload
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.util.FavouriteStatusRouter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import moe.tarsin.coroutines.runSwallowingWithUI
import moe.tarsin.navigate
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.HistoryScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val title = stringResource(id = R.string.history)
    val hint = stringResource(R.string.search_bar_hint, title)
    val animateItems by Settings.animateItems.collectAsState()

    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by remember { mutableIntStateOf(0) }
    var keyword by rememberSaveable { mutableStateOf("") }

    val density = LocalDensity.current
    val historyData = rememberInVM {
        Pager(config = PagingConfig(pageSize = 20, jumpThreshold = 40)) {
            if (keyword.isNotEmpty()) {
                EhDB.searchHistory(keyword)
            } else {
                EhDB.historyLazyList
            }
        }.flow.map { data ->
            val favCat = Settings.favCat
            data.map {
                it.apply { favoriteName = favCat.getOrNull(favoriteSlot) }
            }
        }.cachedIn(viewModelScope)
    }.collectAsLazyPagingItems()
    FavouriteStatusRouter.Observe(historyData)
    // 进入历史页时做一次增量拉取（未启用云同步时内部直接返回）
    LaunchedEffect(Unit) { VpsHistorySyncManager.refreshFromServer() }
    val checkedInfoMap = remember { mutableStateMapOf<Long, GalleryEntity>() }
    val selectMode = checkedInfoMap.isNotEmpty()
    // 已加载项全部勾选时顶栏全选按钮显示激活态
    val loadedItems = historyData.itemSnapshotList.items
    val allSelected = loadedItems.isNotEmpty() && loadedItems.all { it.gid in checkedInfoMap }
    // 进入/退出多选时同步全局状态，主界面据此暂停左右切页
    LaunchedEffect(selectMode) { SelectionModeController.setActive(selectMode) }
    DisposableEffect(Unit) {
        onDispose { SelectionModeController.setActive(false) }
    }
    // 系统侧滑返回优先退出多选
    BackHandler(enabled = selectMode) { checkedInfoMap.clear() }
    DrawerHandle(!selectMode && !searchBarExpanded)
    var isRefreshing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    SearchBarScreen(
        onApplySearch = {
            keyword = it
            historyData.refresh()
        },
        expanded = searchBarExpanded,
        onExpandedChange = {
            searchBarExpanded = it
            if (it) checkedInfoMap.clear()
        },
        title = title,
        searchFieldHint = hint,
        searchBarOffsetY = { searchBarOffsetY },
        trailingIcon = {
            IconButton(
                onClick = {
                    launch {
                        awaitConfirmationOrCancel(
                            confirmText = R.string.clear_all,
                            text = { Text(text = stringResource(id = R.string.clear_all_history)) },
                        )
                        EhDB.clearHistoryInfo()
                        // Explicitly refresh after a bulk delete so an in-flight pager load
                        // cannot leave stale rows visible.
                        historyData.refresh()
                    }
                },
            ) {
                Icon(imageVector = MiuixIcons.Delete, contentDescription = null)
            }
        },
    ) { paddingValues ->
        // 原地旋转时 paddingValues/density 会变化，连接体内惰性读取最新值（remember 无 key 不重建）
        val currentPaddingValues by rememberUpdatedState(paddingValues)
        val currentDensity by rememberUpdatedState(density)
        val searchBarConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = -consumed.y
                    val slop = with(currentDensity) { 8.dp.toPx() }
                    if (dy >= slop) {
                        BottomBarScrollState.hidden = true
                    } else if (dy <= -slop / 2) {
                        BottomBarScrollState.hidden = false
                    }
                    val topPaddingPx = with(currentDensity) { currentPaddingValues.calculateTopPadding().roundToPx() }
                    searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                    return Offset.Zero // We never consume it
                }
            }
        }
        val marginH = dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_list_margin_h)
        val cardHeight by collectListThumbSizeAsState()
        val showPages by Settings.showGalleryPages.collectAsState()
        val showProgress by Settings.showReadingProgress.collectAsState()
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (isRefreshing) return@PullToRefresh
                isRefreshing = true
                launch {
                    VpsHistorySyncManager.refreshFromServer()
                    historyData.refresh()
                    delay(300)
                    isRefreshing = false
                }
            },
            pullToRefreshState = rememberPullToRefreshState(),
            modifier = Modifier.fillMaxSize(),
        ) {
        FastScrollLazyColumn(
            // 滚动容器保持全宽：平板左右两侧空白区域也能触摸上下滑动；
            // 卡片的居中最大宽度约束在每个 item 上单独施加（见下方 SelectableGalleryItem 的 readableWidth）。
            modifier = Modifier.nestedScroll(searchBarConnection).fillMaxSize(),
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(com.hippo.ehviewer.R.dimen.gallery_list_interval)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                count = historyData.itemCount,
                key = historyData.itemKey(key = { item -> item.gid }),
                contentType = historyData.itemContentType(),
            ) { index ->
                val info = historyData[index]
                if (info != null) {
                    val checked = info.gid in checkedInfoMap
                    SelectableGalleryItem(
                        checked = checked,
                        modifier = Modifier
                            .readableWidth()
                            .fillMaxWidth()
                            .padding(horizontal = marginH)
                            .thenIf(animateItems) { animateItem() },
                    ) { interactionSource ->
                        GalleryInfoListItem(
                            onClick = {
                                if (selectMode) {
                                    if (checked) {
                                        checkedInfoMap.remove(info.gid)
                                    } else {
                                        checkedInfoMap[info.gid] = info
                                    }
                                } else {
                                    navigate(info.asDst())
                                }
                            },
                            onLongClick = { checkedInfoMap[info.gid] = info },
                            info = info,
                            showPages = showPages,
                            showProgress = showProgress,
                            modifier = Modifier.height(cardHeight),
                            interactionSource = interactionSource,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(cardHeight).fillMaxWidth())
                }
            }
        }
        }
        Await(keyword, { delay(200) }) {
            if (historyData.itemCount == 0) {
                Column(
                    modifier = Modifier.padding(paddingValues).padding(horizontal = marginH).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = EhIcons.Big.Default.History,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                    val emptyHint = if (keyword.isEmpty()) {
                        stringResource(id = R.string.no_history)
                    } else {
                        stringResource(id = R.string.gallery_list_empty_hit)
                    }
                    Text(
                        text = emptyHint,
                        style = MiuixTheme.textStyles.title2,
                    )
                }
            }
        }
    }
        // 选择态顶栏覆盖在搜索栏之上，下方用页面背景遮住原搜索胶囊
        if (selectMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.background),
            ) {
                SelectionTopBar(
                    selectedCount = checkedInfoMap.size,
                    allSelected = allSelected,
                    onExit = { checkedInfoMap.clear() },
                    onToggleSelectAll = {
                        if (allSelected) {
                            checkedInfoMap.clear()
                        } else {
                            checkedInfoMap.putAll(loadedItems.associateBy { it.gid })
                        }
                    },
                    countText = "已选择 ${checkedInfoMap.size} 项",
                )
                // 补足搜索胶囊底部留白，避免选择态仍露出旧搜索栏边缘
                Spacer(Modifier.height(8.dp))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarContentPadding.current),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = selectMode,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                SelectionActionBar(
                    actions = listOf(
                        SelectionAction(
                            icon = EhIcons.Default.MenuBook,
                            label = stringResource(R.string.read),
                            enabled = checkedInfoMap.size == 1,
                        ) {
                            val info = checkedInfoMap.values.firstOrNull()
                            if (info != null) {
                                checkedInfoMap.clear()
                                navigate(info.asDst())
                            }
                        },
                        SelectionAction(icon = MiuixIcons.Download, label = stringResource(R.string.download)) {
                            val info = checkedInfoMap.takeAndClear()
                            if (info.isNotEmpty()) {
                                launch { runSwallowingWithUI { startDownload(false, *info.toTypedArray()) } }
                            }
                        },
                        SelectionAction(icon = MiuixIcons.Favorites, label = stringResource(R.string.favourite)) {
                            val info = checkedInfoMap.takeAndClear()
                            if (info.isNotEmpty()) {
                                // 复用现有收藏逻辑逐个加入；未设置默认收藏夹时会逐项弹窗选择
                                launch { info.forEach { item -> runSwallowingWithUI { modifyFavorites(item) } } }
                            }
                        },
                        SelectionAction(icon = MiuixIcons.Delete, label = stringResource(R.string.delete)) {
                            val info = checkedInfoMap.takeAndClear()
                            if (info.isNotEmpty()) {
                                launch {
                                    info.forEach { EhDB.deleteHistoryInfo(it) }
                                    historyData.refresh()
                                }
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
