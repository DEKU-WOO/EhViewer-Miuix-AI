package com.hippo.ehviewer.util

import androidx.room.execSQL
import androidx.room.useWriterConnection
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.files.exists
import com.ehviewer.core.util.mapToLongArray
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.download.downloadDir
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

data class CleanResult(
    val deadDownloadsCount: Int,
    val vacuumSuccess: Boolean,
)

object StorageCleaner {

    /** 防止清理过程中重复点击导致并发扫描/删除。 */
    private val deepCleanMutex = Mutex()

    fun isDeadDownload(
        info: DownloadInfo,
        dirExists: (DownloadInfo) -> Boolean = { it.downloadDir?.exists() == true },
        archiveExists: (DownloadInfo) -> Boolean = { it.archiveFile?.exists() == true },
    ): Boolean {
        if (info.dirname.isNullOrBlank()) return true
        val hasDir = runCatching { dirExists(info) }.getOrDefault(false)
        val hasArchive = runCatching { archiveExists(info) }.getOrDefault(false)
        return !hasDir && !hasArchive
    }

    fun filterDeadDownloads(
        downloads: List<DownloadInfo>,
        dirExists: (DownloadInfo) -> Boolean = { it.downloadDir?.exists() == true },
        archiveExists: (DownloadInfo) -> Boolean = { it.archiveFile?.exists() == true },
    ): List<DownloadInfo> = downloads.filter { isDeadDownload(it, dirExists, archiveExists) }

    suspend fun cleanCache() = withIOContext {
        runCatching { EhApplication.thumbCache.clear() }
        safeDeleteContent(AppConfig.tempDir)
        AppConfig.externalTempDir?.let(::safeDeleteContent)
        AppConfig.externalTempPersistDir?.let(::safeDeleteContent)
        runCatching { FileUtils.cleanupDirectory(AppConfig.externalCrashDir) }
        runCatching { FileUtils.cleanupDirectory(AppConfig.externalParseErrorDir) }
    }

    /**
     * 逐个删除目录内容，单个文件被占用/无权限时只跳过该文件，
     * 不会中断整个清理流程，也不会把异常抛回界面导致崩溃。
     */
    private fun safeDeleteContent(path: Path) {
        val dir = runCatching { path.toFile() }.getOrNull() ?: return
        dir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }

    suspend fun cleanDeadDownloads(): Int = withIOContext {
        val allDownloads = runCatching { DownloadManager.downloadInfoList.toList() }.getOrDefault(emptyList())
        val dead = runCatching { filterDeadDownloads(allDownloads) }.getOrDefault(emptyList())
        if (dead.isNotEmpty()) {
            val gids = dead.mapToLongArray { it.gid }
            runCatching { DownloadManager.deleteRangeDownload(gids) }
            dead.forEach { info ->
                runCatching { EhDB.removeDownloadDirname(info.gid) }
            }
        }
        dead.size
    }

    suspend fun vacuumDatabases(): Boolean = withIOContext {
        val r1 = runCatching { EhDB.vacuumDB() }.isSuccess
        val r2 = runCatching {
            EhApplication.searchDatabase.useWriterConnection { conn ->
                conn.execSQL("PRAGMA wal_checkpoint(FULL)")
                conn.execSQL("VACUUM")
            }
        }.isSuccess
        // 两个数据库都必须整理成功才算成功，否则其中一个的失败会被另一个掩盖
        r1 && r2
    }

    suspend fun performDeepClean(): CleanResult = deepCleanMutex.withLock {
        withIOContext {
            cleanCache()
            val deadCount = cleanDeadDownloads()
            val vacuumOk = vacuumDatabases()
            CleanResult(deadDownloadsCount = deadCount, vacuumSuccess = vacuumOk)
        }
    }
}
