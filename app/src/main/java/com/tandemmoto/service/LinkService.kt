package com.tandemmoto.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tandemmoto.R
import com.tandemmoto.TandemMotoApp
import com.tandemmoto.diagnostics.AppLog
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app in the foreground while the phones are linked (#40): without it the S25 closes
 * the socket within a second of the screen locking (spike #22). It does no work itself; being a
 * foreground service keeps the process, and so the link, out of Android's background limits.
 * [LinkSession] decides when it runs. Type `connectedDevice` (allowed by CHANGE_WIFI_STATE);
 * `microphone` joins in Phase 4.
 */
class LinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as TandemMotoApp
    private var updating = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                AppLog.i(TAG, "Disconnect tapped")
                app.link.disconnect()
                app.linkSession.stopNow()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                AppLog.i(TAG, "Connect tapped")
                app.link.connectToPartner()
                return START_NOT_STICKY
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(app.link.status.value),
            type
        )
        if (!updating) updating = true else return START_NOT_STICKY
        scope.launch {
            // Denied notifications (Android 13+) only hide it; the service keeps running.
            app.link.status.collect { status ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this@LinkService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(this@LinkService)
                        .notify(NOTIFICATION_ID, notification(status))
                }
            }
        }
        // Not sticky: a restart from the background may not be allowed to go foreground.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(status: LinkStatus): android.app.Notification {
        val text = status.notificationText()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_link)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(text.text, text.name))
            .setContentIntent(open)
        text.actions.forEach { action ->
            builder.addAction(0, getString(action.label), pendingIntentFor(action))
        }
        return builder
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun pendingIntentFor(action: NotificationAction): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return when (action) {
            NotificationAction.Disconnect, NotificationAction.Close -> PendingIntent.getService(
                this,
                1,
                Intent(this, LinkService::class.java).setAction(ACTION_DISCONNECT),
                flags
            )
            NotificationAction.Connect -> PendingIntent.getService(
                this,
                2,
                Intent(this, LinkService::class.java).setAction(ACTION_CONNECT),
                flags
            )
            NotificationAction.TurnOnWifi -> PendingIntent.getActivity(
                this,
                3,
                Intent(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Settings.Panel.ACTION_WIFI
                    } else {
                        Settings.ACTION_WIFI_SETTINGS
                    }
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags
            )
        }
    }

    companion object {
        private const val TAG = "Service"
        const val CHANNEL_ID = "connection"
        const val NOTIFICATION_ID = 40
        const val ACTION_DISCONNECT = "com.tandemmoto.action.DISCONNECT"
        const val ACTION_CONNECT = "com.tandemmoto.action.CONNECT"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LinkService::class.java)
                )
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+): the app was in the
                // background. The link still works until the screen locks.
                AppLog.w(TAG, "Couldn't start the link service from the background", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkService::class.java))
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_connection),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_connection_summary)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
