package com.hippo.ehviewer.ui.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.sendTo
import com.ehviewer.core.files.toOkioPath
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.BlurredBar
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.util.readableWidth
import com.ehviewer.core.util.isAtLeastO
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.BuildConfig
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ktor.isCronetAvailable
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.NavigationIcon
import com.hippo.ehviewer.ui.showRestartDialog
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.util.AdsPlaceholderFile
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.CrashHandler
import com.hippo.ehviewer.util.ReadableTime
import com.hippo.ehviewer.util.StorageCleaner
import com.hippo.ehviewer.util.displayPath
import com.hippo.ehviewer.util.getAppLanguage
import com.hippo.ehviewer.util.getLanguages
import com.hippo.ehviewer.util.setAppLanguage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.snackbar
import moe.tarsin.string
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

context(ctx: Context)
private fun dumplog(uri: Uri): Unit = with(ctx) {
    grantUriPermission(BuildConfig.APPLICATION_ID, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    contentResolver.openOutputStream(uri)?.use { outputStream ->
        val files = ArrayList<File>()
        AppConfig.externalParseErrorDir?.listFiles()?.let { files.addAll(it) }
        AppConfig.externalCrashDir?.listFiles()?.let { files.addAll(it) }
        ZipOutputStream(outputStream).use { zipOs ->
            files.forEach { file ->
                if (!file.isFile) return@forEach
                val entry = ZipEntry(file.name)
                zipOs.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zipOs) }
            }
            val logcatEntry = ZipEntry("logcat-" + ReadableTime.getFilenamableTime() + ".txt")
            zipOs.putNextEntry(logcatEntry)
            CrashHandler.collectInfo(zipOs.writer())
            Runtime.getRuntime().exec("logcat -d").inputStream.use { it.copyTo(zipOs) }
        }
    }
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.AdvancedScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    fun launchSnackbar(message: String) = launch { snackbar(message) }

    val backdrop = rememberBlurBackdrop()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = stringResource(id = R.string.settings_advanced),
                    navigationIcon = { NavigationIcon() },
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else colorScheme.surface,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.surface)
                .blurBackdropSource(backdrop),
            contentAlignment = Alignment.TopCenter,
        ) {
            SettingsScrollColumn(
                scrollBehavior = scrollBehavior,
                contentPadding = paddingValues,
            ) {
                SettingsSmallTitle(
                    text = stringResource(id = R.string.settings_group_interface_theme),
                    first = true,
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    var currentLanguage by remember { mutableStateOf(getAppLanguage()) }
                    val languages = remember { getLanguages() }
                    val languageKeys = remember(languages) { languages.keys.toList() }
                    val languageValues = remember(languages) { languages.values.toList() }
                    val selectedLangIndex = languageKeys.indexOf(currentLanguage).coerceAtLeast(0)
                    GlassDropdownPreference(
                        title = stringResource(id = R.string.settings_advanced_app_language_title),
                        backdrop = backdrop,
                        visuals = liquidGlassPopupVisuals(),
                        items = languageValues,
                        selectedIndex = selectedLangIndex,
                        showValue = true,
                        onSelectedIndexChange = { index ->
                            if (index in languageKeys.indices) {
                                val key = languageKeys[index]
                                setAppLanguage(key)
                                currentLanguage = key
                            }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.animate_items),
                        summary = stringResource(id = R.string.animate_items_summary),
                        state = Settings.animateItems.asMutableState(),
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.desktop_site),
                        summary = stringResource(id = R.string.desktop_site_summary),
                        state = Settings.desktopSite.asMutableState(),
                    )
                    SimpleMenuPreferenceInt(
                        title = stringResource(id = R.string.settings_advanced_read_cache_size),
                        entry = com.hippo.ehviewer.R.array.read_cache_size_entries,
                        entryValueRes = com.hippo.ehviewer.R.array.read_cache_size_entry_values,
                        state = Settings.readCacheSize.asMutableState(),
                    )
                }

                SettingsSmallTitle(text = stringResource(id = R.string.settings_group_logs_diagnostics))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    SwitchPreference(
                        title = stringResource(id = R.string.settings_advanced_save_parse_error_body),
                        summary = stringResource(id = R.string.settings_advanced_save_parse_error_body_summary),
                        state = Settings.saveParseErrorBody.asMutableState(),
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.settings_advanced_save_crash_log),
                        summary = stringResource(id = R.string.settings_advanced_save_crash_log_summary),
                        state = Settings.saveCrashLog.asMutableState(),
                    )
                    val dumpLogError = stringResource(id = R.string.settings_advanced_dump_logcat_failed)
                    LauncherPreference(
                        title = stringResource(id = R.string.settings_advanced_dump_logcat),
                        summary = stringResource(id = R.string.settings_advanced_dump_logcat_summary),
                        contract = ActivityResultContracts.CreateDocument("application/zip"),
                        key = "log-" + ReadableTime.getFilenamableTime() + ".zip",
                    ) { uri ->
                        uri?.run {
                            runCatching {
                                dumplog(uri)
                                launchSnackbar(string(R.string.settings_advanced_dump_logcat_to, uri.displayPath))
                            }.onFailure {
                                launchSnackbar(dumpLogError)
                                logcat(it)
                            }
                        }
                    }
                }

                SettingsSmallTitle(text = stringResource(id = R.string.settings_group_network_ads))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    val stripAds = Settings.stripExtraneousAds.asMutableState()
                    SwitchPreference(
                        title = stringResource(id = R.string.settings_block_extraneous_ads),
                        state = stripAds,
                    )
                    AnimatedVisibility(visible = stripAds.value) {
                        LauncherPreference(
                            title = stringResource(id = R.string.settings_ads_placeholder),
                            contract = ActivityResultContracts.PickVisualMedia(),
                            key = PickVisualMediaRequest(mediaType = ImageOnly),
                        ) { uri ->
                            withIOContext {
                                if (uri != null) {
                                    uri.toOkioPath() sendTo AdsPlaceholderFile
                                } else {
                                    AdsPlaceholderFile.delete()
                                }
                            }
                        }
                    }
                    if (isCronetAvailable) {
                        val enableCronet = Settings.enableCronet.asMutableState()
                        if (BuildConfig.DEBUG || !enableCronet.value) {
                            SwitchPreference(
                                title = stringResource(id = R.string.settings_advanced_enable_cronet),
                                state = enableCronet,
                            )
                        }
                        AnimatedVisibility(enableCronet.value) {
                            SwitchPreference(
                                title = stringResource(id = R.string.settings_advanced_enable_quic),
                                state = Settings.enableQuic.asMutableState(),
                            )
                        }
                        LaunchedEffect(Unit) {
                            merge(
                                Settings.enableCronet.changesFlow(),
                                Settings.enableQuic.changesFlow(),
                            ).collectLatest {
                                showRestartDialog()
                            }
                        }
                    }
                    if (isAtLeastO) {
                        IntSliderPreference(
                            maxValue = 16384,
                            step = 3,
                            title = stringResource(id = R.string.settings_advanced_hardware_bitmap_threshold),
                            summary = stringResource(id = R.string.settings_advanced_hardware_bitmap_threshold_summary),
                            state = Settings.hardwareBitmapThreshold.asMutableState(),
                        )
                    }
                    SwitchPreference(
                        title = stringResource(id = R.string.preload_thumb_aggressively),
                        state = Settings.preloadThumbAggressively.asMutableState(),
                    )
                }

                SettingsSmallTitle(text = stringResource(id = R.string.settings_group_system_storage))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Preference(title = stringResource(id = R.string.open_by_default)) {
                        openByDefaultSettings()
                    }
                    val deepCleanFailed = stringResource(id = R.string.settings_advanced_deep_clean_failed)
                    WorkPreference(
                        title = stringResource(id = R.string.settings_advanced_deep_clean),
                        summary = stringResource(id = R.string.settings_advanced_deep_clean_summary),
                    ) {
                        awaitConfirmationOrCancel(
                            confirmText = R.string.clear_all,
                            title = R.string.settings_advanced_deep_clean,
                        ) {
                            Text(text = stringResource(id = R.string.settings_advanced_deep_clean_confirm))
                        }
                        runSuspendCatching {
                            StorageCleaner.performDeepClean()
                        }.onSuccess { result ->
                            launchSnackbar(string(R.string.settings_advanced_deep_clean_done, result.deadDownloadsCount))
                        }.onFailure {
                            logcat(it)
                            launchSnackbar(deepCleanFailed)
                        }
                    }
                }
            }
        }
    }
}

context(ctx: Context)
private fun openByDefaultSettings() = with(ctx) {
    try {
        @SuppressLint("InlinedApi")
        val intent = Intent(
            ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            "package:$packageName".toUri(),
        )
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val intent = Intent(
            ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri(),
        )
        startActivity(intent)
    }
}
