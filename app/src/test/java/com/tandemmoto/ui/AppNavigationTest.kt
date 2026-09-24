package com.tandemmoto.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tandemmoto.R
import com.tandemmoto.ui.navigation.AppNavHost
import com.tandemmoto.ui.navigation.Routes
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Before
    fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.NEARBY_WIFI_DEVICES)
        compose.setContent {
            navController = rememberNavController()
            TandemMotoTheme { AppNavHost(navController) }
        }
    }

    private fun goToRide() {
        compose.onNodeWithText(str(R.string.welcome_get_started)).performClick()
        compose.onNodeWithText(str(R.string.permissions_continue)).performClick()
        compose.onNodeWithText(str(R.string.pair_skip)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun startsOnWelcome() {
        compose.onNodeWithText(str(R.string.welcome_title)).assertExists()
    }

    @Test
    fun setupFlowReachesRide() {
        goToRide()
        compose.onNodeWithText(str(R.string.status_not_paired)).assertExists()
        assertEquals(Routes.RIDE, navController.currentDestination?.route)
    }

    @Test
    fun getStartedOpensPermissions() {
        compose.onNodeWithText(str(R.string.welcome_get_started)).performClick()
        compose.onNodeWithText(str(R.string.permissions_intro)).assertExists()
        assertEquals(Routes.PERMISSIONS, navController.currentDestination?.route)
    }

    @Test
    fun permissionsBackReturnsToWelcome() {
        compose.onNodeWithText(str(R.string.welcome_get_started)).performClick()
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        compose.onNodeWithText(str(R.string.welcome_title)).assertExists()
    }

    @Test
    fun pairBackReturnsToPermissions() {
        compose.onNodeWithText(str(R.string.welcome_get_started)).performClick()
        compose.onNodeWithText(str(R.string.permissions_continue)).performClick()
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        assertEquals(Routes.PERMISSIONS, navController.currentDestination?.route)
    }

    @Test
    fun setupScreensAreRemovedFromBackStack() {
        goToRide()
        // Only Ride should remain, so system Back from Ride exits instead of returning to setup.
        compose.runOnIdle { assertEquals(false, navController.popBackStack()) }
    }

    @Test
    fun playlistOpensAndReturns() {
        goToRide()
        compose.onNodeWithContentDescription(str(R.string.ride_open_playlist)).performClick()
        compose.onNodeWithText(str(R.string.playlist_empty)).assertExists()
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        compose.onNodeWithText(str(R.string.status_not_paired)).assertExists()
    }

    @Test
    fun settingsOpensAndReturns() {
        goToRide()
        compose.onNodeWithContentDescription(str(R.string.ride_open_settings)).performClick()
        compose.onNodeWithText(str(R.string.settings_privacy_policy)).assertExists()
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        assertEquals(Routes.RIDE, navController.currentDestination?.route)
    }
}
