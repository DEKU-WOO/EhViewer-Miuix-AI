package com.hippo.ehviewer.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ehviewer.core.ui.util.LocalIsPageInViewport
import com.ehviewer.core.ui.util.LocalIsPageVisible
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.BottomBarScrollState
import com.hippo.ehviewer.ui.MainNavItem
import com.hippo.ehviewer.ui.MainNavItems
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.SearchExpandController
import com.hippo.ehviewer.ui.SelectionModeController
import com.hippo.ehviewer.ui.main.MainTabsViewModel
import com.hippo.ehviewer.ui.settings.SettingsTabContent
import com.hippo.ehviewer.util.findActivity
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

private data class CrossTransitionState(
    val fromPage: Int,
    val toPage: Int,
    val direction: Int,
)

private val CrossPageEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val TAB_TRANSITION_DURATION_MS = 280

/**
 * 根级主导航平滑分页容器：
 * - 基于 [HorizontalPager]（`beyondViewportPageCount = 1`）承载用户配置的所有可见主 Tab，支持 1:1 跟手滑动切页；
 * - 跨多页点击底栏或拖拽直达（`|to - from| >= 2`）时切换为 [CrossTransitionState] 上层双槽紧贴平推，不经过中间页；
 * - 跨页平推激活期间，底层 `HorizontalPager` 的起止槽位严格退让为空 `Box`，确保 `movableContentOf` 任意单帧唯一挂载。
 */
@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.MainTabsScreen(navigator: DestinationsNavigator) {
    val activity = with(LocalContext.current) { remember { findActivity() } }
    val tabsViewModel: MainTabsViewModel = viewModel(activity)

    val rawNavConfig by Settings.bottomNavItems.collectAsState()
    val visibleTabs = remember(rawNavConfig) {
        MainNavItems.visible(MainNavItems.decode(rawNavConfig))
    }
    val latestVisibleTabs by rememberUpdatedState(visibleTabs)

    val initialPage = remember {
        val current = tabsViewModel.currentNavItem
        val idx = if (current != null) visibleTabs.indexOf(current) else 0
        idx.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { visibleTabs.size },
    )

    val saveableStateHolder = rememberSaveableStateHolder()
    val tabSlots = remember(navigator, tabsViewModel, saveableStateHolder) {
        MainNavItem.entries.associateWith { item ->
            movableContentOf {
                saveableStateHolder.SaveableStateProvider(key = item.name) {
                    Screen(navigator) {
                        when (item) {
                            MainNavItem.Home,
                            MainNavItem.Subscription,
                            MainNavItem.Whatshot,
                            MainNavItem.Toplist,
                            -> GalleryListTabContent(
                                session = tabsViewModel.gallerySessionFor(item),
                                ownerKey = "tab_${item.name}",
                            )
                            MainNavItem.Favorites -> FavoritesTabContent(
                                session = tabsViewModel.favoritesSession,
                                ownerKey = "tab_Favorites",
                            )
                            MainNavItem.History -> HistoryTabContent(
                                session = tabsViewModel.historySession,
                                ownerKey = "tab_History",
                            )
                            MainNavItem.Downloads -> DownloadsTabContent(
                                session = tabsViewModel.downloadsSession,
                                ownerKey = "tab_Downloads",
                            )
                            MainNavItem.Settings -> SettingsTabContent(
                                navigator = navigator,
                                session = tabsViewModel.settingsSession,
                            )
                        }
                    }
                }
            }
        }
    }

    var crossTransition by remember { mutableStateOf<CrossTransitionState?>(null) }
    var isProgrammaticSwitch by remember { mutableStateOf(false) }
    val crossProgress = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        onDispose {
            crossTransition = null
            isProgrammaticSwitch = false
            tabsViewModel.updateGesturePosition(null)
        }
    }

    // 动态调整导航顺序或显隐返回时，锚定原激活项位置不乱跳
    LaunchedEffect(visibleTabs) {
        if (visibleTabs.isEmpty()) return@LaunchedEffect
        val activeItem = tabsViewModel.currentNavItem
        val targetIdx = if (activeItem != null && activeItem in visibleTabs) {
            visibleTabs.indexOf(activeItem)
        } else {
            pagerState.currentPage.coerceIn(0, visibleTabs.lastIndex)
        }
        if (pagerState.currentPage != targetIdx) {
            crossTransition = null
            pagerState.scrollToPage(targetIdx)
        }
        tabsViewModel.onPageSettled(targetIdx, visibleTabs)
    }

    // 响应外置悬浮底栏 / 侧栏的切页指令：相邻页平滑滚动，跨多页（>=2）走 280ms 紧贴直达平推
    LaunchedEffect(tabsViewModel) {
        tabsViewModel.switchCommands.collectLatest { command ->
            val currentTabs = latestVisibleTabs
            val targetPage = currentTabs.indexOf(command.targetItem)
            if (targetPage < 0) return@collectLatest

            val interruptedCross = crossTransition
            if (interruptedCross != null) {
                val snapped = interruptedCross.toPage.coerceIn(0, (currentTabs.size - 1).coerceAtLeast(0))
                pagerState.scrollToPage(snapped)
                crossTransition = null
            }

            val fromPage = pagerState.currentPage.coerceIn(0, (currentTabs.size - 1).coerceAtLeast(0))
            BottomBarScrollState.reset()

            if (targetPage == fromPage) {
                isProgrammaticSwitch = false
                tabsViewModel.onPageSettled(targetPage, currentTabs)
                return@collectLatest
            }

            isProgrammaticSwitch = true
            try {
                if (!command.animate) {
                    crossTransition = null
                    pagerState.scrollToPage(targetPage)
                    tabsViewModel.onPageSettled(targetPage, currentTabs)
                } else if (abs(targetPage - fromPage) == 1) {
                    crossTransition = null
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(
                            durationMillis = TAB_TRANSITION_DURATION_MS,
                            easing = CrossPageEasing,
                        ),
                    )
                    tabsViewModel.onPageSettled(targetPage, currentTabs)
                } else {
                    val dir = if (targetPage > fromPage) 1 else -1
                    crossProgress.snapTo(0f)
                    crossTransition = CrossTransitionState(
                        fromPage = fromPage,
                        toPage = targetPage,
                        direction = dir,
                    )
                    crossProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = TAB_TRANSITION_DURATION_MS,
                            easing = CrossPageEasing,
                        ),
                    )
                    pagerState.scrollToPage(targetPage)
                    crossTransition = null
                    tabsViewModel.onPageSettled(targetPage, currentTabs)
                }
            } finally {
                isProgrammaticSwitch = false
            }
        }
    }

    // 手指直接左右滑动 Pager 时，实时驱动外置底栏液态玻璃透镜 1:1 跟手位移
    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(
                pagerState.isScrollInProgress,
                pagerState.currentPage,
                pagerState.currentPageOffsetFraction,
            )
        }.collect { (scrolling, currentPage, offsetFraction) ->
            val currentTabs = latestVisibleTabs
            if (currentTabs.isEmpty()) return@collect
            if (!isProgrammaticSwitch && crossTransition == null) {
                if (scrolling) {
                    val continuous = (currentPage + offsetFraction)
                        .coerceIn(0f, (currentTabs.size - 1).coerceAtLeast(0).toFloat())
                    tabsViewModel.updateGesturePosition(continuous)
                } else {
                    val settled = pagerState.settledPage.coerceIn(0, (currentTabs.size - 1).coerceAtLeast(0))
                    if (tabsViewModel.selectedTabIndex != settled || tabsViewModel.tabGesturePositionState.value != null) {
                        BottomBarScrollState.reset()
                    }
                    tabsViewModel.onPageSettled(settled, currentTabs)
                }
            }
        }
    }

    val activeCross = crossTransition
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidthPx = constraints.maxWidth.toFloat()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !SelectionModeController.active &&
                !SearchExpandController.expanded &&
                activeCross == null,
            key = { page -> visibleTabs[page].name },
        ) { page ->
            if (activeCross != null && (page == activeCross.fromPage || page == activeCross.toPage)) {
                // 严格避免同帧双重挂载：跨页平推期间起止页完全让出 movableContentOf 所有权
                Box(modifier = Modifier.fillMaxSize())
            } else {
                val item = visibleTabs[page]
                val continuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val isSettledVisible = activeCross == null && page == pagerState.settledPage
                val isInViewport = activeCross == null && abs(continuousPage - page) < 0.995f
                CompositionLocalProvider(
                    LocalIsPageVisible provides isSettledVisible,
                    LocalIsPageInViewport provides isInViewport,
                ) {
                    tabSlots.getValue(item)()
                }
            }
        }

        if (activeCross != null && activeCross.fromPage != activeCross.toPage) {
            val fromItem = visibleTabs.getOrNull(activeCross.fromPage)
            val toItem = visibleTabs.getOrNull(activeCross.toPage)
            if (fromItem != null && toItem != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = -activeCross.direction * crossProgress.value * containerWidthPx
                            },
                    ) {
                        CompositionLocalProvider(
                            LocalIsPageVisible provides false,
                            LocalIsPageInViewport provides true,
                        ) {
                            tabSlots.getValue(fromItem)()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = activeCross.direction * (1f - crossProgress.value) * containerWidthPx
                            },
                    ) {
                        CompositionLocalProvider(
                            LocalIsPageVisible provides false,
                            LocalIsPageInViewport provides true,
                        ) {
                            tabSlots.getValue(toItem)()
                        }
                    }
                }
            }
        }
    }
}
