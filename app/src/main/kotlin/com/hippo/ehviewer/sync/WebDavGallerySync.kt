package com.hippo.ehviewer.sync

import android.os.ParcelFileDescriptor
import com.ehviewer.core.files.find
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.isFile
import com.ehviewer.core.files.list
import com.ehviewer.core.files.mkdirs
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
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
import okio.Path
import okio.Path.Companion.toOkioPath
import splitties.init.appCtx

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
        var uploaded = 0
        var downloaded = 0
        var failed = 0

        // 1. 上传本地新增画廊文件夹
        val downloads = runCatching { DownloadManager.downloadInfoList.toList() }.getOrDefault(emptyList())
        downloads.forEach { info ->
            val dirname = info.dirname ?: return@forEach
            localNames += dirname
            info.archiveFile?.let { localNames += it.name }
            if (remoteByName.containsKey(dirname)) return@forEach
            val dir = info.downloadDir ?: return@forEach
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
        val topArchives = localRoot.list().filter { it.isFile && it.name.isArchive() }
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
            val target = localRoot / entry.name
            runCatching {
                if (entry.isDirectory) {
                    downloadDirectory(entry.url, target)
                    check(restoreDownload(target)) { "无法解析已拉回画廊的元数据：${entry.name}" }
                    downloaded++
                } else if (entry.name.isArchive()) {
                    val dirname = entry.name.substringBeforeLast('.', entry.name)
                    val targetDir = localRoot / dirname
                    targetDir.mkdirs()
                    WebDavClient.download(entry.url, targetDir / entry.name)
                    check(restoreDownload(targetDir)) { "无法解析已拉回压缩包的元数据：${entry.name}" }
                    downloaded++
                }
                localNames += entry.name
            }.onFailure {
                failed++
                logcat("WebDavSync", it)
            }
        }

        Settings.webdavLastSyncAt = System.currentTimeMillis()
        WebDavSyncResult(uploaded = uploaded, downloaded = downloaded, failed = failed)
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
                WebDavClient.download(entry.url, child)
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
