package com.tandemmoto.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {
    private val buffer = JitterBuffer()

    /** A frame whose samples all equal [seq], so what plays shows which packet it was. */
    private fun packet(seq: Int, sentMs: Long = seq * 20L) = VoicePacket(
        seq,
        sentMs * 1_000_000,
        ShortArray(JitterBuffer.FRAME_SAMPLES) {
            (seq + 1).toShort()
        }
    )

    private fun put(seq: Int, arrivalMs: Long = seq * 20L + 10) =
        buffer.put(packet(seq), arrivalMs * 1_000_000)

    private fun Frame.seq() = samples[0] - 1

    @Test
    fun silenceUntilItHasFilledUp() {
        assertEquals(FrameKind.Silence, buffer.next().kind)
        put(0)
        put(1)
        assertEquals(FrameKind.Silence, buffer.next().kind)
        put(2) // 3 frames: the starting depth
        val first = buffer.next()
        assertEquals(FrameKind.Voice, first.kind)
        assertEquals(0, first.seq())
    }

    @Test
    fun packetsOutOfOrderPlayInOrder() {
        listOf(2, 0, 1, 4, 3).forEach { put(it) }
        assertEquals(listOf(0, 1, 2, 3, 4), List(5) { buffer.next().seq() })
    }

    @Test
    fun aLostPacketIsFilledInFadingNotClicked() {
        listOf(0, 1, 2, 4, 5).forEach { put(it) }
        repeat(3) { buffer.next() }
        val filled = buffer.next()
        assertEquals(FrameKind.FilledIn, filled.kind)
        assertTrue("quieter than the last frame", filled.samples[0] < 3)
        assertEquals(4, buffer.next().seq())
        assertEquals(1, buffer.takeStats().filledIn)
    }

    @Test
    fun aPacketAfterItsTurnIsDropped() {
        listOf(0, 1, 2, 4).forEach { put(it) }
        repeat(4) { buffer.next() } // 3 was filled in
        put(3)
        assertEquals(4, buffer.next().seq())
        assertEquals(1, buffer.takeStats().late)
    }

    @Test
    fun whenThePartnerStopsItGoesSilentAndFillsUpAgain() {
        (0..3).forEach { put(it) }
        repeat(4) { buffer.next() }
        repeat(3) { assertEquals(FrameKind.FilledIn, buffer.next().kind) }
        assertEquals(FrameKind.Silence, buffer.next().kind)
        // Talking again later: waits for the starting depth, then plays.
        put(200)
        assertEquals(FrameKind.Silence, buffer.next().kind)
        put(201)
        put(202)
        assertEquals(200, buffer.next().seq())
    }

    @Test
    fun anAppRestartCountingFromZeroIsFollowed() {
        (500..503).forEach { put(it) }
        repeat(2) { buffer.next() }
        (0..2).forEach { put(it) }
        assertEquals(0, buffer.next().seq())
    }

    @Test
    fun evenArrivalsKeepTheBufferShallow() {
        (0 until 60).forEach { put(it) }
        assertEquals(JitterBuffer.MIN_FRAMES, buffer.targetFrames)
    }

    @Test
    fun unevenArrivalsDeepenIt() {
        // Every tenth packet 70 ms late.
        (0 until 60).forEach { put(it, arrivalMs = it * 20L + 10 + if (it % 10 == 0) 70 else 0) }
        assertTrue(buffer.targetFrames >= 4)
        assertTrue(buffer.targetFrames <= JitterBuffer.MAX_FRAMES)
    }

    @Test
    fun tooMuchHeldBackIsTrimmed() {
        (0 until 20).forEach { put(it) } // nothing played meanwhile
        val stats = buffer.takeStats()
        assertTrue(stats.trimmed > 0)
        assertTrue(stats.depthFrames <= buffer.targetFrames + 2)
    }
}
