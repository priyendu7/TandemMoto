package com.tandemmoto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import com.tandemmoto.BuildConfig
import com.tandemmoto.R
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.theme.TandemMotoTheme

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
    onNotificationsClick: () -> Unit = {}
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
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    TandemMotoTheme(dynamicColor = false) {
        SettingsScreen(onBack = {}, onExportLogs = {}, versionName = "0.1.0")
    }
}
