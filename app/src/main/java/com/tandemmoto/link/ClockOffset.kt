package com.tandemmoto.link

/**
 * How far the partner's clock (`System.nanoTime` on its phone) is ahead of this phone's, worked
 * out from heartbeat round trips the way NTP does: a Ping leaves at `sent` (this clock), the
 * partner answers at `replied` (its clock), the Pong arrives at `received` (this clock), so the
 * partner's clock read `replied` at about the midpoint of the round trip. Only the fastest round
 * trips of the last [window] count: a slow one was delayed on one leg and skews the midpoint.
 */
class ClockOffset(private val window: Int = WINDOW) {
    private data class Sample(val rttNanos: Long, val offsetNanos: Long)

    private val samples = ArrayDeque<Sample>()

    /** Partner clock minus this clock, in nanoseconds; null before the first usable Pong. */
    var offsetNanos: Long? = null
        private set

    fun add(sentNanos: Long, repliedNanos: Long, receivedNanos: Long) {
        val rtt = receivedNanos - sentNanos
        // An older app answers without its time (0).
        if (rtt < 0 || repliedNanos == 0L) return
        samples += Sample(rtt, repliedNanos - (sentNanos + rtt / 2))
        while (samples.size > window) samples.removeFirst()
        offsetNanos = samples.minBy { it.rttNanos }.offsetNanos
    }

    fun reset() {
        samples.clear()
        offsetNanos = null
    }

    companion object {
        /** 5 s of heartbeats at 10/s. */
        const val WINDOW = 50
    }
}
