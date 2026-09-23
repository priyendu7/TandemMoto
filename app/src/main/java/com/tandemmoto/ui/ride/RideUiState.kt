package com.tandemmoto.ui.ride

import com.tandemmoto.ui.components.ConnectionStatus

data class RideUiState(
    val connection: ConnectionStatus = ConnectionStatus.NotPaired,
    val nowPlaying: String? = null,
    val isPlaying: Boolean = false
) {
    /** Shared playback controls only make sense with a live link to the partner. */
    val controlsEnabled: Boolean get() = connection == ConnectionStatus.Connected

    /** PRD: pausing the music (while connected) opens the intercom on both phones. */
    val intercomOn: Boolean get() = connection == ConnectionStatus.Connected && !isPlaying
}
