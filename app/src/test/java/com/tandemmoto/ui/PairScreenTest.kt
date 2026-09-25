package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tandemmoto.R
import com.tandemmoto.link.DiscoveryState
import com.tandemmoto.link.NearbyDevice
import com.tandemmoto.link.PairingState
import com.tandemmoto.link.Partner
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.ui.setup.PairScreen
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = compose.activity.getString(id)

    private val clicks = mutableListOf<String>()

    private fun show(
        state: DiscoveryState,
        nearby: PermissionStatus = PermissionStatus.Granted,
        pairing: PairingState = PairingState.Idle
    ) = compose.setContent {
        TandemMotoTheme {
            PairScreen(
                state = state,
                pairing = pairing,
                permissions = PermissionsState(34, mapOf(AppPermission.NEARBY to nearby)),
                onRequestPermission = { clicks += "permission" },
                onSearchAgain = { clicks += "search" },
                onDeviceClick = { clicks += "invite ${it.name}" },
                onConfirmReplace = { clicks += "replace" },
                onCancelInvite = { clicks += "cancel" },
                onDismissPairing = { clicks += "dismiss" },
                onOpenWifiSettings = { clicks += "wifi" },
                onOpenLocationSettings = { clicks += "location" },
                onBack = {}
            )
        }
    }

    private fun device(name: String, isPhone: Boolean) =
        NearbyDevice(name, name, NearbyDevice.Status.Available, isPhone)

    @Test
    fun scanningShowsPhonesAndOtherDevicesSeparately() {
        show(
            DiscoveryState.Scanning(
                listOf(device("Redmi Y2", true), device("Living room TV", false))
            )
        )
        compose.onNodeWithText(str(R.string.pair_searching)).assertExists()
        compose.onNodeWithText(str(R.string.pair_phones)).assertExists()
        compose.onNodeWithText("Redmi Y2").assertExists()
        compose.onNodeWithText(str(R.string.pair_other_devices)).assertExists()
        compose.onNodeWithText("Living room TV").assertExists()
    }

    @Test
    fun nothingFoundOffersSearchAgain() {
        show(DiscoveryState.Finished(emptyList()))
        compose.onNodeWithText(str(R.string.pair_none_found)).assertExists()
        compose.onNodeWithText(str(R.string.pair_searching)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.pair_search_again)).performClick()
        assertEquals(listOf("search"), clicks)
    }

    @Test
    fun finishedWithDevicesKeepsThemListed() {
        show(DiscoveryState.Finished(listOf(device("Galaxy S25", true))))
        compose.onNodeWithText("Galaxy S25").assertExists()
        compose.onNodeWithText(str(R.string.pair_search_again)).assertExists()
    }

    @Test
    fun wifiOffOffersWifiSettings() {
        show(DiscoveryState.WifiOff)
        compose.onNodeWithText(str(R.string.pair_wifi_off)).assertExists()
        compose.onNodeWithText(str(R.string.pair_open_wifi_settings)).performClick()
        assertEquals(listOf("wifi"), clicks)
    }

    @Test
    fun nothingFoundWithLocationOffSuggestsLocation() {
        show(DiscoveryState.Finished(emptyList(), suggestLocation = true))
        compose.onNodeWithText(str(R.string.pair_location_hint)).assertExists()
        compose.onNodeWithText(str(R.string.pair_open_location_settings)).performClick()
        assertEquals(listOf("location"), clicks)
    }

    @Test
    fun nothingFoundWithoutTheHintDoesNotMentionLocation() {
        show(DiscoveryState.Finished(emptyList()))
        compose.onNodeWithText(str(R.string.pair_location_hint)).assertDoesNotExist()
    }

    @Test
    fun stuckExplainsTheWifiToggleAndCanSearchAgain() {
        show(DiscoveryState.Stuck)
        compose.onNodeWithText(str(R.string.pair_stuck)).assertExists()
        compose.onNodeWithText(str(R.string.pair_open_wifi_settings)).performClick()
        compose.onNodeWithText(str(R.string.pair_search_again)).performClick()
        assertEquals(listOf("wifi", "search"), clicks)
    }

    @Test
    fun missingPermissionShowsThePromptInPlace() {
        show(DiscoveryState.PermissionMissing, nearby = PermissionStatus.Denied)
        compose.onNodeWithText(str(R.string.permission_nearby_prompt)).assertExists()
        compose.onNodeWithText(str(R.string.permission_allow)).performClick()
        assertEquals(listOf("permission"), clicks)
    }

    @Test
    fun unsupportedExplainsWhy() {
        show(DiscoveryState.Unsupported)
        compose.onNodeWithText(str(R.string.pair_unsupported)).assertExists()
    }

    @Test
    fun tappingAPhoneInvitesIt() {
        show(DiscoveryState.Scanning(listOf(device("Redmi Y2", true))))
        compose.onNodeWithText("Redmi Y2").performClick()
        assertEquals(listOf("invite Redmi Y2"), clicks)
    }

    @Test
    fun invitingShowsWhoAndCanBeCancelled() {
        show(
            DiscoveryState.Idle,
            pairing = PairingState.Inviting(device("Redmi Y2", true))
        )
        compose.onNodeWithText(compose.activity.getString(R.string.pair_inviting, "Redmi Y2"))
            .assertExists()
        compose.onNodeWithText(str(R.string.pair_inviting_hint)).assertExists()
        compose.onNodeWithText(str(R.string.pair_cancel)).performClick()
        assertEquals(listOf("cancel"), clicks)
    }

    @Test
    fun noAnswerExplainsWhatToDo() {
        show(
            DiscoveryState.Scanning(emptyList()),
            pairing = PairingState.Failed(PairingState.Failed.Reason.NoAnswer)
        )
        compose.onNodeWithText(str(R.string.pair_failed_no_answer)).assertExists()
        compose.onNodeWithText(str(R.string.pair_ok)).performClick()
        assertEquals(listOf("dismiss"), clicks)
    }

    @Test
    fun replacingAPartnerAsksFirst() {
        val current = Partner("Galaxy S25", "a", Partner.Role.Initiator, 0)
        show(
            DiscoveryState.Scanning(emptyList()),
            pairing = PairingState.ConfirmReplace(device("Redmi Y2", true), current)
        )
        compose.onNodeWithText(str(R.string.pair_replace_title)).assertExists()
        compose.onNodeWithText(str(R.string.pair_replace_confirm)).performClick()
        assertEquals(listOf("replace"), clicks)
    }

    @Test
    fun devicesAreNotTappableWhileAFailureIsShown() {
        show(
            DiscoveryState.Scanning(listOf(device("Redmi Y2", true))),
            pairing = PairingState.Failed(PairingState.Failed.Reason.Busy)
        )
        compose.onNodeWithText("Redmi Y2").performClick()
        assertEquals(emptyList<String>(), clicks)
    }
}
