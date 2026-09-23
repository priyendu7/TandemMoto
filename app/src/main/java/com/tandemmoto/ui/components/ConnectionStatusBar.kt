package com.tandemmoto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Always-visible strip showing the link state, announced by TalkBack when it changes. */
@Composable
fun ConnectionStatusBar(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (container, content) = status.kind.colors()
    Surface(
        color = container,
        contentColor = content,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(imageVector = status.kind.icon(status), contentDescription = null)
            Text(text = stringResource(status.label), style = MaterialTheme.typography.titleMedium)
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
