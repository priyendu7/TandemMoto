package com.tandemmoto.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tandemmoto.library.LibraryState
import com.tandemmoto.playlist.RideEntry
import com.tandemmoto.playlist.Stamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val songs = MutableStateFlow(listOf(entry("a", "0"), entry("b", "1"), entry("c", "2")))
    private val controls = mutableListOf<String>()
    private var queueChanges = 0
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
            library = MutableStateFlow(LibraryState()),
            downloaded = { null },
            linked = { true },
            storageFull = { false },
            currentIndex = MutableStateFlow(0)
        )
        playback.listener = object : PlaybackListener {
            override fun onControl(control: String) {
                controls += control
            }

            override fun onQueueChanged() {
                queueChanges++
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
}
