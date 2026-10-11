package com.tandemmoto.link

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectScheduleTest {
    @Test
    fun slotsAreAtEveryEvenMinuteOfTheEpoch() {
        val evenMinute = 1_760_000_040_000L // 14:54:00 UTC, an even minute
        assertEquals(0L, evenMinute % ReconnectSchedule.SLOT_EVERY_MS)
        assertEquals(evenMinute + 120_000, ReconnectSchedule.nextSlotStart(evenMinute))
        assertEquals(evenMinute, ReconnectSchedule.nextSlotStart(evenMinute - 1))
        assertEquals(evenMinute, ReconnectSchedule.nextSlotStart(evenMinute - 119_999))
        assertEquals(evenMinute + 120_000, ReconnectSchedule.nextSlotStart(evenMinute + 60_000))
    }

    @Test
    fun twoPhonesAFewSecondsApartPickTheSameSlot() {
        val now = 1_760_000_100_000L
        assertEquals(
            ReconnectSchedule.nextSlotStart(now),
            ReconnectSchedule.nextSlotStart(now + 3_000)
        )
    }
}
