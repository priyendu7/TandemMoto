package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tandemmoto.R
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.ride.RideScreen
import com.tandemmoto.ui.ride.RideUiState
import com.tandemmoto.ui.settings.SettingsScreen
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RideScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private var requested: AppPermission? = null
    private var openedPair = false

    private fun nearby(status: PermissionStatus, sdk: Int = 34, approximateOnly: Boolean = false) =
        PermissionsState(sdk, mapOf(AppPermission.NEARBY to status), approximateOnly)

    private fun showRide(
        state: RideUiState,
        permissions: PermissionsState = nearby(PermissionStatus.Granted)
    ) = compose.setContent {
        TandemMotoTheme {
            RideScreen(
                state = state,
                permissions = permissions,
                onRequestPermission = { requested = it },
                onOpenPair = { openedPair = true },
                onPlayPause = {},
                onNext = {},
                onPrevious = {},
                onOpenPlaylist = {},
                onOpenSettings = {}
            )
        }
    }

    @Test
    fun missingNearbyShowsThePromptInPlaceOfTheConnectionBar() {
        showRide(RideUiState(), nearby(PermissionStatus.Denied))
        compose.onNodeWithText(str(R.string.permission_nearby_prompt)).assertExists()
        compose.onNodeWithText(str(R.string.status_not_paired)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.permission_allow)).performClick()
        assertEquals(AppPermission.NEARBY, requested)
        // The rest of Home still works without it.
        compose.onNodeWithContentDescription(str(R.string.ride_open_settings)).assertIsEnabled()
    }

    @Test
    fun permanentlyDeniedNearbyOffersSettings() {
        showRide(RideUiState(), nearby(PermissionStatus.PermanentlyDenied))
        compose.onNodeWithText(str(R.string.permission_allow)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.permission_open_settings)).performClick()
        assertEquals(AppPermission.NEARBY, requested)
    }

    @Test
    fun olderAndroidAsksForLocation() {
        showRide(RideUiState(), nearby(PermissionStatus.Denied, sdk = 31))
        compose.onNodeWithText(str(R.string.permission_location_prompt)).assertExists()
    }

    @Test
    fun approximateLocationAsksForPrecise() {
        showRide(RideUiState(), nearby(PermissionStatus.Denied, sdk = 31, approximateOnly = true))
        compose.onNodeWithText(str(R.string.permission_precise_location_prompt)).assertExists()
    }

    @Test
    fun tappingNotPairedOpensPairing() {
        showRide(RideUiState())
        compose.onNodeWithText(str(R.string.status_not_paired)).performClick()
        assertTrue(openedPair)
    }

    @Test
    fun controlsDisabledWhenNotPaired() {
        showRide(RideUiState())
        listOf(R.string.ride_previous, R.string.ride_play, R.string.ride_next).forEach {
            compose.onNodeWithContentDescription(str(it)).assertIsNotEnabled()
        }
        compose.onNodeWithText(str(R.string.ride_controls_disabled_hint)).assertExists()
        compose.onNodeWithText(str(R.string.ride_intercom_off)).assertExists()
    }

    @Test
    fun connectedAndPausedEnablesControlsAndIntercom() {
        showRide(RideUiState(connection = ConnectionStatus.Connected, nowPlaying = "Highway Song"))
        compose.onNodeWithContentDescription(str(R.string.ride_play)).assertIsEnabled()
        compose.onNodeWithText("Highway Song").assertExists()
        compose.onNodeWithText(str(R.string.ride_intercom_on)).assertExists()
    }

    @Test
    fun playingShowsPauseAndIntercomOff() {
        showRide(RideUiState(connection = ConnectionStatus.Connected, isPlaying = true))
        compose.onNodeWithContentDescription(str(R.string.ride_pause)).assertIsEnabled()
        compose.onNodeWithText(str(R.string.ride_intercom_off)).assertExists()
    }

    @Test
    fun settingsShowsVersion() {
        compose.setContent {
            TandemMotoTheme {
                SettingsScreen(
                    onBack = {},
                    onExportLogs = {},
                    versionName = "9.9.9"
                )
            }
        }
        compose.onNodeWithText(
            str(R.string.settings_version, "9.9.9"),
            substring = true
        ).assertExists()
    }

    @Test
    fun settingsExportRowStartsTheExport() {
        var exported = false
        compose.setContent {
            TandemMotoTheme { SettingsScreen(onBack = {}, onExportLogs = { exported = true }) }
        }
        compose.onNodeWithText(str(R.string.settings_export_logs)).performClick()
        assertTrue(exported)
    }
}
