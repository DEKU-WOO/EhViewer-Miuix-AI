/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.spider

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import arrow.fx.coroutines.parMap
import com.ehviewer.core.database.model.DownloadArtist
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.exists
import com.ehviewer.core.files.find
import com.ehviewer.core.files.isDirectory
import com.ehviewer.core.files.list
import com.ehviewer.core.files.mkdirs
import com.ehviewer.core.files.moveTo
import com.ehviewer.core.files.openFileDescriptor
import com.ehviewer.core.files.sendTo
import com.ehviewer.core.files.write
import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.util.LogPriority
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUtils.getSuitableTitle
import com.hippo.ehviewer.client.ehRequest
import com.hippo.ehviewer.client.getImageKey
import com.hippo.ehviewer.coil.read
import com.hippo.ehviewer.coil.suspendEdit
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.download.tempDownloadDir
import com.hippo.ehviewer.image.PathSource
import com.hippo.ehviewer.ktbuilder.diskCache
import com.hippo.ehviewer.util.AdsPlaceholderFile
import com.hippo.ehviewer.util.FileUtils
import com.hippo.ehviewer.util.copyTo
import com.hippo.ehviewer.util.sha1
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.read
import kotlin.concurrent.write
import okio.Path
import okio.Path.Companion.toOkioPath
import splitties.init.appCtx

class SpiderDen(val info: GalleryInfo) {
    private val gid = info.gid
    var downloadDir: Path? = null
        private set

    private var tempDownloadDir: Path? = null
    private val saveAsCbz = Settings.saveAsCbz.value
    private val archiveName = "$gid.cbz"

    private val lock = ReentrantReadWriteLock()

    // Search in both directories to maintain compatibility
    private val fileCache = mutableMapOf<String, Path>()
    private var fileCacheInitialized = false

    private fun ensureFileCacheInitialized() {
        if (!fileCacheInitialized) {
            lock.write {
                if (!fileCacheInitialized) {
                    listOfNotNull(tempDownloadDir, downloadDir).forEach { dir ->
                        runCatching { dir.list() }.getOrNull()?.forEach { path ->
                            fileCache[path.name] = path
                        }
                    }
                    fileCacheInitialized = true
                }
            }
        }
    }

    private fun reloadFileCache() = lock.write {
        fileCache.clear()
        listOfNotNull(tempDownloadDir, downloadDir).forEach { dir ->
            runCatching { dir.list() }.getOrNull()?.forEach { path ->
                fileCache[path.name] = path
            }
        }
        fileCacheInitialized = true
    }

    private val imageDir
        get() = tempDownloadDir.takeIf { saveAsCbz } ?: downloadDir

    constructor(info: GalleryInfo, dirname: String) : this(info) {
        downloadDir = downloadLocation / dirname
    }

    @Volatile
    @SpiderQueen.Mode
    var mode = SpiderQueen.MODE_READ
        private set

    suspend fun setMode(value: Int) {
        mode = value
        if (mode == SpiderQueen.MODE_DOWNLOAD) {
            if (downloadDir == null) {
                downloadDir = getGalleryDownloadDir(info).apply { mkdirs() }
            }
            if (saveAsCbz && tempDownloadDir == null) {
                tempDownloadDir = info.tempDownloadDir!!.apply { mkdirs() }
            }
            reloadFileCache()
        }
    }

    private fun containInCache(index: Int): Boolean {
        val key = getImageKey(gid, index)
        return sCache.read(key) {} != null
    }

    private fun findImageFile(index: Int, temp: Boolean = false): Path? {
        ensureFileCacheInitialized()
        return lock.read {
            val head = perFilename(index)
            fileCache.entries.firstOrNull { (name) -> name.startsWith(head) && temp == name.endsWith(TEMP_SUFFIX) }?.value
        }
    }

    private fun containInDownloadDir(index: Int): Boolean = findImageFile(index) != null

    private fun copyFromCacheToDownloadDir(index: Int): Boolean {
        val dir = imageDir ?: return false
        val key = getImageKey(gid, index)
        return runCatching {
            sCache.read(key) {
                val extension = metadata.toFile().readText()
                val file = dir.findDownloadFileForIndex(index, extension)
                data sendTo file
            } != null
        }.onFailure {
            logcat(it)
        }.getOrDefault(false)
    }

    operator fun contains(index: Int): Boolean = when (mode) {
        SpiderQueen.MODE_READ -> {
            containInCache(index) || containInDownloadDir(index)
        }
        SpiderQueen.MODE_DOWNLOAD -> {
            containInDownloadDir(index) || copyFromCacheToDownloadDir(index)
        }
        else -> {
            false
        }
    }

    private fun removeFromCache(index: Int): Boolean {
        val key = getImageKey(gid, index)
        return sCache.remove(key)
    }

    private fun removeTempFile(index: Int) = findImageFile(index, temp = true)?.let { file ->
        file.delete()
        lock.write { fileCache.remove(file.name) }
    }

    fun removeIntermediateFiles(index: Int) {
        removeFromCache(index)
        removeTempFile(index)
    }

    fun clearCache() {
        repeat(info.pages, ::removeFromCache)
    }

    private fun Path.findDownloadFileForIndex(index: Int, extension: String) = with(lock) {
        val name = perFilename(index, extension)
        read { fileCache[name] } ?: resolve(name).also { write { fileCache[name] = it } }
    }

    suspend fun makeHttpCallAndSaveImage(
        index: Int,
        url: String,
        referer: String?,
        notifyProgress: (Long, Long, Int) -> Unit,
    ) = timeoutBySpeed(
        url,
        { ehRequest(url, referer, builder = it) },
        notifyProgress,
        { resp -> check(saveFromHttpResponse(index, resp)) },
    )

    private suspend inline fun saveResponseMeta(
        index: Int,
        ext: String,
        crossinline fops: suspend (Path) -> Unit,
    ): Boolean {
        imageDir?.run {
            val tempFile = findDownloadFileForIndex(index, "$ext$TEMP_SUFFIX")
            fops(tempFile)
            val file = resolve(tempFile.name.removeSuffix(TEMP_SUFFIX))
            file.delete()
            lock.write { fileCache.remove(file.name) }
            tempFile moveTo file
            lock.write {
                fileCache.remove(tempFile.name)
                fileCache[file.name] = file
            }
            return true
        }

        // Read Mode, allow save to cache
        if (mode == SpiderQueen.MODE_READ) {
            val key = getImageKey(gid, index)
            return sCache.suspendEdit(key) {
                metadata.toFile().writeText(ext)
                fops(data)
            }
        }
        return false
    }

    private suspend fun saveFromHttpResponse(index: Int, response: HttpResponse): Boolean {
        val url = response.request.url.toString()
        val extension = MimeTypeMap.getFileExtensionFromUrl(url).ifEmpty { "jpg" }
        return saveResponseMeta(index, extension) { outFile ->
            response.bodyAsChannel().copyTo(outFile)
            FileHashRegex.find(url)?.let {
                val expected = it.groupValues[1]
                val actual = outFile.sha1()
                check(expected == actual) { "File hash mismatch: expected $expected, but got $actual\nURL: $url" }
            }
        }
    }

    // 「屏蔽外部广告」开启时，被屏蔽的广告页写入占位图并计入已完成，
    // 避免外部广告图拉取失败导致整本画廊被判为失败、CBZ 无法打包。
    suspend fun saveAdsPlaceholder(index: Int): Boolean {
        val hasPlaceholder = AdsPlaceholderFile.exists()
        val extension = if (hasPlaceholder) {
            FileUtils.getExtensionFromFilename(AdsPlaceholderFile.name)?.lowercase() ?: "jpg"
        } else {
            "jpg"
        }
        val bytes = if (hasPlaceholder) null else blankImageBytes()
        return saveResponseMeta(index, extension) { outFile ->
            if (bytes == null) {
                AdsPlaceholderFile sendTo outFile
            } else {
                outFile.write { write(bytes) }
            }
        }
    }

    // 未设置占位图时退化为一张 1x1 白色 JPEG，保证广告页仍有一个可打包进 CBZ 的图片条目
    private fun blankImageBytes(): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.WHITE) }
            .compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }

    fun saveToPath(index: Int, file: Path): Boolean {
        val key = getImageKey(gid, index)

        // Read from diskCache first
        sCache.read(key) {
            runCatching {
                data sendTo file
                return true
            }.onFailure {
                logcat(it)
                return false
            }
        }

        // Read from download dir
        runCatching {
            requireNotNull(findImageFile(index)) sendTo file
        }.onFailure {
            logcat(it)
            return false
        }.onSuccess {
            return true
        }
        return false
    }

    fun getExtension(index: Int): String? {
        val key = getImageKey(gid, index)
        return sCache.read(key) { metadata.toFile().readText() }
            ?: findImageFile(index)?.name.let { FileUtils.getExtensionFromFilename(it) }
    }

    fun getImageSource(index: Int): PathSource {
        if (mode == SpiderQueen.MODE_READ) {
            val key = getImageKey(gid, index)
            val snapshot = sCache.openSnapshot(key)
            if (snapshot != null) {
                return object : PathSource, AutoCloseable by snapshot {
                    override val source = snapshot.data
                    override val type by lazy {
                        snapshot.metadata.toFile().readText()
                    }
                }
            }
        }
        val fileSource = findImageFile(index) ?: run {
            if (mode == SpiderQueen.MODE_DOWNLOAD && copyFromCacheToDownloadDir(index)) {
                findImageFile(index)
            } else {
                null
            }
        }
        if (fileSource != null) {
            val path = requireNotNull(fileSource)
            return object : PathSource {
                override val source = path
                override val type by lazy {
                    FileUtils.getExtensionFromFilename(path.name)!!
                }

                override fun close() = Unit
            }
        }
        if (mode != SpiderQueen.MODE_READ) {
            val key = getImageKey(gid, index)
            val snapshot = sCache.openSnapshot(key)
            if (snapshot != null) {
                return object : PathSource, AutoCloseable by snapshot {
                    override val source = snapshot.data
                    override val type by lazy { snapshot.metadata.toFile().readText() }
                }
            }
        }
        error("Source $index not found for gallery $gid in $mode mode")
    }

    /** 返回 true 表示归档失败；未启用 CBZ 时直接沿用普通下载完成流程。 */
    suspend fun archive(pages: Int = info.pages): Boolean {
        if (!saveAsCbz) return false
        val dir = downloadDir
        if (dir == null) {
            logcat(CBZ_TAG, LogPriority.ERROR) { "CBZ 归档失败：gid=$gid，下载目录为空" }
            return true
        }
        val file = dir / archiveName
        return try {
            archiveTo(file, pages)
            check(file.exists()) { "归档结束后目标文件不存在" }
            logcat(CBZ_TAG, LogPriority.INFO) { "CBZ 归档完成：gid=$gid，pages=$pages，target=$file" }
            false
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            try {
                file.delete()
            } catch (deleteError: Throwable) {
                t.addSuppressed(deleteError)
            }
            logcat(CBZ_TAG, LogPriority.ERROR) {
                "CBZ 归档失败：gid=$gid，pages=$pages，tempDir=$tempDownloadDir，target=$file，${t.javaClass.simpleName}: ${t.message}\n${t.stackTraceToString()}"
            }
            true
        }
    }

    // Postpone this to `SpiderQueen.stop` because files may still be in use by reader
    suspend fun postArchive(): Boolean {
        val dir = downloadDir
        val archived = saveAsCbz && dir?.find(archiveName) != null
        if (archived) {
            dir.list().parMap(concurrency = 10) {
                if (it.name.matches(FileNameRegex)) {
                    it.delete()
                }
            }
            (dir / SpiderQueen.SPIDER_INFO_FILENAME).delete()
            tempDownloadDir?.delete()
        }
        return archived
    }

    suspend fun exportAsCbz(file: Path) = downloadDir!!.find(archiveName)?.sendTo(file) ?: archiveTo(file)

    private suspend fun archiveTo(file: Path, pages: Int = info.pages) {
        val dir = requireNotNull(downloadDir) { "下载目录为空，无法创建 $file" }
        val comicInfoFile = dir / COMIC_INFO_FILE
        val savedPages = if (info.pages == 0 && comicInfoFile.exists()) {
            readComicInfo(comicInfoFile)?.pageCount
        } else {
            null
        }
        val pageCount = pages.takeIf { it > 0 } ?: info.pages.takeIf { it > 0 } ?: savedPages ?: 0
        check(pageCount > 0) { "无效页数：pages=$pages，info.pages=${info.pages}，savedPages=$savedPages" }
        if (info.pages <= 0) info.pages = pageCount

        val includeMetadata = saveAsCbz && Settings.archiveMetadata.value
        if (includeMetadata && !comicInfoFile.exists()) {
            try {
                writeComicInfo()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logcat(CBZ_TAG, LogPriority.WARN) {
                    "CBZ 元数据在线更新失败，改用本地信息：gid=$gid，${t.javaClass.simpleName}: ${t.message}\n${t.stackTraceToString()}"
                }
                writeComicInfo(info.getComicInfo(), comicInfoFile)
            }
        }

        val cacheDir = File(appCtx.cacheDir, "cbz")
        check(cacheDir.isDirectory || cacheDir.mkdirs()) { "无法创建 CBZ 私有缓存目录：$cacheDir" }
        val tempArchive = File.createTempFile("gallery-$gid-", ".cbz", cacheDir)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempArchive))).use { zip ->
                for (idx in 0 until pageCount) {
                    try {
                        getImageSource(idx).use { source ->
                            ParcelFileDescriptor.AutoCloseInputStream(source.source.openFileDescriptor("r")).use { input ->
                                zip.putNextEntry(ZipEntry(perFilename(idx, source.type)))
                                try {
                                    input.copyTo(zip)
                                } finally {
                                    zip.closeEntry()
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        logcat(CBZ_TAG, LogPriority.ERROR) {
                            "CBZ 写入页面失败：gid=$gid，page=${idx + 1}/$pageCount，${t.javaClass.simpleName}: ${t.message}\n${t.stackTraceToString()}"
                        }
                        throw t
                    }
                }
                if (includeMetadata) {
                    ParcelFileDescriptor.AutoCloseInputStream(comicInfoFile.openFileDescriptor("r")).use { input ->
                        zip.putNextEntry(ZipEntry(COMIC_INFO_FILE))
                        try {
                            input.copyTo(zip)
                        } finally {
                            zip.closeEntry()
                        }
                    }
                }
                zip.flush()
            }
            check(tempArchive.isFile && tempArchive.length() > 0) { "私有缓存 CBZ 为空：$tempArchive" }

            tempArchive.inputStream().use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(file.openFileDescriptor("wt")).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            check(file.exists()) { "SAF 写入结束后目标 CBZ 不存在：$file" }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            logcat(CBZ_TAG, LogPriority.ERROR) {
                "CBZ 缓存打包或 UniFile 流式写入失败：gid=$gid，temp=$tempArchive，target=$file，${t.javaClass.simpleName}: ${t.message}\n${t.stackTraceToString()}"
            }
            throw t
        } finally {
            if (!tempArchive.delete() && tempArchive.exists()) {
                logcat(CBZ_TAG, LogPriority.WARN) { "CBZ 私有缓存临时文件删除失败：$tempArchive" }
            }
        }
    }

    suspend fun initDownloadDirIfExist() {
        downloadDir = getGalleryDownloadDir(info).takeIf { it.isDirectory }
        tempDownloadDir = info.tempDownloadDir?.takeIf { it.isDirectory }
        if (downloadDir != null || tempDownloadDir != null) {
            reloadFileCache()
        }
    }

    suspend fun initDownloadDir() {
        downloadDir = getGalleryDownloadDir(info).apply { mkdirs() }
    }

    suspend fun writeComicInfo(fetchMetadata: Boolean = true) {
        downloadDir?.run {
            resolve(COMIC_INFO_FILE).also {
                if (info !is GalleryDetail && fetchMetadata) {
                    EhEngine.fillGalleryListByApi(listOf(info))
                }
                info.getComicInfo().apply {
                    writeComicInfo(this, it)
                    DownloadManager.getDownloadInfo(gid)?.let { downloadInfo ->
                        downloadInfo.artistInfoList = DownloadArtist.from(gid, penciller.orEmpty())
                        EhDB.putDownloadArtist(gid, downloadInfo.artistInfoList)
                    }
                }
            }
        }
    }
}

private val sCache by lazy {
    diskCache {
        directory(appCtx.cacheDir.toOkioPath() / "image_cache")
        maxSizeBytes(Settings.readCacheSize.value.coerceIn(320, 5120).toLong() * 1024 * 1024)
    }
}
private const val TEMP_SUFFIX = ".tmp"
private const val CBZ_TAG = "CbzArchive"
private val FileNameRegex = Regex("^\\d{8}\\.\\w{3,4}")
private val FileHashRegex = Regex("/h/([0-9a-f]{40})")

fun perFilename(index: Int, extension: String = ""): String = "%08d.%s".format(index + 1, extension)

suspend fun GalleryInfo.downloadDirname(): String {
    var dirname = EhDB.getDownloadDirname(gid)
    if (dirname == null) {
        val title = getSuitableTitle(this)
        dirname = FileUtils.sanitizeFilename("$gid-$title")
        EhDB.putDownloadDirname(gid, dirname)
    }
    return dirname
}

suspend fun getGalleryDownloadDir(info: GalleryInfo): Path {
    val dirname = info.downloadDirname()
    return downloadLocation / dirname
}
