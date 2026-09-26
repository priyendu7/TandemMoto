package com.tandemmoto.player

import com.tandemmoto.link.ChannelState
import com.tandemmoto.playlist.Stamp
import com.tandemmoto.state.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackMirrorTest {
    private class FakePlayer : LocalPlayer {
        var now = PlayerPosition("a", playing = false, positionMs = 0)
        val queue = mutableListOf("a", "b", "c")
        val applied = mutableListOf<PlayerPosition>()

        override fun position() = now

        override fun hasSong(songId: String) = songId in queue

        override fun apply(songId: String, positionMs: Long, playing: Boolean) {
            now = PlayerPosition(songId, playing, positionMs)
            applied += now
        }
    }

    private val hello = Message.Hello("1", "them", "me", "Initiator")
    private val player = FakePlayer()
    private val channel = MutableStateFlow<ChannelState>(ChannelState.Idle)
    private val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    private val sent = mutableListOf<Message.PlaybackState>()
    private val offset = MutableStateFlow<Long?>(null)

    /** This phone's clock in nanoseconds: the test scheduler's virtual time. */
    private var nowNanos = 0L

    private fun TestScope.mirror(): PlaybackMirror = PlaybackMirror(
        player = player,
        installId = { "me" },
        channelState = channel,
        incoming = incoming,
        send = { message ->
            sent += message as Message.PlaybackState
            true
        },
        clockOffsetNanos = offset,
        scope = backgroundScope,
        nanoTime = { nowNanos }
    ).also {
        it.start()
        runCurrent()
    }

    private fun theirs(
        songId: String?,
        playing: Boolean,
        positionMs: Long,
        clock: Long,
        atNanos: Long = 0,
        by: String = "them"
    ) = Message.PlaybackState(songId, playing, positionMs, atNanos, Stamp(clock, by), "Test")

    @Test
    fun aControlSendsTheResultWithANewStamp() = runTest {
        val mirror = mirror()
        player.now = PlayerPosition("b", playing = true, positionMs = 1_000)
        nowNanos = 5
        mirror.onControl("Next")
        runCurrent()
        assertEquals(
            listOf(Message.PlaybackState("b", true, 1_000, 5, Stamp(1, "me"), "Next")),
            sent
        )
        mirror.onControl("Pause")
        runCurrent()
        assertEquals(Stamp(2, "me"), sent.last().stamp)
    }

    @Test
    fun aNewerPartnerStateIsAppliedAndNotSentBack() = runTest {
        mirror()
        incoming.emit(theirs("c", playing = false, positionMs = 30_000, clock = 3))
        runCurrent()
        assertEquals(listOf(PlayerPosition("c", false, 30_000)), player.applied)
        assertTrue("nothing echoed", sent.isEmpty())
    }

    @Test
    fun anOlderOrEqualStateChangesNothing() = runTest {
        val mirror = mirror()
        incoming.emit(theirs("c", playing = true, positionMs = 0, clock = 3))
        runCurrent()
        mirror.onControl("Pause") // stamp 4, me
        runCurrent()
        incoming.emit(theirs("a", playing = true, positionMs = 0, clock = 4, by = "aaa"))
        incoming.emit(theirs("b", playing = true, positionMs = 0, clock = 2))
        runCurrent()
        assertEquals(1, player.applied.size)
    }

    @Test
    fun simultaneousControlsEndTheSameWayOnBothPhones() = runTest {
        val mirror = mirror()
        mirror.onControl("Pause") // Stamp(1, "me")
        runCurrent()
        // The partner pressed Next at the same moment: Stamp(1, "them") > Stamp(1, "me").
        incoming.emit(theirs("b", playing = true, positionMs = 0, clock = 1))
        runCurrent()
        assertEquals(PlayerPosition("b", true, 0), player.now)
        // On their phone ours loses the tie; it applies nothing (checked by stamp order).
        assertTrue(Stamp(1, "me") < Stamp(1, "them"))
    }

    @Test
    fun aPartnerStateWeHaveSeenBumpsOurClock() = runTest {
        val mirror = mirror()
        incoming.emit(theirs("c", playing = true, positionMs = 0, clock = 7))
        runCurrent()
        mirror.onControl("Pause")
        runCurrent()
        assertEquals(Stamp(8, "me"), sent.last().stamp)
    }

    @Test
    fun aPlayingSongIsJoinedWhereItIsNowUsingTheClockOffset() = runTest {
        mirror()
        // The partner's clock is 1 s ahead; it pressed Play at its 10 s, at 60 s into the song.
        offset.value = 1_000_000_000
        nowNanos = 9_200_000_000 // our 9.2 s = its 10.2 s: 200 ms later
        incoming.emit(
            theirs("b", playing = true, positionMs = 60_000, clock = 1, atNanos = 10_000_000_000)
        )
        runCurrent()
        assertEquals(PlayerPosition("b", true, 60_200), player.now)
    }

    @Test
    fun aPauseIsJoinedAtTheExactPosition() = runTest {
        mirror()
        offset.value = 0
        nowNanos = 500_000_000
        incoming.emit(theirs("b", playing = false, positionMs = 42_000, clock = 1))
        runCurrent()
        assertEquals(PlayerPosition("b", false, 42_000), player.now)
    }

    @Test
    fun withoutAClockOffsetThePositionIsTakenAsIs() = runTest {
        mirror()
        nowNanos = 9_000_000_000
        incoming.emit(theirs("b", playing = true, positionMs = 5_000, clock = 1))
        runCurrent()
        assertEquals(PlayerPosition("b", true, 5_000), player.now)
    }

    @Test
    fun onConnectBothSendWhereTheyAreWithTheirLatestStamp() = runTest {
        val mirror = mirror()
        mirror.onControl("Play")
        runCurrent()
        sent.clear()
        player.now = PlayerPosition("a", playing = true, positionMs = 90_000)
        channel.value = ChannelState.Open(hello)
        runCurrent()
        assertEquals(
            Message.PlaybackState("a", true, 90_000, 0, Stamp(1, "me"), "Connect"),
            sent.single()
        )
    }

    @Test
    fun anAppJustStartedSendsClockZeroAndFollowsThePartner() = runTest {
        mirror()
        channel.value = ChannelState.Open(hello)
        runCurrent()
        assertEquals(Stamp(0, "me"), sent.single().stamp)
        // The partner's clock-0 state isn't a control: nothing to follow.
        incoming.emit(theirs("c", playing = false, positionMs = 0, clock = 0))
        runCurrent()
        assertTrue(player.applied.isEmpty())
        // Its playing state is.
        incoming.emit(theirs("b", playing = true, positionMs = 0, clock = 5))
        runCurrent()
        assertEquals("b", player.now.songId)
    }

    @Test
    fun aSongNotInTheQueueYetIsAppliedWhenItArrives() = runTest {
        val mirror = mirror()
        incoming.emit(theirs("new", playing = true, positionMs = 0, clock = 2))
        runCurrent()
        assertTrue(player.applied.isEmpty())
        mirror.onQueueChanged()
        assertTrue("still not here", player.applied.isEmpty())
        player.queue += "new"
        mirror.onQueueChanged()
        assertEquals("new", player.now.songId)
    }

    @Test
    fun aLocalControlDropsAWaitingPartnerState() = runTest {
        val mirror = mirror()
        incoming.emit(theirs("new", playing = true, positionMs = 0, clock = 2))
        runCurrent()
        mirror.onControl("Next")
        runCurrent()
        player.queue += "new"
        mirror.onQueueChanged()
        assertTrue(player.applied.isEmpty())
    }

    @Test
    fun aStateWithNoSongChangesNothing() = runTest {
        mirror()
        incoming.emit(theirs(null, playing = false, positionMs = 0, clock = 3))
        runCurrent()
        assertTrue(player.applied.isEmpty())
    }
}
