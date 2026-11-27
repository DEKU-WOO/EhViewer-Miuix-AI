package com.hippo.ehviewer.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ehviewer.core.ui.util.LocalIsPageInViewport
import com.ehviewer.core.ui.util.LocalIsPageVisible
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.BottomBarScrollState
import com.hippo.ehviewer.ui.MainActivity
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
import kotlinx.coroutines.flow.filterNotNull
import moe.tarsin.coroutines.runSuspendCatching

private class OutgoingSlideSnapshot(
    val bitmap: ImageBitmap,
    val direction: Int,
)

private val CrossPageEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val TAB_TRANSITION_DURATION_MS = 280

/**
 * 根级主导航平滑分页容器：
 * - 基于 [HorizontalPager]（`beyondViewportPageCount = 1`）原地承载所有可见主 Tab，100% 不改变 Compose 节点树层级；
 * - 相邻切页（`|to - from| == 1`）走原生平滑滚动；
 * - 跨多页切页（`|to - from| >= 2`）采用 `rememberGraphicsLayer()` 静态截屏 + `scrollToPage` 瞬时落位 + 280ms 双图层紧贴平推，
 *   彻底杜绝 `movableContentOf` 跨 `SubcomposeLayout` 挪动导致的崩溃，截屏异常或未就绪时自动安全降级直达。
 */
@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.MainTabsScreen(navigator: DestinationsNavigator) {
    val activity: MainActivity = with(LocalContext.current) { remember { findActivity() } }
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

    val stageGraphicsLayer = rememberGraphicsLayer()
    var stageWidthPx by remember { mutableFloatStateOf(0f) }
    var outgoingSnapshot by remember { mutableStateOf<OutgoingSlideSnapshot?>(null) }
    var isProgrammaticSwitch by remember { mutableStateOf(false) }
    val crossProgress = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        onDispose {
            outgoingSnapshot = null
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
            outgoingSnapshot = null
            runSuspendCatching { pagerState.scrollToPage(targetIdx) }
        }
        tabsViewModel.onPageSettled(targetIdx, visibleTabs)
    }

    // 响应外置悬浮底栏 / 侧栏的切页指令：相邻页平滑滚动，跨多页（>=2）走静态图层 + scrollToPage 280ms 紧贴平推
    LaunchedEffect(tabsViewModel) {
        tabsViewModel.switchCommand.filterNotNull().collectLatest { command ->
            if (command.seq <= tabsViewModel.lastHandledCommandSeq) return@collectLatest
            tabsViewModel.lastHandledCommandSeq = command.seq

            val currentTabs = latestVisibleTabs
            val totalPages = pagerState.pageCount
            val targetPage = currentTabs.indexOf(command.targetItem)
            if (totalPages <= 0 || targetPage !in 0 until totalPages) return@collectLatest

            val fromPage = pagerState.currentPage.coerceIn(0, totalPages - 1)
            val residualOffset = abs(pagerState.currentPageOffsetFraction)
            BottomBarScrollState.reset()

            if (targetPage == fromPage && residualOffset <= 0.001f && outgoingSnapshot == null) {
                isProgrammaticSwitch = false
                tabsViewModel.onPageSettled(targetPage, currentTabs)
                return@collectLatest
            }

            isProgrammaticSwitch = true
            try {
                if (!command.animate) {
                    outgoingSnapshot = null
                    runSuspendCatching { pagerState.scrollToPage(targetPage) }
                    tabsViewModel.onPageSettled(targetPage, currentTabs)
                } else if (abs(targetPage - fromPage) <= 1 && outgoingSnapshot == null) {
                    runSuspendCatching {
                        pagerState.animateScrollToPage(
                            page = targetPage,
                            animationSpec = tween(
                                durationMillis = TAB_TRANSITION_DURATION_MS,
                                easing = CrossPageEasing,
                            ),
                        )
                    }
                    tabsViewModel.onPageSettled(targetPage, currentTabs)
                } else {
                    val dir = if (targetPage >= fromPage) 1 else -1
                    val captured = if (
                        stageWidthPx > 1f &&
                        stageGraphicsLayer.size.width > 0 &&
                        stageGraphicsLayer.size.height > 0
                    ) {
                        runSuspendCatching { stageGraphicsLayer.toImageBitmap() }
                            .getOrNull()
                            ?.takeIf { it.width > 0 && it.height > 0 }
                    } else {
                        null
                    }

                    if (captured == null) {
                        outgoingSnapshot = null
                        runSuspendCatching { pagerState.scrollToPage(targetPage) }
                        tabsViewModel.onPageSettled(targetPage, currentTabs)
                    } else {
                        crossProgress.snapTo(0f)
                        outgoingSnapshot = OutgoingSlideSnapshot(
                            bitmap = captured,
                            direction = dir,
                        )
                        runSuspendCatching { pagerState.scrollToPage(targetPage) }
                        crossProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = TAB_TRANSITION_DURATION_MS,
                                easing = CrossPageEasing,
                            ),
                        )
                        outgoingSnapshot = null
                        tabsViewModel.onPageSettled(targetPage, currentTabs)
                    }
                }
            } finally {
                outgoingSnapshot = null
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
            if (!isProgrammaticSwitch && outgoingSnapshot == null) {
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

    val activeSlide = outgoingSnapshot
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { stageWidthPx = it.width.toFloat() }
            .drawWithContent {
                stageGraphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(stageGraphicsLayer)
            },
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (activeSlide != null) {
                        activeSlide.direction * (1f - crossProgress.value) * containerWidthPx
                    } else {
                        0f
                    }
                },
            beyondViewportPageCount = 1,
            userScrollEnabled = !SelectionModeController.active &&
                !SearchExpandController.expanded &&
                activeSlide == null,
            key = { page -> visibleTabs.getOrNull(page)?.name ?: "page_$page" },
        ) { page ->
            val item = visibleTabs.getOrNull(page) ?: return@HorizontalPager
            val continuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val isSettledVisible = activeSlide == null && page == pagerState.settledPage
            val isInViewport = activeSlide != null || abs(continuousPage - page) < 0.995f
            key(item) {
                CompositionLocalProvider(
                    LocalIsPageVisible provides isSettledVisible,
                    LocalIsPageInViewport provides isInViewport,
                ) {
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

        if (activeSlide != null) {
            Image(
                bitmap = activeSlide.bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -activeSlide.direction * crossProgress.value * containerWidthPx
                    },
            )
        }
    }
}
