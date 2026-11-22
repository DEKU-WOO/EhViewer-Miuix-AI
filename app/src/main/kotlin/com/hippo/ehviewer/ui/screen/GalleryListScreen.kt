package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.fork.SwipeToDismissBox
import androidx.compose.material3.fork.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import com.ehviewer.core.ui.component.DismissDeleteBackground
import com.ehviewer.core.ui.component.FAB_ANIMATE_TIME
import com.ehviewer.core.ui.component.FabLayout
import com.ehviewer.core.ui.component.FastScrollLazyColumn
import com.ehviewer.core.ui.component.LocalSideSheetState
import com.ehviewer.core.ui.component.ProvideSideSheetContent
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.component.dismissDeleteAction
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.Bookmarks
import com.ehviewer.core.ui.icons.filled.FormatListNumbered
import com.ehviewer.core.ui.icons.filled.GoTo
import com.ehviewer.core.ui.icons.filled.LastPage
import com.ehviewer.core.ui.icons.filled.Shuffle
import com.ehviewer.core.ui.util.Await
import com.ehviewer.core.ui.util.HapticFeedbackType
import com.ehviewer.core.ui.util.asyncState
import com.ehviewer.core.ui.util.rememberHapticFeedback
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.launchIO
import com.ehviewer.core.util.onEachLatest
import com.ehviewer.core.util.withUIContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
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
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.destinations.ProgressScreenDestination
import com.hippo.ehviewer.ui.destinations.ToplistScreenDestination
import com.hippo.ehviewer.ui.doGalleryInfoAction
import com.hippo.ehviewer.ui.main.AdvancedSearchOption
import com.hippo.ehviewer.ui.main.AvatarIcon
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.GalleryList
import com.hippo.ehviewer.ui.main.SearchFilter
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitInputTextWithCheckBox
import com.hippo.ehviewer.ui.tools.rememberMutableStateInDataStore
import com.hippo.ehviewer.util.FavouriteStatusRouter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import moe.tarsin.navigate
import moe.tarsin.snackbar
import moe.tarsin.string
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.HomePageScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(), navigator)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.SubscriptionScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(MODE_SUBSCRIPTION), navigator)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.WhatshotScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(MODE_WHATS_HOT), navigator)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.ToplistScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(MODE_TOPLIST, keyword = Settings.recentToplist), navigator)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.GalleryListScreen(
    lub: ListUrlBuilder,
    navigator: DestinationsNavigator,
    viewModel: GalleryListViewModel = viewModel { GalleryListViewModel(lub, createSavedStateHandle()) },
) = Screen(navigator) {
    val searchFieldState = rememberTextFieldState()
    var urlBuilder by viewModel.urlBuilder
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarOffsetY by remember { mutableIntStateOf(0) }

    val animateItems by Settings.animateItems.collectAsState()

    var category by rememberMutableStateInDataStore("SearchCategory") { EhUtils.ALL_CATEGORY }
    var advancedSearchOption by rememberMutableStateInDataStore("AdvancedSearchOption") { AdvancedSearchOption() }

    DrawerHandle(!searchBarExpanded)

    LaunchedEffect(urlBuilder) {
        if (urlBuilder.category != EhUtils.NONE) category = urlBuilder.category
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
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val listState = rememberLazyGridState()
    val gridState = rememberLazyStaggeredGridState()
    val isTopList = remember(urlBuilder) { urlBuilder.mode == MODE_TOPLIST }
    val ehHint = stringResource(R.string.gallery_list_search_bar_hint_e_hentai)
    val exHint = stringResource(R.string.gallery_list_search_bar_hint_exhentai)
    val searchBarHint by rememberUpdatedState(if (EhUtils.isExHentai) exHint else ehHint)
    val suitableTitle = getSuitableTitleForUrlBuilder(urlBuilder)
    val data = viewModel.data.collectAsLazyPagingItems()
    ReportDrawnWhen { data.loadState.refresh !is LoadState.Loading }
    FavouriteStatusRouter.Observe(data)
    val listMode by Settings.listMode.collectAsState()

    val entries = stringArrayResource(id = com.hippo.ehviewer.R.array.toplist_entries)
    val values = stringArrayResource(id = com.hippo.ehviewer.R.array.toplist_values)
    val toplists = remember { entries zip values }
    val quickSearchName = getSuitableTitleForUrlBuilder(urlBuilder, false)
    var saveProgress by Settings.qSSaveProgress.asMutableState()
    var languageFilter by Settings.languageFilter.asMutableState()

    fun getFirstVisibleItemIndex() = if (listMode == 0) {
        listState.firstVisibleItemIndex
    } else {
        gridState.firstVisibleItemIndex
    }

    if (isTopList) {
        ProvideSideSheetContent { sheetState ->
            SmallTopAppBar(
                title = stringResource(id = R.string.toplist),
                color = MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = false,
            )
            // 有意的设计差异：榜单选择是单选列表，不是设置项分组，因此不套 Card，
            // 选中态由行底色表达；与设置页的 Card 分组是两套语义，不要按"缺 Card"处理。
            toplists.forEach { (name, keyword) ->
                val isSelected = urlBuilder.keyword == keyword
                val bgColor = if (isSelected) MiuixTheme.colorScheme.primaryContainer else Color.Transparent
                val textColor = if (isSelected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(SquircleShape(12.dp))
                        .background(bgColor)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                Settings.recentToplist = keyword
                                urlBuilder = ListUrlBuilder(MODE_TOPLIST, keyword = keyword)
                                data.refresh()
                                launch { sheetState.close() }
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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
        val builder = ListUrlBuilder()
        val oldMode = urlBuilder.mode
        // If it's MODE_SUBSCRIPTION, keep it
        val newMode = if (oldMode == MODE_SUBSCRIPTION) MODE_SUBSCRIPTION else MODE_NORMAL
        builder.mode = newMode
        builder.keyword = query
        builder.category = category
        builder.language = languageFilter
        builder.advanceSearch = advancedSearchOption.advanceSearch
        builder.minRating = advancedSearchOption.minRating
        builder.pageFrom = advancedSearchOption.fromPage
        builder.pageTo = advancedSearchOption.toPage
        when (oldMode) {
            MODE_TOPLIST, MODE_WHATS_HOT -> {
                // Wait for search view to hide
                delay(300)
                withUIContext { navigate(builder.asDst()) }
            }
            else -> {
                urlBuilder = builder
                data.refresh()
            }
        }
    }

    SearchBarScreen(
        onApplySearch = ::onApplySearch,
        expanded = searchBarExpanded,
        onExpandedChange = {
            searchBarExpanded = it
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
        trailingIcon = {
            if (isTopList) {
                // 榜单页仍使用侧边抽屉选择榜单
                val sheetState = LocalSideSheetState.current
                IconButton(onClick = { launch { sheetState.open() } }) {
                    Icon(imageVector = EhIcons.Default.Bookmarks, contentDescription = stringResource(id = R.string.toplist))
                }
            } else {
                // 快速搜索改为图标正下方的悬浮下拉菜单，替代原本占半屏的全高侧边抽屉
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
                        // A refresh can temporarily expose an empty snapshot even when the
                        // load state is idle (for example after a failed/empty search). Do
                        // not index into it; quick-search names can still be created without
                        // the optional progress suffix.
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
                val quickSearchEntry = DropdownEntry(
                    items = buildList {
                        quickSearchList.forEach { item ->
                            add(
                                DropdownItem(
                                    text = item.name,
                                    selected = urlBuilder.equalsQuickSearch(item),
                                    onClick = {
                                        if (urlBuilder.mode == MODE_WHATS_HOT) {
                                            val builder = ListUrlBuilder(item).apply {
                                                language = languageFilter
                                            }
                                            navigate(builder.asDst())
                                        } else {
                                            urlBuilder = ListUrlBuilder(item).apply {
                                                language = languageFilter
                                            }
                                            data.refresh()
                                        }
                                    },
                                ),
                            )
                        }
                        add(
                            DropdownItem(
                                text = "+ " + stringResource(R.string.add_quick_search_dialog_title),
                                onClick = { saveCurrentQuickSearch() },
                            ),
                        )
                    },
                )
                WindowIconDropdownMenu(
                    entry = quickSearchEntry,
                    maxHeight = 480.dp,
                ) {
                    Icon(imageVector = EhIcons.Default.Bookmarks, contentDescription = stringResource(id = R.string.quick_search))
                }
            }
            // 手机端底部栏只有 6 项，排行榜没有入口，在热门页补一个
            if (urlBuilder.mode == MODE_WHATS_HOT) {
                IconButton(onClick = { navigate(ToplistScreenDestination) }) {
                    Icon(imageVector = EhIcons.Default.FormatListNumbered, contentDescription = stringResource(id = R.string.toplist))
                }
            }
            AvatarIcon()
        },
        filter = {
            SearchFilter(
                category = category,
                onCategoryChange = { category = it },
                language = languageFilter,
                onLanguageChange = { languageFilter = it },
                advancedOption = advancedSearchOption,
                onAdvancedOptionChange = { advancedSearchOption = it },
            )
        },
    ) { contentPadding ->
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
            detailListState = listState,
            detailItemContent = { info ->
                GalleryInfoListItem(
                    onClick = { navigate(info.asDst()) },
                    onLongClick = { launch { doGalleryInfoAction(info) } },
                    info = info,
                    showPages = showPages,
                    showProgress = showProgress,
                    modifier = Modifier.height(height),
                )
            },
            thumbListState = gridState,
            thumbItemContent = { info ->
                GalleryInfoGridItem(
                    onClick = { navigate(info.asDst()) },
                    onLongClick = { launch { doGalleryInfoAction(info) } },
                    info = info,
                    showPages = showPages,
                    showProgress = showProgress,
                )
            },
            searchBarOffsetY = { searchBarOffsetY },
            onRefresh = {
                urlBuilder.setRange(0)
                data.refresh()
            },
            onLoading = {
                searchBarOffsetY = 0
                BottomBarScrollState.hidden = false
            },
        )
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
