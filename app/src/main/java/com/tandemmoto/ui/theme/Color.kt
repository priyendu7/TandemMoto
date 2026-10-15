package com.tandemmoto.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The ride palette: a near-black page with charcoal cards and one amber accent, readable at night
// and in sunlight on a handlebar mount. Light mode keeps the same accent on a warm off-white page.
// Cards use `surfaceContainer` with an `outlineVariant` edge; the page is `background`/`surface`.

/** The accent fill (play button, Add): the same amber in both themes, with dark text on it. */
internal val Amber = Color(0xFFF2A33A)
internal val Ink = Color(0xFF0E0F11)
internal val Paper = Color(0xFFF3F1EC)

internal val DarkPage = Ink
internal val DarkCard = Color(0xFF16171A)
internal val LightPage = Color(0xFFF5F3EE)
internal val LightCard = Color(0xFFFFFFFF)

internal val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2B2112),
    onPrimaryContainer = Color(0xFFFFC170),
    inversePrimary = Color(0xFF9A5800),
    secondary = Color(0xFFC9CBCF),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF202226),
    onSecondaryContainer = Paper,
    tertiary = Color(0xFF7AB8FF),
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF14243A),
    onTertiaryContainer = Color(0xFFB9D8FF),
    background = DarkPage,
    onBackground = Paper,
    surface = DarkPage,
    onSurface = Paper,
    surfaceVariant = Color(0xFF202226),
    onSurfaceVariant = Color(0xFF9A9DA3),
    surfaceTint = Amber,
    surfaceBright = Color(0xFF2A2C31),
    surfaceDim = DarkPage,
    surfaceContainerLowest = Color(0xFF0A0B0C),
    surfaceContainerLow = Color(0xFF121316),
    surfaceContainer = DarkCard,
    surfaceContainerHigh = Color(0xFF1C1D21),
    surfaceContainerHighest = Color(0xFF202226),
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    error = Color(0xFFFF8A80),
    onError = Ink,
    errorContainer = Color(0xFF2A1514),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF3A3D43),
    outlineVariant = Color(0xFF26282C),
    scrim = Color.Black
)

internal val LightColors = lightColorScheme(
    // Amber text on white is too faint to read, so text and switches use a deeper amber; the
    // filled accent (AppColors.accent) stays bright.
    primary = Color(0xFF9A5800),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE6C4),
    onPrimaryContainer = Color(0xFF3A2200),
    inversePrimary = Amber,
    secondary = Color(0xFF44474D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEAE5),
    onSecondaryContainer = Color(0xFF141518),
    tertiary = Color(0xFF1D63D8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE8FF),
    onTertiaryContainer = Color(0xFF0A2A66),
    background = LightPage,
    onBackground = Color(0xFF141518),
    surface = LightPage,
    onSurface = Color(0xFF141518),
    surfaceVariant = Color(0xFFECEAE5),
    onSurfaceVariant = Color(0xFF5E6168),
    surfaceTint = Color(0xFF9A5800),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE6E3DD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = LightCard,
    surfaceContainerHigh = Color(0xFFEFEDE8),
    surfaceContainerHighest = Color(0xFFE9E7E1),
    inverseSurface = Color(0xFF1C1D21),
    inverseOnSurface = Paper,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF7A1810),
    outline = Color(0xFF8C8F95),
    outlineVariant = Color(0xFFE0DDD6),
    scrim = Color.Black
)
