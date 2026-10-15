package com.tandemmoto.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Large, glove-friendly playback button (well above the 48dp minimum touch target): just the
 * icon, with no fill behind it. [primary] tints the icon in the accent colour, for play/pause.
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
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (primary) colors.primary else colors.onSurface
        ),
        modifier = modifier.size(size)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            // A bigger icon than a filled button's, since there's no fill to show the target.
            modifier = Modifier.size(size * 0.6f)
        )
    }
}
