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

class WebDavSyncWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withIOContext {
        runCatching { WebDavGallerySync.sync() }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                logcat(WORK_NAME, it)
                Result.retry()
            },
        )
    }
}

/** 根据开关增删周期任务；仅在非计费网络（Wi-Fi）下执行。 */
fun updateWebDavAutoSync(context: Context) {
    if (Settings.webdavAutoSync.value && WebDavClient.isConfigured()) {
        val request = PeriodicWorkRequestBuilder<WebDavSyncWork>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
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
