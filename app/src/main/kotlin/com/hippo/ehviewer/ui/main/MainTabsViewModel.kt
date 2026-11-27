package com.hippo.ehviewer.ui.main

import androidx.collection.MutableLongSet
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.ehviewer.core.database.model.GalleryEntity
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_NORMAL
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_SUBSCRIPTION
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TAG
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TOPLIST
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_UPLOADER
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_WHATS_HOT
import com.hippo.ehviewer.ui.MainNavItem
import com.hippo.ehviewer.ui.screen.TOPLIST_PAGES
import com.hippo.ehviewer.ui.screen.wrapTagKeyword
import com.hippo.ehviewer.ui.tools.foldToLoadResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.tarsin.coroutines.runSuspendCatching

private fun initialSearchFieldTextFor(builder: ListUrlBuilder): String = builder.keyword?.let { kw ->
    when (builder.mode) {
        MODE_UPLOADER -> "uploader:\"$kw\""
        MODE_TAG -> wrapTagKeyword(kw)
        MODE_TOPLIST -> ""
        else -> kw
    }
}.orEmpty()

/**
 * 单个画廊网络 Tab（主页 / 订阅 / 热门 / 排行）的常驻会话：
 * 托管 PagingData 内存流、本地磁盘快照种子（含上次离开时的搜索 `ListUrlBuilder`）、列表滚动位置与搜索栏折叠偏移。
 */
class GalleryTabSession(
    val slotKey: String,
    initialBuilder: ListUrlBuilder,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val initialSeed: SeedPage? = TabDiskSnapshotStore.readSnapshot(slotKey)
    private val restoredInitialBuilder: ListUrlBuilder = run {
        val saved = initialSeed?.listUrlBuilder
        when {
            saved == null -> initialBuilder
            initialBuilder.mode == MODE_TOPLIST -> {
                if (saved.mode == MODE_TOPLIST && saved.keyword == Settings.recentToplist) {
                    saved.copy()
                } else {
                    initialBuilder
                }
            }
            else -> saved.copy()
        }
    }

    val urlBuilder = mutableStateOf(restoredInitialBuilder)
    val listState = LazyGridState()
    val gridState = LazyStaggeredGridState()
    val searchBarOffsetY = mutableIntStateOf(0)
    val searchFieldState = TextFieldState(initialText = initialSearchFieldTextFor(restoredInitialBuilder))

    private val seedRef = AtomicReference<SeedPage?>(initialSeed)
    private val allowSeed = AtomicBoolean(initialSeed != null)
    val isHydratingSeed = mutableStateOf(initialSeed != null)
    private var currentPagingSource: PagingSource<String, BaseGalleryInfo>? = null

    /** 用户主动下拉刷新、提交搜索、切换榜单或切换快速搜索时调用：跳过本地快照强制拉取最新网络第 1 页并覆写快照。 */
    fun markExplicitNetworkRefresh() {
        allowSeed.set(false)
        seedRef.set(null)
        isHydratingSeed.value = false
    }

    /** 站点切换或退出登录时清空内存种子并失效当前 PagingSource。 */
    fun invalidateOnSiteOrAccountChange(resetBuilder: ListUrlBuilder) {
        allowSeed.set(false)
        seedRef.set(null)
        isHydratingSeed.value = false
        urlBuilder.value = resetBuilder
        currentPagingSource?.invalidate()
    }

    /** 供 Phase 2 放射状后台预取器查询该 Tab 是否已具备可用快照或已加载过数据。 */
    fun hasCachedOrPersistedData(): Boolean = seedRef.get() != null || TabDiskSnapshotStore.hasSnapshot(slotKey)

    val data: Flow<PagingData<BaseGalleryInfo>> = Pager(PagingConfig(25, prefetchDistance = 20)) {
        object : PagingSource<String, BaseGalleryInfo>() {
            override fun getRefreshKey(state: PagingState<String, BaseGalleryInfo>): String? = null

            override suspend fun load(params: LoadParams<String>) = withIOContext {
                val builder = urlBuilder.value
                val isInitialRefresh = params is LoadParams.Refresh && params.key == null

                if (builder.mode == MODE_TOPLIST) {
                    val key: Int
                    val page1Snapshot: ListUrlBuilder?
                    val url = mutex.withLock {
                        with(builder) {
                            if (isInitialRefresh) {
                                page = 1
                            } else {
                                params.key?.let { page = it.toInt() }
                            }
                            key = page
                            page1Snapshot = if (isInitialRefresh) copy() else null
                            build()
                        }
                    }
                    if (isInitialRefresh && allowSeed.compareAndSet(true, false)) {
                        val seed = seedRef.getAndSet(null) ?: TabDiskSnapshotStore.readSnapshot(slotKey)
                        isHydratingSeed.value = false
                        if (seed != null && seed.urlSignature == url && seed.items.isNotEmpty()) {
                            return@withIOContext LoadResult.Page(seed.items, seed.prevKey, seed.nextKey)
                        }
                    } else if (isInitialRefresh) {
                        isHydratingSeed.value = false
                    }
                    val prev = (key - 1).takeIf { it > 0 }?.toString()
                    val next = (key + 1).takeIf { it <= TOPLIST_PAGES }?.toString()
                    runSuspendCatching {
                        EhEngine.getGalleryList(url)
                    }.foldToLoadResult { result ->
                        if (isInitialRefresh && result.galleryInfoList.isNotEmpty()) {
                            TabDiskSnapshotStore.writeSnapshotAsync(
                                slotKey = slotKey,
                                seed = SeedPage(
                                    urlSignature = url,
                                    prevKey = prev,
                                    nextKey = next,
                                    items = result.galleryInfoList,
                                    listUrlBuilder = page1Snapshot,
                                ),
                            )
                        }
                        LoadResult.Page(result.galleryInfoList, prev, next)
                    }
                } else {
                    val page1Snapshot: ListUrlBuilder?
                    val url = mutex.withLock {
                        with(builder) {
                            when (params) {
                                is LoadParams.Prepend -> setIndex(params.key, isNext = false)
                                is LoadParams.Append -> setIndex(params.key, isNext = true)
                                is LoadParams.Refresh -> params.key?.let { setIndex(it, false) }
                            }
                            page1Snapshot = if (isInitialRefresh) copy() else null
                            build()
                        }
                    }
                    if (isInitialRefresh && allowSeed.compareAndSet(true, false)) {
                        val seed = seedRef.getAndSet(null) ?: TabDiskSnapshotStore.readSnapshot(slotKey)
                        isHydratingSeed.value = false
                        if (seed != null && seed.urlSignature == url && seed.items.isNotEmpty()) {
                            return@withIOContext LoadResult.Page(seed.items, seed.prevKey, seed.nextKey)
                        }
                    } else if (isInitialRefresh) {
                        isHydratingSeed.value = false
                    }
                    runSuspendCatching {
                        EhEngine.getGalleryList(url)
                    }.foldToLoadResult { result ->
                        if (isInitialRefresh && result.galleryInfoList.isNotEmpty()) {
                            TabDiskSnapshotStore.writeSnapshotAsync(
                                slotKey = slotKey,
                                seed = SeedPage(
                                    urlSignature = url,
                                    prevKey = result.prev,
                                    nextKey = result.next,
                                    items = result.galleryInfoList,
                                    listUrlBuilder = page1Snapshot,
                                ),
                            )
                        }
                        LoadResult.Page(result.galleryInfoList, result.prev, result.next)
                    }
                }
            }
        }.also { currentPagingSource = it }
    }.flow.cachedIn(scope)
}

/**
 * 收藏页常驻会话：本地收藏走 Room 零网络查询，云端收藏同步持久化并恢复 `FavListUrlBuilder` 与第 1 页快照。
 */
class FavoritesTabSession(scope: CoroutineScope) {
    private val slotKey = "favorites"
    private val mutex = Mutex()

    private val defaultFavBuilder = FavListUrlBuilder(favCat = Settings.recentFavCat)
    private val initialSeed: SeedPage? = if (defaultFavBuilder.isLocal) {
        null
    } else {
        TabDiskSnapshotStore.readSnapshot(slotKey)
    }
    private val restoredFavBuilder: FavListUrlBuilder = run {
        val saved = initialSeed?.favUrlBuilder
        if (!defaultFavBuilder.isLocal && saved != null && saved.favCat == defaultFavBuilder.favCat) {
            saved.copy()
        } else {
            defaultFavBuilder
        }
    }

    val urlBuilder = mutableStateOf(restoredFavBuilder)
    val listState = LazyGridState()
    val gridState = LazyStaggeredGridState()
    val searchBarOffsetY = mutableIntStateOf(0)
    val searchFieldState = TextFieldState(initialText = restoredFavBuilder.keyword.orEmpty())

    val localFavCount = EhDB.localFavCount

    private val seedRef = AtomicReference<SeedPage?>(initialSeed)
    private val allowSeed = AtomicBoolean(initialSeed != null)
    val isHydratingSeed = mutableStateOf(initialSeed != null)
    private var currentCloudPagingSource: PagingSource<String, BaseGalleryInfo>? = null

    fun markExplicitNetworkRefresh() {
        allowSeed.set(false)
        seedRef.set(null)
        isHydratingSeed.value = false
    }

    fun invalidateOnSiteOrAccountChange() {
        allowSeed.set(false)
        seedRef.set(null)
        isHydratingSeed.value = false
        currentCloudPagingSource?.invalidate()
    }

    fun hasCachedOrPersistedData(): Boolean =
        urlBuilder.value.isLocal || seedRef.get() != null || TabDiskSnapshotStore.hasSnapshot(slotKey)

    val data: Flow<PagingData<BaseGalleryInfo>> = snapshotFlow { urlBuilder.value.isLocal }.flatMapLatest { isLocalFav ->
        if (isLocalFav) {
            isHydratingSeed.value = false
            Pager(PagingConfig(20, jumpThreshold = 40)) {
                val keywordNow = urlBuilder.value.keyword.orEmpty()
                if (keywordNow.isBlank()) {
                    EhDB.localFavLazyList
                } else {
                    EhDB.searchLocalFav(keywordNow)
                }
            }.flow.map { paging -> paging.map<_, BaseGalleryInfo> { it } }
        } else {
            Pager(PagingConfig(FAV_PAGE_SIZE, prefetchDistance = 20)) {
                object : PagingSource<String, BaseGalleryInfo>() {
                    override fun getRefreshKey(state: PagingState<String, BaseGalleryInfo>): String? = null

                    override suspend fun load(params: LoadParams<String>) = withIOContext {
                        val isInitialRefresh = params is LoadParams.Refresh && params.key == null
                        val page1Snapshot: FavListUrlBuilder?
                        val url = mutex.withLock {
                            with(urlBuilder.value) {
                                when (params) {
                                    is LoadParams.Prepend -> setIndex(params.key, isNext = false)
                                    is LoadParams.Append -> setIndex(params.key, isNext = true)
                                    is LoadParams.Refresh -> params.key?.let { setIndex(it, false) }
                                }
                                page1Snapshot = if (isInitialRefresh) copy() else null
                                build()
                            }
                        }
                        if (isInitialRefresh && allowSeed.compareAndSet(true, false)) {
                            val seed = seedRef.getAndSet(null) ?: TabDiskSnapshotStore.readSnapshot(slotKey)
                            isHydratingSeed.value = false
                            if (seed != null && seed.urlSignature == url && seed.items.isNotEmpty()) {
                                return@withIOContext LoadResult.Page(seed.items, seed.prevKey, seed.nextKey)
                            }
                        } else if (isInitialRefresh) {
                            isHydratingSeed.value = false
                        }
                        runSuspendCatching {
                            EhEngine.getFavorites(url)
                        }.foldToLoadResult { result ->
                            Settings.favCat = result.catArray.toTypedArray()
                            Settings.favCount = result.countArray.toIntArray()
                            Settings.favCloudCount = result.countArray.sum()
                            if (isInitialRefresh && result.galleryInfoList.isNotEmpty()) {
                                TabDiskSnapshotStore.writeSnapshotAsync(
                                    slotKey = slotKey,
                                    seed = SeedPage(
                                        urlSignature = url,
                                        prevKey = result.prev,
                                        nextKey = result.next,
                                        items = result.galleryInfoList,
                                        favUrlBuilder = page1Snapshot,
                                    ),
                                )
                            }
                            LoadResult.Page(result.galleryInfoList, result.prev, result.next)
                        }
                    }
                }.also { currentCloudPagingSource = it }
            }.flow.map { paging ->
                val gidSet = MutableLongSet(FAV_PAGE_SIZE)
                paging.filter { gidSet.add(it.gid) }
            }
        }
    }.cachedIn(scope)
}

class HistoryTabSession(scope: CoroutineScope) {
    val keyword = mutableStateOf("")
    val listState = LazyListState()
    val searchBarOffsetY = mutableIntStateOf(0)
    val searchFieldState = TextFieldState()

    val data: Flow<PagingData<GalleryEntity>> = Pager(config = PagingConfig(pageSize = 20, jumpThreshold = 40)) {
        val kw = keyword.value
        if (kw.isNotEmpty()) {
            EhDB.searchHistory(kw)
        } else {
            EhDB.historyLazyList
        }
    }.flow.map { paging ->
        val favCat = Settings.favCat
        paging.map {
            it.apply { favoriteName = favCat.getOrNull(favoriteSlot) }
        }
    }.cachedIn(scope)
}

class DownloadsTabSession {
    val detailGridState = LazyGridState()
    val staggeredGridState = LazyStaggeredGridState()
    val searchBarOffsetY = mutableIntStateOf(0)
    val searchFieldState = TextFieldState()
}

class SettingsTabSession {
    val scrollState = ScrollState(0)
}

data class TabSwitchCommand(
    val seq: Long,
    val targetItem: MainNavItem,
    val animate: Boolean,
)

/**
 * 宿主级（MainActivity 作用域）Tab 缓存池与分页联动 ViewModel：
 * 持有全部 8 个主导航 Tab 的常驻数据流、半永久本地快照种子、滚动偏移状态，以及外置悬浮底栏与根级 Pager 的双向无环同步状态。
 */
class MainTabsViewModel : ViewModel() {
    val homeSession = GalleryTabSession("home", ListUrlBuilder(MODE_NORMAL), viewModelScope)
    val subscriptionSession = GalleryTabSession("subscription", ListUrlBuilder(MODE_SUBSCRIPTION), viewModelScope)
    val whatshotSession = GalleryTabSession("whatshot", ListUrlBuilder(MODE_WHATS_HOT), viewModelScope)
    val toplistSession = GalleryTabSession("toplist", ListUrlBuilder(MODE_TOPLIST, keyword = Settings.recentToplist), viewModelScope)
    val favoritesSession = FavoritesTabSession(viewModelScope)
    val historySession = HistoryTabSession(viewModelScope)
    val downloadsSession = DownloadsTabSession()
    val settingsSession = SettingsTabSession()

    /** 记录当前激活或正在动画前往的主导航项（动态调整导航顺序或进出二级页时锚定原位）。 */
    var currentNavItem by mutableStateOf<MainNavItem?>(null)

    /** 供外置底栏读取的当前选中索引（点击瞬间立即切换为目标索引，杜绝弹簧回跳）。 */
    var selectedTabIndex by mutableIntStateOf(0)

    /** 手指在 HorizontalPager 上拖动时的连续浮点位置（非手势滚动时为 null，交由底栏自身阻尼弹簧驱动）。 */
    private val _tabGesturePosition = mutableStateOf<Float?>(null)
    val tabGesturePositionState: State<Float?> = _tabGesturePosition

    private val commandSeq = AtomicLong(0L)
    private val _switchCommand = MutableStateFlow<TabSwitchCommand?>(null)
    val switchCommand: StateFlow<TabSwitchCommand?> = _switchCommand.asStateFlow()
    var lastHandledCommandSeq: Long = 0L

    init {
        TabDiskSnapshotStore.invalidateEvents
            .onEach {
                homeSession.invalidateOnSiteOrAccountChange(ListUrlBuilder(MODE_NORMAL))
                subscriptionSession.invalidateOnSiteOrAccountChange(ListUrlBuilder(MODE_SUBSCRIPTION))
                whatshotSession.invalidateOnSiteOrAccountChange(ListUrlBuilder(MODE_WHATS_HOT))
                toplistSession.invalidateOnSiteOrAccountChange(ListUrlBuilder(MODE_TOPLIST, keyword = Settings.recentToplist))
                favoritesSession.invalidateOnSiteOrAccountChange()
            }
            .launchIn(viewModelScope)
    }

    fun ensureInitialTab(defaultItem: MainNavItem, visibleTabs: List<MainNavItem>): MainNavItem {
        val existing = currentNavItem
        if (existing != null && existing in visibleTabs) {
            selectedTabIndex = visibleTabs.indexOf(existing)
            return existing
        }
        val resolved = if (defaultItem in visibleTabs) {
            defaultItem
        } else {
            visibleTabs.firstOrNull() ?: MainNavItem.Home
        }
        currentNavItem = resolved
        selectedTabIndex = visibleTabs.indexOf(resolved).coerceAtLeast(0)
        return resolved
    }

    fun updateGesturePosition(position: Float?) {
        if (_tabGesturePosition.value != position) {
            _tabGesturePosition.value = position
        }
    }

    fun onPageSettled(index: Int, visibleTabs: List<MainNavItem>) {
        val item = visibleTabs.getOrNull(index) ?: return
        currentNavItem = item
        selectedTabIndex = index
        _tabGesturePosition.value = null
    }

    /** 外置底栏 / 侧边导航栏点击或拖拽松手切换 Tab（在 MainTabsScreen 内触发相邻滑动或 280ms 静态图层紧贴平推）。 */
    fun requestSwitchToItem(item: MainNavItem, visibleTabs: List<MainNavItem>, animate: Boolean = true) {
        val targetIndex = visibleTabs.indexOf(item)
        if (targetIndex < 0) return
        currentNavItem = item
        selectedTabIndex = targetIndex
        _tabGesturePosition.value = null
        val seq = commandSeq.incrementAndGet()
        _switchCommand.value = TabSwitchCommand(seq = seq, targetItem = item, animate = animate)
    }

    /** 从登录页或二级页面返回前同步设定目标 Tab（无过渡直达）。 */
    fun selectTabImmediate(item: MainNavItem, visibleTabs: List<MainNavItem>) {
        requestSwitchToItem(item, visibleTabs, animate = false)
    }

    fun gallerySessionFor(item: MainNavItem): GalleryTabSession = when (item) {
        MainNavItem.Home -> homeSession
        MainNavItem.Subscription -> subscriptionSession
        MainNavItem.Whatshot -> whatshotSession
        MainNavItem.Toplist -> toplistSession
        else -> error("Unsupported gallery tab: $item")
    }
}

private const val FAV_PAGE_SIZE = 50
