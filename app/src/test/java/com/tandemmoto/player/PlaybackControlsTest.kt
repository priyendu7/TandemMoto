package com.tandemmoto.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tandemmoto.library.LibraryState
import com.tandemmoto.library.Song
import com.tandemmoto.playlist.RideEntry
import com.tandemmoto.playlist.Stamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every control reaches the mirror, the media session's too; the partner's state doesn't (#60). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackControlsTest {
    // The main thread, like the app: the player may only be used there.
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val songs = MutableStateFlow(listOf(entry("a", "0"), entry("b", "1"), entry("c", "2")))
    private val controls = mutableListOf<String>()
    private var queueChanges = 0
    private var linked = false
    private var partner = PartnerSongs()
    private lateinit var playback: Playback

    private fun entry(id: String, position: String): RideEntry {
        val stamp = Stamp(1, "me")
        return RideEntry(id, "Song $id", null, 180_000, 1_000, "me", stamp, position, stamp)
    }

    @Before
    fun setUp() {
        playback = Playback(
            context = ApplicationProvider.getApplicationContext<Context>(),
            scope = scope,
            songs = songs,
            // This phone's own songs: all here.
            library = MutableStateFlow(
                LibraryState(
                    songs = listOf("a", "b", "c", "z").map { Song(it, "uri-$it", it, null, 1, 1) },
                    loaded = true
                )
            ),
            downloaded = { null },
            linked = { linked },
            storageFull = { false },
            currentIndex = MutableStateFlow(0),
            partnerSongs = { partner }
        )
        playback.listener = object : PlaybackListener {
            override fun onControl(control: String) {
                controls += control
            }

            override fun onQueueChanged() {
                queueChanges++
            }

            override fun onStartTogether(): Long {
                controls += "StartTogether"
                return 0
            }
        }
        playback.start()
    }

    @After
    fun tearDown() {
        playback.release()
        scope.cancel()
    }

    @Test
    fun homeControlsAreMirrored() {
        playback.play()
        playback.next()
        playback.seekTo(30_000)
        playback.previous()
        playback.pause()
        playback.playAt(2)
        assertEquals(listOf("Play", "Next", "Seek", "Previous", "Pause", "PlaylistTap"), controls)
        assertEquals("c", playback.position().songId)
    }

    @Test
    fun theMediaSessionsCommandsAreMirroredToo() {
        val lockScreen = playback.session.player
        lockScreen.play()
        lockScreen.seekToNext()
        lockScreen.seekTo(10_000)
        lockScreen.pause()
        assertEquals(listOf("Play", "Next", "Seek", "Pause"), controls)
        assertEquals(PlayerPosition("b", false, 10_000), playback.position())
    }

    @Test
    fun thePartnersStateIsAppliedQuietly() {
        playback.apply("c", 42_000, playing = false)
        assertTrue(controls.isEmpty())
        assertEquals(PlayerPosition("c", false, 42_000), playback.position())
        playback.apply("b", 5_000, playing = true)
        assertTrue(controls.isEmpty())
        assertEquals(PlayerPosition("b", true, 5_000), playback.position())
    }

    @Test
    fun theQueueKnowsItsSongs() {
        assertTrue(playback.hasSong("b"))
        assertFalse(playback.hasSong("z"))
        val before = queueChanges
        songs.value = songs.value + entry("z", "3")
        assertTrue(playback.hasSong("z"))
        assertTrue(queueChanges > before)
    }

    @Test
    fun linkedASongThePartnerHasntGotHoldsWantingToPlay() {
        linked = true
        playback.play()
        val state = playback.state.value
        assertTrue(state.playWhenReady)
        assertFalse(playback.player.playWhenReady)
        assertEquals(MissingOn.Partner, state.waitingOn)
        assertEquals(PlayerPosition("a", true, 0, waiting = true), playback.position())
    }

    @Test
    fun whenThePartnerGetsItBothStartTogether() {
        linked = true
        playback.play()
        partner = PartnerSongs(has = setOf("a"))
        playback.recheck()
        assertEquals(listOf("Play", "StartTogether"), controls)
        assertTrue(playback.player.playWhenReady)
        assertNull(playback.state.value.waitingOn)
    }

    @Test
    fun onBothPhonesItPlaysAtOnce() {
        linked = true
        partner = PartnerSongs(has = setOf("a", "b", "c"))
        playback.play()
        assertTrue(playback.player.playWhenReady)
        assertNull(playback.state.value.waitingOn)
    }

    @Test
    fun pauseStopsWaiting() {
        linked = true
        playback.play()
        playback.pause()
        assertNull(playback.state.value.waitingOn)
        assertFalse(playback.state.value.playWhenReady)
    }

    @Test
    fun theLinkDroppingWhileHeldPlaysOnThisPhone() {
        linked = true
        playback.play()
        linked = false
        playback.recheck()
        assertTrue(playback.player.playWhenReady)
        assertEquals(listOf("Play"), controls) // no start together to send
    }

    @Test
    fun aSongThePartnerCantPlayIsSkippedOnPlay() {
        linked = true
        partner = PartnerSongs(has = setOf("a", "b", "c"), cantPlay = setOf("a"))
        playback.play()
        assertEquals("b", playback.position().songId)
        assertTrue(playback.player.playWhenReady)
    }

    @Test
    fun thePartnerPlayingASongMeansItHasIt() {
        linked = true
        playback.apply("b", 0, playing = true, partnerPlaying = true)
        assertTrue(playback.player.playWhenReady)
        assertTrue(controls.isEmpty())
    }

    @Test
    fun aStartAheadWaitsThenPlays() {
        linked = true
        partner = PartnerSongs(has = setOf("b"))
        playback.apply("b", 0, playing = true, startInMs = 300, partnerPlaying = true)
        assertFalse(playback.player.playWhenReady)
        assertTrue(playback.state.value.playWhenReady)
    }
}
