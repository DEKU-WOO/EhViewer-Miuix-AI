package com.hippo.ehviewer.ui.settings

import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.BlurredBar
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.util.readableWidth
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val SETTINGS_MIME = "application/json"

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.BackupRestoreScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val backdrop = rememberBlurBackdrop()
    val context = LocalContext.current

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    // LauncherPreference 的回调运行在 IO 线程，Toast 需要切回主线程
    fun postToast(message: String) = mainHandler.post { toast(message) }

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
    val webdavSyncFailed = stringResource(R.string.backup_webdav_sync_failed)
    val webdavSyncDone = stringResource(R.string.backup_webdav_sync_done)

    val serverUrl = Settings.vpsServerUrl.asMutableState()
    val accessToken = Settings.vpsToken.asMutableState()
    val webdavAutoSync = Settings.webdavAutoSync.asMutableState()
    val webdavSyncState by WebDavGallerySync.syncState.collectAsState()
    val hasSignedIn by Settings.hasSignedIn.collectAsState()

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
            WindowDialog(
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
                                        Spacer(modifier = Modifier.height(0.dp))
                                        Text(
                                            text = stringResource(R.string.backup_cloud_this_device),
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = colorScheme.onPrimary,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colorScheme.primary)
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
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
            WindowDialog(
                show = true,
                title = stringResource(R.string.backup_cloud_restore),
                onDismissRequest = { cont.resume(null) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    if (!isLocal) {
                        Text(
                            text = stringResource(R.string.backup_cloud_cross_device_warning),
                            color = Color(0xFFE53935),
                            style = MiuixTheme.textStyles.footnote1,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
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
        } ?: return@launch

        val raw = chosen.settingsJson
        val error = withIOContext {
            runCatching {
                if (option == 0) {
                    if (!SettingsBackup.restoreAll(raw)) throw IllegalStateException("invalid backup")
                } else {
                    SettingsBackup.restoreApiProfiles(raw)
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
                // 悬浮顶栏下方留出安全间距，避免首个分组标题被返回箭头遮挡
                Spacer(modifier = Modifier.height(12.dp))
                // 分组一：本地备份
                SmallTitle(text = stringResource(id = R.string.backup_local_title))
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
                SmallTitle(text = stringResource(id = R.string.backup_cloud_title))
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
                        SwitchPreference(
                            title = stringResource(R.string.backup_cloud_history_switch),
                            summary = stringResource(R.string.backup_cloud_history_switch_summary),
                            state = Settings.vpsHistorySyncEnabled.asMutableState(),
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
                SmallTitle(text = stringResource(id = R.string.backup_webdav_title))
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
                        val syncProgress = if (webdavSyncState.total > 0) {
                            "同步中 ${webdavSyncState.current}/${webdavSyncState.total}..."
                        } else {
                            "同步中..."
                        }
                        BasicComponent(
                            title = if (webdavSyncState.isSyncing) "$syncTitle（$syncProgress）" else syncTitle,
                            summary = webdavSyncState.stage.takeIf { webdavSyncState.isSyncing && it.isNotBlank() },
                            enabled = !webdavSyncState.isSyncing,
                            onClick = {
                                WebDavGallerySync.syncAsync { result ->
                                    result.fold(
                                        onSuccess = { toast(webdavSyncDone) },
                                        onFailure = { toast(webdavSyncFailed.format(it.message.orEmpty())) },
                                    )
                                }
                            },
                            endActions = {
                                if (webdavSyncState.isSyncing) {
                                    InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                                }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.backup_webdav_auto),
                            summary = stringResource(R.string.backup_webdav_auto_summary),
                            state = webdavAutoSync,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
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
