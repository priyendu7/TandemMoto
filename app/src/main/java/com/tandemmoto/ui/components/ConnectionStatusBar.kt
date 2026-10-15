package com.tandemmoto.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tandemmoto.ui.theme.TandemMotoTheme

/**
 * Always-visible strip showing the link state on one line: icon, the short status with the
 * partner's name in their C2 box, and a trailing action chip ("Connect") when tapping does
 * something. Its colour follows [ConnectionStatus.tone] and fades between states. TalkBack reads
 * only the full label, announces it when it changes (polite live region), and offers the tap as a
 * button with [onClickLabel] ("double-tap to connect"), so the chip's action isn't read twice.
 */
@Composable
fun ConnectionStatusBar(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    /** The paired phone's name, shown in the partner's box ("Partner" until it's known). */
    partnerName: String? = null,
    /** Shown after the status, e.g. "next try 13:22" (#85); not announced each time it changes. */
    detail: String? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val (toneContainer, toneContent) = status.tone.colors()
    val container by animateColorAsState(toneContainer, tween(TONE_FADE_MS), label = "bar")
    val content by animateColorAsState(toneContent, tween(TONE_FADE_MS), label = "barContent")
    val label = status.namedLabel
        ?.takeIf { !partnerName.isNullOrBlank() }
        ?.let { stringResource(it, partnerName!!) }
        ?: stringResource(status.label)
    // Split the short text around the name, so the name can sit in its own box.
    val short = stringResource(status.short, NAME_MARKER).split(NAME_MARKER)
    val name =
        partnerName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.status_partner_tag)
    // The chip only makes sense when tapping does something.
    val action = status.action?.takeIf { onClick != null }?.let { stringResource(it) }
    StatusStrip(
        container = container,
        content = content,
        icon = status.kind.icon(status),
        modifier = modifier
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
            // The text before the name (if it has one), the name's box, then the rest.
            val head = short.takeIf { it.size > 1 }?.first()
            val tail = short.last() + detail?.let { " · $it" }.orEmpty()
            val style = MaterialTheme.typography.titleMedium
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (!head.isNullOrEmpty()) Text(head, style = style, maxLines = 1, softWrap = false)
                if (head != null) PartnerTag(name, Modifier.weight(1f, fill = false), style)
                Text(
                    tail,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        },
        // Same size as the status text, so the boxes don't look like fine print.
        trailing = action?.let {
            { StatusChip(it, content, container, style = MaterialTheme.typography.titleMedium) }
        }
    )
}

/** How long the bar takes to fade to a new tone. */
private const val TONE_FADE_MS = 250

/** Stands in for the name while splitting a short status around it. */
private const val NAME_MARKER = "\u0000"

/**
 * Height shared by the connection bar and the permission prompt that can replace it. The bar is
 * one line; the prompt may wrap to two on a narrow phone, and this fits both, so swapping one for
 * the other doesn't shift the rest of Home.
 */
internal val StatusStripMinHeight = 56.dp

/** The strip layout used by [ConnectionStatusBar] and [PermissionPrompt], with [text] as body. */
@Composable
internal fun StatusStrip(
    container: Color,
    content: Color,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) = StatusStrip(
    container = container,
    content = content,
    icon = icon,
    modifier = modifier,
    trailing = trailing,
    body = {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
    }
)

/** Icon, [body] (which takes the room left) and an optional [trailing] item, at least 56 dp tall. */
@Composable
internal fun StatusStrip(
    container: Color,
    content: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    body: @Composable RowScope.() -> Unit
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
            body()
            trailing?.invoke()
        }
    }
}

@Composable
private fun BarTone.colors(): Pair<Color, Color> {
    val colors = TandemMotoTheme.appColors
    return when (this) {
        BarTone.Connected -> colors.connected to colors.onConnected
        BarTone.Looking -> colors.searching to colors.onSearching
        BarTone.NotConnected -> colors.notConnected to colors.onNotConnected
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
            ConnectionStatus.entries.forEach {
                ConnectionStatusBar(it, partnerName = "Redmi Y2", onClick = {})
            }
        }
    }
}
