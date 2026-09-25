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

    private fun start(grantNearby: Boolean = true) {
        if (grantNearby) {
            shadowOf(RuntimeEnvironment.getApplication())
                .grantPermissions(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        compose.setContent {
            navController = rememberNavController()
            TandemMotoTheme { AppNavHost(navController) }
        }
    }

    @Test
    fun opensStraightOnHome() {
        start()
        compose.onNodeWithText(str(R.string.status_not_paired)).assertExists()
        assertEquals(Routes.RIDE, navController.currentDestination?.route)
    }

    @Test
    fun homeIsTheRootSoBackExits() {
        start()
        compose.runOnIdle { assertEquals(false, navController.popBackStack()) }
    }

    @Test
    fun withoutNearbyHomeShowsThePromptInsteadOfPairing() {
        start(grantNearby = false)
        compose.onNodeWithText(str(R.string.permission_nearby_prompt)).assertExists()
        compose.onNodeWithText(str(R.string.status_not_paired)).assertDoesNotExist()
        assertEquals(Routes.RIDE, navController.currentDestination?.route)
    }

    @Test
    fun notPairedOpensPairAndBackReturnsHome() {
        start()
        compose.onNodeWithText(str(R.string.status_not_paired)).performClick()
        compose.onNodeWithText(str(R.string.pair_instructions)).assertExists()
        assertEquals(Routes.PAIR, navController.currentDestination?.route)
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        compose.waitForIdle()
        assertEquals(Routes.RIDE, navController.currentDestination?.route)
    }

    @Test
    fun playlistOpensAndReturns() {
        start()
        compose.onNodeWithContentDescription(str(R.string.ride_open_playlist)).performClick()
        compose.onNodeWithText(str(R.string.playlist_empty)).assertExists()
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        compose.onNodeWithText(str(R.string.status_not_paired)).assertExists()
    }

    @Test
    fun settingsOpensAndReturns() {
        start()
        compose.onNodeWithContentDescription(str(R.string.ride_open_settings)).performClick()
        compose.onNodeWithText(str(R.string.settings_privacy_policy)).assertExists()
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        compose.waitForIdle()
        assertEquals(Routes.RIDE, navController.currentDestination?.route)
    }
}
