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
    private var connected = false
    private var openedWifi = false

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
                onConnect = { connected = true },
                onOpenWifiSettings = { openedWifi = true },
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

    @Test
    fun notConnectedTapsToConnect() {
        showRide(RideUiState(connection = ConnectionStatus.NotConnected))
        compose.onNodeWithText(str(R.string.status_not_connected)).performClick()
        assertTrue(connected)
        assertTrue(!openedPair)
    }

    @Test
    fun pairedElsewhereTapsToPairAgain() {
        showRide(RideUiState(connection = ConnectionStatus.PairedElsewhere))
        compose.onNodeWithText(str(R.string.status_paired_elsewhere)).performClick()
        assertTrue(openedPair)
    }

    @Test
    fun wifiOffTapsToOpenWifiSettings() {
        showRide(RideUiState(connection = ConnectionStatus.WifiOff))
        compose.onNodeWithText(str(R.string.status_wifi_off)).performClick()
        assertTrue(openedWifi && !connected)
    }

    @Test
    fun noLongerPairedTapsToPairAgain() {
        showRide(RideUiState(connection = ConnectionStatus.NoLongerPaired, partnerName = "Redmi"))
        compose.onNodeWithText(str(R.string.status_no_longer_paired_named, "Redmi")).performClick()
        assertTrue(openedPair)
    }

    @Test
    fun partnerAppClosedAsksToOpenItAndIsNotATapTarget() {
        showRide(RideUiState(connection = ConnectionStatus.PartnerAppClosed, partnerName = "Redmi"))
        compose.onNodeWithText(
            str(R.string.status_partner_app_closed_named, "Redmi")
        ).performClick()
        assertTrue(!connected && !openedPair)
    }

    @Test
    fun settingsForgetPartnerAsksBeforeForgetting() {
        var forgot = false
        compose.setContent {
            TandemMotoTheme {
                SettingsScreen(
                    onBack = {},
                    onExportLogs = {},
                    partnerName = "Redmi Y2",
                    onForgetPartner = { forgot = true }
                )
            }
        }
        compose.onNodeWithText(str(R.string.settings_forget_partner)).performClick()
        assertTrue(!forgot)
        compose.onNodeWithText(str(R.string.settings_forget_confirm)).performClick()
        assertTrue(forgot)
    }

    @Test
    fun settingsHidesForgetWhenNotPaired() {
        compose.setContent { TandemMotoTheme { SettingsScreen(onBack = {}, onExportLogs = {}) } }
        compose.onNodeWithText(str(R.string.settings_forget_partner)).assertDoesNotExist()
    }

    @Test
    fun settingsNotificationsRowShowsOffAndAsks() {
        var clicked = false
        compose.setContent {
            TandemMotoTheme {
                SettingsScreen(
                    onBack = {},
                    onExportLogs = {},
                    notifications = PermissionStatus.Denied,
                    onNotificationsClick = { clicked = true }
                )
            }
        }
        compose.onNodeWithText(str(R.string.settings_notifications_off)).performClick()
        assertTrue(clicked)
    }

    @Test
    fun settingsNotificationsRowIsHiddenWhereItDoesNotApply() {
        compose.setContent { TandemMotoTheme { SettingsScreen(onBack = {}, onExportLogs = {}) } }
        compose.onNodeWithText(str(R.string.settings_notifications)).assertDoesNotExist()
    }

    @Test
    fun partnerDisconnectedTapsToConnect() {
        showRide(
            RideUiState(connection = ConnectionStatus.PartnerDisconnected, partnerName = "Redmi")
        )
        compose.onNodeWithText(str(R.string.status_partner_disconnected_named, "Redmi"))
            .performClick()
        assertTrue(connected)
    }

    @Test
    fun statusShowsThePairedPhonesName() {
        showRide(RideUiState(connection = ConnectionStatus.Connected, partnerName = "Redmi Y2"))
        compose.onNodeWithText(str(R.string.status_connected_named, "Redmi Y2")).assertExists()
        compose.onNodeWithText(str(R.string.status_connected)).assertDoesNotExist()
    }

    @Test
    fun notConnectedNamesThePhoneToo() {
        showRide(
            RideUiState(connection = ConnectionStatus.NotConnected, partnerName = "Galaxy S25")
        )
        compose.onNodeWithText(
            str(R.string.status_not_connected_named, "Galaxy S25")
        ).performClick()
        assertTrue(connected)
    }
}
