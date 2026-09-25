package com.tandemmoto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tandemmoto.BuildConfig
import com.tandemmoto.R
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.theme.TandemMotoTheme

private const val SOURCE_URL = "https://github.com/priyendu7/TandemMoto"
private const val PRIVACY_URL = "$SOURCE_URL/blob/main/docs/PRIVACY.md"

// Links open in the browser, so the app itself needs no INTERNET permission for them.
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExportLogs: () -> Unit,
    versionName: String = BuildConfig.VERSION_NAME,
    onOpenLab: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
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
            // Spike (#22) only, never merged.
            if (onOpenLab != null) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Wi-Fi Direct lab (spike)") },
                    supportingContent = { Text("Measure discovery, connect and ping") },
                    modifier = Modifier.clickable(onClick = onOpenLab)
                )
            }
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
