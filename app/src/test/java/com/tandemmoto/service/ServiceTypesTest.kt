package com.tandemmoto.service

import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE as DEVICE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK as MEDIA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE as MIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTypesTest {
    @Test
    fun theMicTypeIsTakenOnScreenWithThePermission() {
        assertTrue(
            ServiceTypes.wantsMic(34, micGranted = true, appVisible = true, micTaken = false)
        )
    }

    @Test
    fun inTheBackgroundItCantBeTakenButIsKept() {
        assertFalse(
            ServiceTypes.wantsMic(34, micGranted = true, appVisible = false, micTaken = false)
        )
        assertTrue(
            ServiceTypes.wantsMic(34, micGranted = true, appVisible = false, micTaken = true)
        )
    }

    @Test
    fun noPermissionNoMicType() {
        assertFalse(
            ServiceTypes.wantsMic(34, micGranted = false, appVisible = true, micTaken = true)
        )
    }

    @Test
    fun androidTenAndOlderNeedNoMicType() {
        assertFalse(
            ServiceTypes.wantsMic(29, micGranted = true, appVisible = true, micTaken = false)
        )
        assertFalse(
            ServiceTypes.wantsMic(28, micGranted = true, appVisible = true, micTaken = false)
        )
    }

    @Test
    fun typesAddUp() {
        assertEquals(
            DEVICE,
            ServiceTypes.forState(34, linkWanted = true, playing = false, mic = false)
        )
        assertEquals(
            DEVICE or MEDIA or MIC,
            ServiceTypes.forState(34, linkWanted = true, playing = true, mic = true)
        )
        assertEquals(
            MEDIA,
            ServiceTypes.forState(34, linkWanted = false, playing = true, mic = false)
        )
        // Nothing else: connectedDevice, so the service always has a type.
        assertEquals(
            DEVICE,
            ServiceTypes.forState(34, linkWanted = false, playing = false, mic = false)
        )
    }

    @Test
    fun androidTenHasNoMicTypeAndNineHasNoTypesAtAll() {
        assertEquals(
            DEVICE,
            ServiceTypes.forState(29, linkWanted = true, playing = false, mic = true)
        )
        assertEquals(0, ServiceTypes.forState(28, linkWanted = true, playing = true, mic = true))
    }

    @Test
    fun micAccess() {
        assertEquals(MicAccess.NoPermission, MicAccess.of(34, micGranted = false, micTaken = false))
        assertEquals(MicAccess.Ready, MicAccess.of(34, micGranted = true, micTaken = true))
        assertEquals(MicAccess.OpenAppFirst, MicAccess.of(34, micGranted = true, micTaken = false))
        assertEquals(MicAccess.Ready, MicAccess.of(28, micGranted = true, micTaken = false))
    }
}
