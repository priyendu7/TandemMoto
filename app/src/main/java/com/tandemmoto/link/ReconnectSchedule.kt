package com.tandemmoto.link

import java.time.Instant
import java.time.ZoneId

/**
 * When to search after the partner isn't reached (#85). Wi-Fi Direct only finds a phone while it
 * is searching itself, so after the first [FIRST_WINDOW_MS] both phones search for
 * [SLOT_LENGTH_MS] at every even wall-clock minute: the same slots on both, with no messages
 * needed. It relies on both clocks being close; the link logs the difference on connect.
 */
object ReconnectSchedule {
    /** Right after a drop, a tap or app start. */
    const val FIRST_WINDOW_MS = 60_000L

    const val SLOT_EVERY_MS = 120_000L
    const val SLOT_LENGTH_MS = 20_000L

    /** The next slot strictly after [nowMs] (epoch ms), e.g. 13:22:00.000 for 13:20:00.000. */
    fun nextSlotStart(nowMs: Long): Long = (Math.floorDiv(nowMs, SLOT_EVERY_MS) + 1) * SLOT_EVERY_MS

    /** "13:22:00" in this phone's time zone, for the logs. */
    fun clockTime(epochMs: Long): String {
        val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
        return "%02d:%02d:%02d".format(time.hour, time.minute, time.second)
    }
}
