package com.tandemmoto.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.tandemmoto.R
import java.util.Locale

/**
 * Where the song is, on Home and in Playlist. Dragging only moves the thumb; letting go seeks,
 * which the partner's phone follows (#60), so a drag doesn't send a seek for every step.
 */
@Composable
fun SongSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(0)
    val shown = dragging?.toLong() ?: positionMs.coerceIn(0, duration)
    val description = stringResource(R.string.ride_seek)
    val position = stringResource(
        R.string.ride_seek_position,
        formatTime(shown),
        formatTime(duration)
    )
    Column(modifier = modifier) {
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = enabled && duration > 0,
            modifier = Modifier.semantics {
                contentDescription = description
                stateDescription = position
            }
        )
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Text(
                text = formatTime(shown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "4:05", or "1:02:03" past an hour. */
fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1_000
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}
