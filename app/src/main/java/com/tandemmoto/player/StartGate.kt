package com.tandemmoto.player

/** Which phone a held song is missing on. */
enum class MissingOn { ThisPhone, Partner }

/** Whether a song may start playing now (#61). */
sealed interface StartDecision {
    data object Play : StartDecision

    /** Wait at the start of the song until both phones have it. */
    data class Hold(val missingOn: MissingOn) : StartDecision

    /** One of the phones can't decode it: skipped on both. */
    data object Skip : StartDecision
}

/** What the partner's phone has, from its `SongsOnPhone` (#50, #61). */
data class PartnerSongs(val has: Set<String> = emptySet(), val cantPlay: Set<String> = emptySet())

/**
 * A new song starts only when **both** phones have it (#61): song windows are per phone (#50),
 * so the next song may be on one phone and not yet on the other, and starting it on one alone
 * would split the ride. Not linked, each phone plays on its own (the player waits for a song
 * that isn't here, as in #51).
 */
object StartGate {
    fun decide(
        songId: String,
        linked: Boolean,
        hereReady: Boolean,
        cantPlayHere: Set<String>,
        partner: PartnerSongs,
        /** The partner said it's playing this song, so it has it (its list may be on its way). */
        partnerPlaying: Boolean = false
    ): StartDecision = when {
        songId in cantPlayHere -> StartDecision.Skip
        !linked -> StartDecision.Play
        songId in partner.cantPlay -> StartDecision.Skip
        !hereReady -> StartDecision.Hold(MissingOn.ThisPhone)
        !partnerPlaying && songId !in partner.has -> StartDecision.Hold(MissingOn.Partner)
        else -> StartDecision.Play
    }
}
