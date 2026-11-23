package com.hippo.ehviewer.sync

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.ui.MainActivity

/**
 * WebDAV 画廊同步的系统通知。
 *
 * 进度通知由前台服务常驻展示，完成/失败/停止结果使用独立通知，
 * 不再依赖应用内一闪而过的 Toast。
 */
internal object WebDavSyncNotifications {
    const val CHANNEL_ID = "webdav_sync_channel"
    const val ID_PROGRESS = 2001
    const val ID_RESULT = 2002

    fun ensureChannel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("WebDAV 画廊同步")
                    .setDescription("显示已下载画廊的同步进度与结果")
                    .build(),
            )
        }.onFailure { logcat("WebDavSync", it) }
    }

    fun buildProgress(context: Context, state: WebDavSyncState): Notification {
        ensureChannel(context)
        val stage = state.stage.ifBlank { "正在准备" }
        val text = buildString {
            append(stage)
            if (state.total > 0) append(" (").append(state.current).append('/').append(state.total).append(')')
            if (state.title.isNotBlank()) append("：").append(state.title)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在同步已下载画廊")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, "停止", stopIntent(context))
            .setContentIntent(openAppIntent(context))
        if (state.total > 0) {
            builder.setProgress(state.total, state.current.coerceIn(0, state.total), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun foregroundInfo(context: Context): ForegroundInfo {
        val notification = buildProgress(context, WebDavGallerySync.syncState.value)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_PROGRESS, notification)
        }
    }

    fun notifyResult(context: Context, result: WebDavSyncResult) {
        val title = if (result.failed == 0) {
            "WebDAV 同步完成：已同步 ${result.success} 个画廊"
        } else {
            "WebDAV 同步结束：成功 ${result.success} 个，失败 ${result.failed} 个"
        }
        val text = if (result.failed == 0) {
            "上传 ${result.uploaded} 个，拉取 ${result.downloaded} 个"
        } else {
            "部分画廊未完成，下次同步会继续增量补齐"
        }
        post(context, title, text, android.R.drawable.stat_sys_download_done)
    }

    fun notifyStopped(context: Context) {
        post(
            context,
            "WebDAV 同步已停止",
            "已完成的画廊会保留，下次同步将继续增量补齐",
            android.R.drawable.ic_menu_close_clear_cancel,
        )
    }

    fun notifyFailure(context: Context, error: Throwable) {
        post(
            context,
            "WebDAV 同步失败",
            "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
            com.hippo.ehviewer.R.drawable.ic_baseline_warning_24,
        )
    }

    private fun post(context: Context, title: String, text: String, icon: Int) {
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                ID_RESULT,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent(context))
                    .build(),
            )
        }.onFailure { logcat("WebDavSync", it) }
    }

    private fun stopIntent(context: Context): PendingIntent {
        val intent = Intent(context, WebDavSyncService::class.java)
            .setAction(WebDavSyncService.ACTION_STOP)
        return PendingIntent.getService(
            context,
            ID_PROGRESS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            ID_RESULT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
