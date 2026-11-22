package com.hippo.ehviewer.sync

import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.downloadDir
import com.hippo.ehviewer.download.downloadLocation
import java.io.File

data class WebDavSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val failed: Int,
)

/**
 * 已下载画廊的 WebDAV 增量双向同步。
 *
 * 以本地下载目录为基准：远端已有同名画廊则跳过上传，本地缺失的远端画廊再拉回本地。
 * 目录内文件逐个流式上传/下载，不会一次性读入内存。
 */
object WebDavGallerySync {
    suspend fun sync(): WebDavSyncResult = withIOContext {
        if (!WebDavClient.isConfigured()) {
            throw WebDavClient.WebDavException("未配置 WebDAV 地址")
        }
        val localRoot = runCatching { downloadLocation.toFile() }.getOrNull()
            ?: throw WebDavClient.WebDavException("无法访问本地下载目录")
        if (!localRoot.isDirectory) {
            throw WebDavClient.WebDavException("本地下载目录不存在")
        }
        val rootUrl = WebDavClient.rootUrl()
        runCatching { WebDavClient.ensureDirectory(rootUrl) }

        val remoteEntries = runCatching { WebDavClient.list(rootUrl) }.getOrDefault(emptyList())
        val remoteByName = remoteEntries.associateBy { it.name }
        val localNames = mutableSetOf<String>()
        var uploaded = 0
        var downloaded = 0
        var failed = 0

        // 1. 上传本地新增画廊文件夹
        val downloads = runCatching { DownloadManager.downloadInfoList.toList() }.getOrDefault(emptyList())
        downloads.forEach { info ->
            val dirname = info.dirname ?: return@forEach
            localNames += dirname
            if (remoteByName.containsKey(dirname)) return@forEach
            val dir = runCatching { info.downloadDir?.toFile() }.getOrNull() ?: return@forEach
            if (!dir.isDirectory) return@forEach
            runCatching {
                uploadDirectory(dir, "$rootUrl/${WebDavClient.encodeSegment(dirname)}")
                uploaded++
            }.onFailure {
                failed++
                logcat("WebDavSync", it)
            }
        }

        // 2. 上传下载目录顶层的 .cbz / .zip 画廊文件
        val topArchives = localRoot.listFiles()
            ?.filter { it.isFile && it.name.isArchive() }
            .orEmpty()
        topArchives.forEach { file ->
            localNames += file.name
            if (remoteByName.containsKey(file.name)) return@forEach
            runCatching {
                WebDavClient.upload(file, "$rootUrl/${WebDavClient.encodeSegment(file.name)}")
                uploaded++
            }.onFailure {
                failed++
                logcat("WebDavSync", it)
            }
        }

        // 3. 拉回本地缺失的远端画廊
        remoteEntries.forEach { entry ->
            if (entry.name in localNames) return@forEach
            val target = File(localRoot, entry.name)
            runCatching {
                if (entry.isDirectory) {
                    downloadDirectory(entry.url, target)
                    downloaded++
                } else if (entry.name.isArchive()) {
                    WebDavClient.download(entry.url, target)
                    downloaded++
                }
            }.onFailure {
                failed++
                logcat("WebDavSync", it)
            }
        }

        Settings.webdavLastSyncAt = System.currentTimeMillis()
        WebDavSyncResult(uploaded = uploaded, downloaded = downloaded, failed = failed)
    }

    private fun uploadDirectory(local: File, remoteUrl: String) {
        WebDavClient.ensureDirectory(remoteUrl)
        local.listFiles()?.sortedBy { it.name }?.forEach { child ->
            if (child.name.startsWith(".")) return@forEach
            val childUrl = "$remoteUrl/${WebDavClient.encodeSegment(child.name)}"
            when {
                child.isDirectory -> uploadDirectory(child, childUrl)
                child.isFile -> WebDavClient.upload(child, childUrl)
            }
        }
    }

    private fun downloadDirectory(remoteUrl: String, target: File) {
        target.mkdirs()
        WebDavClient.list(remoteUrl).forEach { entry ->
            val child = File(target, entry.name)
            if (entry.isDirectory) {
                downloadDirectory(entry.url, child)
            } else {
                WebDavClient.download(entry.url, child)
            }
        }
    }

    private fun String.isArchive(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".cbz") || lower.endsWith(".zip")
    }
}
