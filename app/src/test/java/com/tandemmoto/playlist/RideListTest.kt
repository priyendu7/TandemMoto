package com.tandemmoto.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideListTest {
    private fun entry(
        id: String,
        owner: String = "A",
        added: Stamp = Stamp(1, owner),
        position: String = "V",
        moved: Stamp = added,
        removed: Stamp? = null,
        title: String = "Song $id"
    ) = RideEntry(id, title, "Artist", 200_000, 1_000, owner, added, position, moved, removed)

    private fun merged(local: List<RideEntry>, incoming: List<RideEntry>) =
        RideList.merge(local.associateBy { it.id }, incoming).entries

    private fun titles(entries: Map<String, RideEntry>) = RideList.ordered(entries.values).map {
        it.id
    }

    @Test
    fun songsAddedOnBothPhonesWhileApartAreAllKept() {
        val a = listOf(entry("a1", "A", position = "V"), entry("a2", "A", position = "X"))
        val b = listOf(entry("b1", "B", position = "W"))
        assertEquals(listOf("a1", "b1", "a2"), titles(merged(a, b)))
        assertEquals(merged(a, b), merged(b, a))
    }

    @Test
    fun theNewestMoveWins() {
        val base = entry("s", position = "V")
        val movedHere = base.copy(position = "a", moved = Stamp(5, "A"))
        val movedThere = base.copy(position = "b", moved = Stamp(7, "B"))
        assertEquals("b", merged(listOf(movedHere), listOf(movedThere))["s"]!!.position)
        assertEquals("b", merged(listOf(movedThere), listOf(movedHere))["s"]!!.position)
    }

    @Test
    fun removeAndMoveInEitherOrderEndTheSame() {
        val base = entry("s")
        val removed = base.copy(removed = Stamp(5, "A"))
        val moved = base.copy(position = "Z", moved = Stamp(6, "B"))
        val one = merged(listOf(removed), listOf(moved))
        val two = merged(listOf(moved), listOf(removed))
        assertEquals(one, two)
        assertFalse(one["s"]!!.visible) // the move doesn't bring it back
    }

    @Test
    fun anOldCopyCannotBringARemovedSongBack() {
        val removed = entry("s", removed = Stamp(5, "A"))
        val stale = entry("s") // from before the removal
        assertFalse(merged(listOf(removed), listOf(stale))["s"]!!.visible)
    }

    @Test
    fun addingARemovedSongAgainBringsItBack() {
        val removed = entry("s", removed = Stamp(5, "A"))
        val readded = entry("s", added = Stamp(9, "B"), moved = Stamp(9, "B"))
        assertTrue(merged(listOf(removed), listOf(readded))["s"]!!.visible)
    }

    @Test
    fun receivingTheSameChangeTwiceChangesNothing() {
        val local = listOf(entry("s"))
        val change = listOf(entry("s").copy(position = "Z", moved = Stamp(4, "B")))
        val once = merged(local, change)
        val result = RideList.merge(once, change)
        assertEquals(once, result.entries)
        assertTrue(result.changed.isEmpty())
    }

    @Test
    fun equalClocksFromTheTwoPhonesPickTheSameWinner() {
        val base = entry("s")
        val fromA = base.copy(position = "a", moved = Stamp(3, "A"))
        val fromB = base.copy(position = "b", moved = Stamp(3, "B"))
        assertEquals(merged(listOf(fromA), listOf(fromB)), merged(listOf(fromB), listOf(fromA)))
    }

    @Test
    fun theSameTrackInTwoFilesKeepsTheFirstAdded() {
        val mp3 = entry("mp3", "A", added = Stamp(2, "A"), title = "Highway Star")
        val flac = entry("flac", "B", added = Stamp(5, "B"), title = "highway star")
        val result = merged(listOf(mp3), listOf(flac))
        assertEquals(listOf("mp3"), titles(result))
        assertEquals(result, merged(listOf(flac), listOf(mp3)))
    }

    @Test
    fun movingFindsAKeyBetweenTheNewNeighbours() {
        val entries =
            listOf(
                entry("a", position = "V"),
                entry("b", position = "W"),
                entry("c", position = "X")
            )
        val position = RideList.positionFor(entries, from = 2, to = 0)!!
        assertTrue(position < "V")
        assertEquals(null, RideList.positionFor(entries, from = 1, to = 1))
        assertEquals(null, RideList.positionFor(entries, from = 1, to = 7))
    }
}
