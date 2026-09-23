package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.components.ConnectionStatusBar
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionStatusBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyStatusShowsItsLabel() {
        val status = mutableStateOf(ConnectionStatus.NotPaired)
        compose.setContent { TandemMotoTheme { ConnectionStatusBar(status.value) } }
        ConnectionStatus.entries.forEach {
            status.value = it
            compose.onNodeWithText(compose.activity.getString(it.label)).assertExists()
        }
    }
}
