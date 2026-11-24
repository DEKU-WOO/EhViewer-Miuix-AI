package com.hippo.ehviewer.ui.settings

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.BlurredBar
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.Compass
import com.ehviewer.core.ui.icons.filled.SadPanda
import com.ehviewer.core.ui.util.readableWidth
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.destinations.AboutScreenDestination
import com.hippo.ehviewer.ui.destinations.AdvancedScreenDestination
import com.hippo.ehviewer.ui.destinations.AiTranslationScreenDestination
import com.hippo.ehviewer.ui.destinations.BackupRestoreScreenDestination
import com.hippo.ehviewer.ui.destinations.BottomNavItemsScreenDestination
import com.hippo.ehviewer.ui.destinations.DownloadScreenDestination
import com.hippo.ehviewer.ui.destinations.EhScreenDestination
import com.hippo.ehviewer.ui.destinations.PrivacyScreenDestination
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.SettingsScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                // 与二级页面同款大标题顶栏，仅去掉左上角返回按钮（保留系统边缘返回手势）
                TopAppBar(
                    title = stringResource(id = R.string.settings),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                )
            }
        },
    ) { paddingValues ->
        SettingsScrollColumn(
            scrollBehavior = scrollBehavior,
            contentPadding = paddingValues,
            modifier = Modifier.blurBackdropSource(backdrop),
        ) {
            // 与二级页面首个分组标题保持一致的大标题 → 首张卡片间距
            Spacer(Modifier.height(SettingsContentTopGap))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                PreferenceHeader(
                    icon = EhIcons.Default.SadPanda,
                    title = R.string.settings_eh,
                    childRoute = EhScreenDestination,
                    navigator = navigator,
                )
                PreferenceHeader(
                    icon = MiuixIcons.Download,
                    title = R.string.settings_download,
                    childRoute = DownloadScreenDestination,
                    navigator = navigator,
                )
                PreferenceHeader(
                    icon = MiuixIcons.Lock,
                    title = R.string.settings_privacy,
                    childRoute = PrivacyScreenDestination,
                    navigator = navigator,
                )
                PreferenceHeader(
                    icon = MiuixIcons.Tune,
                    title = R.string.settings_advanced,
                    childRoute = AdvancedScreenDestination,
                    navigator = navigator,
                )
                PreferenceHeader(
                    icon = MiuixIcons.Translate,
                    title = R.string.settings_ai_translation,
                    childRoute = AiTranslationScreenDestination,
                    navigator = navigator,
                )
                PreferenceHeader(
                    icon = EhIcons.Default.Compass,
                    title = R.string.desktop_nav_items_title,
                    childRoute = BottomNavItemsScreenDestination,
                    navigator = navigator,
                )
                PreferenceHeader(
                    icon = MiuixIcons.Basic.ArrowUpDown,
                    title = R.string.backup_restore_title,
                    childRoute = BackupRestoreScreenDestination,
                    navigator = navigator,
                )
                PreferenceHeader(
                    icon = MiuixIcons.Info,
                    title = R.string.settings_about,
                    childRoute = AboutScreenDestination,
                    navigator = navigator,
                )
            }
        }
    }
}
