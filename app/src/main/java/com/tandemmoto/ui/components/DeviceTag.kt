package com.tandemmoto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tandemmoto.R
import com.tandemmoto.ui.theme.Radius
import com.tandemmoto.ui.theme.TandemMotoTheme

/** Small filled tag: radius 4, one line, ellipsised. Base for device tags and status chips. */
@Composable
fun Tag(text: String, color: Color, onColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(Radius.Small))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = onColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Marks something as this phone's, in C1. */
@Composable
fun MeTag(modifier: Modifier = Modifier) {
    val colors = TandemMotoTheme.appColors
    Tag(stringResource(R.string.tag_me), colors.me, colors.onMe, modifier)
}

/** Marks something as the partner's, in C2, with their phone's [name]. */
@Composable
fun PartnerTag(name: String, modifier: Modifier = Modifier) {
    val colors = TandemMotoTheme.appColors
    Tag(name, colors.partner, colors.onPartner, modifier)
}
