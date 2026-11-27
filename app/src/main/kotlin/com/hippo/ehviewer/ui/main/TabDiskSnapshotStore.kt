package com.hippo.ehviewer.ui.main

import com.ehviewer.core.model.BaseGalleryInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import splitties.init.appCtx

@Serializable
data class PersistedGallerySnapshotItem(
    val gid: Long,
    val token: String,
    val title: String? = null,
    val titleJpn: String? = null,
    val thumbKey: String? = null,
    val category: Int = 0,
    val posted: String? = null,
    val uploader: String? = null,
    val rating: Float = 0f,
    val simpleTags: List<String>? = null,
    val pages: Int = 0,
    val simpleLanguage: String? = null,
    val favoriteSlot: Int = -2,
    val disowned: Boolean = false,
    val rated: Boolean = false,
    val thumbWidth: Int = 0,
    val thumbHeight: Int = 0,
    val favoriteName: String? = null,
    val favoriteNote: String? = null,
) {
    fun toBaseGalleryInfo(): BaseGalleryInfo = BaseGalleryInfo(
        gid = gid,
        token = token,
        title = title,
        titleJpn = titleJpn,
        thumbKey = thumbKey,
        category = category,
        posted = posted,
        uploader = uploader,
        disowned = disowned,
        rating = rating,
        rated = rated,
        simpleTags = simpleTags,
        pages = pages,
        thumbWidth = thumbWidth,
        thumbHeight = thumbHeight,
        simpleLanguage = simpleLanguage,
        favoriteSlot = favoriteSlot,
        favoriteName = favoriteName,
        favoriteNote = favoriteNote,
    )
}

fun BaseGalleryInfo.toSnapshotItem(): PersistedGallerySnapshotItem = PersistedGallerySnapshotItem(
    gid = gid,
    token = token,
    title = title,
    titleJpn = titleJpn,
    thumbKey = thumbKey,
    category = category,
    posted = posted,
    uploader = uploader,
    rating = rating,
    simpleTags = simpleTags,
    pages = pages,
    simpleLanguage = simpleLanguage,
    favoriteSlot = favoriteSlot,
    disowned = disowned,
    rated = rated,
    thumbWidth = thumbWidth,
    thumbHeight = thumbHeight,
    favoriteName = favoriteName,
    favoriteNote = favoriteNote,
)

@Serializable
data class PersistedTabSeedPayload(
    val urlSignature: String,
    val prevKey: String? = null,
    val nextKey: String? = null,
    val items: List<PersistedGallerySnapshotItem>,
    val savedAtMillis: Long = 0L,
)

data class SeedPage(
    val urlSignature: String,
    val prevKey: String?,
    val nextKey: String?,
    val items: List<BaseGalleryInfo>,
)

/**
 * 半永久本地磁盘快照池（每个网络 Tab 定长覆写单文件，仅存最近一次第 1 页元数据与分页游标）。
 *
 * - 冷启动时同步/预热读取为 [SeedPage]，实现零转圈、零请求秒开上次离开时的列表；
 * - 读取全量包裹 `runCatching`，损坏或版本不兼容时静默删除并回退网络拉取，绝不闪退；
 * - 切换表里站（E-Hentai / ExHentai）或退出账号时调用 [clearAll] 清空全部本地快照。
 */
object TabDiskSnapshotStore {
    private const val DIR_NAME = "tab_page_snapshots_v1"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _invalidateEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val invalidateEvents = _invalidateEvents.asSharedFlow()

    private val snapshotDir: File
        get() = File(appCtx.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    private fun slotFile(slotKey: String): File = File(snapshotDir, "$slotKey.json")

    fun readSnapshot(slotKey: String): SeedPage? = runCatching {
        val file = slotFile(slotKey)
        if (!file.isFile || file.length() <= 0L) return null
        val text = file.readText(Charsets.UTF_8)
        val payload = json.decodeFromString<PersistedTabSeedPayload>(text)
        if (payload.items.isEmpty()) return null
        SeedPage(
            urlSignature = payload.urlSignature,
            prevKey = payload.prevKey,
            nextKey = payload.nextKey,
            items = payload.items.map { it.toBaseGalleryInfo() },
        )
    }.onFailure {
        runCatching { slotFile(slotKey).delete() }
    }.getOrNull()

    fun writeSnapshotAsync(slotKey: String, seed: SeedPage) {
        if (seed.items.isEmpty()) return
        val payload = PersistedTabSeedPayload(
            urlSignature = seed.urlSignature,
            prevKey = seed.prevKey,
            nextKey = seed.nextKey,
            items = seed.items.map { it.toSnapshotItem() },
            savedAtMillis = System.currentTimeMillis(),
        )
        ioScope.launch {
            writePayloadSync(slotKey, payload)
        }
    }

    private fun writePayloadSync(slotKey: String, payload: PersistedTabSeedPayload) {
        runCatching {
            val dir = snapshotDir
            val target = File(dir, "$slotKey.json")
            val tmp = File(dir, "$slotKey.json.tmp")
            tmp.writeText(json.encodeToString(payload), Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun hasSnapshot(slotKey: String): Boolean = runCatching {
        val file = slotFile(slotKey)
        file.isFile && file.length() > 2L
    }.getOrDefault(false)

    fun clearAll() {
        ioScope.launch {
            runCatching {
                snapshotDir.listFiles()?.forEach { it.delete() }
            }
        }
        _invalidateEvents.tryEmit(Unit)
    }
}
