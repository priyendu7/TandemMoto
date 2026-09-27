package com.tandemmoto.ui.ride

import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.voice.IntercomLine

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
    /** Holding until the partner's phone has the song: "Getting song on …" (#61). */
    val waitingForPartner: Boolean = false,
    /** The song's embedded picture (album art), if any. */
    val artwork: ByteArray? = null,
    val hasSongs: Boolean = false,
    /** Where the current song is and how long it is, for the seek bar (0: not known yet). */
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** The intercom (#72): what its line says, and who is muted. */
    val intercom: IntercomLine = IntercomLine.NotLinked,
    val muted: Boolean = false,
    val partnerMuted: Boolean = false
) {
    /**
     * Each phone plays the ride playlist, linked or not; while linked, every control is mirrored
     * to the partner (#60).
     */
    val controlsEnabled: Boolean get() = hasSongs

    /** PRD: pausing the music (while connected) opens the intercom on both phones (#72). */
    val intercomOn: Boolean
        get() = intercom == IntercomLine.On || intercom == IntercomLine.OnPhone
}
