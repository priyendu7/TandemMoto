package com.tandemmoto.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Large, glove-friendly playback button (well above the 48dp minimum touch target).
 * [primary] draws the filled style used for play/pause.
 */
@Composable
fun ControlButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: Dp = if (primary) 96.dp else 72.dp
) {
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
            modifier = modifier.size(size),
            content = iconContent
        )
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(size),
            content = iconContent
        )
    }
}
