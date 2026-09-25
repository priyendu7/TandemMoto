package com.tandemmoto.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingTest {
    private val ms = 1_000_000L

    @Test
    fun protocolRoundTrips() {
        assertEquals(PingMessage.Ping(7, 123L), PingProtocol.parse(PingProtocol.ping(7, 123L)))
        assertEquals(PingMessage.Pong(7, 123L), PingProtocol.parse(PingProtocol.pong(7, 123L)))
    }

    @Test
    fun protocolRejectsGarbage() {
        listOf("", "PING", "PING x 1", "PING 1 y", "PONG 1 2 3", "HELLO 1 2").forEach {
            assertNull(it, PingProtocol.parse(it))
        }
    }

    @Test
    fun statsComputeMedianAndP95() {
        val stats = PingStats()
        (1..20).forEach { seq ->
            stats.recordSent(seq, seq * 1_000 * ms)
            stats.recordPong(seq, seq * ms, seq * 1_000 * ms + seq * ms)
        }
        val snapshot = stats.snapshot(nowNanos = 21_000 * ms)
        assertEquals(20, snapshot.sent)
        assertEquals(20, snapshot.received)
        assertEquals(0, snapshot.missed)
        assertEquals(10.0, snapshot.medianMs!!, 0.0)
        assertEquals(19.0, snapshot.p95Ms!!, 0.0)
        assertEquals(20.0, snapshot.lastMs!!, 0.0)
    }

    @Test
    fun unansweredPingsCountAsMissedAfterTheTimeout() {
        val stats = PingStats(timeoutNanos = 3_000 * ms)
        stats.recordSent(1, 0)
        stats.recordSent(2, 5_000 * ms)
        val snapshot = stats.snapshot(nowNanos = 6_000 * ms)
        assertEquals(1, snapshot.missed)
    }

    @Test
    fun gapsBetweenPongsAreTracked() {
        val stats = PingStats()
        stats.recordSent(1, 0)
        stats.recordSent(2, 1_000 * ms)
        assertNull(stats.recordPong(1, 5 * ms, 5 * ms))
        assertEquals(12_000 * ms, stats.recordPong(2, 11_005 * ms, 12_005 * ms))
        assertEquals(12_000L, stats.snapshot(12_005 * ms).maxGapMs)
    }

    @Test
    fun unknownPongsAreIgnoredAndResetClearsEverything() {
        val stats = PingStats()
        assertNull(stats.recordPong(99, ms, ms))
        stats.recordSent(1, 0)
        stats.reset()
        assertEquals(PingSnapshot(), stats.snapshot(0))
    }
}
