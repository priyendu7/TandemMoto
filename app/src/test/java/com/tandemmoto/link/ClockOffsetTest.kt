package com.tandemmoto.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClockOffsetTest {
    @Test
    fun unknownUntilTheFirstPong() {
        assertNull(ClockOffset().offsetNanos)
    }

    @Test
    fun aSymmetricRoundTripGivesTheExactOffset() {
        val offset = ClockOffset()
        // Partner clock 1 000 ahead; 10 each way.
        offset.add(sentNanos = 100, repliedNanos = 1_110, receivedNanos = 120)
        assertEquals(1_000L, offset.offsetNanos)
    }

    @Test
    fun theFastestRoundTripWins() {
        val offset = ClockOffset()
        offset.add(sentNanos = 0, repliedNanos = 1_010, receivedNanos = 20) // exact: 1 000
        // Slow on the way back only: its midpoint would say 1 000 - 45.
        offset.add(sentNanos = 100, repliedNanos = 1_110, receivedNanos = 200)
        assertEquals(1_000L, offset.offsetNanos)
    }

    @Test
    fun oldSamplesLeaveTheWindow() {
        val offset = ClockOffset(window = 2)
        offset.add(sentNanos = 0, repliedNanos = 1_001, receivedNanos = 2) // fastest, offset 1 000
        offset.add(sentNanos = 10, repliedNanos = 2_015, receivedNanos = 20) // offset 2 000
        offset.add(sentNanos = 30, repliedNanos = 2_035, receivedNanos = 40) // offset 2 000
        assertEquals(2_000L, offset.offsetNanos)
    }

    @Test
    fun pongsWithoutAReplyTimeAreIgnored() {
        val offset = ClockOffset()
        offset.add(sentNanos = 0, repliedNanos = 0, receivedNanos = 20)
        assertNull(offset.offsetNanos)
    }

    @Test
    fun resetForgetsEverything() {
        val offset = ClockOffset()
        offset.add(sentNanos = 0, repliedNanos = 1_010, receivedNanos = 20)
        offset.reset()
        assertNull(offset.offsetNanos)
    }
}
