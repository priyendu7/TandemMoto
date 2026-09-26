package com.tandemmoto.service

import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.LinkStatus.NotConnected.Reason
import com.tandemmoto.link.Partner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkSessionTest {
    private val partner = Partner("Redmi Y2", "addr", Partner.Role.Initiator, 0L)
    private val status = MutableStateFlow<LinkStatus>(LinkStatus.NotConnected(partner))
    private var starts = 0
    private var stops = 0

    private fun TestScope.session() = LinkSession(
        status = status,
        scope = backgroundScope,
        start = { starts++ },
        stop = { stops++ }
    ).also {
        it.begin()
        runCurrent()
    }

    private fun TestScope.set(value: LinkStatus) {
        status.value = value
        runCurrent()
    }

    @Test
    fun startsOnTheFirstConnectionNotBefore() = runTest {
        val session = session()
        set(LinkStatus.Connecting(partner))
        assertEquals(0, starts)
        set(LinkStatus.Connected(partner))
        assertEquals(1, starts)
        assertTrue(session.running)
    }

    @Test
    fun keepsRunningThroughShortDrops() = runTest {
        session()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.NotConnected(partner, Reason.WifiOff))
        advanceTimeBy(LinkSession.IDLE_STOP_MS - 1_000)
        set(LinkStatus.Connected(partner))
        advanceTimeBy(LinkSession.IDLE_STOP_MS * 2)
        assertEquals(1, starts)
        assertEquals(0, stops)
    }

    @Test
    fun stopsAfterTheIdleWindowNotConnected() = runTest {
        val session = session()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.NotConnected(partner))
        advanceTimeBy(LinkSession.IDLE_STOP_MS - 1)
        assertEquals(0, stops)
        advanceTimeBy(2)
        assertEquals(1, stops)
        assertFalse(session.running)

        set(LinkStatus.Connected(partner)) // connected again: runs again
        assertEquals(2, starts)
    }

    @Test
    fun aConnectionAttemptPausesTheIdleWindow() = runTest {
        session()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.NotConnected(partner))
        advanceTimeBy(LinkSession.IDLE_STOP_MS - 1_000)
        set(LinkStatus.Connecting(partner))
        advanceTimeBy(LinkSession.IDLE_STOP_MS)
        assertEquals(0, stops)
    }

    @Test
    fun reconnectingKeepsItRunning() = runTest {
        session()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.NotConnected(partner))
        advanceTimeBy(LinkSession.IDLE_STOP_MS - 1_000)
        set(LinkStatus.Reconnecting(partner))
        advanceTimeBy(LinkSession.IDLE_STOP_MS)
        assertEquals(0, stops)
    }

    @Test
    fun disconnectStopsAtOnce() = runTest {
        val session = session()
        set(LinkStatus.Connected(partner))
        session.stopNow()
        assertEquals(1, stops)
        set(LinkStatus.NotConnected(partner))
        advanceTimeBy(LinkSession.IDLE_STOP_MS * 2)
        assertEquals(1, stops) // not stopped twice
    }

    @Test
    fun forgettingThePartnerStopsIt() = runTest {
        session()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.NotPaired)
        assertEquals(1, stops)
    }

    @Test
    fun playingMusicKeepsItRunningWithoutTheLink() = runTest {
        // #51: one service for the link and the music.
        val playing = MutableStateFlow(false)
        val session =
            LinkSession(status, backgroundScope, { starts++ }, { stops++ }, playing = playing)
        session.begin()
        runCurrent()
        playing.value = true
        runCurrent()
        assertEquals(1, starts)
        assertTrue(session.running)
        assertFalse(session.linkWanted)
        playing.value = false
        runCurrent()
        assertEquals(1, stops)
    }

    @Test
    fun disconnectLeavesTheMusicPlaying() = runTest {
        val playing = MutableStateFlow(true)
        val session =
            LinkSession(status, backgroundScope, { starts++ }, { stops++ }, playing = playing)
        session.begin()
        runCurrent()
        set(LinkStatus.Connected(partner))
        session.stopNow()
        runCurrent()
        assertEquals(0, stops) // still playing
        assertTrue(session.running)
        playing.value = false
        runCurrent()
        assertEquals(1, stops)
    }

    @Test
    fun nothingToStopWhenItNeverStarted() = runTest {
        val session = session()
        set(LinkStatus.NotPaired)
        session.stopNow()
        assertEquals(0, stops)
    }
}
