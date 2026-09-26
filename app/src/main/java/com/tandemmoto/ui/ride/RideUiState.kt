package com.tandemmoto.ui.ride

import com.tandemmoto.ui.components.ConnectionStatus

data class RideUiState(
    val connection: ConnectionStatus = ConnectionStatus.NotPaired,
    /** The paired phone's name, for "Connected to Redmi Y2"; null when not paired. */
    val partnerName: String? = null,
    /** The current song's title, and its artist. */
    val nowPlaying: String? = null,
    val artist: String? = null,
    /** Playing, or wanting to (the button shows Pause), including while getting the song. */
    val isPlaying: Boolean = false,
    /** The current song isn't on this phone yet: "Getting song…" (#51). */
    val gettingSong: Boolean = false,
    val hasSongs: Boolean = false,
    /** Where the current song is and how long it is, for the seek bar (0: not known yet). */
    val positionMs: Long = 0,
    val durationMs: Long = 0
) {
    /**
     * Each phone plays the ride playlist, linked or not; while linked, every control is mirrored
     * to the partner (#60).
     */
    val controlsEnabled: Boolean get() = hasSongs

    /** PRD: pausing the music (while connected) opens the intercom on both phones. */
    val intercomOn: Boolean get() = connection == ConnectionStatus.Connected && !isPlaying
}
