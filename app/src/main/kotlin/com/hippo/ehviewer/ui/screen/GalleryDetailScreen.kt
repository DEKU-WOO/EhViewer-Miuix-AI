package com.hippo.ehviewer.ui.screen

import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.collection.SieveCache
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewModelScope
import com.ehviewer.core.data.model.findBaseInfo
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.toOkioPath
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.ui.component.BlurredBar
import com.ehviewer.core.ui.component.LiquidGlassCircleButtonIconSize
import com.ehviewer.core.ui.component.LiquidGlassCircleButtonSize
import com.ehviewer.core.ui.component.LiquidGlassNavHighlight
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.liquidGlassOverlayMaterial
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.util.launchInVM
import com.ehviewer.core.ui.util.rememberInVM
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launchIO
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.fillInfo
import com.hippo.ehviewer.coil.justDownload
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.ktbuilder.executeIn
import com.hippo.ehviewer.ktbuilder.imageRequest
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.GalleryDetailErrorTip
import com.hippo.ehviewer.ui.main.rememberGalleryDetailTitleState
import com.hippo.ehviewer.ui.navToReader
import com.hippo.ehviewer.ui.openBrowser
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitSelectAction
import com.hippo.ehviewer.ui.tools.awaitSelectTags
import com.hippo.ehviewer.util.awaitActivityResult
import com.hippo.ehviewer.util.bgWork
import com.hippo.ehviewer.util.displayString
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.snackbar
import moe.tarsin.tip
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

typealias VoteTag = suspend GalleryDetail.(String, Int) -> Unit

val detailCache = SieveCache<Long, GalleryDetail>(25)

@Serializable
sealed interface GalleryDetailScreenArgs

@Serializable
data class GalleryInfoArgs(
    val galleryInfo: BaseGalleryInfo,
) : GalleryDetailScreenArgs

@Serializable
data class TokenArgs(
    val gid: Long,
    val token: String,
    val page: Int = 0,
) : GalleryDetailScreenArgs

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.GalleryDetailScreen(args: GalleryDetailScreenArgs, navigator: DestinationsNavigator) = Screen(navigator) {
    val (gid, token) = remember(args) {
        when (args) {
            is GalleryInfoArgs -> with(args.galleryInfo) { gid to token }
            is TokenArgs -> args.gid to args.token
        }
    }
    val galleryDetailUrl = remember(gid, token) { EhUrl.getGalleryDetailUrl(gid, token) }
    contextOf<MainActivity>().ProvideAssistContent(galleryDetailUrl)

    var galleryInfo by rememberInVM {
        val casted = args as? GalleryInfoArgs
        mutableStateOf<GalleryInfo?>(casted?.galleryInfo)
    }

    var getDetailError by rememberSaveable { mutableStateOf("") }
    val scrollBehavior = MiuixScrollBehavior()

    (galleryInfo as? GalleryDetail)?.apply {
        rememberInVM(this) {
            if (Settings.preloadThumbAggressively.value) {
                previewList.forEach {
                    imageRequest(it) { justDownload() }.executeIn(viewModelScope)
                }
            }
        }
    }

    if (galleryInfo !is GalleryDetail && getDetailError.isBlank()) {
        LaunchedEffect(Unit) {
            val galleryDetail = detailCache[gid]
                ?: runSuspendCatching {
                    withIOContext { EhEngine.getGalleryDetail(galleryDetailUrl) }
                }.onSuccess { galleryDetail ->
                    detailCache[galleryDetail.gid] = galleryDetail
                }.onFailure { e ->
                    galleryInfo?.let { EhDB.putHistoryInfo(it) }
                    getDetailError = e.displayString()
                }.getOrNull()
            galleryDetail?.let {
                EhDB.putHistoryInfo(it)
                galleryInfo = it
            }
        }
    }

    val voteTag: VoteTag = { tag, vote ->
        runSuspendCatching {
            EhEngine.voteTag(apiUid, apiKey, gid, token, tag, vote)
        }.onSuccess { result ->
            val new = copy(tagGroups = result).apply { fillInfo() }
            detailCache[gid] = new
            galleryInfo = new
            tip(R.string.tag_vote_successfully)
        }.onFailure { e ->
            tip(e.displayString())
        }
    }

    val signInFirst = stringResource(R.string.sign_in_first)
    val addTag = stringResource(id = R.string.action_add_tag)
    val refresh = stringResource(id = R.string.refresh)
    val clearCache = stringResource(id = R.string.clear_image_cache)
    val cacheCleared = stringResource(R.string.image_cache_cleared)
    val openInBrowser = stringResource(id = R.string.open_in_other_app)
    val exportArchive = stringResource(id = R.string.export_as_archive)
    val exportSuccess = stringResource(id = R.string.export_as_archive_success)
    val exportFailed = stringResource(id = R.string.export_as_archive_failed)
    val titleState = rememberGalleryDetailTitleState(gid, galleryInfo?.let { EhUtils.getSuitableTitle(it) }.orEmpty())
    val title = titleState.title
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                GalleryDetailTopAppBar(
                    title = title,
                    titleClickable = titleState.translationEnabled,
                    onTitleClick = titleState.onTranslateOrToggle,
                    navigationIcon = {
                        DetailTopBarCircleAction(
                            backdrop = backdrop,
                            onClick = { navigator.popBackStack() },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(id = R.string.desktop_a11y_back),
                                modifier = Modifier.size(LiquidGlassCircleButtonIconSize),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    actions = {
                        DetailTopBarCircleAction(
                            backdrop = backdrop,
                            onClick = {
                                launchIO {
                                    awaitSelectAction {
                                        onSelect(addTag) {
                                            val detail = galleryInfo as? GalleryDetail ?: return@onSelect
                                            if (detail.apiUid < 0) {
                                                snackbar(signInFirst)
                                            } else {
                                                val tags = awaitSelectTags()
                                                if (tags.isNotEmpty()) {
                                                    val text = tags.fastJoinToString(",")
                                                    detail.voteTag(text, 1)
                                                }
                                            }
                                        }
                                        onSelect(refresh) {
                                            detailCache.remove(gid)
                                            galleryInfo = galleryInfo?.findBaseInfo()
                                            getDetailError = ""
                                        }
                                        onSelect(clearCache) {
                                            val gd = galleryInfo as? GalleryDetail ?: return@onSelect
                                            awaitConfirmationOrCancel(
                                                confirmText = R.string.clear_all,
                                                title = R.string.clear_image_cache,
                                            ) {
                                                Text(text = stringResource(id = R.string.clear_image_cache_confirm))
                                            }
                                            SpiderDen(gd).clearCache()
                                            snackbar(cacheCleared)
                                        }
                                        onSelect(openInBrowser) {
                                            openBrowser(galleryDetailUrl)
                                        }
                                        onSelect(exportArchive) {
                                            val downloadInfo = DownloadManager.getDownloadInfo(gid)
                                            val canExport = downloadInfo?.state == DownloadInfo.STATE_FINISH
                                            if (!canExport) {
                                                awaitConfirmationOrCancel(
                                                    showCancelButton = false,
                                                    text = { Text(text = stringResource(id = R.string.download_gallery_first)) },
                                                )
                                            } else {
                                                val info = galleryInfo ?: return@onSelect
                                                val uri = awaitActivityResult(
                                                    CreateDocument("application/vnd.comicbook+zip"),
                                                    EhUtils.getSuitableTitle(info) + ".cbz",
                                                )
                                                val dirname = downloadInfo.dirname
                                                if (uri != null && dirname != null) {
                                                    val file = uri.toOkioPath()
                                                    val msg = runCatching {
                                                        bgWork {
                                                            withIOContext {
                                                                SpiderDen(info, dirname).exportAsCbz(file)
                                                            }
                                                        }
                                                        exportSuccess
                                                    }.getOrElse {
                                                        logcat(it)
                                                        file.delete()
                                                        exportFailed
                                                    }
                                                    snackbar(message = msg)
                                                }
                                            }
                                        }
                                    }()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.more_actions),
                                modifier = Modifier.size(LiquidGlassCircleButtonIconSize),
                            )
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blurBackdropSource(backdrop),
        ) {
            val gi = galleryInfo
            if (gi != null) {
                if (args is TokenArgs && args.page != 0) {
                    val from = stringResource(id = R.string.read_from, args.page)
                    val read = stringResource(id = R.string.read)
                    launchInVM {
                        val result = snackbar(from, read, true)
                        if (result == SnackbarResult.ActionPerformed) {
                            navToReader(gi.findBaseInfo(), args.page)
                        }
                    }
                }
                GalleryDetailContent(
                    galleryInfo = gi,
                    contentPadding = contentPadding,
                    getDetailError = getDetailError,
                    onRetry = { getDetailError = "" },
                    voteTag = voteTag,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            } else if (getDetailError.isNotBlank()) {
                GalleryDetailErrorTip(error = getDetailError, onClick = { getDetailError = "" })
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            }
        }
    }
}

private val galleryDetailTitleStyle = TextStyle(
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight.Medium,
)

private val galleryDetailCollapsedTitleStyle = TextStyle(
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.Medium,
)

// 与主页 SearchBarScreen 圆形玻璃按钮严格同口径的外边距与顶部呼吸基准线
private val DetailTopBarHorizontalMargin = 16.dp
private val DetailTopBarCenterGap = 12.dp
private val DetailTopBarBreathingSpace = SearchBarDefaults.InputFieldMinHeight - LiquidGlassCircleButtonSize
private val DetailTopBarBottomBreathing = 8.dp
private val DetailTopBarCollapsedHeight = SearchBarDefaults.InputFieldMinHeight + DetailTopBarBottomBreathing
private val DetailLargeTitleTopGap = 6.dp
private val DetailLargeTitleBottomGap = 6.dp

/** 与主页搜索栏右侧圆钮 100% 同源的独立正圆形液态玻璃高光按钮。 */
@Composable
private fun DetailTopBarCircleAction(
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
        inheritTopBarMaterial = false,
        backdrop = backdrop,
        material = liquidGlassOverlayMaterial(),
        size = LiquidGlassCircleButtonSize,
        stroke = LiquidGlassNavHighlight,
        content = content,
    )
}

/**
 * 详情页顶栏：
 * 1) 顶部常驻水平三段式收缩行（左圆形玻璃钮 + 12.dp + weight(1f) 单行省略标题 + 12.dp + 右圆形玻璃钮），
 *    左右外边距与垂直中心基准线严格对齐主页顶部搜索栏圆钮，且三者互不重叠、绝不遮挡按钮热区；
 * 2) 下方完整多行标题置于独立裁剪层，上滑时在按钮行下方平滑折叠渐隐，不侵入上方按钮热区。
 */
@Composable
private fun GalleryDetailTopAppBar(
    title: String,
    titleClickable: Boolean,
    onTitleClick: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    scrollBehavior: ScrollBehavior,
    color: Color,
) {
    val scrolledOffset = remember(scrollBehavior) { { scrollBehavior.state.heightOffset } }
    val largeTitleAlpha = remember(scrollBehavior) {
        {
            1f - (scrollBehavior.state.collapsedFraction * 2.5f).coerceIn(0f, 1f)
        }
    }
    val updateHeightOffsetLimit = remember(scrollBehavior) {
        { expansionPx: Int ->
            val limit = -expansionPx.toFloat()
            if (scrollBehavior.state.heightOffsetLimit != limit) {
                scrollBehavior.state.heightOffsetLimit = limit
            }
            Unit
        }
    }
    val smallTitleVisible by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.collapsedFraction * 2.5f >= 1f }
    }
    val largeTitleVisible by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.collapsedFraction * 2.5f < 1f }
    }
    val smallTitleAlpha = remember { Animatable(if (smallTitleVisible) 1f else 0f) }
    val smallTitleTranslationY = remember { Animatable(if (smallTitleVisible) 0f else 14f) }

    LaunchedEffect(smallTitleVisible) {
        if (smallTitleVisible) {
            val showSpec = folmeSpring<Float>(damping = 1.0f, response = 0.3f)
            launch { smallTitleAlpha.animateTo(1f, showSpec) }
            launch { smallTitleTranslationY.animateTo(0f, showSpec) }
        } else {
            val hideSpec = folmeSpring<Float>(damping = 1.0f, response = 0.15f)
            launch { smallTitleAlpha.animateTo(0f, hideSpec) }
            launch { smallTitleTranslationY.animateTo(14f, hideSpec) }
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = MiuixTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 50),
    )

    Layout(
        content = {
            // 先声明大标题层（Z 轴在下），并严格在测量布局中限制于收缩行下方区域
            Box(
                Modifier
                    .layoutId("largeTitle")
                    .fillMaxWidth()
                    .onSizeChanged { updateHeightOffsetLimit(it.height) }
                    .clipToBounds()
                    .padding(
                        start = DetailTopBarHorizontalMargin,
                        end = DetailTopBarHorizontalMargin,
                        top = DetailLargeTitleTopGap,
                        bottom = DetailLargeTitleBottomGap,
                    )
                    .graphicsLayer { alpha = largeTitleAlpha() },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset {
                            IntOffset(0, scrolledOffset().fastRoundToInt())
                        },
                ) {
                    Text(
                        text = title,
                        color = animatedTitleColor,
                        style = galleryDetailTitleStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .thenIf(titleClickable && largeTitleVisible) {
                                clickable(onClick = onTitleClick)
                            },
                    )
                }
            }

            // 后声明收缩态三段式顶栏行（Z 轴最上层）：左圆钮 + 12.dp + 中间自适应标题 + 12.dp + 右圆钮
            Row(
                modifier = Modifier
                    .layoutId("collapsedRow")
                    .fillMaxWidth()
                    .padding(
                        start = DetailTopBarHorizontalMargin,
                        end = DetailTopBarHorizontalMargin,
                        top = DetailTopBarBreathingSpace,
                        bottom = DetailTopBarBottomBreathing,
                    )
                    .height(LiquidGlassCircleButtonSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationIcon()
                Spacer(modifier = Modifier.width(DetailTopBarCenterGap))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = smallTitleAlpha.value
                            translationY = smallTitleTranslationY.value
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = title,
                        color = animatedTitleColor,
                        style = galleryDetailCollapsedTitleStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .thenIf(titleClickable && smallTitleVisible) {
                                clickable(onClick = onTitleClick)
                            },
                    )
                }
                Spacer(modifier = Modifier.width(DetailTopBarCenterGap))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        },
        modifier = Modifier
            .background(color)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { /* Consume click */ }
            },
    ) { measurables, constraints ->
        val collapsedHeightPx = DetailTopBarCollapsedHeight.roundToPx()
        val collapsedRowPlaceable = measurables
            .fastFirst { it.layoutId == "collapsedRow" }
            .measure(
                constraints.copy(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = collapsedHeightPx,
                    maxHeight = collapsedHeightPx,
                ),
            )
        val largeTitlePlaceable = measurables
            .fastFirst { it.layoutId == "largeTitle" }
            .measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                ),
            )

        val expansion = largeTitlePlaceable.height.coerceAtLeast(0)

        val barHeight = if (expansion > 0) {
            val offset = scrolledOffset()
            val collapseFraction = if (offset.isNaN()) {
                0f
            } else {
                (abs(offset) / expansion.toFloat()).coerceIn(0f, 1f)
            }
            lerp(
                start = collapsedHeightPx,
                stop = collapsedHeightPx + expansion,
                fraction = 1f - collapseFraction,
            )
        } else {
            collapsedHeightPx
        }

        // 当完全折叠后不再放置大标题层，彻底杜绝大标题越界覆盖上方左右圆钮触摸区
        val visibleExpansionHeight = (barHeight - collapsedHeightPx).coerceAtLeast(0)
        val clippedLargeTitlePlaceable = if (visibleExpansionHeight > 0) {
            largeTitlePlaceable
        } else {
            null
        }

        layout(constraints.maxWidth, barHeight) {
            if (clippedLargeTitlePlaceable != null) {
                clippedLargeTitlePlaceable.placeRelative(x = 0, y = collapsedHeightPx)
            }
            collapsedRowPlaceable.placeRelative(x = 0, y = 0)
        }
    }
}
