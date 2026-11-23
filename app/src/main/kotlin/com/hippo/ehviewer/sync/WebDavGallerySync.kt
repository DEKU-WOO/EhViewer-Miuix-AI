package com.hippo.ehviewer.sync

import android.os.ParcelFileDescriptor
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.exists
import com.ehviewer.core.files.find
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.isFile
import com.ehviewer.core.files.list
import com.ehviewer.core.files.metadataOrNull
import com.ehviewer.core.files.mkdirs
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.download.downloadDir
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.spider.COMIC_INFO_FILE
import com.hippo.ehviewer.spider.ComicInfo
import com.hippo.ehviewer.spider.SpiderQueen.Companion.SPIDER_INFO_FILENAME
import com.hippo.ehviewer.spider.readComicInfo
import com.hippo.ehviewer.spider.readCompatFromPath
import com.hippo.ehviewer.spider.toSimpleTags
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import okio.Path
import okio.Path.Companion.toOkioPath
import splitties.init.appCtx

data class WebDavSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val failed: Int,
    /** 本次同步中遇到的第一条具体失败原因，便于在系统通知中直接定位问题。 */
    val failureReason: String? = null,
) {
    val success: Int get() = uploaded + downloaded
}

data class WebDavSyncState(
    val isSyncing: Boolean = false,
    val stage: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val title: String = "",
)

/**
 * 已下载画廊的 WebDAV 增量双向同步。
 *
 * 以本地下载目录为基准：远端已有同名画廊则跳过上传，本地缺失的远端画廊再拉回本地。
 * 目录内文件逐个流式上传/下载，不会一次性读入内存。
 */
object WebDavGallerySync {
    const val MODE_BOTH = 0
    const val MODE_UPLOAD_ONLY = 1
    const val MODE_DOWNLOAD_ONLY = 2

    private val mutableSyncState = MutableStateFlow(WebDavSyncState())
    val syncState = mutableSyncState.asStateFlow()

    private val syncMutex = Mutex()

    /**
     * 执行一次同步。已有同步在运行时返回 null，避免手动与自动任务重复传输。
     *
     * 调用方应位于前台服务或 WorkManager 中，保证进程不被系统回收。
     */
    suspend fun sync(): WebDavSyncResult? = withIOContext {
        if (!syncMutex.tryLock()) {
            logcat("WebDavSync") { "已有同步任务在运行，跳过本次请求" }
            return@withIOContext null
        }
        try {
            syncLocked()
        } finally {
            mutableSyncState.value = WebDavSyncState()
            syncMutex.unlock()
        }
    }

    private fun updateProgress(stage: String, current: Int, total: Int, title: String = "") {
        mutableSyncState.value = WebDavSyncState(
            isSyncing = true,
            stage = stage,
            current = current,
            total = total,
            title = title,
        )
    }

    private suspend fun syncLocked(): WebDavSyncResult {
        updateProgress("正在扫描", 0, 0)
        if (!WebDavClient.isConfigured()) {
            throw WebDavClient.WebDavException("未配置 WebDAV 地址")
        }
        val localRoot = runCatching { downloadLocation }.getOrNull()
            ?: throw WebDavClient.WebDavException("无法访问本地下载目录")
        if (!localRoot.isDirectory) {
            throw WebDavClient.WebDavException("本地下载目录不存在")
        }
        val rootUrl = WebDavClient.rootUrl()
        WebDavClient.ensureDirectory(rootUrl)

        val remoteEntries = WebDavClient.list(rootUrl)
        val remoteByName = remoteEntries.associateBy { it.name }
        val localNames = mutableSetOf<String>()
        val downloads = runCatching { DownloadManager.downloadInfoList.toList() }.getOrDefault(emptyList())
        val topArchives = localRoot.list().filter { it.isFile && it.name.isArchive() }
        downloads.forEach { info ->
            info.dirname?.let { localNames += it }
            info.archiveFile?.let { localNames += it.name }
        }
        topArchives.forEach { localNames += it.name }

        val mode = Settings.webdavSyncMode.value
        val allowUpload = mode != MODE_DOWNLOAD_ONLY
        val allowDownload = mode != MODE_UPLOAD_ONLY

        val total = run {
            val uploadTotal = if (allowUpload) {
                downloads.count { info ->
                    val dirname = info.dirname
                    dirname != null && !remoteByName.containsKey(dirname) && info.downloadDir?.isDirectory == true
                } + topArchives.count { !remoteByName.containsKey(it.name) }
            } else {
                0
            }
            val downloadTotal = if (allowDownload) {
                remoteEntries.count { entry ->
                    entry.name !in localNames && (entry.isDirectory || entry.name.isArchive())
                }
            } else {
                0
            }
            uploadTotal + downloadTotal
        }
        var current = 0
        var uploaded = 0
        var downloaded = 0
        var failed = 0
        var firstFailure: String? = null

        fun recordFailure(cause: Throwable) {
            if (firstFailure == null) {
                firstFailure = cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
            }
        }

        // 1. 上传本地新增画廊文件夹
        if (allowUpload) {
            downloads.forEach { info ->
                val dirname = info.dirname ?: return@forEach
                if (remoteByName.containsKey(dirname)) return@forEach
                val dir = info.downloadDir ?: return@forEach
                if (!dir.isDirectory) return@forEach
                val title = runCatching { EhUtils.getSuitableTitle(info) }.getOrDefault(dirname)
                updateProgress("正在上传", current, total, title)
                try {
                    uploadDirectory(dir, "$rootUrl/${WebDavClient.encodeSegment(dirname)}")
                    uploaded++
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    failed++
                    recordFailure(t)
                    logcat("WebDavSync", t)
                }
                current++
                updateProgress("正在上传", current, total)
                currentCoroutineContext().ensureActive()
            }

            // 2. 上传下载目录顶层的 .cbz / .zip 画廊文件
            topArchives.forEach { file ->
                if (remoteByName.containsKey(file.name)) return@forEach
                updateProgress("正在上传", current, total, file.name)
                try {
                    WebDavClient.upload(file, "$rootUrl/${WebDavClient.encodeSegment(file.name)}")
                    uploaded++
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    failed++
                    recordFailure(t)
                    logcat("WebDavSync", t)
                }
                current++
                updateProgress("正在上传", current, total)
                currentCoroutineContext().ensureActive()
            }
        }

        // 3. 拉回本地缺失的远端画廊：只有传输阶段失败/取消才回滚删除残缺目录
        if (allowDownload) {
            remoteEntries.forEach { entry ->
                if (entry.name in localNames) return@forEach
                if (!entry.isDirectory && !entry.name.isArchive()) return@forEach

                val isArchive = !entry.isDirectory
                val dirname = if (isArchive) entry.name.substringBeforeLast('.', entry.name) else entry.name
                val targetDir = localRoot / dirname
                updateProgress("正在下载", current, total, entry.name)
                var transferDone = false
                try {
                    check(dirname.isNotBlank()) { "非法远端条目名称：${entry.name}" }
                    if (targetDir.exists()) targetDir.delete()
                    targetDir.mkdirs()
                    if (isArchive) {
                        WebDavClient.download(entry.url, targetDir / entry.name, entry.contentLength)
                    } else {
                        downloadDirectory(entry.url, targetDir)
                    }
                    verifyPulledDirectory(targetDir)
                    transferDone = true
                } catch (e: CancellationException) {
                    runCatching { targetDir.delete() }
                    logcat("WebDavSync") { "同步被取消，已清理半成品目录：$targetDir" }
                    throw e
                } catch (t: Throwable) {
                    failed++
                    recordFailure(t)
                    logcat("WebDavSync", t)
                    runCatching { targetDir.delete() }.onFailure { logcat("WebDavSync", it) }
                }

                // 文件已完整落盘：后续元数据解析/入库异常只记录并降级入库，严禁回滚删除
                if (transferDone) {
                    val registered = runCatching { restoreDownload(targetDir) }
                        .onFailure {
                            logcat("WebDavSync", it)
                            recordFailure(it)
                        }
                        .getOrDefault(false)
                    val fallback = registered || runCatching { registerPulledDirectory(targetDir) }
                        .onFailure {
                            logcat("WebDavSync", it)
                            recordFailure(it)
                        }
                        .getOrDefault(false)
                    if (fallback) {
                        downloaded++
                        localNames += entry.name
                    } else {
                        failed++
                    }
                }
                current++
                updateProgress("正在下载", current, total)
                currentCoroutineContext().ensureActive()
            }
        }

        Settings.webdavLastSyncAt = System.currentTimeMillis()
        Settings.webdavLastSyncCount = uploaded + downloaded
        return WebDavSyncResult(
            uploaded = uploaded,
            downloaded = downloaded,
            failed = failed,
            failureReason = firstFailure,
        )
    }

    /** 目录内所有文件都必须存在且大小大于 0，才算完整拉回。 */
    private fun verifyPulledDirectory(target: Path) {
        val files = mutableListOf<Path>()
        fun collect(dir: Path) {
            dir.list().forEach { child ->
                when {
                    child.isDirectory -> collect(child)
                    // 点开头的标记文件（如 .ehviewer / .nomedia）允许为空
                    child.isFile && !child.name.startsWith(".") -> files += child
                }
            }
        }
        collect(target)
        check(files.isNotEmpty()) { "拉回目录中没有任何文件：${target.name}" }
        files.forEach { file ->
            // 优先用 SAF 元数据；若缓存尚未刷新返回 null/0，则回退到底层文件描述符读取真实字节数
            val size = file.metadataOrNull()?.size?.takeIf { it > 0L }
                ?: runCatching { file.openFileDescriptor("r").use { it.statSize } }.getOrDefault(0L)
            check(size > 0L) { "拉回文件大小异常：${file.name}（$size 字节）" }
        }
    }

    /**
     * 元数据解析失败时的兜底入库：直接用文件夹名作为标题，保证已完整下载的画廊
     * 仍能出现在下载列表，而不是被当作失败回滚。
     */
    private suspend fun registerPulledDirectory(target: Path): Boolean {
        val files = target.list()
        val gid = target.name.parseGid()
            ?: files.firstOrNull { it.isFile && it.name.isArchive() }?.name?.parseGid()
            ?: return false
        val cachedInfo = runCatching { EhDB.getGalleryInfo(gid) }.getOrNull()
        val pages = cachedInfo?.pages?.takeIf { it > 0 }
            ?: files.count { it.isFile && it.name.isImage() }
        val galleryInfo = BaseGalleryInfo(
            gid = gid,
            token = cachedInfo?.token.orEmpty(),
            title = cachedInfo?.title ?: target.name,
            titleJpn = cachedInfo?.titleJpn,
            thumbKey = cachedInfo?.thumbKey,
            rating = cachedInfo?.rating ?: 0f,
            simpleTags = cachedInfo?.simpleTags,
            pages = pages,
            simpleLanguage = cachedInfo?.simpleLanguage,
        )
        EhDB.putDownloadDirname(gid, target.name)
        DownloadManager.restoreDownload(galleryInfo, target.name)
        return true
    }

    private fun uploadDirectory(local: Path, remoteUrl: String) {
        WebDavClient.ensureDirectory(remoteUrl)
        local.list().sortedBy { it.name }.forEach { child ->
            if (child.name.startsWith(".")) return@forEach
            val childUrl = "$remoteUrl/${WebDavClient.encodeSegment(child.name)}"
            when {
                child.isDirectory -> uploadDirectory(child, childUrl)
                child.isFile -> WebDavClient.upload(child, childUrl)
            }
        }
    }

    private fun downloadDirectory(remoteUrl: String, target: Path) {
        target.mkdirs()
        WebDavClient.list(remoteUrl).forEach { entry ->
            val child = target / entry.name
            if (entry.isDirectory) {
                downloadDirectory(entry.url, child)
            } else {
                WebDavClient.download(entry.url, child, entry.contentLength)
            }
        }
    }

    private suspend fun restoreDownload(target: Path): Boolean {
        val spiderInfo = target.find(SPIDER_INFO_FILENAME)?.let(::readCompatFromPath)
        val archive = target.list().firstOrNull { it.isFile && it.name.isArchive() }
        val archiveMetadata = archive?.let { file ->
            runCatching { readArchiveMetadata(file) }
                .onFailure { logcat("WebDavSync", it) }
                .getOrNull()
        }
        val comicInfo = target.find(COMIC_INFO_FILE)?.let(::readComicInfo) ?: archiveMetadata?.comicInfo
        val parsedUrl = GalleryDetailUrlParser.parse(comicInfo?.web)
        val gid = spiderInfo?.gid ?: parsedUrl?.gid ?: archive?.name?.parseGid() ?: target.name.parseGid() ?: return false
        val cachedInfo = EhDB.getGalleryInfo(gid)
        val token = spiderInfo?.token?.takeIf(String::isNotBlank)
            ?: parsedUrl?.token
            ?: cachedInfo?.token.orEmpty()
        val localPages = target.list().count { it.isFile && it.name.isImage() }
        val pages = spiderInfo?.pages?.takeIf { it > 0 }
            ?: comicInfo?.pageCount?.takeIf { it > 0 }
            ?: archiveMetadata?.pageCount?.takeIf { it > 0 }
            ?: localPages.takeIf { it > 0 }
            ?: cachedInfo?.pages
            ?: 0
        val galleryInfo = BaseGalleryInfo(
            gid = gid,
            token = token,
            title = comicInfo?.series ?: cachedInfo?.title ?: target.name,
            titleJpn = comicInfo?.alternateSeries ?: cachedInfo?.titleJpn,
            thumbKey = cachedInfo?.thumbKey,
            rating = comicInfo?.communityRating?.toFloatOrNull() ?: cachedInfo?.rating ?: 0f,
            simpleTags = comicInfo?.toSimpleTags() ?: cachedInfo?.simpleTags,
            pages = pages,
            simpleLanguage = comicInfo?.languageISO ?: cachedInfo?.simpleLanguage,
        )
        if (token.isNotBlank()) {
            runCatching { EhEngine.fillGalleryListByApi(listOf(galleryInfo), EhUrl.referer) }
                .onFailure { logcat("WebDavSync", it) }
        }
        EhDB.putDownloadDirname(gid, target.name)
        DownloadManager.restoreDownload(galleryInfo, target.name)
        return true
    }

    private fun readArchiveMetadata(file: Path): ArchiveMetadata {
        val tempFile = File.createTempFile("webdav-comic-info-", ".xml", appCtx.cacheDir)
        var comicInfo: ComicInfo? = null
        var pageCount = 0
        try {
            ParcelFileDescriptor.AutoCloseInputStream(file.openFileDescriptor("r")).use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/')
                            when {
                                name.equals(COMIC_INFO_FILE, ignoreCase = true) -> {
                                    tempFile.outputStream().use { output -> zip.copyTo(output) }
                                    comicInfo = readComicInfo(tempFile.toOkioPath())
                                }
                                name.isImage() -> pageCount++
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        } finally {
            tempFile.delete()
        }
        return ArchiveMetadata(comicInfo, pageCount)
    }

    private data class ArchiveMetadata(val comicInfo: ComicInfo?, val pageCount: Int)

    private fun String.parseGid(): Long? = substringBefore('.').substringBefore('-').toLongOrNull()

    private fun String.isImage(): Boolean = when (substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heif", "heic" -> true
        else -> false
    }

    private fun String.isArchive(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".cbz") || lower.endsWith(".zip")
    }
}
