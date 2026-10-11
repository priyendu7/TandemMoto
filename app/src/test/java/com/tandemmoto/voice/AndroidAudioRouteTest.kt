package com.tandemmoto.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder

/** The Redmi's path (Android 9): no call mode at all for earbuds unless asked (#72). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidAudioRouteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(AudioManager::class.java)

    private fun connect(type: Int) = shadowOf(audio).setOutputDevices(
        listOf(AudioDeviceInfoBuilder.newBuilder().setType(type).build())
    )

    @Test
    fun earbudsStayInMusicModeByDefault() = runBlocking {
        connect(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        val route = AndroidAudioRoute(context, main = Dispatchers.Unconfined)
        assertEquals(RouteResult(RouteKind.EarbudsMusicMode), route.open())
        assertEquals("no call mode", AudioManager.MODE_NORMAL, audio.mode)
        route.close()
        assertEquals(AudioManager.MODE_NORMAL, audio.mode)
    }

    @Test
    fun aWiredHeadsetUsesCallModeAndCloseRestoresIt() = runBlocking {
        connect(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        val route =
            AndroidAudioRoute(context, useEarbudMic = { false }, main = Dispatchers.Unconfined)
        assertEquals(RouteKind.WiredHeadset, route.open().kind)
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audio.mode)
        route.close()
        assertEquals(AudioManager.MODE_NORMAL, audio.mode)
    }

    @Test
    fun noHeadsetUsesThePhone() = runBlocking {
        val route = AndroidAudioRoute(context, main = Dispatchers.Unconfined)
        assertEquals(RouteKind.Phone, route.open().kind)
        route.close()
        assertEquals(AudioManager.MODE_NORMAL, audio.mode)
    }
}
