package com.tandemmoto.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Fixed colours outside the Material roles: one per phone (C1 = this phone, C2 = the partner),
 * the accent fill, and one per connection status. Each fill has an `on*` colour for text on it
 * with at least 4.5:1 contrast, and a `*Tint` for the status icon's tile, on which the fill
 * itself (the icon) keeps at least 3:1 (`AppColorsTest`).
 */
@Immutable
data class AppColors(
    val me: Color,
    val onMe: Color,
    val partner: Color,
    val onPartner: Color,
    /** The big play button and the Add button. */
    val accent: Color,
    val onAccent: Color,
    val connected: Color,
    val onConnected: Color,
    val connectedTint: Color,
    val searching: Color,
    val onSearching: Color,
    val searchingTint: Color,
    /** A problem: not reached, partner dropped, Wi-Fi off. */
    val notConnected: Color,
    val onNotConnected: Color,
    val notConnectedTint: Color,
    /** Nothing wrong, just not linked yet: not paired, or disconnected on purpose. */
    val idle: Color,
    val onIdle: Color,
    val idleTint: Color
) {
    /** A song being sent or received. */
    val transferring: Color get() = searching
    val onTransferring: Color get() = onSearching

    /** A song stored on this phone ("On this phone", "On both phones"). */
    val stored: Color get() = connected
    val onStored: Color get() = onConnected
}

val LightAppColors = AppColors(
    me = Color(0xFF1D63D8),
    onMe = Color.White,
    partner = Color(0xFFA65A00),
    onPartner = Color.White,
    accent = Amber,
    onAccent = Ink,
    connected = Color(0xFF17703D),
    onConnected = Color.White,
    connectedTint = Color(0xFFE1F3E7),
    searching = Color(0xFFB86A00),
    onSearching = Ink,
    searchingTint = Color(0xFFFCEFD9),
    notConnected = Color(0xFFB3261E),
    onNotConnected = Color.White,
    notConnectedTint = Color(0xFFFBE4E1),
    idle = Color(0xFF5E6168),
    onIdle = Color.White,
    idleTint = Color(0xFFECEAE5)
)

val DarkAppColors = AppColors(
    me = Color(0xFF7AB8FF),
    onMe = Ink,
    partner = Amber,
    onPartner = Ink,
    accent = Amber,
    onAccent = Ink,
    connected = Color(0xFF5BD68A),
    onConnected = Ink,
    connectedTint = Color(0xFF13261A),
    searching = Amber,
    onSearching = Ink,
    searchingTint = Color(0xFF2B2112),
    notConnected = Color(0xFFFF8A80),
    onNotConnected = Ink,
    notConnectedTint = Color(0xFF2A1514),
    idle = Color(0xFF9A9DA3),
    onIdle = Ink,
    idleTint = Color(0xFF202226)
)

/** Read through `TandemMotoTheme.appColors` / `LocalAppColors.current`. */
val LocalAppColors = staticCompositionLocalOf { LightAppColors }
