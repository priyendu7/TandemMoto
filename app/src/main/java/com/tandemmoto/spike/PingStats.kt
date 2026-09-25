package com.tandemmoto.spike

import kotlin.math.ceil

data class PingSnapshot(
    val sent: Int = 0,
    val received: Int = 0,
    /** Pings still unanswered after the timeout. */
    val missed: Int = 0,
    val lastMs: Double? = null,
    val medianMs: Double? = null,
    val p95Ms: Double? = null,
    /** Longest time between two pongs: how long the link was effectively silent. */
    val maxGapMs: Long? = null
)

/** Round-trip statistics for the spike's ping session. Times are in nanoseconds. */
class PingStats(private val timeoutNanos: Long = 3_000_000_000L) {
    private val pending = LinkedHashMap<Int, Long>()
    private val rtts = ArrayList<Long>()
    private var sent = 0
    private var lastRtt: Long? = null
    private var lastPongAt: Long? = null
    private var maxGap: Long? = null

    @Synchronized
    fun recordSent(seq: Int, atNanos: Long) {
        sent++
        pending[seq] = atNanos
    }

    /** Returns the time since the previous pong, or null for the first one or an unknown seq. */
    @Synchronized
    fun recordPong(seq: Int, rttNanos: Long, atNanos: Long): Long? {
        if (pending.remove(seq) == null) return null
        rtts += rttNanos
        lastRtt = rttNanos
        val gap = lastPongAt?.let { atNanos - it }
        lastPongAt = atNanos
        if (gap != null && gap > (maxGap ?: Long.MIN_VALUE)) maxGap = gap
        return gap
    }

    @Synchronized
    fun snapshot(nowNanos: Long): PingSnapshot {
        val sorted = rtts.sorted()
        return PingSnapshot(
            sent = sent,
            received = rtts.size,
            missed = pending.values.count { nowNanos - it > timeoutNanos },
            lastMs = lastRtt?.toMillis(),
            medianMs = sorted.percentile(0.5)?.toMillis(),
            p95Ms = sorted.percentile(0.95)?.toMillis(),
            maxGapMs = maxGap?.let { it / 1_000_000 }
        )
    }

    @Synchronized
    fun reset() {
        pending.clear()
        rtts.clear()
        sent = 0
        lastRtt = null
        lastPongAt = null
        maxGap = null
    }

    private fun Long.toMillis() = this / 1_000_000.0

    /** Nearest-rank percentile of an already sorted list. */
    private fun List<Long>.percentile(p: Double): Long? =
        if (isEmpty()) null else this[(ceil(p * size).toInt() - 1).coerceIn(0, size - 1)]
}
