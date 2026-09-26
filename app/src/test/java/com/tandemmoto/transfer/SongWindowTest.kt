package com.tandemmoto.transfer

import com.tandemmoto.playlist.RideEntry
import com.tandemmoto.playlist.Stamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongWindowTest {
    private val mb = 1024L * 1024
    private val plenty = 10_000 * mb

    /** Songs "0".."n-1"; [mine] are this phone's, the rest the partner's. 10 MB each. */
    private fun playlist(n: Int, mine: Set<Int> = emptySet(), size: Long = 10 * mb) =
        (0 until n).map {
            val owner = if (it in mine) "me" else "them"
            RideEntry(
                "$it", "Song $it", null, 200_000, size, owner,
                Stamp(
                    1,
                    owner
                ),
                "V$it", Stamp(1, owner)
            )
        }

    private fun plan(
        songs: List<RideEntry>,
        current: Int = 0,
        settings: WindowSettings = WindowSettings(),
        downloaded: Map<String, Long> = emptyMap(),
        own: Set<String> = emptySet(),
        free: Long = plenty
    ) = SongWindow.plan(songs, current, "me", settings, downloaded, { it in own }, free)

    private fun WindowPlan.ids() = wanted.map { it.id }

    @Test
    fun theCurrentSongThenAheadNearestFirstThenBehind() {
        val result = plan(playlist(20), current = 5)
        assertEquals(listOf("5", "6", "7", "8", "9", "10", "11", "12", "4", "3"), result.ids())
    }

    @Test
    fun ownSongsAndCopiesAreNeverDownloadedButStillCountAsPlaces() {
        val result = plan(playlist(12, mine = setOf(1, 2)), own = setOf("3"))
        // Places 1-7 ahead: 1, 2 mine and 3 a copy, so only 4-7 download.
        assertEquals(listOf("0", "4", "5", "6", "7"), result.ids())
    }

    @Test
    fun songsOutsideTheWindowAreDeleted() {
        val downloaded = mapOf("0" to 10 * mb, "15" to 10 * mb)
        val result = plan(playlist(20), current = 5, downloaded = downloaded)
        assertEquals(setOf("0", "15"), result.delete)
    }

    @Test
    fun theSettingsSizeTheWindow() {
        val result =
            plan(playlist(20), current = 5, settings = WindowSettings(behind = 0, ahead = 3))
        assertEquals(listOf("5", "6", "7", "8"), result.ids())
    }

    @Test
    fun lowSpaceCutsTheSongsBehindFirstThenTheFarEndAhead() {
        // 50 MB above the 500 MB floor: 5 songs of 10 MB.
        val result = plan(playlist(20), current = 5, free = SongWindow.FLOOR_FREE_BYTES + 50 * mb)
        assertEquals(listOf("5", "6", "7", "8", "9"), result.ids())
        assertEquals(4, result.aheadLimitedTo)
        assertFalse(result.storageFull)
    }

    @Test
    fun storedSongsInTheWindowDontNeedMoreSpace() {
        val stored = (5..9).associate { "$it" to 10 * mb }
        val result = plan(
            playlist(20),
            current = 5,
            downloaded = stored,
            free = SongWindow.FLOOR_FREE_BYTES + 20 * mb
        )
        // 50 MB already here + 20 MB free above the floor: 7 songs.
        assertEquals(listOf("5", "6", "7", "8", "9", "10", "11"), result.ids())
    }

    @Test
    fun belowTheFloorTheCurrentSongStillComesInOnDemand() {
        val result = plan(playlist(5), free = SongWindow.HARD_MIN_FREE_BYTES + 30 * mb)
        assertEquals(listOf("0"), result.ids())
        assertTrue(result.currentOnDemand)
        assertEquals(0, result.aheadLimitedTo)
    }

    @Test
    fun underTheHardMinimumTheStorageIsFull() {
        val result = plan(playlist(5), free = SongWindow.HARD_MIN_FREE_BYTES + 5 * mb)
        assertTrue(result.wanted.isEmpty())
        assertTrue(result.storageFull)
    }

    @Test
    fun anEmptyPlaylistDeletesEverything() {
        val result = plan(emptyList(), downloaded = mapOf("x" to mb))
        assertTrue(result.wanted.isEmpty())
        assertEquals(setOf("x"), result.delete)
        assertNull(result.aheadLimitedTo)
    }

    @Test
    fun aCurrentPlaceBeyondTheEndIsClamped() {
        assertEquals(listOf("4", "3", "2"), plan(playlist(5), current = 9).ids())
    }
}
