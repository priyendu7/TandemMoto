package com.tandemmoto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii: small for tags and inner list corners, medium for buttons, large for cards,
 * extra large for the artwork and the play button.
 */
object Radius {
    val Small: Dp = 4.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 18.dp
    val ExtraLarge: Dp = 28.dp
}

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.Small),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(Radius.Medium),
    large = RoundedCornerShape(Radius.Large),
    extraLarge = RoundedCornerShape(Radius.ExtraLarge)
)

/**
 * Shape of row [index] in a grouped list of [count] rows: the group's outer corners are large,
 * the corners between rows small. A single row is rounded large all round.
 */
fun groupItemShape(index: Int, count: Int): Shape {
    val top = if (index == 0) Radius.Large else Radius.Small
    val bottom = if (index == count - 1) Radius.Large else Radius.Small
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom
    )
}
