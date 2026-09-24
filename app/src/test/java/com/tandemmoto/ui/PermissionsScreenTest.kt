package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tandemmoto.R
import com.tandemmoto.permissions.AppPermission.NEARBY
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionStatus.Denied
import com.tandemmoto.permissions.PermissionStatus.Granted
import com.tandemmoto.permissions.PermissionStatus.PermanentlyDenied
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.ui.setup.PermissionsScreen
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private var openedSettings = false

    private fun show(nearby: PermissionStatus, sdk: Int = 34, approximateOnly: Boolean = false) =
        compose.setContent {
            TandemMotoTheme {
                PermissionsScreen(
                    state = PermissionsState(sdk, mapOf(NEARBY to nearby), approximateOnly),
                    onAllow = {},
                    onOpenSettings = { openedSettings = true },
                    onContinue = {},
                    onBack = {}
                )
            }
        }

    private val requiredMissing get() =
        str(R.string.permissions_required_missing, str(R.string.permission_nearby_title))

    @Test
    fun continueIsBlockedUntilNearbyIsGranted() {
        show(nearby = Denied)
        compose.onNodeWithText(str(R.string.permission_nearby_reason)).assertExists()
        compose.onNodeWithText(str(R.string.permissions_continue)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.permissions_allow)).assertIsEnabled()
        compose.onNodeWithText(requiredMissing).assertExists()
    }

    @Test
    fun grantedNearbyContinues() {
        show(nearby = Granted)
        compose.onNodeWithText(str(R.string.permissions_continue)).assertIsEnabled()
        compose.onNodeWithText(str(R.string.permissions_allow)).assertDoesNotExist()
        compose.onNodeWithText(requiredMissing).assertDoesNotExist()
    }

    @Test
    fun permanentlyDeniedOffersSettings() {
        show(nearby = PermanentlyDenied)
        // The system dialog won't show again, so only settings can grant it.
        compose.onNodeWithText(str(R.string.permissions_allow)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.permissions_continue)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.permission_open_settings)).performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun olderAndroidShowsLocationAndPreciseHint() {
        show(nearby = Denied, sdk = 31, approximateOnly = true)
        compose.onNodeWithText(str(R.string.permission_location_reason)).assertExists()
        compose.onNodeWithText(str(R.string.permissions_precise_location_hint)).assertExists()
    }
}
