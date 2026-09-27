package com.tandemmoto.voice

import com.tandemmoto.service.MicAccess
import com.tandemmoto.state.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntercomRulesTest {
    @Test
    fun micModeIsLinkedWantedAndNotPlaying() {
        assertTrue(IntercomRules.micMode(linked = true, wanted = true, playing = false))
        assertFalse(IntercomRules.micMode(linked = true, wanted = true, playing = true))
        assertFalse(IntercomRules.micMode(linked = false, wanted = true, playing = false))
        // Never played this ride and no Start intercom: off (#72 decision).
        assertFalse(IntercomRules.micMode(linked = true, wanted = false, playing = false))
    }

    @Test
    fun sendingNeedsTheRouteAnUnmutedMicAndAccess() {
        assertTrue(IntercomRules.sending(true, routeReady = true, muted = false, MicAccess.Ready))
        assertFalse(IntercomRules.sending(true, routeReady = false, muted = false, MicAccess.Ready))
        assertFalse(IntercomRules.sending(true, routeReady = true, muted = true, MicAccess.Ready))
        assertFalse(
            IntercomRules.sending(true, routeReady = true, muted = false, MicAccess.NoPermission)
        )
        assertFalse(IntercomRules.sending(false, routeReady = true, muted = false, MicAccess.Ready))
    }

    @Test
    fun lines() {
        val open = RouteState.Open(RouteResult(RouteKind.WiredHeadset))
        val fellBack = RouteState.Open(RouteResult(RouteKind.Phone, fellBack = true))
        assertEquals(IntercomLine.NotLinked, IntercomRules.line(false, true, false, open))
        assertEquals(IntercomLine.Ready, IntercomRules.line(true, false, false, RouteState.Closed))
        assertEquals(
            IntercomLine.WaitingForPause,
            IntercomRules.line(true, true, true, RouteState.Closed)
        )
        assertEquals(
            IntercomLine.Connecting,
            IntercomRules.line(true, true, false, RouteState.Opening)
        )
        assertEquals(IntercomLine.On, IntercomRules.line(true, true, false, open))
        assertEquals(IntercomLine.OnPhone, IntercomRules.line(true, true, false, fellBack))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class IntercomTest {
    private class FakeRoute : AudioRoute {
        var gate: CompletableDeferred<RouteResult>? = null
        var opens = 0
        var closes = 0
        val open: Boolean get() = opens > closes

        override suspend fun open(): RouteResult {
            opens++
            return gate?.await() ?: RouteResult(RouteKind.WiredHeadset)
        }

        override fun close() {
            closes++
        }
    }

    private class FakeMute(initial: Boolean = false) : MuteStore {
        private val flow = MutableStateFlow(initial)
        override val muted: StateFlow<Boolean> = flow

        override fun set(muted: Boolean) {
            flow.value = muted
        }
    }

    private val linked = MutableStateFlow(true)
    private val wanted = MutableStateFlow(false)
    private val playing = MutableStateFlow(false)
    private val access = MutableStateFlow(MicAccess.Ready)
    private val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 8)
    private val sentMessages = mutableListOf<Message>()
    private val route = FakeRoute()
    private var sending = false

    private fun TestScope.intercom(mute: MuteStore = FakeMute()) = Intercom(
        linked = linked,
        wanted = wanted,
        playing = playing,
        micAccess = access,
        mute = mute,
        incoming = incoming,
        send = {
            sentMessages += it
            true
        },
        route = route,
        setSending = { sending = it },
        scope = backgroundScope
    ).also {
        it.start()
        runCurrent()
    }

    @Test
    fun beforeTheFirstPauseOrStartItsOff() = runTest {
        val intercom = intercom()
        assertEquals(IntercomLine.Ready, intercom.state.value.line)
        assertEquals(0, route.opens)
        assertFalse(sending)
    }

    @Test
    fun pausingOpensTheRouteThenSends() = runTest {
        val intercom = intercom()
        wanted.value = true // music played this ride
        runCurrent()
        assertTrue(route.open)
        assertTrue(sending)
        assertEquals(IntercomLine.On, intercom.state.value.line)
        assertEquals(RouteKind.WiredHeadset, intercom.state.value.route)
    }

    @Test
    fun whileTheEarbudsSwitchItSaysConnectingAndDoesntSendYet() = runTest {
        route.gate = CompletableDeferred()
        val intercom = intercom()
        wanted.value = true
        runCurrent()
        assertEquals(IntercomLine.Connecting, intercom.state.value.line)
        assertFalse(sending)
        route.gate!!.complete(RouteResult(RouteKind.BluetoothEarbuds, switchMs = 900))
        runCurrent()
        assertEquals(IntercomLine.On, intercom.state.value.line)
        assertTrue(sending)
    }

    @Test
    fun everyWayOutReleasesTheMicAndTheRoute() = runTest {
        val intercom = intercom()
        wanted.value = true
        runCurrent()
        playing.value = true // music resumes
        runCurrent()
        assertFalse(sending)
        assertFalse(route.open)
        assertEquals(IntercomLine.WaitingForPause, intercom.state.value.line)

        playing.value = false
        runCurrent()
        assertTrue(sending)
        linked.value = false // the link drops
        runCurrent()
        assertFalse(sending)
        assertFalse(route.open)

        linked.value = true
        runCurrent()
        assertTrue(sending)
        wanted.value = false // Stop intercom or Disconnect
        runCurrent()
        assertFalse(sending)
        assertFalse(route.open)
    }

    @Test
    fun musicResumingWhileTheEarbudsSwitchClosesTheRoute() = runTest {
        route.gate = CompletableDeferred()
        intercom()
        wanted.value = true
        runCurrent()
        playing.value = true
        runCurrent()
        assertFalse(route.open)
        assertFalse(sending)
    }

    @Test
    fun muteReleasesTheMicButKeepsHearingThePartner() = runTest {
        val intercom = intercom()
        wanted.value = true
        runCurrent()
        intercom.setMuted(true)
        runCurrent()
        assertFalse(sending)
        assertTrue("the route stays for the partner's voice", route.open)
        assertTrue(intercom.state.value.muted)
        assertEquals(Message.Muted(true), sentMessages.last())
        intercom.setMuted(false)
        runCurrent()
        assertTrue(sending)
    }

    @Test
    fun noMicModeChangeTouchesMute() = runTest {
        val mute = FakeMute(initial = true)
        val intercom = intercom(mute)
        wanted.value = true
        runCurrent()
        playing.value = true
        runCurrent()
        linked.value = false
        runCurrent()
        linked.value = true
        wanted.value = false
        runCurrent()
        assertTrue(mute.muted.value)
        assertTrue(intercom.state.value.muted)
        assertFalse(sending)
    }

    @Test
    fun theMuteIsToldOnEveryConnection() = runTest {
        intercom(FakeMute(initial = true))
        assertEquals(listOf<Message>(Message.Muted(true)), sentMessages)
        linked.value = false
        runCurrent()
        linked.value = true
        runCurrent()
        assertEquals(Message.Muted(true), sentMessages.last())
        assertEquals(2, sentMessages.size)
    }

    @Test
    fun thePartnersMuteShowsUntilTheLinkDrops() = runTest {
        val intercom = intercom()
        incoming.emit(Message.Muted(true))
        runCurrent()
        assertTrue(intercom.state.value.partnerMuted)
        linked.value = false
        runCurrent()
        assertFalse(intercom.state.value.partnerMuted)
    }

    @Test
    fun withoutMicAccessItListensButDoesntSend() = runTest {
        access.value = MicAccess.OpenAppFirst
        intercom()
        wanted.value = true
        runCurrent()
        assertTrue(route.open)
        assertFalse(sending)
        access.value = MicAccess.Ready // the app came on screen
        runCurrent()
        assertTrue(sending)
    }
}
