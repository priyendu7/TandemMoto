package com.tandemmoto.link

import kotlin.math.ceil

/** Heartbeat round trips over one reporting period, logged so phone tests can read latency. */
class RttStats {
    private val rttNanos = ArrayList<Long>()

    val count: Int get() = rttNanos.size

    fun add(nanos: Long) {
        if (nanos >= 0) rttNanos += nanos
    }

    /** "RTT median 9 ms, p95 41 ms, max 80 ms (50 pongs)", or null when there were none. */
    fun summary(): String? {
        if (rttNanos.isEmpty()) return null
        val sorted = rttNanos.sorted()
        return "RTT median ${sorted.percentileMs(0.5)} ms, p95 ${sorted.percentileMs(0.95)} ms, " +
            "max ${sorted.last() / NANOS_PER_MS} ms (${sorted.size} pongs)"
    }

    fun medianMs(): Long? = rttNanos.sorted().takeIf { it.isNotEmpty() }?.percentileMs(0.5)

    fun p95Ms(): Long? = rttNanos.sorted().takeIf { it.isNotEmpty() }?.percentileMs(0.95)

    fun reset() = rttNanos.clear()

    /** Nearest-rank percentile of a sorted, non-empty list. */
    private fun List<Long>.percentileMs(p: Double): Long =
        this[(ceil(p * size).toInt() - 1).coerceIn(0, size - 1)] / NANOS_PER_MS

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
