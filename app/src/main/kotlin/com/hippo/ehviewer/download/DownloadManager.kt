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
package com.hippo.ehviewer.download

import android.net.Uri
import android.util.SparseLongArray
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.size
import arrow.fx.coroutines.parMapNotNull
import com.ehviewer.core.data.model.asEntity
import com.ehviewer.core.database.model.DownloadArtist
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.database.model.DownloadLabel
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.exists
import com.ehviewer.core.files.find
import com.ehviewer.core.files.list
import com.ehviewer.core.files.metadataOrNull
import com.ehviewer.core.files.toOkioPath
import com.ehviewer.core.files.toUri
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.preferences.edit
import com.ehviewer.core.util.LogPriority
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.mapNotNull
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.exception.FatalException
import com.hippo.ehviewer.spider.COMIC_INFO_FILE
import com.hippo.ehviewer.spider.SpeedTracker
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.spider.SpiderQueen.Companion.SPIDER_INFO_FILENAME
import com.hippo.ehviewer.spider.SpiderQueen.OnSpiderListener
import com.hippo.ehviewer.spider.downloadDirname
import com.hippo.ehviewer.spider.readComicInfo
import com.hippo.ehviewer.spider.readCompatFromPath
import com.hippo.ehviewer.spider.toSimpleTags
import com.hippo.ehviewer.util.AppConfig
import com.hippo.ehviewer.util.insertWith
import com.hippo.ehviewer.util.runAssertingNotMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

object DownloadManager : OnSpiderListener, CoroutineScope {
    override val coroutineContext = Dispatchers.IO + Job()

    // All download info list
    private val allInfoList = runAssertingNotMainThread { EhDB.getAllDownloadInfo() as MutableList }

    val downloadInfoList: List<DownloadInfo>
        get() = allInfoList

    // All download info map
    private val mAllInfoMap = allInfoList.associateBy { it.gid } as MutableMap<Long, DownloadInfo>

    // All labels without default label
    // Create the SnapshotStateList first in case the database query is time-consuming
    val labelList = mutableStateListOf<DownloadLabel>().apply {
        addAll(runAssertingNotMainThread { EhDB.getAllDownloadLabelList() })
    }

    // Store download info wait to start
    private val mWaitList = ArrayDeque<DownloadInfo>()
    private var mDownloadListener: DownloadListener? = null
    private var mCurrentTask: DownloadInfo? = null
    private var mCurrentSpider: SpiderQueen? = null

    private val mutableNotifyFlow = MutableSharedFlow<DownloadInfo>(extraBufferCapacity = 1)
    val notifyFlow = mutableNotifyFlow.asSharedFlow()

    private val mutableDownloadListVersion = MutableStateFlow(0L)
    val downloadListVersion = mutableDownloadListVersion.asStateFlow()

    private val mutex = Mutex()

    private val sortMutex = Mutex()
    private val sortMode
        get() = SortMode.from(Settings.downloadSortMode.value)

    var isInitialized by mutableStateOf(false)
        private set

    init {
        launch {
            hydrateUnfinishedProgress()
            val mode = sortMode
            if (mode != SortMode.Default) sortDownloads(mode)
            isInitialized = true
        }
    }

    /**
     * 冷启动从本地 `.ehviewer`（SpiderInfo）与已落盘有效图片文件恢复未完成/已暂停任务的真实进度，
     * 避免重启后 transient 字段归零导致卡片显示 `0/总页数P`。
     */
    private suspend fun hydrateUnfinishedProgress() {
        val unfinished = sortMutex.withLock {
            allInfoList.filter { it.state != DownloadInfo.STATE_FINISH }
        }
        var anyUpdated = false
        for (info in unfinished) {
            val dir = info.downloadDir ?: EhDB.getDownloadDirname(info.gid)?.let { downloadLocation / it }
            if (dir == null || !dir.exists()) continue

            val spiderInfo = dir.find(SPIDER_INFO_FILENAME)?.let { readCompatFromPath(it) }
            val resolvedTotal = spiderInfo?.pages?.takeIf { it > 0 }
                ?: info.pages.takeIf { it > 0 }
                ?: 0

            val metaCount = spiderInfo?.downloadedPages?.takeIf { it > 0 } ?: 0
            val rawCount = if (metaCount > 0) {
                metaCount
            } else {
                countValidDownloadedImages(dir)
            }
            val hydrated = if (resolvedTotal > 0) {
                rawCount.coerceIn(0, resolvedTotal)
            } else {
                rawCount.coerceAtLeast(0)
            }

            if (resolvedTotal > 0) {
                info.total = resolvedTotal
            }
            if (hydrated > 0 || resolvedTotal > 0) {
                info.finished = hydrated
                info.downloaded = hydrated
                mutableNotifyFlow.emit(info)
                anyUpdated = true
            }
        }
        if (anyUpdated) {
            mutableDownloadListVersion.value++
        }
    }

    private fun countValidDownloadedImages(dir: Path): Int {
        var count = 0
        for (entry in dir.list()) {
            val lower = entry.name.lowercase()
            if (lower == SPIDER_INFO_FILENAME || lower == COMIC_INFO_FILE.lowercase() || lower.startsWith("thumb.")) {
                continue
            }
            val dot = lower.lastIndexOf('.')
            if (dot <= 0 || dot == lower.lastIndex) continue
            val ext = lower.substring(dot + 1)
            if (ext !in IMAGE_EXTENSIONS) continue
            val size = entry.metadataOrNull()?.size ?: 0L
            if (size > 0L) {
                count++
            }
        }
        return count
    }

    fun containLabel(label: String?): Boolean {
        if (label == null) {
            return false
        }
        return labelList.any { it.label == label }
    }

    fun containDownloadInfo(gid: Long) = mAllInfoMap.containsKey(gid)

    fun getDownloadInfo(gid: Long) = mAllInfoMap[gid]

    fun getDownloadState(gid: Long): Int {
        val info = mAllInfoMap[gid]
        return info?.state ?: DownloadInfo.STATE_INVALID
    }

    fun setDownloadListener(listener: DownloadListener?) {
        mDownloadListener = listener
    }

    private suspend fun ensureDownload() {
        mutex.withLock {
            if (mCurrentTask != null) {
                // Only one download
                return
            }

            // Get download from wait list
            if (!mWaitList.isEmpty()) {
                val info = mWaitList.removeFirst()
                mCurrentTask = info
                with(info) {
                    archiveFile?.let {
                        return onFinish(pages, pages, pages, failed = false)
                    }
                    state = DownloadInfo.STATE_DOWNLOAD
                    speed = -1
                    remaining = -1
                    total = -1
                    finished = 0
                    downloaded = 0
                    legacy = -1
                }
                val spider = SpiderQueen.obtainSpiderQueen(info, SpiderQueen.MODE_DOWNLOAD)
                mCurrentSpider = spider
                spider.addOnSpiderListener(this)
                // Update in DB
                EhDB.putDownloadInfo(info)
                // Start speed count
                mSpeedReminder.start()
                // Notify start downloading
                mDownloadListener?.onStart(info)
                // Notify state update
                mutableNotifyFlow.emit(info)
            }
        }
    }

    suspend fun startDownload(galleryInfo: BaseGalleryInfo, label: String?) {
        val infoToNotify = mutex.withLock {
            if (mCurrentTask?.gid == galleryInfo.gid) {
                return@withLock null
            }

            // Check in download list
            var info = mAllInfoMap[galleryInfo.gid]
            if (info != null) { // Get it in download list
                if (info.state != DownloadInfo.STATE_WAIT) {
                    // Set state DownloadInfo.STATE_WAIT
                    info.state = DownloadInfo.STATE_WAIT
                    // Add to wait list
                    mWaitList.add(info)
                    // Update in DB
                    EhDB.putDownloadInfo(info)
                    info
                } else {
                    null
                }
            } else {
                // It is new download info
                info = DownloadInfo(galleryInfo.asEntity(), galleryInfo.downloadDirname())
                info.label = label
                info.state = DownloadInfo.STATE_WAIT
                // Add to all download list and map
                sortMutex.withLock {
                    allInfoList.insertWith(info, sortMode.comparator())
                }
                mAllInfoMap[galleryInfo.gid] = info

                // Add to wait list
                mWaitList.add(info)

                // Save to
                EhDB.putDownloadInfo(info)

                // Add it to history
                EhDB.putHistoryInfo(info)
                info
            }
        }
        if (infoToNotify != null) {
            mutableNotifyFlow.emit(infoToNotify)
            ensureDownload()
        }
    }

    suspend fun startRangeDownload(gidList: LongArray) {
        val updateList = gidList.mapNotNull { mAllInfoMap[it] }
            .filter {
                when (it.state) {
                    DownloadInfo.STATE_NONE, DownloadInfo.STATE_FAILED, DownloadInfo.STATE_FINISH -> true
                    else -> false
                }
            }
        if (updateList.isNotEmpty()) {
            updateList.onEach {
                it.state = DownloadInfo.STATE_WAIT
                EhDB.putDownloadInfo(it)
                mutableNotifyFlow.emit(it)
            }
            mWaitList.addAll(updateList)
            ensureDownload()
        }
    }

    @Stable
    @Composable
    fun collectDownloadState(gid: Long): State<Int> = remember {
        notifyFlow.transform { if (it.gid == gid) emit(getDownloadState(gid)) }
    }.collectAsState(getDownloadState(gid))

    @Stable
    @Composable
    fun collectContainDownloadInfo(gid: Long): State<Boolean> = remember {
        notifyFlow.transform { if (it.gid == gid) emit(containDownloadInfo(gid)) }
    }.collectAsState(containDownloadInfo(gid))

    @Stable
    @Composable
    inline fun <T> updatedDownloadInfo(info: DownloadInfo, crossinline transform: @DisallowComposableCalls DownloadInfo.() -> T): T = remember {
        notifyFlow.transform { if (it.gid == info.gid) emit(transform(it)) }
    }.collectAsState(transform(info)).value

    suspend fun startAllDownload() {
        val updateList = sortMutex.withLock {
            allInfoList.filter {
                when (it.state) {
                    DownloadInfo.STATE_NONE, DownloadInfo.STATE_FAILED -> true
                    else -> false
                }
            }
        }
        if (updateList.isNotEmpty()) {
            updateList.onEach {
                it.state = DownloadInfo.STATE_WAIT
                EhDB.putDownloadInfo(it)
                mutableNotifyFlow.emit(it)
            }
            mWaitList.addAll(updateList)
            ensureDownload()
        }
    }

    suspend fun addDownload(downloadInfoList: List<DownloadInfo>) = sortMutex.withLock {
        val comparator = sortMode.comparator()
        downloadInfoList.forEach { info ->
            if (containDownloadInfo(info.gid)) return@forEach

            // Ensure download state
            if (DownloadInfo.STATE_WAIT == info.state || DownloadInfo.STATE_DOWNLOAD == info.state) {
                info.state = DownloadInfo.STATE_NONE
            }

            // Add to all download list and map
            allInfoList.insertWith(info, comparator)
            mAllInfoMap[info.gid] = info

            // Save to
            EhDB.putDownloadInfo(info)
            EhDB.putDownloadArtist(info.gid, info.artistInfoList)
        }
    }

    suspend fun addDownloadLabel(downloadLabelList: List<DownloadLabel>) {
        val offset = downloadLabelList.size
        downloadLabelList.forEachIndexed { index, label ->
            if (!containLabel(label.label)) {
                label.position = index + offset
                labelList.add(EhDB.addDownloadLabel(label))
            }
        }
    }

    suspend fun restoreDownload(galleryInfo: BaseGalleryInfo, dirname: String) {
        mAllInfoMap[galleryInfo.gid]?.let { existing ->
            if (existing.state == DownloadInfo.STATE_FINISH && existing.dirname == dirname) return
            existing.state = DownloadInfo.STATE_FINISH
            val updated = existing.copy(galleryInfo = galleryInfo.asEntity(), dirname = dirname)
            sortMutex.withLock {
                val index = allInfoList.indexOfFirst { it.gid == galleryInfo.gid }
                if (index >= 0) allInfoList[index] = updated else allInfoList.insertWith(updated, sortMode.comparator())
            }
            mAllInfoMap[galleryInfo.gid] = updated
            EhDB.putDownloadInfo(updated)
            mutableNotifyFlow.emit(updated)
            mutableDownloadListVersion.value++
            return
        }

        val info = DownloadInfo(galleryInfo.asEntity(), dirname)
        info.state = DownloadInfo.STATE_FINISH
        info.finished = galleryInfo.pages
        info.downloaded = galleryInfo.pages
        info.total = galleryInfo.pages

        sortMutex.withLock {
            allInfoList.insertWith(info, sortMode.comparator())
        }
        mAllInfoMap[galleryInfo.gid] = info
        EhDB.putDownloadInfo(info)

        mutableNotifyFlow.emit(info)
        mutableDownloadListVersion.value++
    }

    suspend fun stopDownload(gid: Long) {
        val info = stopDownloadInternal(gid)
        if (info != null) {
            // Update listener
            mutableNotifyFlow.emit(info)
            // Ensure download
            ensureDownload()
        }
    }

    suspend fun stopRangeDownload(gidList: LongArray) {
        stopRangeDownloadInternal(gidList)

        // Update listener
        gidList.mapNotNull { mAllInfoMap[it] }.forEach {
            mutableNotifyFlow.emit(it)
        }

        // Ensure download
        ensureDownload()
    }

    suspend fun stopAllDownload() {
        mutex.withLock {
            // Stop all in wait list
            for (info in mWaitList) {
                info.state = DownloadInfo.STATE_NONE
                // Update in DB
                EhDB.putDownloadInfo(info)

                // Notify
                mutableNotifyFlow.emit(info)
            }
            mWaitList.clear()

            // Stop current
            val info = stopCurrentDownloadInternalLocked()
            info?.let { mutableNotifyFlow.emit(it) }
        }
    }

    suspend fun deleteDownload(gid: Long, deleteFiles: Boolean = false) {
        stopDownloadInternal(gid)
        val info = mAllInfoMap[gid]
        if (info != null) {
            try {
                EhDB.removeDownloadInfo(info)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logcat(TAG, LogPriority.ERROR) { "删除下载数据库记录失败：gid=$gid\n${t.stackTraceToString()}" }
            }

            // Remove all list and map
            sortMutex.withLock {
                allInfoList.remove(info)
            }
            mAllInfoMap.remove(info.gid)

            // Update listener
            mutableNotifyFlow.emit(info)

            // Ensure download
            ensureDownload()

            if (deleteFiles) {
                try {
                    info.downloadDir?.delete()
                } catch (t: Throwable) {
                    logcat(TAG, LogPriority.ERROR) { "删除下载目录失败：gid=$gid\n${t.stackTraceToString()}" }
                }
                try {
                    info.tempDownloadDir?.delete()
                } catch (t: Throwable) {
                    logcat(TAG, LogPriority.ERROR) { "删除临时目录失败：gid=$gid\n${t.stackTraceToString()}" }
                }
                try {
                    EhDB.removeDownloadDirname(info.gid)
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    logcat(TAG, LogPriority.ERROR) { "删除下载路径记录失败：gid=$gid\n${t.stackTraceToString()}" }
                }
            }
        }
    }

    suspend fun deleteRangeDownload(gidList: LongArray) {
        val targetIds = gidList.toList()
        targetIds.forEach { gid ->
            try {
                stopDownloadInternal(gid)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logcat(TAG, LogPriority.ERROR) { "停止下载任务失败：gid=$gid\n${t.stackTraceToString()}" }
            }
        }
        val list = targetIds.mapNotNull { gid -> mAllInfoMap.remove(gid) }
        list.forEach { info ->
            try {
                EhDB.removeDownloadInfo(info)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logcat(TAG, LogPriority.ERROR) { "删除下载数据库记录失败：gid=${info.gid}\n${t.stackTraceToString()}" }
            }
        }
        val idSet = targetIds.toSet()
        sortMutex.withLock {
            allInfoList.removeAll { it.gid in idSet }
        }

        list.forEach { info ->
            try {
                mutableNotifyFlow.emit(info)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logcat(TAG, LogPriority.ERROR) { "通知下载记录删除失败：gid=${info.gid}\n${t.stackTraceToString()}" }
            }
        }

        ensureDownload()
    }

    suspend fun sortDownloads(mode: SortMode) = sortMutex.withLock {
        allInfoList.sortWith(mode.comparator())
    }

    suspend fun resetAllReadingProgress() = runCatching {
        EhDB.clearProgressInfo()
    }.onFailure { logcat(it) }

    // Update in DB
    // Update listener
    // No ensureDownload
    private suspend fun stopDownloadInternal(gid: Long): DownloadInfo? = mutex.withLock {
        stopDownloadInternalLocked(gid)
    }

    private suspend fun stopDownloadInternalLocked(gid: Long): DownloadInfo? {
        // Check current task
        if (mCurrentTask?.gid == gid) {
            // Stop current
            return stopCurrentDownloadInternalLocked()
        }
        val iterator = mWaitList.iterator()
        while (iterator.hasNext()) {
            val info = iterator.next()
            if (info.gid == gid) {
                // Remove from wait list
                iterator.remove()
                // Update state
                info.state = DownloadInfo.STATE_NONE
                // Update in DB
                EhDB.putDownloadInfo(info)
                return info
            }
        }
        return null
    }

    // Update in DB
    // Update mDownloadListener
    private suspend fun stopCurrentDownloadInternalLocked(): DownloadInfo? {
        val info = mCurrentTask
        val spider = mCurrentSpider
        // Release spider
        if (spider != null) {
            mCurrentSpider = null
            spider.removeOnSpiderListener(this@DownloadManager)
            spider.flushSpiderInfo()
            SpiderQueen.releaseSpiderQueen(spider, SpiderQueen.MODE_DOWNLOAD)
        }
        mCurrentTask = null
        // Stop speed reminder
        mSpeedReminder.stop()
        if (info == null) {
            return null
        }

        // Update state
        info.state = DownloadInfo.STATE_NONE
        // Update in DB
        EhDB.putDownloadInfo(info)
        // Listener
        mDownloadListener?.onCancel(info)
        return info
    }

    // Update in DB
    // Update mDownloadListener
    private suspend fun stopRangeDownloadInternal(gidList: LongArray) = mutex.withLock {
        // Two way
        if (gidList.size < mWaitList.size) {
            for (element in gidList) {
                stopDownloadInternalLocked(element)
            }
        } else {
            // Check current task
            if (mCurrentTask != null && gidList.contains(mCurrentTask!!.gid)) {
                // Stop current
                stopCurrentDownloadInternalLocked()
            }

            // Check all in wait list
            val iterator = mWaitList.iterator()
            while (iterator.hasNext()) {
                val info = iterator.next()
                if (gidList.contains(info.gid)) {
                    // Remove from wait list
                    iterator.remove()
                    // Update state
                    info.state = DownloadInfo.STATE_NONE
                    // Update in DB
                    EhDB.putDownloadInfo(info)
                }
            }
        }
    }

    /**
     * @param label Not allow new label
     */
    suspend fun changeLabel(list: Collection<DownloadInfo>, label: String?) {
        if (null != label && !containLabel(label)) {
            logcat(TAG, LogPriority.ERROR) { "Not exits label: $label" }
            return
        }
        list.forEach {
            it.label = label
        }
        EhDB.updateDownloadInfo(list)
    }

    suspend fun addLabel(label: String?) {
        if (label == null || containLabel(label)) {
            return
        }
        labelList.add(EhDB.addDownloadLabel(DownloadLabel(label, labelList.size)))
    }

    suspend fun renameLabel(from: String, to: String) {
        val index = labelList.indexOfFirst { it.label == from }
        if (index != -1) {
            val exist = labelList.removeAt(index)
            val new = exist.copy(label = to)
            labelList.add(index, new)
            EhDB.updateDownloadLabel(new)
            sortMutex.withLock {
                allInfoList.forEach {
                    if (it.label == from) {
                        it.label = to
                    }
                }
            }
        }
    }

    suspend fun deleteLabel(label: String) {
        val (removed, shifted) = synchronized(labelList) {
            removeLabelAndShiftPositions(labelList, label)
        }
        if (removed != null) {
            EhDB.removeDownloadLabel(removed)
            shifted.forEach { EhDB.updateDownloadLabel(it) }
        }
        sortMutex.withLock {
            allInfoList.forEach {
                if (it.label == label) {
                    it.label = null
                }
            }
        }
    }

    suspend fun readMetadataFromLocal() {
        val list = sortMutex.withLock {
            allInfoList.mapNotNull {
                val updateGallery = it.pages == 0 || it.simpleTags == null
                val updateArtist = it.artistInfoList.isEmpty()
                if (updateGallery || updateArtist) {
                    Triple(it, updateGallery, updateArtist)
                } else {
                    null
                }
            }
        }.parMapNotNull(concurrency = 5) { (info, updateGallery, updateArtist) ->
            info.downloadDir?.run {
                val comicInfo = find(COMIC_INFO_FILE)?.let { readComicInfo(it) }
                if (comicInfo != null) {
                    val galleryInfo = if (updateGallery) {
                        info.pages = comicInfo.pageCount
                        info.simpleTags = comicInfo.toSimpleTags()
                        info.galleryInfo
                    } else {
                        null
                    }

                    val artistList = if (updateArtist && comicInfo.penciller != null) {
                        info.artistInfoList = DownloadArtist.from(info.gid, comicInfo.penciller)
                        info.gid to info.artistInfoList
                    } else {
                        null
                    }

                    if (galleryInfo != null || artistList != null) {
                        galleryInfo to artistList
                    } else {
                        null
                    }
                } else if (info.pages == 0) {
                    val galleryInfo = find(SPIDER_INFO_FILENAME)?.let {
                        readCompatFromPath(it)?.run {
                            info.pages = pages
                            info.galleryInfo
                        }
                    }

                    galleryInfo?.let { it to null }
                } else {
                    null
                }
            }
        }

        val galleryInfoList = buildList {
            list.forEach { (info, artists) ->
                info?.let {
                    add(it)
                }
                artists?.let { (gid, updateList) ->
                    EhDB.putDownloadArtist(gid, updateList)
                }
            }
        }
        if (galleryInfoList.isNotEmpty()) EhDB.updateGalleryInfo(galleryInfoList)
    }

    val isIdle: Boolean
        get() = mCurrentTask == null && mWaitList.isEmpty()

    override fun onGetPages(pages: Int) {
        launch {
            mCurrentTask?.let { info ->
                info.total = pages
                mutableNotifyFlow.emit(info)
            } ?: logcat(TAG, LogPriority.ERROR) { "Current task is null, but it should not be" }
        }
    }

    override fun onFatal(error: FatalException) {
        launch {
            mDownloadListener?.onFatal(error)
            stopAllDownload()
        }
    }

    override fun onPageDownload(
        index: Int,
        contentLength: Long,
        receivedSize: Long,
        bytesRead: Int,
    ) {
        launch {
            mSpeedReminder.onDownload(index, contentLength, receivedSize, bytesRead)
        }
    }

    override fun onPageSuccess(index: Int, finished: Int, downloaded: Int, total: Int) {
        launch {
            mSpeedReminder.onDone(index)
            mCurrentTask?.let { info ->
                info.finished = finished
                info.downloaded = downloaded
                info.total = total
                mDownloadListener?.onDownload(info)
                mutableNotifyFlow.emit(info)
            } ?: logcat(TAG, LogPriority.ERROR) { "Current task is null, but it should not be" }
        }
    }

    override fun onPageFailure(
        index: Int,
        error: String?,
        finished: Int,
        downloaded: Int,
        total: Int,
    ) {
        launch {
            mSpeedReminder.onDone(index)
            mCurrentTask?.let { info ->
                info.finished = finished
                info.downloaded = downloaded
                info.total = total
                mutableNotifyFlow.emit(info)
            } ?: logcat(TAG, LogPriority.ERROR) { "Current task is null, but it should not be" }
        }
    }

    override fun onFinish(finished: Int, downloaded: Int, total: Int, failed: Boolean) {
        launch {
            var finishedInfo: DownloadInfo? = null
            mutex.withLock {
                mSpeedReminder.onFinish()
                mCurrentSpider?.let { spider ->
                    mCurrentSpider = null
                    spider.removeOnSpiderListener(DownloadManager)
                    SpiderQueen.releaseSpiderQueen(spider, SpiderQueen.MODE_DOWNLOAD)
                }
                mCurrentTask?.let { info ->
                    mCurrentTask = null
                    mSpeedReminder.stop()
                    info.finished = finished
                    info.downloaded = downloaded
                    info.total = total
                    info.legacy = total - finished
                    info.state = if (failed) DownloadInfo.STATE_FAILED else DownloadInfo.STATE_FINISH
                    EhDB.putDownloadInfo(info)
                    finishedInfo = info
                } ?: logcat(TAG, LogPriority.ERROR) { "Current task is null, but it should not be" }
            }
            finishedInfo?.let { info ->
                mDownloadListener?.onFinish(info)
                mutableNotifyFlow.emit(info)
                // 只有真的结束了一个任务才推进等待队列
                ensureDownload()
            }
        }
    }

    interface DownloadListener {
        /**
         * Fatal error
         */
        fun onFatal(error: FatalException)

        /**
         * Start download
         */
        fun onStart(info: DownloadInfo)

        /**
         * Update download speed
         */
        fun onDownload(info: DownloadInfo)

        /**
         * Download done
         */
        fun onFinish(info: DownloadInfo)

        /**
         * Download done
         */
        fun onCancel(info: DownloadInfo)
    }

    private val mSpeedReminder = object {
        private val contentLengthMap = SparseLongArray()
        private val receivedSizeMap = SparseLongArray()
        private val tracker = SpeedTracker()
        private val mutex = Mutex()
        private var currentJob: Job? = null
        fun start() {
            if (currentJob == null) {
                currentJob = launch {
                    tracker.speedFlow().collect(::updateSpeed)
                }
            }
        }

        suspend fun stop() {
            tracker.reset()
            mutex.withLock {
                contentLengthMap.clear()
                receivedSizeMap.clear()
            }
            currentJob?.cancel()
            currentJob = null
        }

        suspend fun onDownload(index: Int, contentLength: Long, receivedSize: Long, bytesRead: Int) {
            mutex.withLock {
                contentLengthMap.put(index, contentLength)
                receivedSizeMap.put(index, receivedSize)
            }
            tracker.track(bytesRead)
        }

        suspend fun onDone(index: Int) {
            mutex.withLock {
                contentLengthMap.delete(index)
                receivedSizeMap.delete(index)
            }
        }

        suspend fun onFinish() {
            mutex.withLock {
                contentLengthMap.clear()
                receivedSizeMap.clear()
            }
        }

        private suspend fun updateSpeed(speed: Long) {
            mCurrentTask?.let { info ->
                info.speed = speed

                // Calculate remaining
                if (info.total <= 0) {
                    info.remaining = -1
                } else if (speed == 0L) {
                    info.remaining = 300L * 24L * 60L * 60L * 1000L // 300 days
                } else {
                    var downloadingCount = 0
                    var downloadingContentLengthSum = 0L
                    var totalSize = 0L
                    mutex.withLock {
                        for (i in 0..<contentLengthMap.size) {
                            val contentLength = contentLengthMap.valueAt(i)
                            val receivedSize = receivedSizeMap.valueAt(i)
                            downloadingCount++
                            downloadingContentLengthSum += contentLength
                            totalSize += contentLength - receivedSize
                        }
                    }
                    if (downloadingCount != 0) {
                        totalSize += downloadingContentLengthSum * (info.total - info.downloaded - downloadingCount) / downloadingCount
                        info.remaining = totalSize / speed * 1000
                    }
                }
                mDownloadListener?.onDownload(info)
                mutableNotifyFlow.emit(info)
            }
        }
    }

    private val IMAGE_EXTENSIONS = setOf("webp", "jpg", "jpeg", "png", "gif")
    private const val TAG = "DownloadManager"
}

var downloadLocation: Path
    get() = with(Settings) {
        if (downloadScheme != null) {
            Uri.Builder().apply {
                scheme(downloadScheme)
                encodedAuthority(downloadAuthority)
                encodedPath(downloadPath)
                encodedQuery(downloadQuery)
                encodedFragment(downloadFragment)
            }.build().toOkioPath()
        } else {
            AppConfig.defaultDownloadDir?.toOkioPath() ?: "".toPath()
        }
    }
    set(value) = with(value.toUri()) {
        Settings.edit {
            downloadScheme = scheme
            downloadAuthority = encodedAuthority
            downloadPath = encodedPath
            downloadQuery = encodedQuery
            downloadFragment = encodedFragment
        }
    }

val DownloadInfo.downloadDir get() = dirname?.let { downloadLocation / it }
val DownloadInfo.archiveFile get() = downloadDir?.run { find("$gid.cbz") ?: find("$gid.zip") }
val GalleryInfo.tempDownloadDir get() = AppConfig.externalTempPersistDir?.let { it / "$gid" }

internal fun removeLabelAndShiftPositions(
    list: MutableList<DownloadLabel>,
    targetLabel: String,
): Pair<DownloadLabel?, List<DownloadLabel>> {
    val index = list.indexOfFirst { it.label == targetLabel }
    if (index == -1) return null to emptyList()
    val item = list.removeAt(index)
    val shifted = ArrayList<DownloadLabel>(list.size - index)
    for (i in index until list.size) {
        val current = list[i]
        current.position--
        shifted.add(current)
    }
    return item to shifted
}
