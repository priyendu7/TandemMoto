package com.tandemmoto.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tandemmoto.ui.theme.Radius
import com.tandemmoto.ui.theme.TandemMotoTheme

/**
 * Always-visible card showing the link state on two lines: the status, with the partner's name
 * in their C2 box, then what to do next. A trailing action chip ("Connect") shows when tapping
 * does something. Its colours follow [ConnectionStatus.tone] (pale tint, solid border) and fade
 * between states. TalkBack reads only the full label, announces it when it changes (polite live
 * region), and offers the tap as a button with [onClickLabel] ("double-tap to connect"), so the
 * chip's action isn't read twice.
 */
@Composable
fun ConnectionStatusBar(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    /** The paired phone's name, shown in the partner's box ("Partner" until it's known). */
    partnerName: String? = null,
    /** Replaces the second line, e.g. "next try 13:22" (#85); not announced when it changes. */
    detail: String? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val tone = status.tone.colors()
    val container by animateColorAsState(tone.container, tween(TONE_FADE_MS), label = "bar")
    val content by animateColorAsState(tone.content, tween(TONE_FADE_MS), label = "barContent")
    val border by animateColorAsState(tone.border, tween(TONE_FADE_MS), label = "barBorder")
    val label = status.namedLabel
        ?.takeIf { !partnerName.isNullOrBlank() }
        ?.let { stringResource(it, partnerName!!) }
        ?: stringResource(status.label)
    // Split the first line around the name, so the name can sit in its own box.
    val short = stringResource(status.short, NAME_MARKER).split(NAME_MARKER)
    val name = partnerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.status_partner_tag)
    val subtitle = detail?.replaceFirstChar { it.uppercase() } ?: stringResource(status.subtitle)
    // The chip only makes sense when tapping does something.
    val action = status.action?.takeIf { onClick != null }?.let { stringResource(it) }
    StatusStrip(
        container = container,
        content = content,
        border = border,
        icon = status.kind.icon(status),
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
            // The text before the name (if it has one), the name's box, then the rest. The texts
            // are measured first and the name's box gets what's left, so a long name is the
            // first thing ellipsised.
            val head = short.takeIf { it.size > 1 }?.first()
            val style = MaterialTheme.typography.titleMedium
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!head.isNullOrEmpty()) Text(head, style = style, maxLines = 1, softWrap = false)
                if (head != null) {
                    // One step smaller than the line, so the box with its padding is no taller
                    // than the text beside it; a gap on each side keeps it off the " · ".
                    PartnerTag(
                        name,
                        Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 4.dp),
                        MaterialTheme.typography.titleSmall
                    )
                }
                Text(short.last(), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = action?.let {
            {
                StatusChip(
                    it,
                    border,
                    tone.onBorder,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

/** How long the card takes to fade to a new tone. */
private const val TONE_FADE_MS = 250

/** Stands in for the name while splitting a short status around it. */
private const val NAME_MARKER = "\u0000"

/**
 * Height shared by the status card and the permission prompt that can replace it: two lines of
 * text, so swapping one for the other doesn't shift the rest of Home.
 */
internal val StatusStripMinHeight = 64.dp

/** The status card layout used by [PermissionPrompt], with [text] as its body. */
@Composable
internal fun StatusStrip(
    container: Color,
    content: Color,
    border: Color,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) = StatusStrip(
    container = container,
    content = content,
    border = border,
    icon = icon,
    modifier = modifier,
    trailing = trailing,
    body = {
        Box(contentAlignment = Alignment.CenterStart) {
            // Reserve the status card's two lines, so both are the same height at any font size.
            Column(Modifier.alpha(0f).clearAndSetSemantics {}) {
                Text(" ", style = MaterialTheme.typography.titleMedium)
                Text(" ", style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
)

/**
 * A rounded card with a solid [border]: icon, [body] (which takes the room left) and an optional
 * [trailing] item, at least [StatusStripMinHeight] tall. [clickModifier] goes inside the card, so
 * its ripple is clipped to the corners.
 */
@Composable
internal fun StatusStrip(
    container: Color,
    content: Color,
    border: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    clickModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(Radius.Medium),
        border = BorderStroke(1.5.dp, border),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = clickModifier
                .heightIn(min = StatusStripMinHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
                body()
            }
            trailing?.invoke()
        }
    }
}

/** A tone's card colours: pale [container], [content] text on it, and a solid [border]. */
private class ToneColors(
    val container: Color,
    val content: Color,
    val border: Color,
    val onBorder: Color
)

@Composable
private fun BarTone.colors(): ToneColors {
    val c = TandemMotoTheme.appColors
    return when (this) {
        BarTone.Connected ->
            ToneColors(c.connectedTint, c.onConnectedTint, c.connected, c.onConnected)
        BarTone.Looking ->
            ToneColors(c.searchingTint, c.onSearchingTint, c.searching, c.onSearching)
        BarTone.NotConnected ->
            ToneColors(c.notConnectedTint, c.onNotConnectedTint, c.notConnected, c.onNotConnected)
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
