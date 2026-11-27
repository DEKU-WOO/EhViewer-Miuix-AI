package com.hippo.ehviewer.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.ehviewer.core.database.model.GalleryEntity
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FastScrollLazyColumn
import com.ehviewer.core.ui.component.SelectionAction
import com.ehviewer.core.ui.component.SelectionActionBar
import com.ehviewer.core.ui.component.SelectionTopBar
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.History
import com.ehviewer.core.ui.icons.filled.MenuBook
import com.ehviewer.core.ui.util.Await
import com.ehviewer.core.ui.util.LocalIsPageVisible
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.readableWidth
import com.ehviewer.core.ui.util.takeAndClear
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.sync.VpsHistorySyncManager
import com.hippo.ehviewer.ui.BottomBarScrollState
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.SelectionModeController
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.HistoryTabSession
import com.hippo.ehviewer.ui.main.MainTabsViewModel
import com.hippo.ehviewer.ui.main.SelectableGalleryItem
import com.hippo.ehviewer.ui.modifyFavorites
import com.hippo.ehviewer.ui.startDownload
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.util.FavouriteStatusRouter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import moe.tarsin.coroutines.runSwallowingWithUI
import moe.tarsin.navigate
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SnackbarHostState
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
    val tabsViewModel: MainTabsViewModel = viewModel(contextOf<MainActivity>())
    HistoryTabContent(session = tabsViewModel.historySession, ownerKey = "route_history")
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
fun HistoryTabContent(
    session: HistoryTabSession,
    ownerKey: String = "tab_history",
) {
    val isPageVisible = LocalIsPageVisible.current
    val title = stringResource(id = R.string.history)
    val hint = stringResource(R.string.search_bar_hint, title)
    val animateItems by Settings.animateItems.collectAsState()

    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by session.searchBarOffsetY
    var keyword by session.keyword

    val density = LocalDensity.current
    val historyData = session.data.collectAsLazyPagingItems()
    FavouriteStatusRouter.Observe(historyData)
    // 仅在真正滑入历史页可见时做增量拉取，杜绝离屏预组合触发后台网络请求
    LaunchedEffect(isPageVisible) {
        if (isPageVisible) {
            VpsHistorySyncManager.refreshFromServer()
        }
    }
    val checkedInfoMap = remember { mutableStateMapOf<Long, GalleryEntity>() }
    val selectMode = checkedInfoMap.isNotEmpty()
    val backdrop = rememberBlurBackdrop()
    val loadedItems = historyData.itemSnapshotList.items
    val allSelected = loadedItems.isNotEmpty() && loadedItems.all { it.gid in checkedInfoMap }
    LaunchedEffect(ownerKey, isPageVisible, selectMode) {
        SelectionModeController.update(ownerKey, isPageVisible && selectMode)
    }
    DisposableEffect(ownerKey) {
        onDispose { SelectionModeController.clearOwner(ownerKey) }
    }
    BackHandler(enabled = isPageVisible && selectMode) { checkedInfoMap.clear() }
    DrawerHandle(isPageVisible && !selectMode && !searchBarExpanded)
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
            searchFieldState = session.searchFieldState,
            searchBarOffsetY = { searchBarOffsetY },
            backdrop = backdrop,
            hideSearchBar = selectMode,
            ownerKey = ownerKey,
            trailingIcon = {
                SearchBarCircleAction(
                    onClick = {
                        launch {
                            awaitConfirmationOrCancel(
                                confirmText = R.string.clear_all,
                                text = { Text(text = stringResource(id = R.string.clear_all_history)) },
                            )
                            EhDB.clearHistoryInfo()
                            historyData.refresh()
                        }
                    },
                ) {
                    Icon(imageVector = MiuixIcons.Delete, contentDescription = null, modifier = Modifier.size(SearchBarIconSize))
                }
            },
        ) { paddingValues ->
            val currentPaddingValues by rememberUpdatedState(paddingValues)
            val currentDensity by rememberUpdatedState(density)
            val currentVisible by rememberUpdatedState(isPageVisible)
            val searchBarConnection = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        val dy = -consumed.y
                        val slop = with(currentDensity) { 8.dp.toPx() }
                        if (currentVisible) {
                            if (dy >= slop) {
                                BottomBarScrollState.hidden = true
                            } else if (dy <= -slop / 2) {
                                BottomBarScrollState.hidden = false
                            }
                        }
                        val topPaddingPx = with(currentDensity) { currentPaddingValues.calculateTopPadding().roundToPx() }
                        searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                        return Offset.Zero
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
                    modifier = Modifier.nestedScroll(searchBarConnection).fillMaxSize(),
                    state = session.listState,
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
        if (selectMode) {
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
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxWidth(),
                    backdrop = backdrop,
                )
            }
        }
    }
}
