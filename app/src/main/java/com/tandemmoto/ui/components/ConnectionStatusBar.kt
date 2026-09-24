package com.tandemmoto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tandemmoto.ui.theme.TandemMotoTheme

/**
 * Always-visible strip showing the link state, announced by TalkBack when it changes. With
 * [onClick] it's also the way in to pairing (e.g. tapping "Not paired").
 */
@Composable
fun ConnectionStatusBar(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val (container, content) = status.kind.colors()
    StatusStrip(
        container = container,
        content = content,
        icon = status.kind.icon(status),
        text = stringResource(status.label),
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/**
 * Height shared by the connection bar and the permission prompt that can replace it. It fits two
 * lines of text, so a prompt that wraps on a narrow phone is still the same height as the bar.
 * Keep strip texts short enough for two lines at the default font size.
 */
internal val StatusStripMinHeight = 56.dp

/**
 * The one-line strip layout used by [ConnectionStatusBar] and [PermissionPrompt], so they're the
 * same height and swapping one for the other doesn't shift the rest of Home.
 */
@Composable
internal fun StatusStrip(
    container: Color,
    content: Color,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(color = container, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .heightIn(min = StatusStripMinHeight)
                .padding(horizontal = 16.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun StatusKind.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        StatusKind.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        StatusKind.InProgress -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StatusKind.Ok -> scheme.primaryContainer to scheme.onPrimaryContainer
        StatusKind.Info -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StatusKind.Problem -> scheme.errorContainer to scheme.onErrorContainer
    }
}

private fun StatusKind.icon(status: ConnectionStatus): ImageVector = when {
    status == ConnectionStatus.PartnerOnCall -> Icons.Filled.Phone
    this == StatusKind.Ok -> Icons.Filled.CheckCircle
    this == StatusKind.InProgress -> Icons.Filled.Refresh
    this == StatusKind.Problem -> Icons.Filled.Warning
    else -> Icons.Filled.Info
}

@Preview(name = "All statuses", showBackground = true)
@Composable
private fun ConnectionStatusBarPreview() {
    TandemMotoTheme(dynamicColor = false) {
        Column {
            ConnectionStatus.entries.forEach { ConnectionStatusBar(it) }
        }
    }
}
