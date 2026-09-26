package com.tandemmoto.ui

import com.tandemmoto.ui.ride.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {
    @Test
    fun minutesAndSeconds() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:09", formatTime(9_999))
        assertEquals("4:05", formatTime(245_000))
    }

    @Test
    fun hoursPastAnHour() {
        assertEquals("1:02:03", formatTime(3_723_000))
    }

    @Test
    fun negativeIsZero() {
        assertEquals("0:00", formatTime(-5))
    }
}
