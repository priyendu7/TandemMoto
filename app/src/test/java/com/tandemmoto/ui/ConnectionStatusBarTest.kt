package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import com.tandemmoto.R
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

    @Test
    fun talkBackReadsTheLabelAndOffersTheTapAsAButton() {
        compose.setContent {
            TandemMotoTheme {
                ConnectionStatusBar(
                    ConnectionStatus.NotConnected,
                    partnerName = "Redmi",
                    onClickLabel = "Connect to your partner",
                    onClick = {}
                )
            }
        }
        val label = compose.activity.getString(R.string.status_not_connected_named, "Redmi")
        compose.onNodeWithText(label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .assert(
                SemanticsMatcher("click label is the action") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label ==
                        "Connect to your partner"
                }
            )
        // The visible "· Tap to connect" isn't read out on top of TalkBack's own action.
        val hint = compose.activity.getString(R.string.status_hint_connect)
        compose.onNodeWithText(hint, substring = true).assertDoesNotExist()
    }

    @Test
    fun aStatusWithoutAnActionIsAnnouncedButIsNotAButton() {
        compose.setContent {
            TandemMotoTheme { ConnectionStatusBar(ConnectionStatus.UpdateNeeded) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.status_update_needed))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
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
