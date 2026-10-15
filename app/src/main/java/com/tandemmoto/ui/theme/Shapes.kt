package com.tandemmoto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii: small for tags and inner list corners, medium for cards, large for groups. */
object Radius {
    val Small: Dp = 4.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 20.dp
}

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
