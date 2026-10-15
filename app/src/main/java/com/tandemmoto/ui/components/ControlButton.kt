package com.tandemmoto.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Large, glove-friendly playback button (well above the 48dp minimum touch target), glowing in
 * its container colour while enabled. [primary] draws the filled style used for play/pause.
 * A rounded square, with corners in proportion to its size.
 */
@Composable
fun ControlButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: Dp = 64.dp
) {
    val colors = MaterialTheme.colorScheme
    val container = if (primary) colors.primary else colors.secondaryContainer
    val sized = modifier
        .size(size)
        .let { if (enabled) it.glow(container, shape = ControlShape) else it }
    val iconContent: @Composable () -> Unit = {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(size / 2)
        )
    }
    if (primary) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = sized,
            shape = ControlShape,
            content = iconContent
        )
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = sized,
            shape = ControlShape,
            content = iconContent
        )
    }
}

private val ControlShape = RoundedCornerShape(percent = 45)
