package com.tandemmoto.player

import com.tandemmoto.library.LibraryState
import java.io.File

/** Where a song plays from on this phone, or that it isn't here (yet). */
sealed interface SongFileState {
    /** A content Uri (this phone's song or copy) or a file Uri (a downloaded partner song). */
    data class Ready(val uri: String) : SongFileState

    data object NotHere : SongFileState
}

/**
 * Which file plays for a ride playlist song (#51): this phone's own song, then its copy of the
 * partner's (#49), then a downloaded partner song (#50).
 */
object SongFiles {
    fun find(id: String, library: LibraryState, downloaded: (String) -> File?): SongFileState {
        library.songs.firstOrNull { it.id == id }?.let { return SongFileState.Ready(it.uri) }
        library.copyOf[id]?.let { copy ->
            library.songs.firstOrNull { it.id == copy }?.let { return SongFileState.Ready(it.uri) }
        }
        downloaded(id)?.let { return SongFileState.Ready(it.toURI().toString()) }
        return SongFileState.NotHere
    }
}

/** What to do about a song that isn't on the phone when the player needs it. */
enum class WaitDecision { Wait, GiveUp }

/**
 * "Getting song…" (#51): keep waiting while it can still arrive. Only the song that's playing
 * counts down (the next one may be opened early and just waits its turn). Give up at once when
 * storage is full; after [NOT_LINKED_WAIT_MS] not linked; after [LINKED_WAIT_MS] at all.
 */
object WaitRules {
    const val NOT_LINKED_WAIT_MS = 20_000L
    const val LINKED_WAIT_MS = 60_000L

    fun decide(
        isCurrent: Boolean,
        currentForMs: Long,
        linked: Boolean,
        storageFull: Boolean
    ): WaitDecision = when {
        !isCurrent -> WaitDecision.Wait
        storageFull -> WaitDecision.GiveUp
        !linked && currentForMs >= NOT_LINKED_WAIT_MS -> WaitDecision.GiveUp
        currentForMs >= LINKED_WAIT_MS -> WaitDecision.GiveUp
        else -> WaitDecision.Wait
    }
}
