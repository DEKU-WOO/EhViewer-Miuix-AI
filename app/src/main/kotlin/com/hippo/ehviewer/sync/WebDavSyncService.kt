package com.hippo.ehviewer.sync

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ehviewer.core.util.logcat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * WebDAV 画廊同步前台服务。
 *
 * 同步期间保持前台状态，避免切后台或锁屏后网络被冻结；
 * 通知栏常驻进度并提供「停止」操作，结束后以系统通知汇报结果。
 */
class WebDavSyncService : Service(), CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    private var syncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        WebDavSyncNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 先关闭正在传输的网络流，再取消协程，确保停止立即生效
            WebDavClient.cancelActive()
            if (syncJob?.isActive == true) {
                syncJob?.cancel(CancellationException("用户停止 WebDAV 同步"))
            } else {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (syncJob?.isActive == true) return START_NOT_STICKY
        startForegroundSafely(WebDavSyncNotifications.buildProgress(this, WebDavGallerySync.syncState.value))
        syncJob = launch {
            val progressJob = launch {
                WebDavGallerySync.syncState.collect { state ->
                    if (!state.isSyncing) return@collect
                    runCatching {
                        NotificationManagerCompat.from(this@WebDavSyncService).notify(
                            WebDavSyncNotifications.ID_PROGRESS,
                            WebDavSyncNotifications.buildProgress(this@WebDavSyncService, state),
                        )
                    }.onFailure { logcat("WebDavSyncService", it) }
                }
            }
            try {
                val result = WebDavGallerySync.sync()
                if (result != null) {
                    WebDavSyncNotifications.notifyResult(this@WebDavSyncService, result)
                }
            } catch (e: CancellationException) {
                logcat("WebDavSyncService") { "同步已取消：${e.message}" }
                WebDavSyncNotifications.notifyStopped(this@WebDavSyncService)
                throw e
            } catch (t: Throwable) {
                logcat("WebDavSyncService", t)
                WebDavSyncNotifications.notifyFailure(this@WebDavSyncService, t)
            } finally {
                progressJob.cancel()
                ServiceCompat.stopForeground(this@WebDavSyncService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, WebDavSyncNotifications.ID_PROGRESS, notification, type)
        }.onFailure {
            logcat("WebDavSyncService", it)
        }
    }

    /** Android 15+ 前台服务超时：主动取消，避免被系统直接杀进程。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        logcat("WebDavSyncService") { "前台服务到达系统时长限制，取消本次同步" }
        syncJob?.cancel(CancellationException("前台服务超时"))
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.hippo.ehviewer.action.STOP_WEBDAV_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, WebDavSyncService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { logcat("WebDavSyncService", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebDavSyncService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { logcat("WebDavSyncService", it) }
        }
    }
}
