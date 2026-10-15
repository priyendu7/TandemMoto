package com.tandemmoto.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Fixed colours that don't come from Material You: one per phone (C1 = this phone, C2 = the
 * partner) and one per connection or transfer status. Each fill has an `on*` colour for text
 * and icons on it, with at least 4.5:1 contrast (`AppColorsTest`).
 */
@Immutable
data class AppColors(
    val me: Color,
    val onMe: Color,
    val partner: Color,
    val onPartner: Color,
    val connected: Color,
    val onConnected: Color,
    val searching: Color,
    val onSearching: Color,
    val notConnected: Color,
    val onNotConnected: Color
) {
    /** A song being sent or received. */
    val transferring: Color get() = searching
    val onTransferring: Color get() = onSearching

    /** A song stored on this phone. */
    val stored: Color get() = connected
    val onStored: Color get() = onConnected
}

val LightAppColors = AppColors(
    me = Color(0xFF2F6FEB),
    onMe = Color.White,
    partner = Color(0xFFC2185B),
    onPartner = Color.White,
    connected = Color(0xFF2E7D32),
    onConnected = Color.White,
    searching = Color(0xFFF9A825),
    onSearching = Color.Black,
    notConnected = Color(0xFFEF6C00),
    onNotConnected = Color.Black
)

val DarkAppColors = AppColors(
    me = Color(0xFF8AB4FF),
    onMe = Color.Black,
    partner = Color(0xFFF48FB1),
    onPartner = Color.Black,
    connected = Color(0xFF66BB6A),
    onConnected = Color.Black,
    searching = Color(0xFFFFD54F),
    onSearching = Color.Black,
    notConnected = Color(0xFFFFB74D),
    onNotConnected = Color.Black
)

/** Read through `TandemMotoTheme.appColors` / `LocalAppColors.current`. */
val LocalAppColors = staticCompositionLocalOf { LightAppColors }
