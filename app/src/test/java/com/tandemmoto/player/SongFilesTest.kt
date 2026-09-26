package com.tandemmoto.player

import com.tandemmoto.library.LibraryState
import com.tandemmoto.library.Song
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class SongFilesTest {
    private val library = LibraryState(
        songs = listOf(
            Song("mine", "content://mine", "Mine", null, 1, 1),
            Song("mycopy", "content://copy", "Copy", null, 1, 1)
        ),
        copyOf = mapOf("theirs" to "mycopy")
    )
    private val downloaded = File("/data/partner_songs/fetched")

    private fun find(id: String) =
        SongFiles.find(id, library) { if (it == "fetched") downloaded else null }

    @Test
    fun ownSongThenCopyThenDownloadedThenNotHere() {
        assertEquals(SongFileState.Ready("content://mine"), find("mine"))
        assertEquals(SongFileState.Ready("content://copy"), find("theirs"))
        assertEquals(SongFileState.Ready(downloaded.toURI().toString()), find("fetched"))
        assertEquals(SongFileState.NotHere, find("missing"))
    }

    @Test
    fun theNextSongWaitsItsTurnWithoutCountingDown() {
        assertEquals(
            WaitDecision.Wait,
            WaitRules.decide(false, 10 * 60_000, linked = false, storageFull = true)
        )
    }

    @Test
    fun theCurrentSongGivesUpWhenItCannotArrive() {
        val notLinked = WaitRules.NOT_LINKED_WAIT_MS
        assertEquals(
            WaitDecision.Wait,
            WaitRules.decide(true, notLinked - 1, linked = false, storageFull = false)
        )
        assertEquals(
            WaitDecision.GiveUp,
            WaitRules.decide(true, notLinked, linked = false, storageFull = false)
        )
        assertEquals(
            WaitDecision.GiveUp,
            WaitRules.decide(true, 0, linked = true, storageFull = true)
        )
        assertEquals(
            WaitDecision.Wait,
            WaitRules.decide(true, notLinked * 2, linked = true, storageFull = false)
        )
        assertEquals(
            WaitDecision.GiveUp,
            WaitRules.decide(true, WaitRules.LINKED_WAIT_MS, linked = true, storageFull = false)
        )
    }
}
