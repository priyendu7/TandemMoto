package com.tandemmoto.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tandemmoto.R
import com.tandemmoto.transfer.WindowSettings
import com.tandemmoto.ui.components.AppCard
import com.tandemmoto.ui.components.CardDivider
import com.tandemmoto.ui.theme.AppText
import com.tandemmoto.ui.theme.Radius
import com.tandemmoto.ui.theme.TandemMotoTheme

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
    val window = ui.window
    val total = window.behind + 1 + window.ahead
    val scheme = MaterialTheme.colorScheme
    AppCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_partner_songs_stored),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.settings_megabytes, megabytes(ui.storedBytes)),
                    style = AppText.number,
                    color = scheme.secondary
                )
            }
            // How full the song window is: the songs here out of the most it keeps.
            LinearProgressIndicator(
                progress = { (ui.storedCount.toFloat() / total).coerceIn(0f, 1f) },
                color = TandemMotoTheme.appColors.accent,
                trackColor = scheme.outlineVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
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
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
        CardDivider()
        Stepper(
            label = stringResource(R.string.settings_partner_songs_behind),
            value = window.behind,
            range = WindowSettings.BEHIND_RANGE,
            onChange = { onWindowChange(window.copy(behind = it)) }
        )
        Stepper(
            label = stringResource(R.string.settings_partner_songs_ahead),
            value = window.ahead,
            range = WindowSettings.AHEAD_RANGE,
            onChange = { onWindowChange(window.copy(ahead = it)) }
        )
        CardDivider()
        val canRemove = ui.storedCount > 0
        val danger = if (canRemove) TandemMotoTheme.appColors.notConnected else scheme.outline
        CardListItem(
            headlineContent = { Text(stringResource(R.string.settings_partner_songs_remove)) },
            supportingContent = {
                Text(stringResource(R.string.settings_partner_songs_remove_summary))
            },
            trailingContent = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = danger)
            },
            headlineColor = danger,
            modifier = Modifier.clickable(enabled = canRemove) { confirm = true }
        )
    }
}

/** A setting's [label] with its value between − and + on a pill. */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    CardListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(Radius.Medium)
                )
            ) {
                IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
                    Icon(
                        painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(R.string.settings_decrease, label)
                    )
                }
                Text(
                    "$value",
                    style = AppText.number.copy(fontSize = 16.sp),
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
