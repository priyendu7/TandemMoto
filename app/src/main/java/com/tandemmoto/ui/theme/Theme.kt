package com.tandemmoto.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Material You dynamic colour on Android 12+ for the accent, with a fixed fallback palette below
 * that. Page and surface colours are fixed either way. Follows the system light/dark setting, and
 * uses the system font at Material's default sizes, so text follows the user's font and
 * font-size settings. [AppColors] (device and status colours) are provided via [LocalAppColors].
 */
@Composable
fun TandemMotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context).copy(background = DarkPage, surface = DarkSurface)
            } else {
                dynamicLightColorScheme(context).copy(
                    background = LightPage,
                    surface = LightSurface
                )
            }
        }
        darkTheme -> FallbackDarkColors
        else -> FallbackLightColors
    }
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

object TandemMotoTheme {
    /** Device (C1/C2) and status colours for the current light/dark mode. */
    val appColors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
