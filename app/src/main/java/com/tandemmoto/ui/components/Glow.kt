package com.tandemmoto.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tandemmoto.ui.theme.Radius

/**
 * A soft glow in [color] around the element: a shadow of [radius] elevation, in [shape], whose
 * ambient and spot colours are set.
 * Shadows are only tinted on Android 9 (API 28)+; older versions draw a plain grey shadow.
 */
fun Modifier.glow(
    color: Color,
    radius: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(Radius.Medium)
): Modifier = shadow(
    elevation = radius,
    shape = shape,
    clip = false,
    ambientColor = color,
    spotColor = color
)
