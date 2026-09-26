package com.tandemmoto.service

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaStyleNotificationHelper
import com.tandemmoto.R
import com.tandemmoto.TandemMotoApp
import com.tandemmoto.diagnostics.AppLog
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.player.PlaybackState
import com.tandemmoto.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
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
                app.disconnect()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                AppLog.i(TAG, "Connect tapped")
                app.link.connectToPartner()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> {
                app.playback.togglePlay()
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                app.playback.next()
                return START_NOT_STICKY
            }
            ACTION_PREVIOUS -> {
                app.playback.previous()
                return START_NOT_STICKY
            }
        }
        goForeground(app.link.status.value, app.playback.state.value)
        if (!updating) updating = true else return START_NOT_STICKY
        scope.launch {
            combine(app.link.status, app.playback.state, ::Pair).collect { (status, playback) ->
                if (typesFor(playback) != currentTypes) {
                    goForeground(status, playback) // the playing state changes the service type
                } else {
                    // Denied notifications (Android 13+) only hide it; the service keeps running.
                    post(NOTIFICATION_ID, linkNotification(status))
                }
                showPlayer(playback)
            }
        }
        // Not sticky: a restart from the background may not be allowed to go foreground.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // An update could land just as the service stops and leave a notification nothing can
        // remove (seen on the Redmi, #51): clear both.
        notifications.cancel(NOTIFICATION_ID)
        notifications.cancel(PLAYER_NOTIFICATION_ID)
        super.onDestroy()
    }

    private val notifications get() = NotificationManagerCompat.from(this)

    private fun mayNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by mayNotify()
    private fun post(id: Int, notification: android.app.Notification) {
        if (mayNotify()) notifications.notify(id, notification)
    }

    private var currentTypes = -1

    /** connectedDevice while the link wants it, mediaPlayback while music plays (#51). */
    private fun typesFor(playback: PlaybackState): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = 0
        if (app.linkSession.linkWanted) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        if (playback.playWhenReady) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
        return if (types == 0) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else types
    }

    private fun goForeground(status: LinkStatus, playback: PlaybackState) {
        currentTypes = typesFor(playback)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, linkNotification(status), currentTypes)
    }

    private fun openApp() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** The service's own notification: the link's status and its button (#40). */
    private fun linkNotification(status: LinkStatus): android.app.Notification {
        val text = status.notificationText()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_link)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(text.text, text.name))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        text.actions.forEach { action ->
            builder.addAction(action.icon, getString(action.label), pendingIntentFor(action))
        }
        return builder.build()
    }

    /**
     * The media player (#51): the song and ⏮ ⏯ ⏭, tied to the MediaSession. On Android 13+ it
     * becomes the media card (quick settings and lock screen), which shows only the song, so the
     * link keeps its own notification rather than sharing this one (phone test on the S25).
     * It can be swiped away while paused.
     */
    @OptIn(UnstableApi::class)
    private fun showPlayer(playback: PlaybackState) {
        if (!playback.hasSongs || playback.currentId == null || !mayNotify()) {
            notifications.cancel(PLAYER_NOTIFICATION_ID)
            return
        }
        val line = if (playback.gettingSong) {
            getString(
                R.string.ride_getting_song
            )
        } else {
            playback.artist
        }
        val notification = NotificationCompat.Builder(this, PLAYER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_link)
            .setContentTitle(playback.title ?: getString(R.string.app_name))
            .setContentText(line)
            .setContentIntent(openApp())
            .setOngoing(playback.playWhenReady)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_skip_previous,
                getString(R.string.ride_previous),
                playerIntent(ACTION_PREVIOUS, 10)
            )
            .addAction(
                if (playback.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play,
                getString(if (playback.playWhenReady) R.string.ride_pause else R.string.ride_play),
                playerIntent(ACTION_PLAY_PAUSE, 11)
            )
            .addAction(
                R.drawable.ic_skip_next,
                getString(R.string.ride_next),
                playerIntent(ACTION_NEXT, 12)
            )
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(app.playback.session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        post(PLAYER_NOTIFICATION_ID, notification)
    }

    private fun playerIntent(action: String, code: Int) = PendingIntent.getService(
        this,
        code,
        Intent(this, LinkService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun pendingIntentFor(action: NotificationAction): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return when (action) {
            NotificationAction.Disconnect,
            NotificationAction.Stop,
            NotificationAction.Close -> PendingIntent.getService(
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
        const val PLAYER_NOTIFICATION_ID = 41
        const val PLAYER_CHANNEL_ID = "player"
        const val ACTION_DISCONNECT = "com.tandemmoto.action.DISCONNECT"
        const val ACTION_CONNECT = "com.tandemmoto.action.CONNECT"
        const val ACTION_PLAY_PAUSE = "com.tandemmoto.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.tandemmoto.action.NEXT"
        const val ACTION_PREVIOUS = "com.tandemmoto.action.PREVIOUS"

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
            val player = NotificationChannel(
                PLAYER_CHANNEL_ID,
                context.getString(R.string.notification_channel_player),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_player_summary)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannels(listOf(channel, player))
        }
    }
}
