package com.tandemmoto.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** A [Tag] filled with a status colour from `TandemMotoTheme.appColors`, e.g. `connected`. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium
) {
    Tag(text, color, onColor, modifier, style)
}
