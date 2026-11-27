package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import arrow.core.raise.ensure
import com.ehviewer.core.database.model.QuickSearch
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.ui.component.SelectionAction
import com.ehviewer.core.ui.component.SelectionActionBar
import com.ehviewer.core.ui.component.SelectionTopBar
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.theme.activeAccentColor
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.Bookmarks
import com.ehviewer.core.ui.icons.filled.MenuBook
import com.ehviewer.core.ui.util.LocalIsPageVisible
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.launchIO
import com.ehviewer.core.util.withUIContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_IMAGE_SEARCH
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_NORMAL
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_SUBSCRIPTION
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TAG
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TOPLIST
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_UPLOADER
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_WHATS_HOT
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.BottomBarScrollState
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.SelectionModeController
import com.hippo.ehviewer.ui.destinations.ProgressScreenDestination
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.GalleryList
import com.hippo.ehviewer.ui.main.GalleryTabSession
import com.hippo.ehviewer.ui.main.MainTabsViewModel
import com.hippo.ehviewer.ui.main.SearchFilter
import com.hippo.ehviewer.ui.main.SearchFilterStore
import com.hippo.ehviewer.ui.main.SelectableGalleryItem
import com.hippo.ehviewer.ui.navToReader
import com.hippo.ehviewer.ui.startDownload
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitInputTextWithCheckBox
import com.hippo.ehviewer.util.FavouriteStatusRouter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import moe.tarsin.coroutines.runSwallowingWithUI
import moe.tarsin.navigate
import moe.tarsin.snackbar
import moe.tarsin.string
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.HomePageScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val tabsViewModel: MainTabsViewModel = viewModel(contextOf<MainActivity>())
    GalleryListTabContent(session = tabsViewModel.homeSession, ownerKey = "route_home")
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.SubscriptionScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val tabsViewModel: MainTabsViewModel = viewModel(contextOf<MainActivity>())
    GalleryListTabContent(session = tabsViewModel.subscriptionSession, ownerKey = "route_subscription")
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.WhatshotScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val tabsViewModel: MainTabsViewModel = viewModel(contextOf<MainActivity>())
    GalleryListTabContent(session = tabsViewModel.whatshotSession, ownerKey = "route_whatshot")
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.ToplistScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val tabsViewModel: MainTabsViewModel = viewModel(contextOf<MainActivity>())
    GalleryListTabContent(session = tabsViewModel.toplistSession, ownerKey = "route_toplist")
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.GalleryListScreen(
    lub: ListUrlBuilder,
    navigator: DestinationsNavigator,
    viewModel: GalleryListViewModel = viewModel { GalleryListViewModel(lub, createSavedStateHandle()) },
) = Screen(navigator) {
    GalleryListBody(
        session = null,
        standaloneViewModel = viewModel,
        ownerKey = remember { "standalone_${System.identityHashCode(viewModel)}" },
    )
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
fun GalleryListTabContent(
    session: GalleryTabSession,
    ownerKey: String,
) {
    GalleryListBody(
        session = session,
        standaloneViewModel = null,
        ownerKey = ownerKey,
    )
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
private fun GalleryListBody(
    session: GalleryTabSession?,
    standaloneViewModel: GalleryListViewModel?,
    ownerKey: String,
) {
    val isPageVisible = LocalIsPageVisible.current
    val searchFieldState = session?.searchFieldState ?: rememberTextFieldState()
    var urlBuilder by (session?.urlBuilder ?: standaloneViewModel!!.urlBuilder)
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by (session?.searchBarOffsetY ?: remember { mutableIntStateOf(0) })

    val animateItems by Settings.animateItems.collectAsState()

    // 长按多选：按 gid 去重记录选中项，非空即进入选择态
    val checkedInfoMap = remember { mutableStateMapOf<Long, BaseGalleryInfo>() }
    val selectMode = checkedInfoMap.isNotEmpty()
    val backdrop = rememberBlurBackdrop()
    // 选择态期间暂停主界面左右滑动切页（按页面 ownerKey 隔离，离屏页不污染前台页）
    LaunchedEffect(ownerKey, isPageVisible, selectMode) {
        SelectionModeController.update(ownerKey, isPageVisible && selectMode)
    }
    DisposableEffect(ownerKey) {
        onDispose { SelectionModeController.clearOwner(ownerKey) }
    }
    // 系统返回（含侧滑返回）仅在当前可见页激活时退出选择态
    BackHandler(enabled = isPageVisible && selectMode) { checkedInfoMap.clear() }

    DrawerHandle(isPageVisible && !selectMode && !searchBarExpanded)

    LaunchedEffect(urlBuilder) {
        val keyword = urlBuilder.keyword?.let { keyword ->
            when (urlBuilder.mode) {
                MODE_UPLOADER -> "uploader:\"$keyword\""
                MODE_TAG -> wrapTagKeyword(keyword)
                MODE_TOPLIST -> ""
                else -> keyword
            }
        }.orEmpty()
        searchFieldState.setTextAndPlaceCursorAtEnd(keyword)
    }

    val density = LocalDensity.current
    val listState = session?.listState ?: rememberLazyGridState()
    val gridState = session?.gridState ?: rememberLazyStaggeredGridState()
    val isTopList = remember(urlBuilder) { urlBuilder.mode == MODE_TOPLIST }
    val ehHint = stringResource(R.string.gallery_list_search_bar_hint_e_hentai)
    val exHint = stringResource(R.string.gallery_list_search_bar_hint_exhentai)
    val searchBarHint by rememberUpdatedState(if (EhUtils.isExHentai) exHint else ehHint)
    val suitableTitle = getSuitableTitleForUrlBuilder(urlBuilder)
    val data = (session?.data ?: standaloneViewModel!!.data).collectAsLazyPagingItems()
    if (isPageVisible) {
        ReportDrawnWhen { data.loadState.refresh !is LoadState.Loading }
    }
    FavouriteStatusRouter.Observe(data)
    val listMode by Settings.listMode.collectAsState()

    val entries = stringArrayResource(id = com.hippo.ehviewer.R.array.toplist_entries)
    val values = stringArrayResource(id = com.hippo.ehviewer.R.array.toplist_values)
    val toplists = remember { entries zip values }
    val quickSearchName = getSuitableTitleForUrlBuilder(urlBuilder, false)
    var saveProgress by Settings.qSSaveProgress.asMutableState()

    fun getFirstVisibleItemIndex() = if (listMode == 0) {
        listState.firstVisibleItemIndex
    } else {
        gridState.firstVisibleItemIndex
    }

    val openGalleryKeyword = stringResource(R.string.gallery_list_search_bar_open_gallery)
    abstract class UrlSuggestion : Suggestion() {
        override val keyword = openGalleryKeyword
        override val canOpenDirectly = true
        override fun onClick() = navigate(destination)
        abstract val destination: Direction
    }

    class GalleryDetailUrlSuggestion(gid: Long, token: String) : UrlSuggestion() {
        override val destination = gid asDstWith token
    }

    class GalleryPageUrlSuggestion(gid: Long, pToken: String, page: Int) : UrlSuggestion() {
        override val destination = ProgressScreenDestination(gid, pToken, page)
    }

    fun onApplySearch(query: String) = launchIO {
        SearchFilterStore.persist()
        val builder = ListUrlBuilder()
        val oldMode = urlBuilder.mode
        val advancedOption = SearchFilterStore.advancedOption
        // If it's MODE_SUBSCRIPTION, keep it
        val newMode = if (oldMode == MODE_SUBSCRIPTION) MODE_SUBSCRIPTION else MODE_NORMAL
        builder.mode = newMode
        builder.keyword = query
        builder.category = SearchFilterStore.category
        builder.language = SearchFilterStore.language
        builder.advanceSearch = advancedOption.advanceSearch
        builder.minRating = advancedOption.minRating
        builder.pageFrom = advancedOption.fromPage
        builder.pageTo = advancedOption.toPage
        when (oldMode) {
            MODE_TOPLIST, MODE_WHATS_HOT -> {
                // Wait for search view to hide
                delay(300)
                withUIContext { navigate(builder.asDst()) }
            }
            else -> {
                session?.markExplicitNetworkRefresh()
                urlBuilder = builder
                data.refresh()
            }
        }
    }

    // 榜单 / 快速搜索菜单状态提升到页面级，菜单本体渲染在根 Box 内以获得整屏实时模糊
    val toplistMenuAnchor = rememberGlassPopupAnchor()
    var toplistMenuShow by remember { mutableStateOf(false) }
    val toplistEntry = DropdownEntry(
        items = toplists.map { (name, keyword) ->
            DropdownItem(
                text = name,
                selected = urlBuilder.keyword == keyword,
                onClick = {
                    Settings.recentToplist = keyword
                    session?.markExplicitNetworkRefresh()
                    urlBuilder = ListUrlBuilder(MODE_TOPLIST, keyword = keyword)
                    data.refresh()
                },
            )
        },
    )

    val quickSearchMenuAnchor = rememberGlassPopupAnchor()
    var quickSearchMenuShow by remember { mutableStateOf(false) }
    val quickSearchList = remember { mutableStateListOf<QuickSearch>() }
    LaunchedEffect(Unit) {
        quickSearchList.addAll(EhDB.getAllQuickSearch())
    }
    val invalidImageQuickSearch = stringResource(R.string.image_search_not_quick_search)
    val nameEmpty = stringResource(R.string.name_is_empty)
    val dupName = stringResource(R.string.duplicate_name)
    fun saveCurrentQuickSearch() = launch {
        if (urlBuilder.mode == MODE_IMAGE_SEARCH) {
            snackbar(invalidImageQuickSearch)
        } else {
            val firstItem = data.itemSnapshotList.items
                .getOrNull(getFirstVisibleItemIndex())
            val next = firstItem?.gid?.plus(1)
            quickSearchList.fastForEach { q ->
                if (next != null && urlBuilder.equalsQuickSearch(q)) {
                    val nextStr = q.name.substringAfterLast('@', "")
                    if (nextStr.toLongOrNull() == next) {
                        snackbar(string(R.string.duplicate_quick_search, q.name))
                        return@launch
                    }
                }
            }
            awaitInputTextWithCheckBox(
                initial = quickSearchName ?: urlBuilder.keyword.orEmpty(),
                title = R.string.add_quick_search_dialog_title,
                hint = R.string.quick_search,
                checked = saveProgress,
                checkBoxText = R.string.save_progress,
            ) { input, checked ->
                var text = input.trim()
                ensure(text.isNotBlank()) { nameEmpty }
                if (checked && next != null) text += "@$next"
                ensure(quickSearchList.none { it.name == text }) { dupName }
                val quickSearch = urlBuilder.toQuickSearch(text)
                quickSearch.position = quickSearchList.size
                // Insert to DB first to update the id
                EhDB.insertQuickSearch(quickSearch)
                quickSearchList.add(quickSearch)
                saveProgress = checked
            }
        }
    }
    // 长按操作态与行内编辑态（同一时间只作用于一条标签）
    var quickSearchActionsId by remember { mutableStateOf<Long?>(null) }
    var quickSearchEditingId by remember { mutableStateOf<Long?>(null) }
    var quickSearchEditingValue by remember { mutableStateOf(TextFieldValue("")) }
    val quickSearchEditFocus = remember { FocusRequester() }

    fun commitQuickSearchRename() {
        val id = quickSearchEditingId ?: return
        quickSearchEditingId = null
        val index = quickSearchList.indexOfFirst { it.id == id }
        if (index < 0) return
        val newName = quickSearchEditingValue.text.trim()
        // 空名称或纯空格时回退保留原名
        if (newName.isEmpty() || newName == quickSearchList[index].name) return
        // 同步当前排序位置，避免删除后 position 出现空洞
        val updated = quickSearchList[index].copy(name = newName, position = index)
        quickSearchList[index] = updated
        launch { EhDB.updateQuickSearch(listOf(updated)) }
    }

    fun closeQuickSearchMenu() {
        commitQuickSearchRename()
        quickSearchActionsId = null
        quickSearchMenuShow = false
    }

    SearchBarScreen(
        onApplySearch = ::onApplySearch,
        expanded = searchBarExpanded,
        onExpandedChange = {
            searchBarExpanded = it
            // 展开搜索时退出选择态，避免两套顶栏叠加
            if (it) checkedInfoMap.clear()
        },
        title = suitableTitle,
        searchFieldHint = searchBarHint,
        searchFieldState = searchFieldState,
        suggestionProvider = {
            GalleryDetailUrlParser.parse(it, false)?.run {
                listOf(GalleryDetailUrlSuggestion(gid, token))
            } ?: GalleryPageUrlParser.parse(it, false)?.run {
                listOf(GalleryPageUrlSuggestion(gid, pToken, page))
            }.orEmpty()
        },
        localSearch = false,
        searchBarOffsetY = { searchBarOffsetY },
        backdrop = backdrop,
        hideSearchBar = selectMode,
        onPersistFilter = SearchFilterStore::persist,
        ownerKey = ownerKey,
        trailingIcon = {
            if (isTopList) {
                // 榜单选择：以图标为锚点形变展开
                SearchBarCircleAction(
                    onClick = { toplistMenuShow = true },
                    modifier = Modifier.glassPopupAnchor(toplistMenuAnchor, cornerRadius = SearchBarActionCornerRadius, floating = true),
                ) {
                    Icon(imageVector = EhIcons.Default.Bookmarks, contentDescription = stringResource(id = R.string.toplist), modifier = Modifier.size(SearchBarIconSize))
                }
            } else {
                SearchBarCircleAction(
                    onClick = { quickSearchMenuShow = true },
                    modifier = Modifier.glassPopupAnchor(quickSearchMenuAnchor, cornerRadius = SearchBarActionCornerRadius, floating = true),
                ) {
                    Icon(imageVector = EhIcons.Default.Bookmarks, contentDescription = stringResource(id = R.string.quick_search), modifier = Modifier.size(SearchBarIconSize))
                }
            }
        },
        filter = {
            SearchFilter(
                category = SearchFilterStore.category,
                onCategoryChange = { SearchFilterStore.category = it },
                language = SearchFilterStore.language,
                onLanguageChange = { SearchFilterStore.language = it },
                advancedOption = SearchFilterStore.advancedOption,
                onAdvancedOptionChange = { SearchFilterStore.advancedOption = it },
            )
        },
    ) { contentPadding ->
        val height by collectListThumbSizeAsState()
        val showPages by Settings.showGalleryPages.collectAsState()
        val showProgress by Settings.showReadingProgress.collectAsState()
        // 原地旋转时 paddingValues/density 会变化，连接体内惰性读取最新值（remember 无 key 不重建）
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
                    return Offset.Zero // We never consume it
                }
            }
        }
        GalleryList(
            data = data,
            contentModifier = Modifier.nestedScroll(searchBarConnection),
            contentPadding = contentPadding,
            listMode = listMode,
            detailListState = listState,
            detailItemContent = { info ->
                val checked = info.gid in checkedInfoMap
                SelectableGalleryItem(
                    checked = checked,
                    modifier = Modifier.thenIf(animateItems) { animateItem() },
                ) { interactionSource ->
                    GalleryInfoListItem(
                        onClick = {
                            if (selectMode) {
                                if (checked) checkedInfoMap.remove(info.gid) else checkedInfoMap[info.gid] = info
                            } else {
                                navigate(info.asDst())
                            }
                        },
                        onLongClick = { checkedInfoMap[info.gid] = info },
                        info = info,
                        showPages = showPages,
                        showProgress = showProgress,
                        modifier = Modifier.height(height),
                        interactionSource = interactionSource,
                    )
                }
            },
            thumbListState = gridState,
            thumbItemContent = { info ->
                val checked = info.gid in checkedInfoMap
                SelectableGalleryItem(
                    checked = checked,
                    modifier = Modifier.thenIf(animateItems) { animateItem() },
                ) { interactionSource ->
                    GalleryInfoGridItem(
                        onClick = {
                            if (selectMode) {
                                if (checked) checkedInfoMap.remove(info.gid) else checkedInfoMap[info.gid] = info
                            } else {
                                navigate(info.asDst())
                            }
                        },
                        onLongClick = { checkedInfoMap[info.gid] = info },
                        info = info,
                        showPages = showPages,
                        showProgress = showProgress,
                        interactionSource = interactionSource,
                    )
                }
            },
            searchBarOffsetY = { searchBarOffsetY },
            suppressInitialLoadingSpinner = session?.isHydratingSeed?.value == true,
            onRefresh = {
                session?.markExplicitNetworkRefresh()
                urlBuilder.setRange(0)
                data.refresh()
            },
            onLoading = {
                searchBarOffsetY = 0
                if (currentVisible) {
                    BottomBarScrollState.hidden = false
                }
            },
        )
    }

    // 选择态：顶部选择栏覆盖搜索栏，底部悬浮胶囊操作栏（仅覆盖绘制，不拦截列表滚动）
    Box(modifier = Modifier.fillMaxSize()) {
        if (selectMode) {
            val snapshotItems = data.itemSnapshotList.items
            val allSelected = snapshotItems.isNotEmpty() && snapshotItems.all { it.gid in checkedInfoMap }
            SelectionTopBar(
                selectedCount = checkedInfoMap.size,
                allSelected = allSelected,
                onExit = { checkedInfoMap.clear() },
                onToggleSelectAll = {
                    if (allSelected) {
                        checkedInfoMap.clear()
                    } else {
                        snapshotItems.forEach { checkedInfoMap[it.gid] = it }
                    }
                },
                countText = "已选择 ${checkedInfoMap.size} 项",
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = selectMode,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            SelectionActionBar(
                actions = listOf(
                    SelectionAction(
                        icon = EhIcons.Default.MenuBook,
                        label = stringResource(R.string.read),
                        // 多选时无法确定要读哪一本，仅在单选时可用
                        enabled = checkedInfoMap.size == 1,
                        onClick = {
                            checkedInfoMap.values.singleOrNull()?.let { info ->
                                checkedInfoMap.clear()
                                launch {
                                    EhDB.putHistoryInfo(info)
                                    navToReader(info)
                                }
                            }
                        },
                    ),
                    SelectionAction(
                        icon = MiuixIcons.Download,
                        label = stringResource(R.string.download),
                        onClick = {
                            val infos = checkedInfoMap.values.toList()
                            checkedInfoMap.clear()
                            launch { runSwallowingWithUI { startDownload(false, *infos.toTypedArray()) } }
                        },
                    ),
                    SelectionAction(
                        icon = MiuixIcons.Favorites,
                        label = stringResource(R.string.favourite),
                        onClick = {
                            val infos = checkedInfoMap.values.toList()
                            checkedInfoMap.clear()
                            launch {
                                runSwallowingWithUI {
                                    // 默认收藏夹：优先用户默认分类，未设置时落到第一分类；未登录写入本地收藏
                                    if (Settings.hasSignedIn.value) {
                                        val dstCat = Settings.defaultFavSlot.value.let { if (it in 0..9) it else 0 }
                                        EhEngine.addFavorites(infos.map { it.gid to it.token }, dstCat)
                                    } else {
                                        EhDB.putLocalFavorites(infos)
                                    }
                                }
                            }
                        },
                    ),
                ),
                backdrop = backdrop,
            )
        }
        // 榜单时间范围：以图标为锚点的液态玻璃菜单
        Box(Modifier.fillMaxSize()) {
            GlassIconDropdownMenu(
                show = toplistMenuShow,
                onDismissRequest = { toplistMenuShow = false },
                anchor = toplistMenuAnchor,
                anchorContent = { Icon(imageVector = EhIcons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(SearchBarIconSize)) },
                entries = listOf(toplistEntry),
                backdrop = backdrop,
                visuals = liquidGlassPopupVisuals(),
            )
            // 快速搜索：自定义行（长按编辑/删除、行内重命名）通过锚点形变展开
            GlassTransformPopup(
                show = quickSearchMenuShow,
                onDismissRequest = { closeQuickSearchMenu() },
                anchor = quickSearchMenuAnchor,
                backdrop = backdrop,
                anchorContent = { Icon(imageVector = EhIcons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(SearchBarIconSize)) },
                sizing = GlassPopupSizing(minWidth = 240.dp, maxWidth = 320.dp),
                visuals = liquidGlassPopupVisuals(),
            ) {
                quickSearchList.forEachIndexed { index, item ->
                    key(item.id) {
                        QuickSearchMenuRow(
                            name = item.name,
                            selected = urlBuilder.equalsQuickSearch(item),
                            actionsVisible = quickSearchActionsId == item.id,
                            editing = quickSearchEditingId == item.id,
                            editValue = quickSearchEditingValue,
                            onEditValueChange = { quickSearchEditingValue = it },
                            focusRequester = quickSearchEditFocus,
                            isFirst = index == 0,
                            onClick = {
                                when {
                                    // 正在编辑本行时点击行内区域不打断输入
                                    quickSearchEditingId == item.id -> Unit
                                    // 点击其他标签：先提交当前修改并退出编辑态
                                    quickSearchEditingId != null -> commitQuickSearchRename()
                                    // 操作态下点击本行只收起操作按钮，不触发选择
                                    quickSearchActionsId == item.id -> quickSearchActionsId = null
                                    else -> {
                                        closeQuickSearchMenu()
                                        if (urlBuilder.mode == MODE_WHATS_HOT) {
                                            val builder = ListUrlBuilder(item).apply {
                                                language = SearchFilterStore.language
                                            }
                                            navigate(builder.asDst())
                                        } else {
                                            session?.markExplicitNetworkRefresh()
                                            urlBuilder = ListUrlBuilder(item).apply {
                                                language = SearchFilterStore.language
                                            }
                                            data.refresh()
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                if (quickSearchEditingId != null) commitQuickSearchRename()
                                quickSearchActionsId = if (quickSearchActionsId == item.id) null else item.id
                            },
                            onEdit = {
                                quickSearchActionsId = null
                                quickSearchEditingId = item.id
                                quickSearchEditingValue = TextFieldValue(
                                    text = item.name,
                                    selection = TextRange(0, item.name.length),
                                )
                            },
                            onDelete = {
                                quickSearchActionsId = null
                                if (quickSearchEditingId == item.id) quickSearchEditingId = null
                                quickSearchList.removeAll { it.id == item.id }
                                launch { EhDB.deleteQuickSearch(item) }
                            },
                            onCommitEdit = { commitQuickSearchRename() },
                        )
                    }
                }
                // 底部添加按钮：删光所有标签后仍保留
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (quickSearchEditingId != null) commitQuickSearchRename()
                            quickSearchActionsId = null
                            saveCurrentQuickSearch()
                        }
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = if (quickSearchList.isEmpty()) 20.dp else 12.dp,
                            bottom = 20.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "+ " + stringResource(R.string.add_quick_search_dialog_title),
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                }
            }
        }
    }
}

const val TOPLIST_PAGES = 200

@Composable
@Stable
context(_: Context)
private fun getSuitableTitleForUrlBuilder(urlBuilder: ListUrlBuilder, appName: Boolean = true): String? {
    val keyword = urlBuilder.keyword
    val category = urlBuilder.category
    val mode = urlBuilder.mode
    return if (mode == MODE_WHATS_HOT) {
        stringResource(R.string.whats_hot)
    } else if (!keyword.isNullOrEmpty()) {
        when (mode) {
            MODE_TOPLIST -> {
                when (keyword) {
                    "11" -> stringResource(R.string.toplist_alltime)
                    "12" -> stringResource(R.string.toplist_pastyear)
                    "13" -> stringResource(R.string.toplist_pastmonth)
                    "15" -> stringResource(R.string.toplist_yesterday)
                    else -> null
                }
            }
            MODE_TAG -> {
                val canTranslate = Settings.showTagTranslations.value && EhTagDatabase.translatable && EhTagDatabase.initialized
                wrapTagKeyword(keyword, canTranslate)
            }
            MODE_UPLOADER -> "uploader:\"$keyword\""
            else -> keyword
        }
    } else if (category == EhUtils.NONE && urlBuilder.advanceSearch == -1) {
        val appNameStr = stringResource(R.string.app_name)
        val homepageStr = stringResource(R.string.homepage)
        when (mode) {
            MODE_NORMAL -> if (appName) appNameStr else homepageStr
            MODE_SUBSCRIPTION -> stringResource(R.string.subscription)
            else -> null
        }
    } else if (category.countOneBits() == 1) {
        EhUtils.getCategory(category)
    } else {
        null
    }
}

private val QuickSearchDeleteRed = Color(0xFFE53935)

/**
 * 快速搜索下拉菜单中的单行：短按选择、长按显示修改/删除、修改时原地切换为输入框。
 */
@Composable
private fun QuickSearchMenuRow(
    name: String,
    selected: Boolean,
    actionsVisible: Boolean,
    editing: Boolean,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    isFirst: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCommitEdit: () -> Unit,
) {
    val accentColor = activeAccentColor()
    val titleColor = if (selected) accentColor else MiuixTheme.colorScheme.onSurfaceContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isFirst) 20.dp else 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (editing) {
            var hadFocus by remember { mutableStateOf(false) }
            BasicTextField(
                value = editValue,
                onValueChange = onEditValueChange,
                singleLine = true,
                textStyle = MiuixTheme.textStyles.body1.merge(color = titleColor),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitEdit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            // 点击其他区域导致失焦：自动提交并退出编辑态
                            hadFocus = false
                            onCommitEdit()
                        }
                    },
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Text(
                text = name,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        when {
            editing -> QuickSearchIconAction(
                icon = MiuixIcons.Basic.Check,
                tint = accentColor,
                contentDescription = null,
                onClick = onCommitEdit,
            )
            actionsVisible -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QuickSearchIconAction(
                    icon = MiuixIcons.Edit,
                    tint = MiuixTheme.colorScheme.onSurfaceContainer,
                    contentDescription = null,
                    onClick = onEdit,
                )
                QuickSearchIconAction(
                    icon = MiuixIcons.Delete,
                    tint = QuickSearchDeleteRed,
                    contentDescription = null,
                    onClick = onDelete,
                )
            }
            selected -> Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(DropdownDefaults.CheckIconSize),
            )
        }
    }
}

/** 下拉菜单行内的透明图标按钮：无实心底色，尺寸与图标完全一致。 */
@Composable
private fun QuickSearchIconAction(
    icon: ImageVector,
    tint: Color,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
