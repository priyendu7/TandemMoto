package com.tandemmoto.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StallCheckTest {
    private val check = StallCheck(stallMs = 3_000)

    @Test
    fun aMovingSongNeverStalls() {
        for (second in 0..20L) {
            assertFalse(check.update(second * 1_000, second * 1_000, playing = true))
        }
    }

    @Test
    fun threeSecondsWithoutMovingIsAStall() {
        assertFalse(check.update(0, 5_000, playing = true))
        assertFalse(check.update(1_000, 5_000, playing = true))
        assertFalse(check.update(2_000, 5_050, playing = true))
        assertTrue(check.update(3_000, 5_100, playing = true))
    }

    @Test
    fun pausedOrWaitingIsntAStall() {
        for (second in 0..10L) {
            assertFalse(check.update(second * 1_000, 5_000, playing = false))
        }
    }

    @Test
    fun aJumpBackStartsCountingAgain() {
        check.update(0, 60_000, playing = true)
        check.update(2_000, 60_000, playing = true)
        assertFalse(check.update(2_500, 0, playing = true)) // seeked back
        assertFalse(check.update(4_000, 0, playing = true))
        assertTrue(check.update(5_500, 0, playing = true))
    }

    @Test
    fun aShortBufferAfterAJumpIsFine() {
        check.update(0, 0, playing = true)
        check.update(1_000, 0, playing = true) // buffering
        assertFalse(check.update(2_000, 600, playing = true))
        assertFalse(check.update(5_000, 3_600, playing = true))
    }
}
