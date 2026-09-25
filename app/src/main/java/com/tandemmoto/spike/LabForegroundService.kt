package com.tandemmoto.spike

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tandemmoto.R
import com.tandemmoto.diagnostics.AppLog

/**
 * Spike-only (#22, lab v2): does a foreground service keep the Wi-Fi Direct socket alive with the
 * screen off? It does nothing itself; being in the foreground keeps the app's process (and so the
 * lab's socket) out of Android's background restrictions.
 */
class LabForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Wi-Fi Direct lab", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Wi-Fi Direct lab")
            .setContentText("Keeping the link alive (spike)")
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        AppLog.i(TAG, "Foreground service running")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AppLog.i(TAG, "Foreground service stopped")
    }

    companion object {
        private const val TAG = "Spike"
        private const val CHANNEL = "spike_lab"
        private const val NOTIFICATION_ID = 22

        fun start(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, LabForegroundService::class.java)
        )

        fun stop(context: Context) =
            context.stopService(Intent(context, LabForegroundService::class.java))
    }
}
