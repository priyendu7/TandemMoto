package com.tandemmoto.playlist

import com.tandemmoto.library.Duplicates

/**
 * The merge rules of the ride playlist (#49), pure so they're tested on the JVM. Lists are merged
 * song by song: songs added on either phone are all kept, and for each song the newest change wins
 * (position by [RideEntry.moved], removal by [RideEntry.removed], re-adding by [RideEntry.added]).
 * The result is the same whatever order the changes arrive in, so both phones converge.
 */
object RideList {
    /** The playlist as shown: visible songs by position (ties, from two phones, by id). */
    fun ordered(entries: Collection<RideEntry>): List<RideEntry> =
        entries.filter { it.visible }.sortedWith(compareBy(RideEntry::position, RideEntry::id))

    /** One song as known on both phones. */
    fun mergeOne(a: RideEntry, b: RideEntry): RideEntry {
        val first = if (a.added <= b.added) a else b
        val placed = if (a.moved >= b.moved) a else b
        return first.copy(
            added = maxOf(a.added, b.added),
            position = placed.position,
            moved = placed.moved,
            removed = listOfNotNull(a.removed, b.removed).maxOrNull()
        )
    }

    /** [local] with [incoming] merged in, plus the entries that changed here. */
    fun merge(local: Map<String, RideEntry>, incoming: Collection<RideEntry>): Merged {
        val result = local.toMutableMap()
        val changed = mutableListOf<RideEntry>()
        for (entry in incoming) {
            val mine = result[entry.id]
            val merged = if (mine == null) entry else mergeOne(mine, entry)
            if (merged != mine) {
                result[entry.id] = merged
                changed += merged
            }
        }
        val deduped = dedupeTracks(result)
        return Merged(
            deduped.first,
            (changed + deduped.second).associateBy {
                it.id
            }.values.toList()
        )
    }

    data class Merged(val entries: Map<String, RideEntry>, val changed: List<RideEntry>)

    /**
     * The same track in two different files (e.g. an MP3 on one phone, a FLAC on the other, met
     * when two lists combine): keep the one added first and hide the other with a removal stamp
     * both phones compute alike. The hidden one's owner plays its own file as a copy (#48).
     */
    fun dedupeTracks(
        entries: Map<String, RideEntry>
    ): Pair<Map<String, RideEntry>, List<RideEntry>> {
        val result = entries.toMutableMap()
        val hidden = mutableListOf<RideEntry>()
        val kept = mutableListOf<RideEntry>()
        for (entry in entries.values.filter {
            it.visible
        }.sortedWith(compareBy(RideEntry::added, RideEntry::id))) {
            if (kept.any { Duplicates.sameTrack(it.asSong(), entry.asSong()) }) {
                val gone = entry.copy(removed = Stamp(entry.added.clock, entry.added.by + DEDUPE))
                result[entry.id] = gone
                hidden += gone
            } else {
                kept += entry
            }
        }
        return result to hidden
    }

    /** The visible entry [entry] duplicates as a track (different file), if any. */
    fun trackDuplicateOf(entries: Collection<RideEntry>, entry: RideEntry): RideEntry? =
        entries.firstOrNull {
            it.visible && it.id != entry.id && Duplicates.sameTrack(it.asSong(), entry.asSong())
        }

    /** A position key after every visible song. */
    fun endPosition(entries: Collection<RideEntry>): String =
        FractionalIndex.between(ordered(entries).lastOrNull()?.position, null)

    /**
     * The position key for moving the song at [from] to [to] (indexes in [ordered] order), or
     * null when it can't move there.
     */
    fun positionFor(entries: Collection<RideEntry>, from: Int, to: Int): String? {
        val list = ordered(entries).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return null
        list.removeAt(from)
        val before = list.getOrNull(to - 1)?.position
        val after = list.getOrNull(to)?.position
        return if (before != null && after != null && before >= after) {
            null // two songs share a key (added on both phones at once): can't split them
        } else {
            FractionalIndex.between(before, after)
        }
    }

    /** Suffix that makes a dedupe removal stamp newer than the add it hides. */
    private const val DEDUPE = "~dup"
}
