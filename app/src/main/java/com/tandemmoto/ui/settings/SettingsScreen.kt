package com.tandemmoto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tandemmoto.BuildConfig
import com.tandemmoto.R
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.transfer.WindowSettings
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.theme.TandemMotoTheme
import com.tandemmoto.voice.MicTestResult
import com.tandemmoto.voice.MicTestState
import com.tandemmoto.voice.TalkTestState
import kotlin.math.roundToInt

private const val SOURCE_URL = "https://github.com/priyendu7/TandemMoto"
private const val PRIVACY_URL = "$SOURCE_URL/blob/main/docs/PRIVACY.md"

// Links open in the browser: the app itself never talks to the internet.
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExportLogs: () -> Unit,
    versionName: String = BuildConfig.VERSION_NAME,
    /** The paired partner's name, or null when not paired (the Forget row is hidden). */
    partnerName: String? = null,
    onForgetPartner: () -> Unit = {},
    /** Android 13+ notifications permission; null where it doesn't apply (row hidden). */
    notifications: PermissionStatus? = null,
    onNotificationsClick: () -> Unit = {},
    /** Shown when paired (#50); null hides the section. */
    partnerSongs: PartnerSongsUi? = null,
    onWindowChange: (WindowSettings) -> Unit = {},
    onRemovePartnerSongs: () -> Unit = {},
    /** Diagnostics → Mic test (#70); null hides the row. */
    micTest: MicTestState? = null,
    onMicTest: (delaySeconds: Int) -> Unit = {},
    onCancelMicTest: () -> Unit = {},
    /** Diagnostics → Talk test (#71); null hides the row (e.g. not linked). */
    talkTest: TalkTestState? = null,
    onTalkTest: (Boolean) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    if (confirmForget && partnerName != null) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.settings_forget_title, partnerName)) },
            text = { Text(stringResource(R.string.settings_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForgetPartner()
                }) { Text(stringResource(R.string.settings_forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text(stringResource(R.string.pair_cancel))
                }
            }
        )
    }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Longer than a phone screen since Songs from partner (#50 phone test).
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_version, versionName) + "\n" +
                            stringResource(R.string.settings_license)
                    )
                }
            )
            HorizontalDivider()
            if (partnerName != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_forget_partner)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_paired_with, partnerName))
                    },
                    modifier = Modifier.clickable { confirmForget = true }
                )
                HorizontalDivider()
            }
            if (partnerSongs != null) {
                PartnerSongsSection(partnerSongs, onWindowChange, onRemovePartnerSongs)
            }
            if (notifications != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (notifications == PermissionStatus.Granted) {
                                    R.string.settings_notifications_on
                                } else {
                                    R.string.settings_notifications_off
                                }
                            )
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNotificationsClick)
                )
                HorizontalDivider()
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_summary)) },
                modifier = Modifier.clickable { uriHandler.openUri(PRIVACY_URL) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                supportingContent = { Text(stringResource(R.string.settings_source_summary)) },
                modifier = Modifier.clickable { uriHandler.openUri(SOURCE_URL) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_logs)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_export_logs_summary))
                },
                modifier = Modifier.clickable(onClick = onExportLogs)
            )
            if (micTest != null) {
                HorizontalDivider()
                MicTestRow(micTest, onMicTest, onCancelMicTest)
            }
            if (talkTest != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_talk_test)) },
                    supportingContent = {
                        Text(
                            if (talkTest.on) {
                                stringResource(R.string.settings_talk_test_on, talkTest.secondsLeft)
                            } else {
                                stringResource(R.string.settings_talk_test_summary)
                            }
                        )
                    },
                    trailingContent = {
                        Switch(checked = talkTest.on, onCheckedChange = null)
                    },
                    modifier = Modifier.toggleable(
                        value = talkTest.on,
                        role = Role.Switch,
                        onValueChange = onTalkTest
                    )
                )
            }
        }
    }
}

/**
 * Checks the mic works with the screen locked or the app in the background (#70): it waits, so
 * there's time to lock the screen or leave the app, then records 5 s and says what it heard. The
 * details go to the logs.
 */
@Composable
private fun MicTestRow(
    state: MicTestState,
    onStart: (delaySeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_mic_test)) },
        supportingContent = {
            Column {
                val result = state.result
                Text(
                    when {
                        state.startsIn != null ->
                            stringResource(R.string.settings_mic_test_starts_in, state.startsIn)
                        state.recording -> stringResource(R.string.settings_mic_test_recording)
                        result is MicTestResult.Heard -> stringResource(
                            R.string.settings_mic_test_heard,
                            result.levelsDb.max().roundToInt()
                        )
                        result == MicTestResult.Silent ->
                            stringResource(R.string.settings_mic_test_silent)
                        result == MicTestResult.CouldNotOpen ->
                            stringResource(R.string.settings_mic_test_could_not_open)
                        else -> stringResource(R.string.settings_mic_test_summary)
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy) {
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(R.string.pair_cancel))
                        }
                    } else {
                        OutlinedButton(onClick = { onStart(SHORT_DELAY_S) }) {
                            Text(
                                stringResource(R.string.settings_mic_test_in_seconds, SHORT_DELAY_S)
                            )
                        }
                        OutlinedButton(onClick = { onStart(LONG_DELAY_S) }) {
                            Text(
                                stringResource(
                                    R.string.settings_mic_test_in_minutes,
                                    LONG_DELAY_S / 60
                                )
                            )
                        }
                    }
                }
            }
        }
    )
}

private const val SHORT_DELAY_S = 10

/** Long enough to lock both phones, or leave the app and make it reconnect. */
private const val LONG_DELAY_S = 5 * 60

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    TandemMotoTheme(dynamicColor = false) {
        SettingsScreen(onBack = {}, onExportLogs = {}, versionName = "0.1.0")
    }
}
