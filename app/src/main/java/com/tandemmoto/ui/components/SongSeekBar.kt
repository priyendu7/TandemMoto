package com.tandemmoto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.tandemmoto.R
import com.tandemmoto.ui.theme.AppText
import java.util.Locale

/**
 * Where the song is, on Home and in Playlist. Dragging only moves the thumb; letting go seeks,
 * which the partner's phone follows (#60), so a drag doesn't send a seek for every step.
 * The times sit under the bar (where the song is, and how long is left), or on its left and right
 * with [timesBeside] (where it is, and its length).
 */
@OptIn(ExperimentalMaterial3Api::class) // the track's look
@Composable
fun SongSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    timesBeside: Boolean = false
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
    val scheme = MaterialTheme.colorScheme
    val colors = SliderDefaults.colors(
        thumbColor = scheme.onSurface,
        activeTrackColor = scheme.primary,
        inactiveTrackColor = scheme.outlineVariant,
        disabledThumbColor = scheme.outline,
        disabledActiveTrackColor = scheme.outline,
        disabledInactiveTrackColor = scheme.outlineVariant
    )
    val active = enabled && duration > 0
    val bar: @Composable (Modifier) -> Unit = { barModifier ->
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = active,
            // A round thumb ringed in the page colour, on a plain track (not Material's default
            // bar and gap).
            thumb = {
                Box(
                    Modifier
                        .size(THUMB + RING * 2)
                        .background(scheme.background, CircleShape)
                        .padding(RING)
                        .background(
                            if (active) colors.thumbColor else colors.disabledThumbColor,
                            CircleShape
                        )
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    enabled = active,
                    colors = colors,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                    modifier = Modifier.height(TRACK)
                )
            },
            modifier = barModifier.semantics {
                contentDescription = description
                stateDescription = position
            }
        )
    }
    val time: @Composable (String) -> Unit = {
        Text(
            text = it,
            style = AppText.time,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
    if (timesBeside) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            time(formatTime(shown))
            bar(Modifier.weight(1f).padding(horizontal = 8.dp))
            time(formatTime(duration))
        }
    } else {
        Column(modifier = modifier) {
            bar(Modifier)
            Row(modifier = Modifier.fillMaxWidth()) {
                time(formatTime(shown))
                Spacer(Modifier.weight(1f))
                time("\u2212" + formatTime(duration - shown))
            }
        }
    }
}

private val THUMB = 20.dp
private val RING = 4.dp
private val TRACK = 6.dp

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
