package com.tandemmoto.cues

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
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundCuesTest {
    private val partner = Partner("Redmi Y2", "addr", Partner.Role.Initiator, 0L)
    private val status = MutableStateFlow<LinkStatus>(LinkStatus.NotConnected(partner))
    private val playing = MutableStateFlow(false)
    private val enabled = MutableStateFlow(true)
    private val played = mutableListOf<Cue>()

    private fun TestScope.cues() {
        SoundCues(status, playing, enabled, { played += it }, backgroundScope).start()
        runCurrent()
    }

    private fun TestScope.link(value: LinkStatus) {
        status.value = value
        runCurrent()
    }

    private fun TestScope.music(on: Boolean) {
        playing.value = on
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(SoundCues.SETTLE_MS + 1)
        runCurrent()
    }

    @Test
    fun linkTransitionsGiveTheirCues() = runTest {
        cues()
        link(LinkStatus.Connecting(partner))
        link(LinkStatus.Connected(partner))
        link(LinkStatus.Reconnecting(partner))
        link(LinkStatus.Connected(partner))
        link(LinkStatus.NotConnected(partner, Reason.Disconnected))
        assertEquals(
            listOf(
                Cue.PartnerConnected,
                Cue.PartnerDisconnected,
                Cue.PartnerConnected,
                Cue.PartnerDisconnected
            ),
            played
        )
    }

    @Test
    fun forgettingThePartnerIsNotADrop() {
        assertNull(SoundCues.linkCue(LinkStatus.Connected(partner), LinkStatus.NotPaired))
        assertNull(
            SoundCues.linkCue(LinkStatus.Connecting(partner), LinkStatus.Reconnecting(partner))
        )
    }

    @Test
    fun musicTransitionsGiveTheirCues() = runTest {
        cues()
        music(true)
        settle()
        music(false)
        settle()
        assertEquals(listOf(Cue.MusicOn, Cue.MusicOff), played)
    }

    @Test
    fun noCuesForTheStartingValues() = runTest {
        status.value = LinkStatus.Connected(partner)
        playing.value = true
        cues()
        settle()
        assertEquals(emptyList<Cue>(), played)
    }

    @Test
    fun pingPongIsSuppressed() = runTest {
        cues()
        music(true)
        settle()
        played.clear()
        // A reconnect: paused, played, paused, played within a moment.
        music(false)
        music(true)
        music(false)
        music(true)
        settle()
        assertEquals(emptyList<Cue>(), played)
        music(false)
        music(true)
        music(false)
        settle()
        assertEquals(listOf(Cue.MusicOff), played)
    }

    @Test
    fun disabledIsSilent() = runTest {
        enabled.value = false
        cues()
        link(LinkStatus.Connected(partner))
        music(true)
        settle()
        link(LinkStatus.Reconnecting(partner))
        assertEquals(emptyList<Cue>(), played)
    }

    @Test
    fun tonesAreAboutAFifthOfASecond() {
        for (cue in Cue.entries) {
            val ms = Tones.pcm(cue).size * 1_000 / Tones.SAMPLE_RATE
            assert(ms in 150..250) { "$cue is $ms ms" }
        }
    }
}
