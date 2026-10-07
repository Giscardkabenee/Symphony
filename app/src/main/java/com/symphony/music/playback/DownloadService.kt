package com.symphony.music.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.symphony.music.R
import com.symphony.music.data.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the app alive while podcast episodes download, so closing the app does not interrupt them. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW)
        )
        try {
            ServiceCompat.startForeground(this, NOTIFICATION, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            // The system refused; the download still runs while the app stays open.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!watching) {
            watching = true
            scope.launch {
                Downloads.progress.collect { map ->
                    if (map.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        try {
                            manager.notify(NOTIFICATION, notification(map.values.minOrNull()))
                        } catch (e: Exception) {
                            // Notifications are not allowed; nothing to show.
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(percent: Int?) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(getString(R.string.download_running))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, percent ?: 0, percent == null)
        .build()

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIFICATION = 41
    }
}
