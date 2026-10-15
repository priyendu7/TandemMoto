package com.tandemmoto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.tandemmoto.ui.theme.groupItemShape

/**
 * A grouped (segmented) list: rows on the surface colour with a 2dp gap between them and no
 * dividers. The first and last rows have large outer corners, the rest small ones
 * ([groupItemShape]). A row with [onClick] ripples inside its own shape.
 */
@Composable
fun <T> GroupedList(
    items: List<T>,
    modifier: Modifier = Modifier,
    onClick: ((T) -> Unit)? = null,
    row: @Composable (T) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            GroupedRow(
                shape = groupItemShape(index, items.size),
                onClick = onClick?.let { { it(item) } }
            ) { row(item) }
        }
    }
}

/**
 * One row of a [GroupedList]; also usable on its own for a hand-built group. [color] sets the
 * row's fill (e.g. `primaryContainer` to mark it), and its content colour follows.
 */
@Composable
fun GroupedRow(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = color,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}
