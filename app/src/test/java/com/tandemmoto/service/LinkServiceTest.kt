package com.tandemmoto.service

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.tandemmoto.R
import com.tandemmoto.TandemMotoApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkServiceTest {
    private val app = ApplicationProvider.getApplicationContext<TandemMotoApp>()

    @Test
    fun goesForegroundWithTheConnectionNotification() {
        val service = Robolectric.buildService(LinkService::class.java).create().startCommand(0, 1)
            .get()
        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        assertEquals(LinkService.CHANNEL_ID, notification.channelId)
        // Not paired in a fresh test app: only Close.
        assertEquals(
            app.getString(R.string.notification_close),
            notification.actions.single().title
        )
        val channel = app.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(LinkService.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun theConnectActionDoesNotGoForeground() {
        val intent = Intent(app, LinkService::class.java).setAction(LinkService.ACTION_CONNECT)
        val service = Robolectric.buildService(LinkService::class.java, intent).create()
            .startCommand(0, 1).get()
        assertEquals(null, shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun theDisconnectActionDoesNotGoForeground() {
        val intent = Intent(app, LinkService::class.java).setAction(LinkService.ACTION_DISCONNECT)
        val service = Robolectric.buildService(LinkService::class.java, intent).create()
            .startCommand(0, 1).get()
        assertEquals(null, shadowOf(service).lastForegroundNotification)
    }
}
