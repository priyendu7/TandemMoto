package com.tandemmoto.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Page and surface colours, used with both dynamic and fallback schemes: rows and cards sit on
// a slightly darker page in light mode and on true black in dark mode.
internal val LightPage = Color(0xFFEEF0F3)
internal val LightSurface = Color(0xFFFFFFFF)
internal val DarkPage = Color(0xFF121212)
internal val DarkSurface = Color(0xFF000000)

// Fallback palette for Android 8–11, where Material You dynamic colour isn't available.
// Based on the launcher icon: navy background, white + amber rings.
private val Navy = Color(0xFF1E2A38)
private val NavyLight = Color(0xFF3A4B60)
private val Amber = Color(0xFFF5A623)
private val AmberDark = Color(0xFF7A4F00)

internal val FallbackLightColors = lightColorScheme(
    primary = NavyLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Navy,
    secondary = Color(0xFF545F71),
    secondaryContainer = Color(0xFFD8E3F8),
    tertiary = AmberDark,
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF291800),
    background = LightPage,
    surface = LightSurface
)

internal val FallbackDarkColors = darkColorScheme(
    primary = Color(0xFFA3C9FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = NavyLight,
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBCC7DB),
    secondaryContainer = Color(0xFF3D4758),
    tertiary = Amber,
    tertiaryContainer = AmberDark,
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = DarkPage,
    surface = DarkSurface
)
