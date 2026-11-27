package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentRecomposeScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.ui.component.SelectionAction
import com.ehviewer.core.ui.component.SelectionActionBar
import com.ehviewer.core.ui.component.SelectionTopBar
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.MenuBook
import com.ehviewer.core.ui.util.LocalIsPageVisible
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.takeAndClear
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.mapToLongArray
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.BottomBarScrollState
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.SelectionModeController
import com.hippo.ehviewer.ui.main.FavoritesTabSession
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.GalleryList
import com.hippo.ehviewer.ui.main.MainTabsViewModel
import com.hippo.ehviewer.ui.main.SelectableGalleryItem
import com.hippo.ehviewer.ui.startDownload
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitSelectItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import moe.tarsin.coroutines.runSwallowingWithUI
import moe.tarsin.navigate
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.MoveFile

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.FavouritesScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val tabsViewModel: MainTabsViewModel = viewModel(contextOf<MainActivity>())
    FavoritesTabContent(session = tabsViewModel.favoritesSession, ownerKey = "route_favorites")
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
fun FavoritesTabContent(
    session: FavoritesTabSession,
    ownerKey: String = "tab_favorites",
) {
    val isPageVisible = LocalIsPageVisible.current
    val localFavName = stringResource(R.string.local_favorites)
    val cloudFavName = stringResource(R.string.cloud_favorites)
    val animateItems by Settings.animateItems.collectAsState()
    val hasSignedIn by Settings.hasSignedIn.collectAsState()

    var urlBuilder by session.urlBuilder
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by session.searchBarOffsetY

    val keyword = urlBuilder.keyword
    val favCatName = remember(urlBuilder) {
        when (val favCat = urlBuilder.favCat) {
            in 0..9 -> Settings.favCat[favCat]
            FavListUrlBuilder.FAV_CAT_LOCAL -> localFavName.also { searchBarOffsetY = 0 }
            else -> cloudFavName
        }
    }
    val title = if (keyword.isNullOrBlank()) {
        stringResource(R.string.favorites_title, favCatName)
    } else {
        stringResource(R.string.favorites_title_2, favCatName, keyword)
    }
    val density = LocalDensity.current
    val searchBarHint = stringResource(R.string.search_bar_hint, favCatName)
    val data = session.data.collectAsLazyPagingItems()

    fun refresh(newUrlBuilder: FavListUrlBuilder = urlBuilder.copy(jumpTo = null, prev = null, next = null)) {
        session.markExplicitNetworkRefresh()
        urlBuilder = newUrlBuilder
        data.refresh()
    }

    val checkedInfoMap = remember { mutableStateMapOf<Long, BaseGalleryInfo>() }
    val selectMode = checkedInfoMap.isNotEmpty()
    val backdrop = rememberBlurBackdrop()
    // 收藏分类数量不是 Compose 快照状态，变更时失效整个页面以刷新菜单数量
    val localFavCount by session.localFavCount.collectAsState(0)
    val favRecomposeScope = currentRecomposeScope
    LaunchedEffect(Unit) {
        Settings.favChangesFlow.collect { favRecomposeScope.invalidate() }
    }
    val favMenuAnchor = rememberGlassPopupAnchor()
    var favMenuShow by remember { mutableStateOf(false) }
    val favItems = buildList {
        add(
            DropdownItem(
                text = stringResource(id = R.string.local_favorites),
                summary = localFavCount.toString(),
                selected = urlBuilder.favCat == -2,
                onClick = {
                    refresh(FavListUrlBuilder(-2))
                    Settings.recentFavCat = -2
                },
            ),
        )
        if (hasSignedIn) {
            add(
                DropdownItem(
                    text = stringResource(id = R.string.cloud_favorites),
                    summary = Settings.favCloudCount.toString(),
                    selected = urlBuilder.favCat == -1,
                    onClick = {
                        refresh(FavListUrlBuilder(-1))
                        Settings.recentFavCat = -1
                    },
                ),
            )
            Settings.favCat.forEachIndexed { index, name ->
                add(
                    DropdownItem(
                        text = name,
                        summary = Settings.favCount[index].toString(),
                        selected = urlBuilder.favCat == index,
                        onClick = {
                            refresh(FavListUrlBuilder(index))
                            Settings.recentFavCat = index
                        },
                    ),
                )
            }
        }
    }
    val loadedItems = data.itemSnapshotList.items
    val allSelected = loadedItems.isNotEmpty() && loadedItems.all { it.gid in checkedInfoMap }
    // 进入/退出多选时按页面 ownerKey 同步全局状态，主界面据此暂停左右切页
    LaunchedEffect(ownerKey, isPageVisible, selectMode) {
        SelectionModeController.update(ownerKey, isPageVisible && selectMode)
    }
    DisposableEffect(ownerKey) {
        onDispose { SelectionModeController.clearOwner(ownerKey) }
    }
    BackHandler(enabled = isPageVisible && selectMode) { checkedInfoMap.clear() }
    DrawerHandle(isPageVisible && !selectMode && !searchBarExpanded)

    Box(Modifier.fillMaxSize()) {
        SearchBarScreen(
            onApplySearch = { refresh(FavListUrlBuilder(urlBuilder.favCat, it)) },
            expanded = searchBarExpanded,
            onExpandedChange = {
                searchBarExpanded = it
                if (it) checkedInfoMap.clear()
            },
            title = title,
            searchFieldHint = searchBarHint,
            searchFieldState = session.searchFieldState,
            localSearch = urlBuilder.isLocal,
            searchBarOffsetY = { searchBarOffsetY },
            backdrop = backdrop,
            hideSearchBar = selectMode,
            ownerKey = ownerKey,
            trailingIcon = {
                SearchBarCircleAction(
                    onClick = { favMenuShow = true },
                    modifier = Modifier.glassPopupAnchor(favMenuAnchor, cornerRadius = SearchBarActionCornerRadius, floating = true),
                ) {
                    Icon(imageVector = MiuixIcons.Folder, contentDescription = null, modifier = Modifier.size(SearchBarIconSize))
                }
            },
        ) { contentPadding ->
            val listMode by Settings.listMode.collectAsState()
            val height by collectListThumbSizeAsState()
            val showPages by Settings.showGalleryPages.collectAsState()
            val showProgress by Settings.showReadingProgress.collectAsState()
            val currentContentPadding by rememberUpdatedState(contentPadding)
            val currentDensity by rememberUpdatedState(density)
            val currentVisible by rememberUpdatedState(isPageVisible)
            val searchBarConnection = remember {
                val slop = ViewConfiguration.get(contextOf<Context>()).scaledTouchSlop
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        val dy = -consumed.y
                        if (currentVisible) {
                            if (dy >= slop) {
                                BottomBarScrollState.hidden = true
                            } else if (dy <= -slop / 2) {
                                BottomBarScrollState.hidden = false
                            }
                        }
                        val topPaddingPx = with(currentDensity) { currentContentPadding.calculateTopPadding().roundToPx() }
                        searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                        return Offset.Zero
                    }
                }
            }
            GalleryList(
                data = data,
                contentModifier = Modifier.nestedScroll(searchBarConnection),
                contentPadding = contentPadding,
                listMode = listMode,
                detailListState = session.listState,
                detailItemContent = { info ->
                    val checked = info.gid in checkedInfoMap
                    SelectableGalleryItem(
                        checked = checked,
                        modifier = Modifier.thenIf(animateItems) { animateItem() },
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
                            onLongClick = {
                                checkedInfoMap[info.gid] = info
                            },
                            info = info,
                            showPages = showPages,
                            showProgress = showProgress,
                            modifier = Modifier.height(height),
                            isInFavScene = true,
                            interactionSource = interactionSource,
                        )
                    }
                },
                thumbListState = session.gridState,
                thumbItemContent = { info ->
                    val checked = info.gid in checkedInfoMap
                    SelectableGalleryItem(
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
                                    navigate(info.asDst())
                                }
                            },
                            onLongClick = {
                                checkedInfoMap[info.gid] = info
                            },
                            info = info,
                            showPages = showPages,
                            showProgress = showProgress,
                            showFavoriteStatus = false,
                            interactionSource = interactionSource,
                        )
                    }
                },
                searchBarOffsetY = { searchBarOffsetY },
                scrollToTopOnRefresh = urlBuilder.favCat != FavListUrlBuilder.FAV_CAT_LOCAL,
                suppressInitialLoadingSpinner = session.isHydratingSeed.value,
                onRefresh = { refresh() },
                onLoading = {
                    searchBarOffsetY = 0
                    if (currentVisible) {
                        BottomBarScrollState.hidden = false
                    }
                },
            )
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
        GlassIconDropdownMenu(
            show = favMenuShow,
            onDismissRequest = { favMenuShow = false },
            anchor = favMenuAnchor,
            anchorContent = { Icon(imageVector = MiuixIcons.Folder, contentDescription = null, modifier = Modifier.size(SearchBarIconSize)) },
            entries = listOf(DropdownEntry(items = favItems)),
            backdrop = backdrop,
            visuals = liquidGlassPopupVisuals(),
        )
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
                        SelectionAction(icon = MiuixIcons.MoveFile, label = stringResource(R.string.download_move_dialog_title)) {
                            launch {
                                val items = buildList {
                                    add(localFavName)
                                    if (hasSignedIn) {
                                        addAll(Settings.favCat)
                                    }
                                }
                                val index = awaitSelectItem(items, R.string.move_favorites_dialog_title)
                                val srcCat = urlBuilder.favCat
                                val dstCat = if (index == 0) FavListUrlBuilder.FAV_CAT_LOCAL else index - 1
                                val info = checkedInfoMap.takeAndClear()
                                if (srcCat != dstCat) {
                                    runSwallowingWithUI {
                                        if (srcCat == FavListUrlBuilder.FAV_CAT_LOCAL) {
                                            val galleryList = info.map { it.gid to it.token }
                                            EhEngine.addFavorites(galleryList, dstCat)
                                            EhDB.removeLocalFavorites(info)
                                        } else if (dstCat == FavListUrlBuilder.FAV_CAT_LOCAL) {
                                            EhDB.putLocalFavorites(info)
                                        } else {
                                            val gidArray = info.mapToLongArray(BaseGalleryInfo::gid)
                                            EhEngine.modifyFavorites(gidArray, srcCat, dstCat)
                                        }
                                    }
                                    session.markExplicitNetworkRefresh()
                                    data.refresh()
                                }
                            }
                        },
                        SelectionAction(icon = MiuixIcons.Delete, label = stringResource(R.string.delete)) {
                            launch {
                                val info = checkedInfoMap.takeAndClear()
                                awaitConfirmationOrCancel(title = R.string.delete_favorites_dialog_title) {
                                    Text(text = stringResource(R.string.delete_favorites_dialog_message, info.size))
                                }
                                val srcCat = urlBuilder.favCat
                                runSwallowingWithUI {
                                    if (srcCat == FavListUrlBuilder.FAV_CAT_LOCAL) {
                                        EhDB.removeLocalFavorites(info)
                                    } else {
                                        val delList = info.mapToLongArray(BaseGalleryInfo::gid)
                                        EhEngine.modifyFavorites(delList, srcCat, -1)
                                    }
                                }
                                session.markExplicitNetworkRefresh()
                                data.refresh()
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
