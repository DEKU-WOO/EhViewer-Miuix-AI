package com.hippo.ehviewer.sync

import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 历史与阅读进度的准实时同步调度器。
 *
 * 上传：点开画廊产生新历史时立即上报；阅读器翻页只保留最后一次并延迟 2 秒，
 * 避免快速翻页时产生大量请求（App 被强杀时最多丢失最后 2 秒的页码）。
 * 拉取：仅在 App 回前台、进入历史页、历史页下拉刷新时主动调用，绝不后台常驻轮询。
 */
object VpsHistorySyncManager {
    private const val DEBOUNCE_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debounceJobs = ConcurrentHashMap<Long, Job>()
    private val pageUpdates = MutableSharedFlow<PageUpdate>(extraBufferCapacity = 128)
    private val refreshMutex = Mutex()

    private data class PageUpdate(val gid: Long, val page: Int)

    init {
        scope.launch {
            pageUpdates.collect { update ->
                debounceJobs.remove(update.gid)?.cancel()
                debounceJobs[update.gid] = scope.launch {
                    delay(DEBOUNCE_MS)
                    debounceJobs.remove(update.gid)
                    runCatching { EhDB.putReadProgress(update.gid, update.page) }
                    pushOne(update.gid)
                }
            }
        }
    }

    fun enabled(): Boolean =
        Settings.vpsHistorySyncEnabled.value && Settings.vpsServerUrl.value.isNotBlank()

    /** 本地新增历史记录（点开画廊）时调用。 */
    fun onHistoryAdded(gid: Long) {
        if (!enabled()) return
        scope.launch { pushOne(gid) }
    }

    /** 阅读器翻页时调用，2 秒防抖后写入本地进度并上传。 */
    fun onReaderPageChanged(gid: Long, page: Int) {
        if (!enabled()) return
        pageUpdates.tryEmit(PageUpdate(gid, page))
    }

    private suspend fun pushOne(gid: Long) {
        if (!enabled()) return
        val entity = runCatching { EhDB.getGalleryInfo(gid) }.getOrNull() ?: return
        val page = runCatching { EhDB.getReadProgress(gid) }.getOrDefault(0)
        val entry = SyncHistoryEntry(
            gid = gid,
            token = entity.token,
            pages = entity.pages,
            lastPage = page,
            updatedAt = System.currentTimeMillis(),
        )
        runCatching { VpsClient.postHistory(entry) }
            .onFailure { logcat("VpsHistory", it) }
    }

    /** 增量拉取云端历史并按需补全元数据；调用点仅限前台恢复/进入历史页/下拉刷新。 */
    suspend fun refreshFromServer() {
        if (!enabled()) return
        refreshMutex.withLock {
            val since = Settings.vpsLastSyncAt
            val entries = runCatching { VpsClient.fetchHistory(since) }
                .onFailure { logcat("VpsHistory", it) }
                .getOrNull()
                ?: return@withLock
            // 先批量补全缺失元数据，再由现有 AI 翻译规则接管中文标题翻译
            runCatching { EhDB.fillSyncedMetadata(entries) }
                .onFailure { logcat("VpsHistory", it) }
            var maxUpdatedAt = since
            entries.forEach { entry ->
                runCatching { EhDB.applySyncedHistory(entry.gid, entry.token, entry.lastPage, entry.updatedAt) }
                    .onFailure { logcat("VpsHistory", it) }
                if (entry.updatedAt > maxUpdatedAt) maxUpdatedAt = entry.updatedAt
            }
            // 空结果也推进时间戳，避免每次进入历史页都重复拉取同一区间
            Settings.vpsLastSyncAt = if (entries.isEmpty()) System.currentTimeMillis() else maxUpdatedAt
            // 记录本机实际完成同步的时间，用于设置页展示「上次同步」摘要
            Settings.vpsLastHistorySyncAt.value = System.currentTimeMillis()
        }
    }
}
