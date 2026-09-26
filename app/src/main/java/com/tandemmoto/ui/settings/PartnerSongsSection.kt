package com.tandemmoto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tandemmoto.R
import com.tandemmoto.transfer.WindowSettings

/** What Settings shows about the partner's songs on this phone (#50). */
data class PartnerSongsUi(
    val window: WindowSettings = WindowSettings(),
    val storedCount: Int = 0,
    val storedBytes: Long = 0,
    /** Ahead songs kept when free space cut the window short. */
    val aheadLimitedTo: Int? = null
)

/**
 * Settings → Songs from partner: the song window's size (per phone), how much the partner's
 * songs take now, and Remove songs from partner.
 */
@Composable
fun PartnerSongsSection(
    ui: PartnerSongsUi,
    onWindowChange: (WindowSettings) -> Unit,
    onRemove: () -> Unit
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_partner_songs_remove_title)) },
            text = { Text(stringResource(R.string.settings_partner_songs_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onRemove()
                }) { Text(stringResource(R.string.playlist_remove)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirm = false
                }) { Text(stringResource(R.string.pair_cancel)) }
            }
        )
    }
    ListItem(
        headlineContent = {
            Text(
                stringResource(R.string.settings_partner_songs),
                style = MaterialTheme.typography.titleSmall
            )
        },
        supportingContent = {
            val window = ui.window
            val total = window.behind + 1 + window.ahead
            val parts = mutableListOf(
                pluralStringResource(R.plurals.settings_partner_songs_keeps, total, total),
                stringResource(
                    R.string.settings_partner_songs_using,
                    ui.storedCount,
                    megabytes(ui.storedBytes)
                )
            )
            ui.aheadLimitedTo?.let {
                parts += pluralStringResource(R.plurals.settings_partner_songs_limited, it, it)
            }
            Text(parts.joinToString(" · "))
        }
    )
    Stepper(
        label = stringResource(R.string.settings_partner_songs_behind),
        value = ui.window.behind,
        range = WindowSettings.BEHIND_RANGE,
        onChange = { onWindowChange(ui.window.copy(behind = it)) }
    )
    Stepper(
        label = stringResource(R.string.settings_partner_songs_ahead),
        value = ui.window.ahead,
        range = WindowSettings.AHEAD_RANGE,
        onChange = { onWindowChange(ui.window.copy(ahead = it)) }
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_partner_songs_remove)) },
        supportingContent = {
            Text(stringResource(R.string.settings_partner_songs_remove_summary))
        },
        modifier = Modifier.clickable(enabled = ui.storedCount > 0) { confirm = true }
    )
    HorizontalDivider()
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
                    Icon(
                        painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(R.string.settings_decrease, label)
                    )
                }
                Text(
                    "$value",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 28.dp)
                )
                IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.settings_increase, label)
                    )
                }
            }
        }
    )
}

private fun megabytes(bytes: Long) = (bytes + 524_288) / 1_048_576
