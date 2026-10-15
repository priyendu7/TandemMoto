package com.tandemmoto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tandemmoto.R

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: FontWeight) = Font(
    R.font.archivo,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Archivo, a sturdy grotesque that stays legible at a glance; one variable font file. */
val Archivo = FontFamily(
    archivo(FontWeight.Normal),
    archivo(FontWeight.Medium),
    archivo(FontWeight.SemiBold),
    archivo(FontWeight.Bold),
    archivo(FontWeight.ExtraBold)
)

/** IBM Plex Mono, for times, counts and section labels, so digits don't jump as they change. */
val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))

/**
 * Material's sizes (so text follows the user's font size) in Archivo, with heavier titles. Sizes
 * are in sp throughout.
 */
internal val AppTypography: Typography = Typography().let { base ->
    fun TextStyle.archivo(weight: FontWeight, spacing: Float = 0f) =
        copy(fontFamily = Archivo, fontWeight = weight, letterSpacing = spacing.em)
    Typography(
        displayLarge = base.displayLarge.archivo(FontWeight.ExtraBold, -0.02f),
        displayMedium = base.displayMedium.archivo(FontWeight.ExtraBold, -0.02f),
        displaySmall = base.displaySmall.archivo(FontWeight.ExtraBold, -0.02f),
        headlineLarge = base.headlineLarge.archivo(FontWeight.Bold, -0.01f),
        headlineMedium = base.headlineMedium.archivo(FontWeight.Bold, -0.01f),
        headlineSmall = base.headlineSmall.archivo(FontWeight.Bold, -0.01f),
        // Screen titles in the top bars.
        titleLarge = base.titleLarge.archivo(FontWeight.ExtraBold, -0.02f),
        titleMedium = base.titleMedium.archivo(FontWeight.SemiBold),
        titleSmall = base.titleSmall.archivo(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.archivo(FontWeight.Normal),
        bodyMedium = base.bodyMedium.archivo(FontWeight.Normal),
        bodySmall = base.bodySmall.archivo(FontWeight.Normal),
        labelLarge = base.labelLarge.archivo(FontWeight.Bold),
        labelMedium = base.labelMedium.archivo(FontWeight.SemiBold),
        labelSmall = base.labelSmall.archivo(FontWeight.SemiBold)
    )
}

/** Styles outside Material's set. */
object AppText {
    /** Song times and durations: "0:21", "4:05". */
    val time = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 12.sp)

    /** Playlist positions and stepper values. */
    val number = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 14.sp)

    /** Settings section headers, written in capitals: "PARTNER". */
    val section = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.08.em
    )
}
