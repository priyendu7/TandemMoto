package com.tandemmoto.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tandemmoto.R
import com.tandemmoto.ui.theme.AppColors
import com.tandemmoto.ui.theme.TandemMotoTheme

/**
 * Always-visible card showing the link state: a status icon on its tinted tile, the status (with
 * the partner's name) and what to do next behind a coloured dot, and a trailing action pill
 * ("Connect") when tapping does something. Its colours follow [ConnectionStatus.tone] and fade
 * between states. TalkBack reads only the full label, announces it when it changes (polite live
 * region), and offers the tap as a button with [onClickLabel] ("double-tap to connect"), so the
 * pill's action isn't read twice.
 */
@Composable
fun ConnectionStatusBar(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    /** The paired phone's name, shown in the status ("Partner" until it's known). */
    partnerName: String? = null,
    /** Replaces the second line, e.g. "next try 13:22" (#85); not announced when it changes. */
    detail: String? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val tone = status.tone.colors()
    val color by animateColorAsState(tone.color, tween(TONE_FADE_MS), label = "barColor")
    val tint by animateColorAsState(tone.tint, tween(TONE_FADE_MS), label = "barTint")
    val label = status.namedLabel
        ?.takeIf { !partnerName.isNullOrBlank() }
        ?.let { stringResource(it, partnerName!!) }
        ?: stringResource(status.label)
    val name = partnerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.status_partner_tag)
    val title = stringResource(status.short, name)
    val subtitle = detail?.replaceFirstChar { it.uppercase() } ?: stringResource(status.subtitle)
    // The pill only makes sense when tapping does something.
    val action = status.action?.takeIf { onClick != null }?.let { stringResource(it) }
    StatusStrip(
        icon = status.kind.icon(status),
        iconColor = color,
        iconTint = tint,
        modifier = modifier,
        clickModifier = Modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClickLabel = onClickLabel,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .clearAndSetSemantics {
                text = AnnotatedString(label)
                liveRegion = LiveRegionMode.Polite
            },
        body = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Dot(color)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailing = action?.let { { ActionPill(it) } }
    )
}

/** How long the card takes to fade to a new tone. */
private const val TONE_FADE_MS = 250

/**
 * Height shared by the status card and the permission prompt that can replace it: two lines of
 * text, so swapping one for the other doesn't shift the rest of Home.
 */
internal val StatusStripMinHeight = 68.dp

/** The status card layout used by [PermissionPrompt], with [text] as its body. */
@Composable
internal fun StatusStrip(
    icon: ImageVector,
    iconColor: Color,
    iconTint: Color,
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) = StatusStrip(
    icon = icon,
    iconColor = iconColor,
    iconTint = iconTint,
    modifier = modifier,
    trailing = trailing,
    body = {
        Box(contentAlignment = Alignment.CenterStart) {
            // Reserve the status card's two lines, so both are the same height at any font size.
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.alpha(0f).clearAndSetSemantics {}
            ) {
                Text(" ", style = MaterialTheme.typography.titleMedium)
                Text(" ", style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
)

/**
 * A card: [icon] on its tile, [body] (which takes the room left) and an optional [trailing]
 * item, at least [StatusStripMinHeight] tall. [clickModifier] goes inside the card, so its ripple
 * is clipped to the corners.
 */
@Composable
internal fun StatusStrip(
    icon: ImageVector,
    iconColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    clickModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = clickModifier
                .heightIn(min = StatusStripMinHeight)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            IconTile(icon, iconColor, iconTint)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                modifier = Modifier.weight(1f)
            ) {
                body()
            }
            trailing?.invoke()
        }
    }
}

/** A tone's colours: the status colour, for the icon and dot, and the icon tile's [tint]. */
private class ToneColors(val color: Color, val tint: Color)

@Composable
private fun BarTone.colors(c: AppColors = TandemMotoTheme.appColors): ToneColors = when (this) {
    BarTone.Connected -> ToneColors(c.connected, c.connectedTint)
    BarTone.Looking -> ToneColors(c.searching, c.searchingTint)
    BarTone.NotConnected -> ToneColors(c.notConnected, c.notConnectedTint)
    BarTone.Idle -> ToneColors(c.idle, c.idleTint)
}

private fun StatusKind.icon(status: ConnectionStatus): ImageVector = when {
    status == ConnectionStatus.PartnerOnCall -> Icons.Filled.Phone
    this == StatusKind.Ok -> Icons.Filled.Check
    this == StatusKind.InProgress -> Icons.Filled.Refresh
    this == StatusKind.Problem -> Icons.Filled.Warning
    this == StatusKind.Neutral -> Icons.Filled.Close
    else -> Icons.Filled.Info
}

@Preview(name = "All statuses", showBackground = true)
@Composable
private fun ConnectionStatusBarPreview() {
    TandemMotoTheme(darkTheme = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            ConnectionStatus.entries.forEach {
                ConnectionStatusBar(it, partnerName = "Redmi Y2", onClick = {})
            }
        }
    }
}
