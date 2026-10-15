package com.tandemmoto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tandemmoto.ui.theme.AppText
import com.tandemmoto.ui.theme.Radius

/** A card: `surfaceContainer` with a hairline `outlineVariant` edge, a step up from the page. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

/** A hairline between rows inside an [AppCard], inset from its edges. */
@Composable
fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

/** A settings section's name in small spaced capitals ("PARTNER"), marked as a heading. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppText.section,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** A status icon on a rounded tile: [color] on its paler [tint]. */
@Composable
fun IconTile(icon: ImageVector, color: Color, tint: Color, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(tint, RoundedCornerShape(Radius.Medium))
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size / 2))
    }
}

/** A small round dot in [color], e.g. beside a status line or an artist. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** A pill-shaped label that looks like a button, for a card whose whole surface is the tap. */
@Composable
fun ActionPill(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(ActionHeight)
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(Radius.Medium))
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.surface,
            maxLines = 1
        )
    }
}

/** Height of the action on a status card and the permission prompt. */
internal val ActionHeight = 40.dp
