package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentRecomposeScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.ui.component.FabLayout
import com.ehviewer.core.ui.component.LocalSideSheetState
import com.ehviewer.core.ui.component.ProvideSideSheetContent
import com.ehviewer.core.ui.component.SquircleShape
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
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.AvatarIcon
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.GalleryList
import com.hippo.ehviewer.ui.startDownload
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitSelectItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt
import moe.tarsin.coroutines.runSwallowingWithUI
import moe.tarsin.navigate
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.FavouritesScreen(navigator: DestinationsNavigator, viewModel: FavoritesViewModel = viewModel()) = Screen(navigator) {
    // Immutables
    val localFavName = stringResource(R.string.local_favorites)
    val cloudFavName = stringResource(R.string.cloud_favorites)
    val animateItems by Settings.animateItems.collectAsState()
    val hasSignedIn by Settings.hasSignedIn.collectAsState()

    // Meta State
    var urlBuilder by viewModel.urlBuilder
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by remember { mutableIntStateOf(0) }

    // Derived State
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
    val data = viewModel.data.collectAsLazyPagingItems()

    fun refresh(newUrlBuilder: FavListUrlBuilder = urlBuilder.copy(jumpTo = null, prev = null, next = null)) {
        urlBuilder = newUrlBuilder
        data.refresh()
    }


    val checkedInfoMap = remember { mutableStateMapOf<Long, BaseGalleryInfo>() }
    val selectMode = checkedInfoMap.isNotEmpty()
    DrawerHandle(!selectMode && !searchBarExpanded)

    SearchBarScreen(
        onApplySearch = { refresh(FavListUrlBuilder(urlBuilder.favCat, it)) },
        expanded = searchBarExpanded,
        onExpandedChange = {
            searchBarExpanded = it
            if (it) checkedInfoMap.clear()
        },
        title = title,
        searchFieldHint = searchBarHint,
        localSearch = urlBuilder.isLocal,
        searchBarOffsetY = { searchBarOffsetY },
        trailingIcon = {
            val localFavCount by viewModel.localFavCount.collectAsState(0)
            // 收藏分类数据不是 Compose 快照状态，变更时手动触发本区域重组以刷新数量
            val scope = currentRecomposeScope
            LaunchedEffect(Unit) {
                Settings.favChangesFlow.collect { scope.invalidate() }
            }
            // 点击文件夹图标后在图标正下方弹出悬浮菜单，替代原本占半屏的全高侧边抽屉
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
            WindowIconDropdownMenu(
                entry = DropdownEntry(items = favItems),
                maxHeight = 480.dp,
            ) {
                Icon(imageVector = MiuixIcons.Folder, contentDescription = null)
            }
            AvatarIcon()
        },
    ) { contentPadding ->
        val listMode by Settings.listMode.collectAsState()
        val height by collectListThumbSizeAsState()
        val showPages by Settings.showGalleryPages.collectAsState()
        val showProgress by Settings.showReadingProgress.collectAsState()
        // 原地旋转时 paddingValues/density 会变化，连接体内惰性读取最新值（remember 无 key 不重建）
        val currentContentPadding by rememberUpdatedState(contentPadding)
        val currentDensity by rememberUpdatedState(density)
        val searchBarConnection = remember {
            val slop = ViewConfiguration.get(contextOf<Context>()).scaledTouchSlop
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = -consumed.y
                    if (dy >= slop) {
                        BottomBarScrollState.hidden = true
                    } else if (dy <= -slop / 2) {
                        BottomBarScrollState.hidden = false
                    }
                    val topPaddingPx = with(currentDensity) { currentContentPadding.calculateTopPadding().roundToPx() }
                    searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                    return Offset.Zero // We never consume it
                }
            }
        }
        GalleryList(
            data = data,
            contentModifier = Modifier.nestedScroll(searchBarConnection),
            contentPadding = contentPadding,
            listMode = listMode,
            detailItemContent = { info ->
                val checked = info.gid in checkedInfoMap
                CheckableItem(
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
            thumbItemContent = { info ->
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
            onRefresh = { refresh() },
            onLoading = {
                searchBarOffsetY = 0
                BottomBarScrollState.hidden = false
            },
        )
    }

    // 日常浏览不再显示右下角悬浮按钮，仅长按卡片进入多选后显示批量操作栏
    if (selectMode) {
        FabLayout(
            hidden = false,
            expanded = true,
            onExpandChanged = { if (!it) checkedInfoMap.clear() },
            autoCancel = false,
        ) {
            onClick(MiuixIcons.SelectAll, autoClose = false) {
                val info = data.itemSnapshotList.items.associateBy { it.gid }
                checkedInfoMap.putAll(info)
            }
            onClick(MiuixIcons.Download) {
                val info = checkedInfoMap.takeAndClear()
                runSwallowingWithUI {
                    startDownload(false, *info.toTypedArray())
                }
            }
            onClick(MiuixIcons.Delete) {
                val info = checkedInfoMap.takeAndClear()
                awaitConfirmationOrCancel(title = R.string.delete_favorites_dialog_title) {
                    Text(text = stringResource(R.string.delete_favorites_dialog_message, info.size))
                }
                val srcCat = urlBuilder.favCat
                runSwallowingWithUI {
                    if (srcCat == FavListUrlBuilder.FAV_CAT_LOCAL) { // Delete local fav
                        EhDB.removeLocalFavorites(info)
                    } else {
                        val delList = info.mapToLongArray(BaseGalleryInfo::gid)
                        EhEngine.modifyFavorites(delList, srcCat, -1)
                    }
                }
                // We refresh anyway as cloud data maybe partially modified
                data.refresh()
            }
            onClick(MiuixIcons.MoveFile) {
                // First is local favorite, the other 10 is cloud favorite
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
                            // Move from local to cloud
                            val galleryList = info.map { it.gid to it.token }
                            EhEngine.addFavorites(galleryList, dstCat)
                            EhDB.removeLocalFavorites(info)
                        } else if (dstCat == FavListUrlBuilder.FAV_CAT_LOCAL) {
                            // Move from cloud to local
                            EhDB.putLocalFavorites(info)
                        } else {
                            // Move from cloud to cloud
                            val gidArray = info.mapToLongArray(BaseGalleryInfo::gid)
                            EhEngine.modifyFavorites(gidArray, srcCat, dstCat)
                        }
                    }
                    // We refresh anyway as cloud data maybe partially modified
                    data.refresh()
                }
            }
        }
    }
}
