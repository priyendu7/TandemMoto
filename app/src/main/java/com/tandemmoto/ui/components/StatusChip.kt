package com.tandemmoto.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** A [Tag] filled with a status colour from `TandemMotoTheme.appColors`, e.g. `connected`. */
@Composable
fun StatusChip(text: String, color: Color, onColor: Color, modifier: Modifier = Modifier) {
    Tag(text, color, onColor, modifier)
}
