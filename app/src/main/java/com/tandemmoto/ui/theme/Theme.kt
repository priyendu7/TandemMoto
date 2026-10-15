package com.tandemmoto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The ride theme: fixed colours (no Material You, so the amber accent and status colours look
 * the same on every phone), dark or light following the system, Archivo and IBM Plex Mono at
 * Material's sizes so text still follows the user's font size. [AppColors] (device, accent and
 * status colours) are provided via [LocalAppColors].
 */
@Composable
fun TandemMotoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

object TandemMotoTheme {
    /** Device (C1/C2), accent and status colours for the current light/dark mode. */
    val appColors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
