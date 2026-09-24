package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.components.ConnectionStatusBar
import com.tandemmoto.ui.components.PermissionPrompt
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
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

    private fun assertPromptMatchesBarHeight(status: PermissionStatus, sdk: Int = 34) {
        compose.setContent {
            TandemMotoTheme {
                Column {
                    PermissionPrompt(
                        permission = AppPermission.NEARBY,
                        state = PermissionsState(sdk, mapOf(AppPermission.NEARBY to status)),
                        onRequest = {},
                        modifier = Modifier.testTag("prompt")
                    )
                    ConnectionStatusBar(ConnectionStatus.NotPaired, Modifier.testTag("bar"))
                }
            }
        }
        assertEquals(
            compose.onNodeWithTag("bar").getUnclippedBoundsInRoot().height,
            compose.onNodeWithTag("prompt").getUnclippedBoundsInRoot().height
        )
    }

    @Test
    fun permissionPromptIsTheSameHeightAsTheBar() =
        assertPromptMatchesBarHeight(PermissionStatus.Denied)

    @Test
    fun openSettingsPromptIsTheSameHeightAsTheBar() =
        assertPromptMatchesBarHeight(PermissionStatus.PermanentlyDenied)

    @Test
    fun locationPromptIsTheSameHeightAsTheBar() =
        assertPromptMatchesBarHeight(PermissionStatus.PermanentlyDenied, sdk = 31)
}
