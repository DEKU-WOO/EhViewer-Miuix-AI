package com.hippo.ehviewer.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.BlurredBar
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.liquidGlassPopupVisuals
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.theme.activeAccentColor
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.AiProfileStore
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.sync.CloudSettingsRecord
import com.hippo.ehviewer.sync.SettingsBackup
import com.hippo.ehviewer.sync.VpsClient
import com.hippo.ehviewer.sync.WebDavClient
import com.hippo.ehviewer.sync.WebDavGallerySync
import com.hippo.ehviewer.sync.WebDavSyncService
import com.hippo.ehviewer.sync.updateWebDavAutoSync
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.NavigationIcon
import com.hippo.ehviewer.ui.tools.dialog
import com.hippo.ehviewer.util.ReadableTime
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import moe.tarsin.snackbar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.ehviewer.core.ui.component.LiquidGlassDialog

private const val SETTINGS_MIME = "application/json"

/** 当前活跃网络是否为计费网络（移动数据）。 */
private fun isMeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.BackupRestoreScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val backdrop = rememberBlurBackdrop()
    val context = LocalContext.current

    fun toast(message: String) = launch { snackbar(message) }
    // LauncherPreference 的回调运行在 IO 线程，launch 投递回页面主协程作用域展示毛玻璃提示
    fun postToast(message: String) = launch { snackbar(message) }

    val exportDone = stringResource(R.string.backup_export_success)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val importDone = stringResource(R.string.backup_import_success)
    val importFailed = stringResource(R.string.backup_import_failed)
    val noServer = stringResource(R.string.backup_cloud_no_server)
    val uploadDone = stringResource(R.string.backup_cloud_uploaded)
    val uploadFailed = stringResource(R.string.backup_cloud_upload_failed)
    val loadFailed = stringResource(R.string.backup_cloud_load_failed)
    val cloudEmpty = stringResource(R.string.backup_cloud_empty)
    val restoreFailed = stringResource(R.string.backup_cloud_restore_failed)
    val restoreDone = stringResource(R.string.backup_restore_done)
    val favoriteNothing = stringResource(R.string.backup_favorites_nothing)
    val favoriteFailed = stringResource(R.string.backup_favorites_failed)
    val favoriteDone = stringResource(R.string.backup_favorites_success)
    val webdavTestDone = stringResource(R.string.backup_webdav_test_success)
    val webdavTestFailed = stringResource(R.string.backup_webdav_test_failed)

    val serverUrl = Settings.vpsServerUrl.asMutableState()
    val accessToken = Settings.vpsToken.asMutableState()
    val webdavAutoSync = Settings.webdavAutoSync.asMutableState()
    val webdavSyncMode = Settings.webdavSyncMode.asMutableState()
    val webdavSyncState by WebDavGallerySync.syncState.collectAsState()
    val hasSignedIn by Settings.hasSignedIn.collectAsState()
    var showMeteredConfirm by remember { mutableStateOf(false) }

    // 周期任务随开关实时生效
    LaunchedEffect(webdavAutoSync.value) {
        withIOContext { updateWebDavAutoSync(context) }
    }

    fun uploadSettings() = launch {
        if (!VpsClient.configured()) {
            toast(noServer)
            return@launch
        }
        val error = withIOContext {
            runCatching {
                val settings = Json.parseToJsonElement(SettingsBackup.exportJson())
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                VpsClient.postSettings(
                    settings = settings,
                    deviceId = Settings.syncDeviceId.value.ifBlank { "unknown" },
                    deviceName = deviceName.ifBlank { "Android" },
                    backupDate = backupDate,
                    profileCount = AiProfileStore.profiles().size,
                )
            }.onFailure { logcat("BackupRestore", it) }.exceptionOrNull()
        }
        toast(if (error == null) uploadDone else uploadFailed.format(error.message.orEmpty()))
    }

    fun testVpsConnection() = launch {
        if (!VpsClient.configured()) {
            toast(noServer)
            return@launch
        }
        val error = withIOContext {
            runCatching { VpsClient.testConnection() }
                .onFailure { logcat("BackupRestore", it) }
                .exceptionOrNull()
        }
        toast(
            if (error == null) {
                "VPS 连接与鉴权测试成功"
            } else {
                "VPS 连接失败：${error.message.orEmpty()}"
            },
        )
    }

    fun restoreFromCloud() = launch {
        if (!VpsClient.configured()) {
            toast(noServer)
            return@launch
        }
        val records = runCatching { withIOContext { VpsClient.fetchSettings() } }
            .onFailure { logcat("BackupRestore", it) }
            .getOrNull()
        if (records == null) {
            toast(loadFailed.format(""))
            return@launch
        }
        if (records.isEmpty()) {
            toast(cloudEmpty)
            return@launch
        }
        val sorted = records.sortedWith(
            compareByDescending<CloudSettingsRecord> { it.backupDate }.thenByDescending { it.updatedAt },
        )
        val thisDeviceId = Settings.syncDeviceId.value
        val chosen = dialog<CloudSettingsRecord?> { cont ->
            LiquidGlassDialog(
                show = true,
                title = stringResource(R.string.backup_cloud_restore),
                onDismissRequest = { cont.resume(null) },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(sorted) { record ->
                        val isLocal = record.deviceId == thisDeviceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cont.resume(record) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = record.deviceName.ifBlank { "Android" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MiuixTheme.textStyles.body1,
                                    )
                                    if (isLocal) {
                                        val accent = activeAccentColor()
                                        Text(
                                            text = stringResource(R.string.backup_cloud_this_device),
                                            style = MiuixTheme.textStyles.footnote2,
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .clip(CircleShape)
                                                .background(accent.copy(alpha = 0.16f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(
                                        R.string.backup_cloud_row_summary,
                                        record.backupDate.ifBlank { "-" },
                                        record.profileCount,
                                    ),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }
        } ?: return@launch

        val isLocal = chosen.deviceId == thisDeviceId
        val option = dialog<Int?> { cont ->
            LiquidGlassDialog(
                show = true,
                title = stringResource(R.string.backup_cloud_restore),
                onDismissRequest = { cont.resume(null) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                ) {
                    if (!isLocal) {
                        Text(
                            text = stringResource(R.string.backup_cloud_cross_device_warning),
                            color = colorScheme.error,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.backup_restore_full),
                            onClick = { cont.resume(0) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = stringResource(R.string.backup_restore_api_only),
                            onClick = { cont.resume(1) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } ?: return@launch

        val raw = chosen.settingsJson
        val error = withIOContext {
            runCatching {
                if (option == 0) {
                    if (!SettingsBackup.restoreAll(raw)) throw IllegalStateException("invalid backup")
                } else {
                    // 解析不到任何方案时按失败处理，避免误报“恢复完成”
                    if (SettingsBackup.restoreApiProfiles(raw) <= 0) throw IllegalStateException("no api profile")
                }
            }.onFailure { logcat("BackupRestore", it) }.exceptionOrNull()
        }
        toast(if (error == null) restoreDone else restoreFailed.format(error.message.orEmpty()))
    }

    fun exportFavorites() = launch {
        val exported = withIOContext {
            runCatching {
                val builder = FavListUrlBuilder()
                var total = 0
                var index = 0
                while (true) {
                    val page = EhEngine.getFavorites(builder.build())
                    if (page.galleryInfoList.isEmpty()) break
                    if (total == 0) total = page.countArray.sum()
                    index += page.galleryInfoList.size
                    EhDB.putLocalFavorites(page.galleryInfoList)
                    val next = page.next ?: break
                    delay(Settings.downloadDelay.value.toLong())
                    builder.setIndex(next, true)
                }
                index
            }.onFailure { logcat("BackupRestore", it) }.getOrNull()
        }
        when {
            exported == null -> toast(favoriteFailed)
            exported == 0 -> toast(favoriteNothing)
            else -> toast(favoriteDone)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = stringResource(id = R.string.backup_restore_title),
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
                // 分组一：本地备份（首个分组，间距由 SettingsSmallTitle 统一控制）
                SettingsSmallTitle(text = stringResource(id = R.string.backup_local_title), first = true)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    LauncherPreference(
                        title = stringResource(R.string.backup_local_export_settings),
                        summary = stringResource(R.string.backup_local_export_settings_summary),
                        contract = ActivityResultContracts.CreateDocument(SETTINGS_MIME),
                        key = "ehviewer-settings-${ReadableTime.getFilenamableTime()}.json",
                    ) { uri ->
                        if (uri == null) return@LauncherPreference
                        runCatching {
                            val text = SettingsBackup.exportJson()
                            writeText(context, uri, text)
                            postToast(exportDone)
                        }.onFailure {
                            logcat("BackupRestore", it)
                            postToast(exportFailed)
                        }
                    }
                    LauncherPreference(
                        title = stringResource(R.string.backup_local_import_settings),
                        summary = stringResource(R.string.backup_local_import_settings_summary),
                        contract = ActivityResultContracts.GetContent(),
                        key = SETTINGS_MIME,
                    ) { uri ->
                        if (uri == null) return@LauncherPreference
                        runCatching {
                            val text = readText(context, uri)
                            if (!SettingsBackup.restoreAll(text)) throw IllegalStateException("invalid backup")
                            postToast(importDone)
                        }.onFailure {
                            logcat("BackupRestore", it)
                            postToast(importFailed)
                        }
                    }
                    if (hasSignedIn) {
                        Preference(
                            title = stringResource(R.string.backup_local_export_favorites),
                            summary = stringResource(R.string.backup_local_export_favorites_summary),
                            onClick = { exportFavorites() },
                        )
                    }
                }

                // 分组二：多端云同步（VPS）
                SettingsSmallTitle(text = stringResource(id = R.string.backup_cloud_title))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BackupTextField(
                            title = stringResource(R.string.backup_cloud_server),
                            value = serverUrl.value,
                            onValueChange = { serverUrl.value = it },
                        )
                        BackupTextField(
                            title = stringResource(R.string.backup_cloud_token),
                            value = accessToken.value,
                            onValueChange = { accessToken.value = it },
                            password = true,
                        )
                        val vpsLastHistorySyncAt = Settings.vpsLastHistorySyncAt.asMutableState()
                        val historySyncSummary = if (vpsLastHistorySyncAt.value > 0) {
                            "上次同步：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(vpsLastHistorySyncAt.value))
                        } else {
                            stringResource(R.string.backup_cloud_history_switch_summary)
                        }
                        SwitchPreference(
                            title = stringResource(R.string.backup_cloud_history_switch),
                            summary = historySyncSummary,
                            state = Settings.vpsHistorySyncEnabled.asMutableState(),
                        )
                        Preference(
                            title = "测试连接",
                            summary = "校验 VPS 服务器可达性与访问密钥 Token 鉴权",
                            onClick = { testVpsConnection() },
                        )
                        Preference(
                            title = stringResource(R.string.backup_cloud_upload),
                            summary = stringResource(R.string.backup_cloud_upload_summary),
                            onClick = { uploadSettings() },
                        )
                        Preference(
                            title = stringResource(R.string.backup_cloud_restore),
                            summary = stringResource(R.string.backup_cloud_restore_summary),
                            onClick = { restoreFromCloud() },
                        )
                    }
                }

                // 分组三：已下载画廊同步（WebDAV）
                SettingsSmallTitle(text = stringResource(id = R.string.backup_webdav_title))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val webdavUrl = Settings.webdavUrl.asMutableState()
                        val webdavUser = Settings.webdavUser.asMutableState()
                        val webdavPassword = Settings.webdavPassword.asMutableState()
                        val webdavDir = Settings.webdavDir.asMutableState()
                        BackupTextField(
                            title = stringResource(R.string.backup_webdav_url),
                            value = webdavUrl.value,
                            onValueChange = { webdavUrl.value = it },
                        )
                        BackupTextField(
                            title = stringResource(R.string.backup_webdav_user),
                            value = webdavUser.value,
                            onValueChange = { webdavUser.value = it },
                        )
                        BackupTextField(
                            title = stringResource(R.string.backup_webdav_password),
                            value = webdavPassword.value,
                            onValueChange = { webdavPassword.value = it },
                            password = true,
                        )
                        BackupTextField(
                            title = stringResource(R.string.backup_webdav_dir),
                            value = webdavDir.value,
                            onValueChange = { webdavDir.value = it },
                        )
                        Preference(
                            title = stringResource(R.string.backup_webdav_test),
                            onClick = {
                                launch {
                                    val error = withIOContext {
                                        runCatching { WebDavClient.testConnection() }
                                            .onFailure { logcat("WebDav", it) }
                                            .exceptionOrNull()
                                    }
                                    toast(if (error == null) webdavTestDone else webdavTestFailed.format(error.message.orEmpty()))
                                }
                            },
                        )
                        val syncTitle = stringResource(R.string.backup_webdav_sync)
                        val syncModeItems = remember {
                            listOf("双向增量同步", "仅上传本地新增", "仅从远端拉取")
                        }
                        GlassDropdownPreference(
                            title = "同步模式",
                            backdrop = backdrop,
                            visuals = liquidGlassPopupVisuals(),
                            summary = when (webdavSyncMode.value) {
                                WebDavGallerySync.MODE_UPLOAD_ONLY -> "只上传本地新增画廊，不从云端拉取"
                                WebDavGallerySync.MODE_DOWNLOAD_ONLY -> "只拉取云端新增画廊，不上传本地文件"
                                else -> "本地新增上传、云端新增拉取并自动入库"
                            },
                            items = syncModeItems,
                            selectedIndex = webdavSyncMode.value.coerceIn(0, syncModeItems.lastIndex),
                            showValue = true,
                            onSelectedIndexChange = { webdavSyncMode.value = it },
                        )
                        val idleSummary = if (Settings.webdavLastSyncAt > 0) {
                            val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(Settings.webdavLastSyncAt))
                            "上次同步：$time · 已同步 ${Settings.webdavLastSyncCount} 个画廊"
                        } else {
                            "手动检查并增量同步本地与云端画廊"
                        }
                        val syncSummary = if (webdavSyncState.isSyncing) {
                            if (webdavSyncState.total > 0) {
                                "正在同步画廊 (${webdavSyncState.current}/${webdavSyncState.total})..."
                            } else {
                                "正在同步画廊..."
                            }
                        } else {
                            idleSummary
                        }
                        BasicComponent(
                            title = syncTitle,
                            summary = syncSummary,
                            onClick = {
                                if (webdavSyncState.isSyncing) return@BasicComponent
                                if (isMeteredNetwork(context)) {
                                    showMeteredConfirm = true
                                } else {
                                    WebDavSyncService.start(context)
                                }
                            },
                            endActions = {
                                if (webdavSyncState.isSyncing) {
                                    TextButton(
                                        text = "停止",
                                        onClick = { WebDavSyncService.stop(context) },
                                    )
                                }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.backup_webdav_auto),
                            summary = "仅在连接 Wi-Fi 且电量充足时，每 12 小时在后台自动增量同步一次（无新增画廊时自动休眠）",
                            state = webdavAutoSync,
                        )
                        if (showMeteredConfirm) {
                            LiquidGlassDialog(
                                show = true,
                                title = "移动数据网络",
                                onDismissRequest = { showMeteredConfirm = false },
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "当前正处于移动数据网络，同步已下载画廊可能消耗较多流量，是否继续？",
                                        modifier = Modifier.padding(bottom = 16.dp),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        TextButton(
                                            text = "取消",
                                            onClick = { showMeteredConfirm = false },
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = "继续同步",
                                            onClick = {
                                                showMeteredConfirm = false
                                                WebDavSyncService.start(context)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.textButtonColorsPrimary(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    var hidden by remember { mutableStateOf(password) }
    val fieldState = rememberTextFieldState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { currentOnValueChange(it) }
    }
    TextField(
        state = fieldState,
        label = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        outputTransformation = if (password && hidden) {
            OutputTransformation { replace(0, length, "\u2022".repeat(length)) }
        } else {
            null
        },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { hidden = !hidden }) {
                    Icon(
                        imageVector = if (hidden) MiuixIcons.Show else MiuixIcons.Hide,
                        contentDescription = null,
                    )
                }
            }
        } else {
            null
        },
    )
}

private fun writeText(context: android.content.Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        ?: throw IllegalStateException("cannot open output stream")
}

private fun readText(context: android.content.Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: throw IllegalStateException("cannot open input stream")
