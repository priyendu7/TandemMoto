package com.tandemmoto.ui

import com.tandemmoto.library.LibraryState
import com.tandemmoto.library.PendingSong
import com.tandemmoto.library.Song
import com.tandemmoto.link.Partner
import com.tandemmoto.playlist.RideEntry
import com.tandemmoto.playlist.RidePlaylistState
import com.tandemmoto.playlist.Stamp
import com.tandemmoto.ui.playlist.PlaylistRow
import com.tandemmoto.ui.playlist.Sharing
import com.tandemmoto.ui.playlist.playlistUi
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistUiMappingTest {
    private fun entry(id: String, owner: String) =
        RideEntry(id, id, null, 1_000, 1, owner, Stamp(1, owner), "V", Stamp(1, owner))

    private val partner = Partner("Redmi", "addr", Partner.Role.Initiator, 0L)
    private val ride = RidePlaylistState(
        songs = listOf(entry("mine", "me"), entry("copy", "them"), entry("theirs", "them")),
        me = "me",
        loaded = true
    )
    private val library = LibraryState(
        songs = listOf(
            Song("mine", "u", "mine", null, 1_000, 1),
            Song("mycopy", "u2", "copy", null, 1_000, 1)
        ),
        missing = setOf("mine"),
        reading = listOf(PendingSong("u3", "new.mp3")),
        copyOf = mapOf("copy" to "mycopy"),
        loaded = true
    )

    @Test
    fun entriesSayWhoseTheyAreAndWhetherThisPhoneHasThem() {
        val ui = playlistUi(ride, library, partner, connected = true, fileSlotsLeft = 10)
        val rows = ui.rows.filterIsInstance<PlaylistRow.Entry>()
        assertEquals(listOf(true, false, false), rows.map { it.mine })
        assertEquals(listOf(true, true, false), rows.map { it.onThisPhone })
        assertEquals(listOf(true, false, false), rows.map { it.missing })
        assertEquals(PlaylistRow.Reading("u3", "new.mp3"), ui.rows.last())
        assertEquals(Sharing.Shared, ui.sharing)
    }

    @Test
    fun sharingFollowsThePartnerAndTheLink() {
        assertEquals(Sharing.NotPaired, playlistUi(ride, library, null, false, 10).sharing)
        assertEquals(Sharing.WaitingToSync, playlistUi(ride, library, partner, false, 10).sharing)
    }
}
