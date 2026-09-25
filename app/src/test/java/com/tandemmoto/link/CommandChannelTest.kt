package com.tandemmoto.link

import com.tandemmoto.state.Message
import com.tandemmoto.state.Message.Bye
import com.tandemmoto.state.MessageCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandChannelTest {
    private val ourHello = Message.Hello("0.1.0", "our-install", "partner-install", "Initiator")
    private val logs = mutableListOf<String>()
    private val awake = mutableListOf<Boolean>()
    private val client = Endpoint(isGroupOwner = false, ownerHost = "192.168.49.1")
    private val owner = Endpoint(isGroupOwner = true, ownerHost = "192.168.49.1")

    private fun TestScope.channel(transport: FrameTransport) = CommandChannel(
        transport = transport,
        scope = backgroundScope,
        nanoTime = { testScheduler.currentTime * 1_000_000 },
        log = { logs += it },
        keepAwake = { awake += it }
    )

    private fun CommandChannel.openAccepting(endpoint: Endpoint = client) =
        open(endpoint, { ourHello }) { null }

    @Test
    fun helloFromBothSidesOpensTheChannel() = runTest {
        val partner = FakePartnerApp(backgroundScope)
        val channel = channel(FakeTransport(partner))
        channel.openAccepting()
        runCurrent()
        assertEquals(ChannelState.Open(partner.hello), channel.state.value)
        assertEquals(listOf(ourHello), partner.hellosReceived)
        assertEquals(listOf(true), awake)
    }

    @Test
    fun theGroupOwnerWaitsForThePartnerToConnect() = runTest {
        val transport = FakeTransport()
        val channel = channel(transport)
        channel.openAccepting(owner)
        advanceTimeBy(30_000)
        assertEquals(ChannelState.Opening, channel.state.value)

        transport.partnerApp.value = FakePartnerApp(backgroundScope)
        runCurrent()
        assertTrue(channel.state.value is ChannelState.Open)
    }

    @Test
    fun theClientRetriesFastThenSlowly() = runTest {
        val transport = FakeTransport() // ENETUNREACH / refused until the partner listens
        val channel = channel(transport)
        channel.openAccepting()
        advanceTimeBy(CommandChannel.FAST_RETRY_MS * CommandChannel.FAST_RETRIES - 1)
        assertEquals(CommandChannel.FAST_RETRIES, transport.connectCalls)
        advanceTimeBy(CommandChannel.SLOW_RETRY_MS * 5)
        assertEquals(CommandChannel.FAST_RETRIES + 5, transport.connectCalls)

        transport.partnerApp.value = FakePartnerApp(backgroundScope)
        advanceTimeBy(CommandChannel.SLOW_RETRY_MS)
        assertTrue(channel.state.value is ChannelState.Open)
    }

    @Test
    fun heartbeatsAreAnsweredAndRoundTripsLogged() = runTest {
        val partner = FakePartnerApp(backgroundScope)
        val channel = channel(FakeTransport(partner))
        channel.openAccepting()
        advanceTimeBy(CommandChannel.STATS_EVERY_MS + 1)
        val pings = (CommandChannel.STATS_EVERY_MS / CommandChannel.HEARTBEAT_MS).toInt()
        assertEquals(pings, partner.pingsReceived)
        assertTrue(logs.toString(), logs.any { it.startsWith("RTT median 0 ms") })
        assertTrue(channel.state.value is ChannelState.Open)
    }

    @Test
    fun weAnswerThePartnersPings() = runTest {
        val (ours, theirs) = connectionPair()
        val channel = channel(SingleConnection(ours))
        channel.openAccepting()
        runCurrent()
        assertEquals(ourHello, theirs.receiveMessage())
        theirs.sendMessage(Message.Ping(42))
        runCurrent()
        assertEquals(Message.Pong(42), theirs.receiveMessage())
    }

    @Test
    fun silenceIsDetectedAfterTheTimeout() = runTest {
        val partner = FakePartnerApp(backgroundScope)
        val channel = channel(FakeTransport(partner))
        channel.openAccepting()
        runCurrent()
        partner.answersPings = false // frozen, but the socket stays up
        advanceTimeBy(CommandChannel.SILENCE_TIMEOUT_MS - 400)
        assertTrue(channel.state.value is ChannelState.Open)
        advanceTimeBy(600)
        assertEquals(ChannelState.Opening, channel.state.value)
        assertTrue(logs.any { it.startsWith("Channel lost: nothing heard") })
        assertEquals(ChannelLoss.Vanished, channel.lastLoss)
        assertEquals(false, awake.last())
    }

    @Test
    fun aClosedSocketIsDetectedAtOnceAndReconnected() = runTest {
        val partner = FakePartnerApp(backgroundScope)
        val transport = FakeTransport(partner)
        val channel = channel(transport)
        channel.openAccepting()
        runCurrent()
        transport.partnerApp.value = null
        partner.quit() // the partner's app was closed
        runCurrent()
        assertEquals(ChannelState.Opening, channel.state.value)
        assertTrue(logs.any { it == "Channel lost: closed by the partner" })
        assertEquals(ChannelLoss.ClosedByPartner, channel.lastLoss)

        transport.partnerApp.value = FakePartnerApp(backgroundScope) // reopened
        advanceTimeBy(CommandChannel.SLOW_RETRY_MS + 1)
        assertTrue(channel.state.value is ChannelState.Open)
    }

    @Test
    fun aRefusedHelloEndsTheChannelWithoutRetrying() = runTest {
        val partner = FakePartnerApp(backgroundScope, refuseWith = Bye.Reason.NotYourPartner)
        val transport = FakeTransport(partner)
        val channel = channel(transport)
        channel.openAccepting()
        advanceTimeBy(10_000)
        assertEquals(ChannelState.Refused(Bye.Reason.NotYourPartner), channel.state.value)
        assertEquals(1, transport.connectCalls)
    }

    @Test
    fun weRefuseAHelloOurCheckRejects() = runTest {
        val partner = FakePartnerApp(backgroundScope)
        val channel = channel(FakeTransport(partner))
        channel.open(client, { ourHello }) { Bye.Reason.NotYourPartner }
        runCurrent()
        assertEquals(ChannelState.Refused(Bye.Reason.NotYourPartner), channel.state.value)
        assertEquals(listOf(Message.Bye(Bye.Reason.NotYourPartner)), partner.byesReceived)
    }

    @Test
    fun anotherProtocolVersionIsRefusedAsAMismatch() = runTest {
        val (ours, theirs) = connectionPair()
        val channel = channel(SingleConnection(ours))
        channel.openAccepting()
        runCurrent()
        theirs.send(MessageCodec.encode(ourHello, seq = 1, version = 99))
        runCurrent()
        assertEquals(ChannelState.Refused(Bye.Reason.ProtocolMismatch), channel.state.value)
        theirs.receiveMessage() // our Hello
        assertEquals(Message.Bye(Bye.Reason.ProtocolMismatch), theirs.receiveMessage())
    }

    @Test
    fun unknownMessageTypesAreSkipped() = runTest {
        val (ours, theirs) = connectionPair()
        val channel = channel(SingleConnection(ours))
        channel.openAccepting()
        theirs.sendMessage(ourHello)
        runCurrent()
        theirs.send("""{"v":1,"type":"skip","seq":2,"payload":{}}""".encodeToByteArray())
        theirs.sendMessage(Message.Ping(1))
        runCurrent()
        assertTrue(channel.state.value is ChannelState.Open)
    }

    @Test
    fun garbageBytesDropTheConnectionCleanly() = runTest {
        val (ours, theirs) = connectionPair()
        val channel = channel(SingleConnection(ours))
        channel.openAccepting()
        theirs.sendMessage(ourHello)
        runCurrent()
        theirs.send("definitely not json".encodeToByteArray())
        runCurrent()
        assertEquals(ChannelState.Opening, channel.state.value)
        assertEquals(ChannelLoss.Vanished, channel.lastLoss)
        assertTrue(ours.closed)
    }

    @Test
    fun byeClosingIsAnOrdinaryLossAndIsRetried() = runTest {
        val (ours, theirs) = connectionPair()
        val channel = channel(SingleConnection(ours))
        channel.openAccepting()
        theirs.sendMessage(ourHello)
        runCurrent()
        theirs.sendMessage(Message.Bye(Bye.Reason.Closing))
        runCurrent()
        assertEquals(ChannelState.Opening, channel.state.value)
        assertEquals(ChannelLoss.ClosedByPartner, channel.lastLoss)
    }

    @Test
    fun byeDisconnectedEndsTheChannelWithoutRetrying() = runTest {
        val (ours, theirs) = connectionPair()
        val channel = channel(SingleConnection(ours))
        channel.openAccepting()
        theirs.sendMessage(ourHello)
        runCurrent()
        theirs.sendMessage(Message.Bye(Bye.Reason.Disconnected))
        runCurrent()
        assertEquals(ChannelState.Refused(Bye.Reason.Disconnected), channel.state.value)
    }

    @Test
    fun closingStopsEverythingAndLeavesNoCoroutinesRunning() = runTest {
        val partner = FakePartnerApp(backgroundScope)
        val transport = FakeTransport(partner)
        val channel = CommandChannel(transport, this, { testScheduler.currentTime * 1_000_000 })
        channel.openAccepting()
        advanceTimeBy(1_000)
        assertTrue(channel.state.value is ChannelState.Open)
        channel.close()
        assertEquals(ChannelState.Idle, channel.state.value)
        assertFalse(channel.send(Message.Ping(1)))
        // runTest fails if any coroutine launched in `this` is still running at the end.
    }

    @Test
    fun sendOnlyWorksWhileOpen() = runTest {
        val channel = channel(FakeTransport())
        assertFalse(channel.send(Message.Ping(1)))
        val partner = FakePartnerApp(backgroundScope)
        val open = channel(FakeTransport(partner))
        open.openAccepting()
        runCurrent()
        assertTrue(open.send(Message.Ping(1)))
    }
}

/** Hands out one connection, then refuses. */
private class SingleConnection(private var connection: FrameConnection?) : FrameTransport {
    override suspend fun accept(port: Int) = connect("", port)

    override suspend fun connect(host: String, port: Int): FrameConnection =
        connection?.also { connection = null } ?: throw java.io.IOException("refused")
}
