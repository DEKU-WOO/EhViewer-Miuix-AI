package com.hippo.ehviewer.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ehviewer.core.util.logcat
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.Settings
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "WebDavGallerySyncWork"
private const val SYNC_INTERVAL_HOURS = 12L

class WebDavSyncWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withIOContext {
        WebDavSyncNotifications.ensureChannel(applicationContext)
        // 周期任务尽量以前台方式运行，避免长传输被系统打断；失败时仍继续本次同步
        runCatching { setForeground(WebDavSyncNotifications.foregroundInfo(applicationContext)) }
            .onFailure { logcat(WORK_NAME, it) }
        runCatching { WebDavGallerySync.sync() }.fold(
            onSuccess = { result ->
                if (result != null) WebDavSyncNotifications.notifyResult(applicationContext, result)
                if (result != null && result.failed > 0) Result.retry() else Result.success()
            },
            onFailure = {
                logcat(WORK_NAME, it)
                WebDavSyncNotifications.notifyFailure(applicationContext, it)
                Result.retry()
            },
        )
    }
}

/**
 * 根据开关增删周期任务：每 12 小时一次，仅在非计费 Wi-Fi 且电量非低时执行。
 * 同步方向由设置中的「同步模式」决定。
 */
fun updateWebDavAutoSync(context: Context) {
    if (Settings.webdavAutoSync.value && WebDavClient.isConfigured()) {
        val request = PeriodicWorkRequestBuilder<WebDavSyncWork>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    } else {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
