package com.tandemmoto.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesTest {
    private val song = Song("id-1", "u1", "Highway Star", "Deep Purple", 367_000, 8_000_000)

    @Test
    fun theSameFileIsTheSameSong() {
        assertTrue(Duplicates.sameSong(song, song.copy(uri = "u2", title = "Renamed")))
    }

    @Test
    fun theSameTrackInAnotherFileIsTheSameSong() {
        // e.g. an MP3 and a FLAC of one song, with slightly different lengths and tag spacing
        val flac = song.copy(
            id = "id-2",
            uri = "u2",
            title = "  highway   STAR ",
            durationMs = 368_900
        )
        assertTrue(Duplicates.sameSong(song, flac))
    }

    @Test
    fun durationDecidesAtTheTwoSecondEdge() {
        val tolerance = Duplicates.DURATION_TOLERANCE_MS
        val atEdge = song.copy(id = "id-2", durationMs = song.durationMs + tolerance)
        val past = song.copy(id = "id-3", durationMs = song.durationMs + tolerance + 1)
        assertTrue(Duplicates.sameSong(song, atEdge))
        assertFalse(Duplicates.sameSong(song, past)) // a live version is longer
    }

    @Test
    fun aDifferentArtistOrTitleIsADifferentSong() {
        assertFalse(Duplicates.sameSong(song, song.copy(id = "id-2", artist = "Cover Band")))
        assertFalse(Duplicates.sameSong(song, song.copy(id = "id-3", title = "Smoke on the Water")))
    }

    @Test
    fun missingArtistsMatchEachOther() {
        val a = song.copy(artist = null)
        assertTrue(Duplicates.sameSong(a, a.copy(id = "id-2")))
        assertFalse(Duplicates.sameSong(a, song.copy(id = "id-3")))
    }
}
