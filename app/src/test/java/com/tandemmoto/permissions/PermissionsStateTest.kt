package com.tandemmoto.permissions

import android.Manifest
import com.tandemmoto.permissions.AppPermission.NEARBY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsStateTest {
    private fun stateOn(
        sdk: Int,
        granted: Set<String> = emptySet(),
        askedBefore: Set<AppPermission> = emptySet(),
        rationale: Set<String> = emptySet()
    ) = PermissionsState.from(sdk, granted::contains, askedBefore, rationale::contains)

    private val location = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun android13AndNewerAskForNearbyDevices() {
        listOf(33, 36).forEach { sdk ->
            assertEquals(listOf(NEARBY), AppPermission.applicable(sdk))
            assertEquals(
                listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                NEARBY.permissionsFor(sdk)
            )
        }
    }

    @Test
    fun android12AndOlderAskForLocation() {
        listOf(26, 31, 32).forEach { sdk ->
            assertEquals(listOf(NEARBY), AppPermission.applicable(sdk))
            assertEquals(location, NEARBY.permissionsFor(sdk))
        }
    }

    @Test
    fun grantedNearbyIsGranted() {
        assertFalse(stateOn(34).isGranted(NEARBY))
        val nearbyOnly = setOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        assertTrue(stateOn(34, granted = nearbyOnly).isGranted(NEARBY))
    }

    @Test
    fun approximateLocationDoesNotCountAsNearby() {
        val state = stateOn(31, granted = setOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(PermissionStatus.Denied, state.statuses[NEARBY])
        assertTrue(state.approximateLocationOnly)
        assertFalse(state.isGranted(NEARBY))

        val precise = stateOn(31, granted = location.toSet())
        assertTrue(precise.isGranted(NEARBY))
        assertFalse(precise.approximateLocationOnly)
    }

    @Test
    fun deniedWithoutRationaleAfterAskingIsPermanentlyDenied() {
        val nearby = Manifest.permission.NEARBY_WIFI_DEVICES
        // Never asked: the dialog can still show.
        assertEquals(PermissionStatus.Denied, stateOn(34).statuses[NEARBY])
        // Denied once: Android allows a rationale, so asking again shows the dialog.
        assertEquals(
            PermissionStatus.Denied,
            stateOn(34, askedBefore = setOf(NEARBY), rationale = setOf(nearby)).statuses[NEARBY]
        )
        // Denied twice: no rationale any more, so only system settings can grant it.
        assertEquals(
            PermissionStatus.PermanentlyDenied,
            stateOn(34, askedBefore = setOf(NEARBY)).statuses[NEARBY]
        )
    }

    @Test
    fun permissionsNotInTheStateCountAsGranted() {
        assertEquals(PermissionStatus.Granted, PermissionsState(34, emptyMap()).status(NEARBY))
    }
}
