package com.hippo.ehviewer.ui.screen

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ehviewer.core.database.dao.SearchDao
import com.ehviewer.core.files.toOkioPath
import com.ehviewer.core.database.model.Search
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.TagNamespace
import com.ehviewer.core.ui.component.LiquidGlassCircleButtonIconSize
import com.ehviewer.core.ui.component.LiquidGlassCircleButtonSize
import com.ehviewer.core.ui.component.LiquidGlassNavHighlight
import com.ehviewer.core.ui.component.LiquidGlassSurface
import com.ehviewer.core.ui.component.LocalBackdrop
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.liquidGlassOverlayMaterial
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.MenuBook
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ehviewer.core.ui.util.LocalBottomBarContentPadding
import com.ehviewer.core.ui.util.LocalIsPageInViewport
import com.ehviewer.core.ui.util.LocalIsPageVisible
import com.hippo.ehviewer.ui.SearchExpandController
import com.ehviewer.core.ui.util.ifNotNullThen
import com.ehviewer.core.ui.util.ifTrueThen
import com.ehviewer.core.ui.util.thenIf
import com.hippo.ehviewer.EhApplication.Companion.searchDatabase
import com.hippo.ehviewer.Settings
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_IMAGE_SEARCH
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.theme.scrim
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.rememberCompositionActiveState
import com.hippo.ehviewer.util.pickVisualMedia
import com.hippo.ehviewer.util.sha1
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import moe.tarsin.navigate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.ehviewer.core.ui.component.LiquidGlassDialog

fun interface SuggestionProvider {
    suspend fun providerSuggestions(text: String): List<Suggestion>
}

abstract class Suggestion {
    abstract val keyword: String
    open val hint: String? = null
    abstract fun onClick()
    open val canDelete: Boolean = false
    open val canOpenDirectly: Boolean = false
}

suspend fun SearchDao.suggestions(prefix: String, limit: Int) = (if (prefix.isBlank()) list(limit) else rawSuggestions(prefix, limit))

@Composable
context(_: DialogState, _: DestinationsNavigator)
fun SearchBarScreen(
    onApplySearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String?,
    searchFieldHint: String,
    searchFieldState: TextFieldState = rememberTextFieldState(),
    suggestionProvider: SuggestionProvider? = null,
    localSearch: Boolean = true,
    searchBarOffsetY: () -> Int = { 0 },
    trailingIcon: @Composable () -> Unit = {},
    subHeader: @Composable (() -> Unit)? = null,
    filter: @Composable (() -> Unit)? = null,
    backdrop: LayerBackdrop? = null,
    /** 多选模式接管顶栏时隐藏搜索栏内容，只保留统一磨砂渐变。 */
    hideSearchBar: Boolean = false,
    onPersistFilter: () -> Unit = {},
    ownerKey: String = "default",
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var mSuggestionList by remember { mutableStateOf(emptyList<Suggestion>()) }
    val mSearchDatabase = searchDatabase.searchDao()
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val context = LocalContext.current
    val animateItems by Settings.animateItems.collectAsState()

    class TagSuggestion(
        override val hint: String?,
        override val keyword: String,
    ) : Suggestion() {
        override fun onClick() {
            val query = searchFieldState.text.toString()
            val (index, keyword) = if (localSearch) {
                query.lastIndexOf(' ') to
                    "${keyword.substringAfter(':')} "
            } else {
                query.lastIndexOfAny(TagTerminators) to
                    if (keyword.endsWith(':')) keyword else "${wrapTagKeyword(keyword)} "
            }
            val keywords = if (index == -1) {
                keyword
            } else {
                "${query.substring(0, index + 1).trimEnd()} $keyword"
            }
            searchFieldState.setTextAndPlaceCursorAtEnd(keywords)
        }
    }

    class KeywordSuggestion(
        override val keyword: String,
    ) : Suggestion() {
        override val canDelete = true
        override fun onClick() {
            searchFieldState.setTextAndPlaceCursorAtEnd(keyword)
        }
    }

    fun mergedSuggestionFlow(): Flow<Suggestion> = with(context) {
        flow {
            val query = searchFieldState.text.toString()
            suggestionProvider?.run { providerSuggestions(query).forEach { emit(it) } }
            mSearchDatabase.suggestions(query, 128).forEach { emit(KeywordSuggestion(it)) }
            val index = if (localSearch) query.lastIndexOf(' ') else query.lastIndexOfAny(TagTerminators)
            val keyword = query.substring(index + 1).trimStart()
            if (keyword.isNotEmpty()) {
                EhTagDatabase.suggestion(keyword, Settings.showTagTranslations.value).take(50)
                    .forEach { emit(TagSuggestion(it.hint, it.tag)) }
            }
        }
    }

    suspend fun updateSuggestions() {
        mSuggestionList = mergedSuggestionFlow().toList()
    }

    if (expanded) {
        LaunchedEffect(Unit) {
            snapshotFlow { searchFieldState.text }.collectLatest {
                updateSuggestions()
            }
        }
    }

    fun hideSearchView() {
        onExpandedChange(false)
    }

    fun onApplySearch() {
        // May have invalid whitespaces if pasted from clipboard, replace them with spaces
        val query = searchFieldState.text.trim().replace(WhitespaceRegex, " ")
        if (query.isNotEmpty()) {
            scope.launch {
                mSearchDatabase.deleteQuery(query)
                val search = Search(System.currentTimeMillis(), query)
                mSearchDatabase.insert(search)
            }
        }
        onApplySearch(query)
    }

    fun deleteKeyword(keyword: String) {
        scope.launch {
            awaitConfirmationOrCancel(confirmText = R.string.delete) {
                Text(text = stringResource(id = R.string.delete_search_history, keyword))
            }
            mSearchDatabase.deleteQuery(keyword)
            updateSuggestions()
        }
    }

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val isPageVisible = LocalIsPageVisible.current
    val isPageInViewport = LocalIsPageInViewport.current

    LaunchedEffect(ownerKey, isPageVisible, expanded) {
        SearchExpandController.update(ownerKey, isPageVisible && expanded)
    }
    DisposableEffect(ownerKey) {
        onDispose { SearchExpandController.clearOwner(ownerKey) }
    }

    var prevFilterSheet by remember { mutableStateOf(showFilterSheet) }
    LaunchedEffect(showFilterSheet) {
        if (prevFilterSheet && !showFilterSheet && filter != null) {
            onPersistFilter()
        }
        prevFilterSheet = showFilterSheet
    }

    var prevExpanded by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        if (prevExpanded && !expanded && filter != null) {
            onPersistFilter()
        }
        prevExpanded = expanded
    }

    if (filter != null) {
        LifecycleResumeEffect(showFilterSheet, expanded) {
            onPauseOrDispose {
                if (showFilterSheet || expanded) {
                    onPersistFilter()
                }
            }
        }
    }
    var showImageSearchPopup by rememberSaveable { mutableStateOf(false) }
    var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val imageSearchAnchor = rememberGlassPopupAnchor()
    val uiScope = rememberCoroutineScope()

    fun pickImageForSearch() {
        uiScope.launch {
            with(context) {
                val picked = pickVisualMedia(ImageOnly)
                if (picked != null) {
                    selectedImageUri = picked
                }
            }
        }
    }

    fun startImageSearch() {
        val uri = selectedImageUri ?: return
        uiScope.launch {
            val hash = withIOContext { uri.toOkioPath().sha1() }
            showImageSearchPopup = false
            hideSearchView()
            navigate(
                ListUrlBuilder(
                    mode = MODE_IMAGE_SEARCH,
                    hash = hash,
                ).asDst(),
            )
        }
    }
    val activeBackdrop = backdrop ?: rememberBlurBackdrop()

    CompositionLocalProvider(LocalBackdrop provides activeBackdrop) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val subHeaderHeight = if (subHeader != null) 40.dp else 0.dp
            val compactBarHeight = 48.dp
            val fullBarHeight = SearchBarDefaults.InputFieldMinHeight + 16.dp + subHeaderHeight
            // 折叠阈值必须与顶栏完整高度同口径，否则带分类条时折叠进度永远到不了终态
            val collapseThresholdPx = with(density) { fullBarHeight.toPx() }
            val collapseProgress = if (expanded) 0f else (-searchBarOffsetY().toFloat() / collapseThresholdPx).coerceIn(0f, 1f)

            Scaffold(
                topBar = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 只对状态栏条带做渐进模糊：搜索胶囊自身已是玻璃材质，若整条顶栏再模糊一次，
                        // 胶囊与其周边会出现双层模糊和模糊强度断层。
                        if (!expanded) {
                            // matchParentSize 让渐变只做绘制、不参与顶栏高度测量；
                            // requiredHeight 允许渐变层向下溢出绘制，避免把列表内容整体顶下去。
                            Box(modifier = Modifier.matchParentSize()) {
                                // 总高度保持收紧：状态栏 + 搜索栏 + 8dp 缓冲带
                                val scrimTotalDp = statusBarPadding + fullBarHeight + TopScrimExtraHeight
                                if (isPageInViewport && activeBackdrop != null && isRuntimeShaderSupported()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .requiredHeight(scrimTotalDp)
                                            .progressiveTextureBlur(
                                                backdrop = activeBackdrop,
                                                shape = RectangleShape,
                                                // 模糊权重从顶部满强度连续衰减到底部 0，恢复自然渐隐
                                                gradient = ProgressiveBlur.Top,
                                                blurRadius = 20f,
                                                colors = BlurDefaults.blurColors(
                                                    blendColors = listOf(
                                                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)),
                                                    ),
                                                ),
                                            )
                                            // 有限高度内叠加垂直渐变着色：顶部遮挡状态栏，向下平滑归零
                                            .background(
                                                Brush.verticalGradient(
                                                    colorStops = arrayOf(
                                                        0f to MiuixTheme.colorScheme.surface.copy(alpha = 0.92f),
                                                        0.5f to MiuixTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                        0.85f to MiuixTheme.colorScheme.surface.copy(alpha = 0.12f),
                                                        1f to Color.Transparent,
                                                    ),
                                                ),
                                            ),
                                    )
                                } else {
                                    val scrim = MiuixTheme.colorScheme.background.scrim()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .requiredHeight(scrimTotalDp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colorStops = arrayOf(
                                                        0f to scrim,
                                                        0.5f to scrim.copy(alpha = scrim.alpha * 0.5f),
                                                        1f to Color.Transparent,
                                                    ),
                                                ),
                                            ),
                                    )
                                }
                            }
                        }
                        Column {
                            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                            // Placeholder, fill immutable SearchBar padding
                            Spacer(modifier = Modifier.height(fullBarHeight))
                        }
                    }
                },
                floatingActionButton = floatingActionButton,
                content = { paddingValues ->
                    val bottomBarPadding = LocalBottomBarContentPadding.current
                    val layoutDirection = LocalLayoutDirection.current
                    val effectivePaddingValues = remember(paddingValues, bottomBarPadding, layoutDirection) {
                        PaddingValues(
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            top = paddingValues.calculateTopPadding(),
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            bottom = maxOf(paddingValues.calculateBottomPadding(), bottomBarPadding),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blurBackdropSource(activeBackdrop),
                    ) {
                        content(effectivePaddingValues)
                    }
                },
            )
            // https://issuetracker.google.com/337191298
            // Workaround for can't exit SearchBar due to refocus in non-touch mode
            Box(Modifier.size(1.dp).focusable())
            val activeState = rememberCompositionActiveState()
            val query = searchFieldState.text.toString()
            val contentActive by activeState.state
            val placeholder = title.takeUnless { expanded || contentActive } ?: searchFieldHint
            val searchBg = MiuixTheme.colorScheme.background
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(searchBg),
                )
            }
            if (!hideSearchBar) {
            CompositionLocalProvider(LocalBackdrop provides null) {
                SearchBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .thenIf(!expanded) {
                            offset { IntOffset(0, searchBarOffsetY()) }
                                .graphicsLayer {
                                    // alpha 在 collapseProgress 2/3 时已归零，无需再用位移瞬移隐藏
                                    alpha = (1f - collapseProgress * 1.5f).coerceIn(0f, 1f)
                                }
                        }
                        .thenIf(expanded) { fillMaxSize() },
                    // 左右外侧留白对称 16.dp：24.dp 图标中心落在胶囊左右半圆圆心，
                    // 右侧多图标与左侧放大镜的间距、触控区域完全不变，仅中间自适应输入区吸收变化
                    insideMargin = DpSize(SearchBarHorizontalPadding, 0.dp),
                    inputField = {
                        // 折叠态与展开态共用同一套高度与顶部呼吸留白，确保两者下边缘垂直基准线完全一致
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalSearchBarBackdrop provides activeBackdrop) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = SearchBarMaxWidth)
                                        // 下边缘与原高度完全一致，缩小省出的高度全部留给顶部上方呼吸空间
                                        .padding(top = SearchBarTopBreathingSpace),
                                    horizontalArrangement = Arrangement.spacedBy(SearchBarActionGap),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!expanded) {
                                        LiquidGlassSurface(
                                            shape = CircleShape,
                                            elevation = 4.dp,
                                            refractionHeight = 14.dp,
                                            refractionAmount = 14.dp,
                                            highlight = LiquidGlassNavHighlight,
                                            containerColor = MiuixTheme.colorScheme.surfaceContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(SearchBarHeight),
                                            backdrop = activeBackdrop,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable(role = Role.Button) { onExpandedChange(true) }
                                                    .padding(
                                                        start = SearchBarHalfHeight,
                                                        end = SearchBarHalfHeight,
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Search,
                                                    contentDescription = null,
                                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.size(SearchBarIconSize),
                                                )
                                                Spacer(modifier = Modifier.width(SearchBarTextGap))
                                                Text(
                                                    text = query.ifEmpty { placeholder },
                                                    style = MiuixTheme.textStyles.body2.copy(fontSize = SearchBarTextSize),
                                                    color = if (query.isEmpty()) {
                                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                    } else {
                                                        MiuixTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                        if (filter != null) {
                                            SearchBarCircleAction(onClick = { showFilterSheet = true }) {
                                                Icon(
                                                    imageVector = MiuixIcons.More,
                                                    contentDescription = stringResource(R.string.more_actions),
                                                    modifier = Modifier.size(SearchBarIconSize),
                                                )
                                            }
                                        }
                                        trailingIcon()
                                    } else {
                                        val focusRequester = remember { FocusRequester() }
                                        LaunchedEffect(Unit) {
                                            focusRequester.requestFocus()
                                        }
                                        // 1) 左侧：独立正圆形毛玻璃高光返回按钮
                                        SearchBarCircleAction(onClick = { hideSearchView() }) {
                                            Icon(
                                                imageVector = MiuixIcons.Back,
                                                contentDescription = null,
                                                modifier = Modifier.size(SearchBarIconSize),
                                            )
                                        }
                                        // 2) 中间：独立全圆角胶囊搜索输入框（与主界面搜索栏同高同基准）
                                        LiquidGlassSurface(
                                            shape = CircleShape,
                                            elevation = 4.dp,
                                            refractionHeight = 14.dp,
                                            refractionAmount = 14.dp,
                                            highlight = LiquidGlassNavHighlight,
                                            containerColor = MiuixTheme.colorScheme.surfaceContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(SearchBarHeight),
                                            backdrop = activeBackdrop,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(
                                                        start = SearchBarHalfHeight,
                                                        end = SearchBarHalfHeight,
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Search,
                                                    contentDescription = null,
                                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.size(SearchBarIconSize),
                                                )
                                                Spacer(modifier = Modifier.width(SearchBarTextGap))
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.CenterStart,
                                                ) {
                                                    if (query.isEmpty()) {
                                                        Text(
                                                            text = placeholder,
                                                            style = MiuixTheme.textStyles.body2.copy(fontSize = SearchBarTextSize),
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = query,
                                                        onValueChange = { searchFieldState.setTextAndPlaceCursorAtEnd(it) },
                                                        singleLine = true,
                                                        textStyle = MiuixTheme.textStyles.body2.copy(
                                                            fontSize = SearchBarTextSize,
                                                            color = MiuixTheme.colorScheme.onSurface,
                                                        ),
                                                        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                        keyboardActions = KeyboardActions(
                                                            onSearch = {
                                                                hideSearchView()
                                                                onApplySearch()
                                                            },
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .focusRequester(focusRequester),
                                                    )
                                                }
                                                if (query.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(SearchBarTextGap))
                                                    Icon(
                                                        imageVector = MiuixIcons.Close,
                                                        contentDescription = null,
                                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                        modifier = Modifier
                                                            .size(SearchBarIconSize)
                                                            .clickable(role = Role.Button) {
                                                                searchFieldState.clearText()
                                                            },
                                                    )
                                                }
                                            }
                                        }
                                        // 3) 右侧：独立正圆形毛玻璃高光「以图搜图」按钮（锚点弹窗入口）
                                        SearchBarCircleAction(
                                            onClick = { showImageSearchPopup = true },
                                            modifier = Modifier.glassPopupAnchor(
                                                imageSearchAnchor,
                                                cornerRadius = SearchBarActionCornerRadius,
                                                floating = true,
                                            ),
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(SearchBarIconSize),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    outsideEndAction = {},
                ) {
                    activeState.Anchor()
                    // 列表页首张画廊卡片上边缘 = statusBars + SearchBarOriginalHeight + 16.dp + gallery_list_margin_v；
                    // 展开态 LazyColumn 起点位于 statusBars + SearchBarOriginalHeight，SearchFilterPanel 自带 8.dp 顶部内边距，
                    // 补足差值使首行标签（「全部」「DOUJINSHI」「MANGA」）上边缘与列表首张卡片上边缘严格齐平。
                    val listMarginV = dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_list_margin_v)
                    val safeBottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
                    val expandedContentTopPadding = (16.dp + listMarginV - 8.dp).coerceAtLeast(0.dp)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = expandedContentTopPadding, bottom = safeBottom),
                    ) {
                        if (filter != null) {
                            item(key = "search_filter_header") {
                                filter()
                            }
                        }
                        items(mSuggestionList, key = { it.keyword.hashCode() * 31 + it.canDelete.hashCode() }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .clip(SquircleShape(12.dp))
                                    .thenIf(animateItems) { animateItem() },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(SquircleShape(12.dp))
                                        .clickable(role = Role.Button) { it.onClick() }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (it.canOpenDirectly) {
                                        Icon(
                                            imageVector = EhIcons.Default.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 12.dp).size(20.dp),
                                            tint = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = it.keyword,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            style = MiuixTheme.textStyles.body1,
                                        )
                                        if (it.hint != null) {
                                            Text(
                                                text = it.hint!!,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                style = MiuixTheme.textStyles.body2,
                                            )
                                        }
                                    }
                                }
                                if (it.canDelete) {
                                    IconButton(
                                        onClick = { deleteKeyword(it.keyword) },
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Close,
                                            contentDescription = stringResource(id = R.string.delete),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            if (!expanded && !hideSearchBar && subHeader != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        // 横屏时缺口/导航栏 insets 在左右，分类条必须与搜索栏用同一组水平 insets
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .offset {
                            val inputFieldOffset = with(density) { (SearchBarDefaults.InputFieldMinHeight + 16.dp).roundToPx() }
                            IntOffset(0, searchBarOffsetY() + inputFieldOffset)
                        }
                        .graphicsLayer {
                            alpha = (1f - collapseProgress * 1.5f).coerceIn(0f, 1f)
                            if (collapseProgress >= 0.8f) {
                                translationY = -10000f
                            }
                        }
                        .fillMaxWidth(),
                ) {
                    subHeader()
                }
            }

            if (!expanded && !hideSearchBar) {
                // 与初始展开态 SearchBar 使用完全一致的水平留白（16.dp + maxWidth 居中）与顶部呼吸偏移，
                // 确保上滑收缩态右侧双圆钮与初始展开态右侧圆钮在 X/Y 坐标基准上无缝原位对齐。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .fillMaxWidth()
                        .padding(horizontal = SearchBarHorizontalPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = SearchBarMaxWidth)
                            .padding(top = SearchBarTopBreathingSpace)
                            .height(SearchBarHeight),
                    ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .graphicsLayer {
                                    alpha = ((collapseProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .graphicsLayer {
                                alpha = ((collapseProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                                scaleX = 0.85f + 0.15f * collapseProgress
                                scaleY = 0.85f + 0.15f * collapseProgress
                            },
                        horizontalArrangement = Arrangement.spacedBy(SearchBarActionGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (collapseProgress > 0.2f) {
                            // 收缩态不再让图标裸渲染在文字上，统一包装为与主界面严格同径的独立正圆毛玻璃按钮
                            CollapsedCircleAction(
                                backdrop = activeBackdrop,
                                onClick = { onExpandedChange(true) },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(SearchBarIconSize),
                                )
                            }
                            if (filter != null) {
                                CollapsedCircleAction(
                                    backdrop = activeBackdrop,
                                    onClick = { showFilterSheet = true },
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.More,
                                        contentDescription = stringResource(R.string.more_actions),
                                        modifier = Modifier.size(SearchBarIconSize),
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }

            // 以图搜图锚点悬浮弹窗：点击展开态右侧圆形「以图搜图」按钮在正下方平滑弹出
            GlassTransformPopup(
                show = showImageSearchPopup && expanded,
                onDismissRequest = { showImageSearchPopup = false },
                anchor = imageSearchAnchor,
                backdrop = activeBackdrop,
                anchorContent = {
                    Icon(
                        imageVector = MiuixIcons.Image,
                        contentDescription = null,
                        modifier = Modifier.size(SearchBarIconSize),
                    )
                },
                sizing = GlassPopupSizing(minWidth = 256.dp, maxWidth = 300.dp),
                visuals = liquidGlassPopupVisuals(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(156.dp)
                            .clip(SquircleShape(14.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .clickable(role = Role.Button) { pickImageForSearch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Image,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = "点击选择图片",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { pickImageForSearch() },
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (selectedImageUri != null) "重新选图" else stringResource(id = R.string.select_image),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = { startImageSearch() },
                            enabled = selectedImageUri != null,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "开始搜索",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (filter != null) {
                // 统一为与「清空历史」一致的底部对齐悬浮弹窗：四周等距留白、四角完整大圆角，
                // 并绘制一整圈连续的微光玻璃描边，替代贴底抽屉
                LiquidGlassDialog(
                    show = showFilterSheet,
                    modifier = Modifier,
                    onDismissRequest = {
                        showFilterSheet = false
                        onPersistFilter()
                    },
                    cornerRadius = FilterDialogCornerRadius,
                    backgroundColor = MiuixTheme.colorScheme.background,
                ) {
                    CompositionLocalProvider(LocalBackdrop provides null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
                                // 滚动兜底只能加在弹层这一侧：同一个 filter() 也会作为 LazyColumn 的
                                // item 渲染，在 item 内嵌 verticalScroll 会因无限高度约束直接抛异常
                                .verticalScroll(rememberScrollState()),
                        ) {
                            filter()
                        }
                    }
                }
            }
        }
    }
}

fun wrapTagKeyword(keyword: String, translate: Boolean = false): String = run {
    val tag = keyword.substringAfter(':')
    val prefix = keyword.dropLast(tag.length + 1)
    if (translate) {
        val namespacePrefix = TagNamespace.from(prefix)?.prefix
        val newPrefix = EhTagDatabase.getTranslation(tag = prefix) ?: prefix
        val newTag = EhTagDatabase.getTranslation(namespacePrefix, tag) ?: tag
        "$newPrefix：$newTag"
    } else if (keyword.contains(' ')) {
        "$prefix:\"$tag$\""
    } else {
        "$keyword$"
    }
}

private val TagTerminators = charArrayOf('"', '$')
private val WhitespaceRegex = Regex("\\s+")
private val SearchBarHorizontalPadding = 16.dp
private val SearchBarMaxWidth = 720.dp
private val FilterDialogCornerRadius = 32.dp

// 顶部搜索栏缩小为原高度的 3/4（H_new = 0.75 * H_old），下边缘固定不动，省出的高度全部留给顶部上方
private val SearchBarOriginalHeight = SearchBarDefaults.InputFieldMinHeight
private val SearchBarHeight = LiquidGlassCircleButtonSize
private val SearchBarTopBreathingSpace = SearchBarOriginalHeight - SearchBarHeight

// 按缩小后的新高度 H_new 明确搜索栏内部水平间距：左右内边距 = H_new / 2，放大镜与文字之间固定 8.dp
private val SearchBarHalfHeight = SearchBarHeight * 0.5f
private val SearchBarTextGap = 8.dp

// 同步等比例缩小搜索栏与所有独立圆形按钮内部图标尺寸及搜索框字号
internal val SearchBarIconSize = LiquidGlassCircleButtonIconSize
private val SearchBarTextSize = 13.sp

// 右侧独立正圆按钮的直径与间距：直径 = 缩小后的胶囊高度 H_new，按钮之间/与胶囊之间均 8.dp。
private val SearchBarActionSize = LiquidGlassCircleButtonSize
internal val SearchBarActionCornerRadius = SearchBarActionSize * 0.5f
private val SearchBarActionGap = 8.dp

/** 供 [SearchBarCircleAction] 取实时 backdrop，由搜索栏在胶囊同一层级提供。 */
internal val LocalSearchBarBackdrop = staticCompositionLocalOf<Backdrop?> { null }

// 顶部磨砂在覆盖整条搜索栏后继续向下延伸缓冲带，渐变终点落在首张卡片上缘下方并完全归零
private val TopScrimExtraHeight = 8.dp

/**
 * 搜索栏右侧的独立正圆玻璃按钮：基于 MIUIX 0.9.5-rc01 原生 [GlassIconButton]，
 * 挂接 [glassPopupAnchor] 时自动向 [GlassTransformPopup] / [GlassIconDropdownMenu] 供给
 * 按钮玻璃表面与收起回弹状态，实现从圆钮边缘柔和形变展开/优雅缩回原位。
 */
@Composable
fun SearchBarCircleAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalSearchBarBackdrop.current
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
        inheritTopBarMaterial = false,
        backdrop = backdrop,
        material = liquidGlassOverlayMaterial(),
        size = SearchBarActionSize,
        stroke = LiquidGlassNavHighlight,
        content = content,
    )
}

/** 收缩态右侧的独立正圆毛玻璃高光按钮：与 [SearchBarCircleAction] 同源 [GlassIconButton]，直径锁定为 [SearchBarActionSize]。 */
@Composable
private fun CollapsedCircleAction(
    backdrop: Backdrop?,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    GlassIconButton(
        onClick = onClick,
        inheritTopBarMaterial = false,
        backdrop = backdrop,
        material = liquidGlassOverlayMaterial(),
        size = SearchBarActionSize,
        stroke = LiquidGlassNavHighlight,
        content = content,
    )
}
