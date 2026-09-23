package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.tandemmoto.R
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.ride.RideScreen
import com.tandemmoto.ui.ride.RideUiState
import com.tandemmoto.ui.settings.SettingsScreen
import com.tandemmoto.ui.theme.TandemMotoTheme
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

    private fun showRide(state: RideUiState) = compose.setContent {
        TandemMotoTheme { RideScreen(state, {}, {}, {}, {}, {}) }
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
                    versionName = "9.9.9"
                )
            }
        }
        compose.onNodeWithText(
            str(R.string.settings_version, "9.9.9"),
            substring = true
        ).assertExists()
    }
}
