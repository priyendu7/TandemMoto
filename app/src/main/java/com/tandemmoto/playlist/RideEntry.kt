package com.tandemmoto.playlist

import com.tandemmoto.library.Song
import kotlinx.serialization.Serializable

/**
 * When a change happened, on a counter both phones keep in step (a Lamport clock), not the wall
 * clock: phone clocks differ. Ties between the two phones break on [by] (the install ID), so both
 * pick the same winner.
 */
@Serializable
data class Stamp(val clock: Long, val by: String) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int =
        compareValuesBy(this, other, Stamp::clock, Stamp::by)
}

/**
 * One song in the shared ride playlist (#49). [owner] is the install ID of the phone that added it
 * (the one with the file). Songs are ordered by [position], a sortable key, so a move changes only
 * the moved song. Removing leaves a marker ([removed]) so an old copy of the list can't bring the
 * song back; adding it again later ([added] newer than [removed]) does.
 */
@Serializable
data class RideEntry(
    val id: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val owner: String,
    val added: Stamp,
    val position: String,
    val moved: Stamp,
    val removed: Stamp? = null
) {
    val visible: Boolean get() = removed == null || added > removed

    /** For duplicate checks against this phone's songs (#48 rules). */
    fun asSong() = Song(id, uri = "", title, artist, durationMs, sizeBytes)
}
