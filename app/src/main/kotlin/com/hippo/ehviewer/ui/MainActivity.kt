/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui

import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.content.ContentResolver.SCHEME_CONTENT
import android.content.ContentResolver.SCHEME_FILE
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_NONE
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.toOkioPath
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.IosLiquidGlassNavigationBar
import com.ehviewer.core.ui.component.LabeledCheckbox
import com.ehviewer.core.ui.component.LocalBackdrop
import com.ehviewer.core.ui.component.LocalSideSheetState
import com.ehviewer.core.ui.component.LocalTabGesturePosition
import com.ehviewer.core.ui.component.MutableSideSheet
import com.ehviewer.core.ui.component.SideSheetValue
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.component.rememberSideSheetState
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.FormatListNumbered
import com.ehviewer.core.ui.icons.filled.Home
import com.ehviewer.core.ui.icons.filled.Subscriptions
import com.ehviewer.core.ui.icons.filled.Whatshot
import com.ehviewer.core.ui.util.BottomBarInsetsCalculator
import com.ehviewer.core.ui.util.LocalBottomBarContentPadding
import com.ehviewer.core.ui.util.LocalSnackBarFabPadding
import com.ehviewer.core.ui.util.LocalWindowLayout
import com.ehviewer.core.ui.util.LocalWindowSizeClass
import com.ehviewer.core.ui.util.NavigationChrome
import com.ehviewer.core.ui.util.WindowLayout
import com.ehviewer.core.util.isAtLeastQ
import com.ehviewer.core.util.isAtLeastR
import com.ehviewer.core.util.isAtLeastS
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhApplication.Companion.initialized
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.download.DownloadService
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.ui.component.rememberAppIconBitmap
import com.hippo.ehviewer.ui.destinations.DownloadScreenDestination
import com.hippo.ehviewer.ui.destinations.DownloadsScreenDestination
import com.hippo.ehviewer.ui.destinations.FavouritesScreenDestination
import com.hippo.ehviewer.ui.destinations.HistoryScreenDestination
import com.hippo.ehviewer.ui.destinations.HomePageScreenDestination
import com.hippo.ehviewer.ui.destinations.MainTabsScreenDestination
import com.hippo.ehviewer.ui.destinations.ProgressScreenDestination
import com.hippo.ehviewer.ui.destinations.SettingsScreenDestination
import com.hippo.ehviewer.ui.destinations.SignInScreenDestination
import com.hippo.ehviewer.ui.destinations.SubscriptionScreenDestination
import com.hippo.ehviewer.ui.destinations.ToplistScreenDestination
import com.hippo.ehviewer.ui.destinations.WebViewSignInScreenDestination
import com.hippo.ehviewer.ui.destinations.WhatshotScreenDestination
import com.hippo.ehviewer.ui.main.MainTabsViewModel
import com.hippo.ehviewer.ui.screen.asDst
import com.hippo.ehviewer.ui.screen.asDstWith
import com.hippo.ehviewer.ui.screen.navWithUrl
import com.hippo.ehviewer.ui.settings.icon
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitInputText
import com.hippo.ehviewer.ui.update.AppForegroundTracker
import com.hippo.ehviewer.ui.update.UpdateDialog
import com.hippo.ehviewer.ui.update.UpdateDownloadManager
import com.hippo.ehviewer.ui.update.UpdateNotifications
import com.hippo.ehviewer.ui.update.UpdatePromptTracker
import com.hippo.ehviewer.ui.update.UpdateStartupPrompt
import com.hippo.ehviewer.ui.update.installUpdatePackage
import com.hippo.ehviewer.ui.update.shouldPromptUpdate
import com.hippo.ehviewer.updater.AppUpdater
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.addTextToClipboard
import com.hippo.ehviewer.util.displayString
import com.hippo.ehviewer.util.getParcelableExtraCompat
import com.hippo.ehviewer.util.getUrlFromClipboard
import com.hippo.ehviewer.util.sha1
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.spec.Direction
import com.ramcosta.composedestinations.utils.currentDestinationAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import eu.kanade.tachiyomi.util.view.setSecureScreen
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.tarsin.coroutines.runSuspendCatching
import splitties.systemservices.clipboardManager
import splitties.systemservices.connectivityManager
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val navItems = arrayOf<Triple<Direction, Int, ImageVector>>(
    Triple(HomePageScreenDestination, R.string.homepage, EhIcons.Default.Home),
    Triple(SubscriptionScreenDestination, R.string.subscription, EhIcons.Default.Subscriptions),
    Triple(WhatshotScreenDestination, R.string.whats_hot, EhIcons.Default.Whatshot),
    Triple(ToplistScreenDestination, R.string.toplist, EhIcons.Default.FormatListNumbered),
    Triple(FavouritesScreenDestination, R.string.favourite, MiuixIcons.Favorites),
    Triple(HistoryScreenDestination, R.string.history, MiuixIcons.Recent),
    Triple(DownloadsScreenDestination, R.string.downloads, MiuixIcons.Download),
    Triple(SettingsScreenDestination, R.string.settings, MiuixIcons.Settings),
)

/**
 * 底部悬浮导航栏与顶部搜索栏共用的滚动联动状态。
 * 列表页上滑时置为隐藏，下拉回弹时恢复；设置页不会写入该状态，从而保持常驻显示。
 */
object BottomBarScrollState {
    var hidden by mutableStateOf(false)

    fun reset() {
        hidden = false
    }
}

class MainActivity : AppCompatActivity() {

    private var shareUrl: String? = null

    @Composable
    fun ProvideAssistContent(url: String) {
        val urlState by rememberUpdatedState(url)
        DisposableEffect(urlState) {
            shareUrl = urlState
            onDispose {
                shareUrl = null
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentChannel.trySend(intent)
    }

    private val tipFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val intentChannel = Channel<Intent>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val intentFlow = intentChannel.receiveAsFlow()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !initialized }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // https://issuetracker.google.com/204791558
        // Fix system bars insets still exist in fullscreen mode on API < 30
        @Suppress("DEPRECATION")
        if (!isAtLeastR) {
            with(window.decorView) {
                systemUiVisibility = systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_STABLE.inv()
            }
        }
        setMiuixContent {
            val globalDialogState = this@setMiuixContent
            val configuration = LocalConfiguration.current
            val sideSheetState = rememberSideSheetState()
            val snackbarState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val navController = rememberNavController()
            val navigator = navController.rememberDestinationsNavigator()
            val tabsViewModel: MainTabsViewModel = viewModel()

            val currentDestination by navController.currentDestinationAsState()
            val rawNavConfig by Settings.bottomNavItems.collectAsState()
            val visibleNavItems = remember(rawNavConfig) {
                val config = MainNavItems.decode(rawNavConfig)
                MainNavItems.visible(config)
            }
            val primaryNavItems = remember(visibleNavItems) {
                visibleNavItems.map { item ->
                    Triple(item.direction, item.titleRes, item.icon())
                }
            }

            val hasNetwork = remember { connectivityManager.activeNetwork != null }
            val launchPage by Settings.launchPage.collectAsState()
            val safeLaunchPage = launchPage.coerceIn(0, 3)
            val launchNavItem = remember(safeLaunchPage, hasNetwork) {
                val configured = MainNavItem.entries.getOrElse(safeLaunchPage) { MainNavItem.Home }
                if (hasNetwork) configured else MainNavItem.Downloads
            }
            remember(visibleNavItems) {
                tabsViewModel.ensureInitialTab(launchNavItem, visibleNavItems)
            }

            val isOnMainTabs = currentDestination?.route == MainTabsScreenDestination.route
            val isStandalonePrimary = navItems.any { it.first.route == currentDestination?.route }
            val isPrimaryDestination = isOnMainTabs || isStandalonePrimary

            fun selectMainNavItem(item: MainNavItem) {
                if (item in visibleNavItems) {
                    if (navController.currentDestination?.route == MainTabsScreenDestination.route) {
                        tabsViewModel.requestSwitchToItem(item, visibleNavItems, animate = true)
                    } else {
                        tabsViewModel.selectTabImmediate(item, visibleNavItems)
                        val popped = navController.popBackStack(MainTabsScreenDestination.route, inclusive = false)
                        if (!popped) {
                            navigator.navigate(MainTabsScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                } else {
                    val direction = item.direction
                    if (navController.currentDestination?.route != direction.route) {
                        navigator.navigate(direction) {
                            launchSingleTop = true
                        }
                    }
                }
            }
            val latestSelectMainNavItem by rememberUpdatedState(::selectMainNavItem)

            suspend fun DialogState.checkDownloadLocation() {
                val valid = withIOContext { downloadLocation.isDirectory }
                if (!valid) {
                    awaitConfirmationOrCancel(
                        confirmText = R.string.open_settings,
                        title = R.string.warning,
                        showCancelButton = false,
                    ) {
                        Text(
                            text = stringResource(id = R.string.invalid_download_location),
                            style = MiuixTheme.textStyles.title4,
                        )
                    }
                    navigator.navigate(DownloadScreenDestination)
                }
            }

            val startupRelease = UpdateStartupPrompt.release

            if (!AppConfig.isBenchmark) {
                val noNetwork = stringResource(R.string.no_network)
                LaunchedEffect(Unit) {
                    runCatching { checkDownloadLocation() }
                    runCatching { checkAppLinkVerify() }
                    if (hasNetwork) {
                        runSuspendCatching {
                            withIOContext {
                                val release = AppUpdater.checkForUpdate()
                                if (
                                    release != null &&
                                    shouldPromptUpdate(
                                        version = release.version,
                                        ignoredVersion = Settings.ignoredUpdateVersion.value,
                                        promptedVersion = UpdatePromptTracker.promptedVersion,
                                        userInitiated = false,
                                    )
                                ) {
                                    UpdatePromptTracker.markPrompted(release.version)
                                    UpdateStartupPrompt.release = release
                                }
                            }
                        }.onFailure {
                            snackbarState.showSnackbar(getString(R.string.update_failed, it.displayString()))
                        }
                    } else {
                        snackbarState.showSnackbar(noNetwork)
                    }
                }
            }

            LaunchedEffect(Unit) {
                Settings.enabledSecurity.valueFlow().collect {
                    window.setSecureScreen(it)
                }
            }
            if (isAuthenticationSupported()) {
                SecurityScreen(onError = { moveTaskToBack(true) }, modifier = Modifier.zIndex(1f))
            }

            val cannotParse = stringResource(R.string.error_cannot_parse_the_url)
            LaunchedEffect(Unit) {
                intentFlow.collect { intent ->
                    when (intent.action) {
                        Intent.ACTION_VIEW -> with(navigator) {
                            val uri = intent.data ?: return@collect
                            when (uri.scheme) {
                                SCHEME_FILE -> navToReader(uri.path!!)
                                SCHEME_CONTENT -> navToReader(uri.toString())
                                else -> {
                                    val url = uri.toString()
                                    if (!navWithUrl(url)) {
                                        val new = awaitInputText(initial = url, title = cannotParse)
                                        addTextToClipboard(new)
                                    }
                                }
                            }
                        }
                        Intent.ACTION_SEND -> with(navigator) {
                            val type = intent.type
                            if ("text/plain" == type) {
                                val keyword = intent.getStringExtra(Intent.EXTRA_TEXT)
                                if (keyword != null && !navWithUrl(keyword)) {
                                    navigate(ListUrlBuilder(keyword = keyword).asDst())
                                }
                            } else if (type != null && type.startsWith("image/")) {
                                val uri = intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)
                                if (null != uri) {
                                    val hash = withIOContext { uri.toOkioPath().sha1() }
                                    navigate(
                                        ListUrlBuilder(
                                            mode = ListUrlBuilder.MODE_IMAGE_SEARCH,
                                            hash = hash,
                                        ).asDst(),
                                    )
                                }
                            }
                        }
                        UpdateNotifications.ACTION_INSTALL_UPDATE -> {
                            val file = UpdateDownloadManager.downloadedFile
                                ?: File(AppConfig.tempDir.toFile(), "update.apk")
                            if (file.exists()) {
                                scope.launch { installUpdatePackage(this@MainActivity, file) }
                            }
                        }
                        DownloadService.ACTION_START_DOWNLOADSCENE -> {
                            val args = intent.getBundleExtra(DownloadService.ACTION_START_DOWNLOADSCENE_ARGS)!!
                            if (args.getString(DownloadService.KEY_ACTION) == DownloadService.ACTION_CLEAR_DOWNLOAD_SERVICE) {
                                DownloadService.clear()
                            }
                            latestSelectMainNavItem(MainNavItem.Downloads)
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                tipFlow.collectLatest {
                    snackbarState.showSnackbar(it)
                }
            }
            val warning = stringResource(R.string.metered_network_warning)
            val settings = stringResource(R.string.settings)
            val checkMeteredNetwork by Settings.meteredNetworkWarning.collectAsState()
            if (checkMeteredNetwork) {
                LaunchedEffect(Unit) {
                    if (connectivityManager.isActiveNetworkMetered) {
                        if (isAtLeastQ) {
                            val ret = snackbarState.showSnackbar(warning, settings, true)
                            if (ret == SnackbarResult.ActionPerformed) {
                                val panelIntent = Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                startActivity(panelIntent)
                            }
                        } else {
                            snackbarState.showSnackbar(warning)
                        }
                    }
                }
            }
            val snackMessage = stringResource(R.string.clipboard_gallery_url_snack_message)
            val snackAction = stringResource(R.string.clipboard_gallery_url_snack_action)
            LifecycleResumeEffect(scope) {
                val job = scope.launch {
                    delay(300)
                    val text = clipboardManager.getUrlFromClipboard(applicationContext)
                    val hashCode = text?.hashCode() ?: 0
                    if (text != null && hashCode != 0 && Settings.clipboardTextHashCode != hashCode) {
                        val result1 = GalleryDetailUrlParser.parse(text, false)
                        var launch: (() -> Unit)? = null
                        if (result1 != null) {
                            launch = { navigator.navigate(result1.gid asDstWith result1.token) }
                        }
                        val result2 = GalleryPageUrlParser.parse(text, false)
                        if (result2 != null) {
                            launch = { navigator.navigate(ProgressScreenDestination(result2.gid, result2.pToken, result2.page)) }
                        }
                        launch?.let {
                            val ret = snackbarState.showSnackbar(snackMessage, snackAction, true)
                            if (ret == SnackbarResult.ActionPerformed) it()
                        }
                    }
                    Settings.clipboardTextHashCode = hashCode
                }
                onPauseOrDispose { job.cancel() }
            }
            val drawerHandle = remember { mutableStateListOf<Long>() }
            var snackbarFabPadding by remember { mutableStateOf(0.dp) }
            val drawerEnabled = drawerHandle.isNotEmpty()
            // 路由或主 Tab 切换时强制收起全局共享侧边抽屉并恢复底栏
            LaunchedEffect(currentDestination?.route, tabsViewModel.currentNavItem) {
                if (!sideSheetState.isClosed) {
                    sideSheetState.snapTo(SideSheetValue.Closed)
                }
                BottomBarScrollState.reset()
            }
            val density = LocalDensity.current
            val adaptiveInfo = currentWindowAdaptiveInfoV2()
            val needSignIn by Settings.needSignIn.collectAsState()

            val windowLayout = WindowLayout(
                widthDp = configuration.screenWidthDp,
                heightDp = configuration.screenHeightDp,
            )
            val navBarStyle by Settings.navBarStyle.collectAsState()
            val navigationChrome = when (navBarStyle) {
                1 -> NavigationChrome.Rail
                2 -> NavigationChrome.BottomBar
                else -> windowLayout.navigationChrome
            }
            val showNavigationRail = navigationChrome == NavigationChrome.Rail

            // 设置主页面及其所有二级子页面固定展示底部导航栏，不参与滚动隐藏
            val settingsPackage = SettingsScreenDestination.route.substringBeforeLast('.') + "."
            val inSettingsTree = currentDestination?.route?.startsWith(settingsPackage) == true
            val isSettingsTabActive = isOnMainTabs && tabsViewModel.currentNavItem == MainNavItem.Settings
            val showBottomBar = isPrimaryDestination || inSettingsTree

            LaunchedEffect(needSignIn) {
                if (!needSignIn && (currentDestination === SignInScreenDestination || currentDestination === WebViewSignInScreenDestination)) {
                    tabsViewModel.selectTabImmediate(launchNavItem, visibleNavItems)
                    navigator.navigate(MainTabsScreenDestination) {
                        popUpTo(SignInScreenDestination) { inclusive = true }
                    }
                }
            }

            val contentBackdrop = rememberBlurBackdrop()
            val navBarBottomPadding = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            val mainContentBottomPadding = BottomBarInsetsCalculator.calculateMainContentBottomPadding(
                isPrimaryDestination = showBottomBar,
                navigationChrome = navigationChrome,
                navBarBottomPadding = navBarBottomPadding,
            )
            val bottomBarPadding = if (showBottomBar && !showNavigationRail) 88.dp else 0.dp
            val effectiveFabPadding = snackbarFabPadding.coerceAtLeast(bottomBarPadding)

            CompositionLocalProvider(
                LocalBackdrop provides contentBackdrop,
                LocalSideSheetState provides sideSheetState,
                LocalDrawerHandle provides drawerHandle,
                LocalSnackBarHostState provides snackbarState,
                LocalSnackBarFabPadding provides animateDpAsState(effectiveFabPadding, label = "SnackbarFabPadding"),
                LocalBottomBarContentPadding provides mainContentBottomPadding,
                LocalWindowSizeClass provides adaptiveInfo.windowSizeClass,
                LocalWindowLayout provides windowLayout,
            ) {
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(
                            state = snackbarState,
                            modifier = Modifier
                                .padding(bottom = bottomBarPadding)
                                .onGloballyPositioned {
                                    with(density) {
                                        snackbarFabPadding = it.size.height.toDp()
                                    }
                                },
                        )
                    },
                ) { _ ->
                    @Composable
                    fun MainContent() {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 底层内容区注册为唯一模糊源：HorizontalPager 左右滑动或跨页平推时底栏实时折射新旧页面背景
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blurBackdropSource(contentBackdrop),
                            ) {
                                SharedTransitionLayout {
                                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                                        val start = if (needSignIn) {
                                            SignInScreenDestination
                                        } else {
                                            MainTabsScreenDestination
                                        }
                                        DestinationsNavHost(
                                            navGraph = NavGraphs.root,
                                            start = start,
                                            defaultTransitions = rememberEhNavAnim(),
                                            navController = navController,
                                        )
                                    }
                                }
                            }

                            val currentTabIndex = when {
                                isOnMainTabs -> tabsViewModel.selectedTabIndex.coerceIn(
                                    0,
                                    (primaryNavItems.size - 1).coerceAtLeast(0),
                                )
                                inSettingsTree -> visibleNavItems.indexOf(MainNavItem.Settings).coerceAtLeast(0)
                                else -> MainNavPolicy.getPrimaryBottomIndex(
                                    currentDestination,
                                    primaryNavItems.map { it.first },
                                )
                            }
                            // 进入二级页后锁定退出瞬间的选中项，避免隐藏滑出过程中指示器向左偏移跳动
                            var lastVisibleTabIndex by remember {
                                mutableStateOf(
                                    currentTabIndex.coerceIn(0, (primaryNavItems.size - 1).coerceAtLeast(0)),
                                )
                            }
                            LaunchedEffect(currentTabIndex, isOnMainTabs, inSettingsTree, isStandalonePrimary) {
                                if (currentTabIndex >= 0 && (isOnMainTabs || inSettingsTree || isStandalonePrimary)) {
                                    lastVisibleTabIndex = currentTabIndex
                                }
                            }
                            val navSelectedIndex = if (currentTabIndex >= 0) currentTabIndex else lastVisibleTabIndex

                            if (!showNavigationRail) {
                                AnimatedVisibility(
                                    visible = showBottomBar &&
                                        (!BottomBarScrollState.hidden || inSettingsTree || isSettingsTabActive) &&
                                        !SelectionModeController.active &&
                                        !SearchExpandController.expanded,
                                    enter = slideInVertically { it } + fadeIn(),
                                    exit = slideOutVertically { it } + fadeOut(),
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                ) {
                                    CompositionLocalProvider(
                                        LocalTabGesturePosition provides tabsViewModel.tabGesturePositionState,
                                    ) {
                                        IosLiquidGlassNavigationBar(
                                            items = primaryNavItems.map { (_, stringId, icon) ->
                                                NavigationItem(label = stringResource(id = stringId), icon = icon)
                                            },
                                            selectedIndex = navSelectedIndex.coerceIn(
                                                0,
                                                (primaryNavItems.size - 1).coerceAtLeast(0),
                                            ),
                                            onItemClick = { index ->
                                                visibleNavItems.getOrNull(index)?.let(::selectMainNavItem)
                                            },
                                            backdrop = contentBackdrop,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showNavigationRail) {
                            val railState = rememberNavigationRailState(NavigationRailValue.Collapsed)
                            NavigationRail(
                                state = railState,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(
                                            WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom,
                                        ),
                                    ),
                                defaultWindowInsetsPadding = false,
                                expandContentDescription = stringResource(id = R.string.nav_rail_expand),
                                collapseContentDescription = stringResource(id = R.string.nav_rail_collapse),
                                scrollState = rememberScrollState(),
                                header = {
                                    val appIcon = rememberAppIconBitmap()
                                    Box(
                                        modifier = Modifier
                                            .padding(vertical = 16.dp)
                                            .size(44.dp)
                                            .clip(SquircleShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon,
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = com.hippo.ehviewer.R.drawable.ic_launcher_foreground),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                },
                            ) {
                                MainNavItem.entries.forEach { item ->
                                    val selected = if (isOnMainTabs) {
                                        tabsViewModel.currentNavItem == item
                                    } else {
                                        currentDestination?.route == item.direction.route
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { selectMainNavItem(item) },
                                        icon = item.icon(),
                                        label = stringResource(id = item.titleRes),
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            MutableSideSheet(
                                drawerState = sideSheetState,
                                modifier = Modifier.fillMaxSize(),
                                enabled = drawerEnabled,
                            ) {
                                MainContent()
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        globalDialogState.Place()
                    }

                    if (startupRelease != null) {
                        val release = startupRelease
                        val ignoreAlertText = stringResource(R.string.update_msg_ignore_alert, release.version)
                        val undoText = stringResource(R.string.update_msg_undo_ignore)
                        UpdateDialog(
                            show = true,
                            release = release,
                            onDismiss = { UpdateStartupPrompt.release = null },
                            onIgnore = { version ->
                                val previous = Settings.ignoredUpdateVersion.value
                                Settings.ignoredUpdateVersion.value = version
                                UpdateStartupPrompt.release = null
                                scope.launch {
                                    val result = snackbarState.showSnackbar(ignoreAlertText, undoText, true)
                                    if (result == SnackbarResult.ActionPerformed) {
                                        Settings.ignoredUpdateVersion.value = previous
                                        UpdatePromptTracker.forget(version)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            if (intent.action != Intent.ACTION_MAIN) {
                onNewIntent(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppForegroundTracker.isForeground = true
    }

    override fun onPause() {
        AppForegroundTracker.isForeground = false
        super.onPause()
    }

    private suspend fun DialogState.checkAppLinkVerify() {
        if (isAtLeastS && !Settings.appLinkVerifyTip) {
            val manager = getSystemService(DomainVerificationManager::class.java)
            val packageName = packageName
            val userState = manager.getDomainVerificationUserState(packageName) ?: return
            val hasUnverified = userState.hostToStateMap.values.any { it == DOMAIN_STATE_NONE }
            if (hasUnverified) {
                var checked by mutableStateOf(false)
                awaitConfirmationOrCancel(
                    confirmText = R.string.open_settings,
                    title = R.string.app_link_not_verified_title,
                    onCancelButtonClick = {
                        if (checked) Settings.appLinkVerifyTip = true
                    },
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.app_link_not_verified_message),
                            style = MiuixTheme.textStyles.title4,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LabeledCheckbox(
                            modifier = Modifier.fillMaxWidth(),
                            checked = checked,
                            onCheckedChange = { checked = it },
                            label = stringResource(id = R.string.dont_show_again),
                            indication = null,
                        )
                    }
                }
                if (checked) {
                    Settings.appLinkVerifyTip = true
                }
                try {
                    val intent = Intent(
                        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        "package:$packageName".toUri(),
                    )
                    startActivity(intent)
                } catch (_: Throwable) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:$packageName".toUri(),
                    )
                    startActivity(intent)
                }
            }
        }
    }

    fun showTip(message: String, useToast: Boolean = false) {
        if (useToast || !tipFlow.tryEmit(message)) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)
        shareUrl?.let { outContent?.webUri = it.toUri() }
    }
}

val LocalDrawerHandle = compositionLocalOf<SnapshotStateList<Long>> { error("CompositionLocal LocalDrawerHandle not present!") }
val LocalSnackBarHostState = compositionLocalOf<SnackbarHostState> { error("CompositionLocal LocalSnackBarHostState not present!") }
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> { error("CompositionLocal LocalSharedTransitionScope not present!") }

@Composable
fun DrawerHandle(enabled: Boolean) {
    if (enabled) {
        val current = currentCompositeKeyHashCode
        val handle = LocalDrawerHandle.current
        DisposableEffect(current) {
            handle.add(current)
            onDispose {
                handle.remove(current)
            }
        }
    }
}
