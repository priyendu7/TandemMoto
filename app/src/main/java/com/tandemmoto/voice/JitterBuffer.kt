package com.tandemmoto.voice

import kotlin.math.ceil

/** What [JitterBuffer.next] gave the speaker. */
enum class FrameKind {
    /** The partner's voice. */
    Voice,

    /** A missing packet, filled with the last frame fading out (no click). */
    FilledIn,

    /** Nothing to play: before the first packets, or after a gap. */
    Silence
}

class Frame(val samples: ShortArray, val kind: FrameKind)

/** Counters since the last [JitterBuffer.takeStats]. */
data class JitterStats(
    val received: Int = 0,
    val filledIn: Int = 0,
    val late: Int = 0,
    val trimmed: Int = 0,
    /** Frames it currently holds back, and the depth it aims for. */
    val depthFrames: Int = 0,
    val targetFrames: Int = 0
)

/**
 * Evens out the gaps between voice packets (#71). Packets go back into order by sequence number,
 * and playback runs [targetFrames] frames (20 ms each) behind arrival. The target follows how
 * uneven the arrivals are: the p95 spread of recent arrival times, between [MIN_FRAMES] and
 * [MAX_FRAMES] (about 40–120 ms). A packet that arrives after its turn is dropped; a missing one
 * is filled with the last frame fading out, and after [MAX_FILL_IN] in a row it goes silent.
 * With nothing for a while (the partner stopped sending) it starts over and fills up again.
 *
 * Called from two threads (receive, playback): every method is synchronized.
 */
class JitterBuffer(private val frameSamples: Int = FRAME_SAMPLES) {
    private val waiting = sortedMapOf<Int, VoicePacket>()
    private var nextSeq: Int? = null
    private var playing = false
    private var missingInARow = 0
    private var last: ShortArray? = null

    /** Recent transit times (arrival minus send, on mixed clocks: only their spread counts). */
    private val transits = ArrayDeque<Long>()
    private var stats = JitterStats()

    var targetFrames = START_FRAMES
        private set

    @Synchronized
    fun put(packet: VoicePacket, arrivalNanos: Long) {
        stats = stats.copy(received = stats.received + 1)
        noteTransit(arrivalNanos - packet.sentAtNanos)
        val expected = nextSeq
        // Far behind where playback is: the partner's app restarted and counts from 0 again.
        if (playing && expected != null && packet.seq < expected - RESYNC_FRAMES) {
            reset(keep = null)
        }
        if (playing && expected != null && packet.seq < expected) {
            stats = stats.copy(late = stats.late + 1)
            return
        }
        waiting[packet.seq] = packet
        // Far ahead of where playback is: the partner restarted; start over from here.
        if (expected != null && packet.seq > expected + RESYNC_FRAMES) reset(keep = packet)
        if (!playing && waiting.size >= targetFrames) {
            playing = true
            nextSeq = waiting.firstKey()
        }
        // More held back than needed (the network calmed down): drop the oldest to catch up.
        while (playing && waiting.size > targetFrames + TRIM_SLACK) {
            waiting.remove(waiting.firstKey())
            nextSeq = waiting.firstKey()
            stats = stats.copy(trimmed = stats.trimmed + 1)
        }
    }

    /** The next 20 ms to play; called by the speaker at its own pace. */
    @Synchronized
    fun next(): Frame {
        val seq = nextSeq
        if (!playing || seq == null) return silence()
        val packet = waiting.remove(seq)
        nextSeq = seq + 1
        if (packet != null) {
            missingInARow = 0
            last = packet.samples
            return Frame(packet.samples, FrameKind.Voice)
        }
        missingInARow++
        if (waiting.isEmpty() && missingInARow > MAX_FILL_IN) {
            // The partner stopped (or the link is gone): wait to fill up again.
            reset(keep = null)
            return silence()
        }
        val previous = last
        if (previous == null || missingInARow > MAX_FILL_IN) return silence()
        stats = stats.copy(filledIn = stats.filledIn + 1)
        val gain = FADE / missingInARow
        return Frame(
            ShortArray(previous.size) {
                (previous[it] * gain).toInt().toShort()
            },
            FrameKind.FilledIn
        )
    }

    @Synchronized
    fun takeStats(): JitterStats {
        val taken = stats.copy(depthFrames = waiting.size, targetFrames = targetFrames)
        stats = JitterStats()
        return taken
    }

    private fun silence() = Frame(ShortArray(frameSamples), FrameKind.Silence)

    private fun reset(keep: VoicePacket?) {
        waiting.clear()
        keep?.let { waiting[it.seq] = it }
        playing = false
        nextSeq = keep?.seq
        missingInARow = 0
        last = null
    }

    private fun noteTransit(transitNanos: Long) {
        transits += transitNanos
        while (transits.size > TRANSIT_WINDOW) transits.removeFirst()
        if (transits.size < MIN_SAMPLES_TO_ADAPT) return
        val fastest = transits.min()
        val spreads = transits.map { it - fastest }.sorted()
        val p95Ms = spreads[(spreads.size * 95 / 100).coerceAtMost(spreads.size - 1)] / 1_000_000.0
        targetFrames = (ceil(p95Ms / FRAME_MS) + 1).toInt().coerceIn(MIN_FRAMES, MAX_FRAMES)
    }

    companion object {
        /** 20 ms at 16 kHz. */
        const val FRAME_SAMPLES = 320
        const val FRAME_MS = 20.0
        const val START_FRAMES = 3
        const val MIN_FRAMES = 2
        const val MAX_FRAMES = 6
        const val MAX_FILL_IN = 3
        private const val TRIM_SLACK = 2
        private const val RESYNC_FRAMES = 50
        private const val TRANSIT_WINDOW = 100
        private const val MIN_SAMPLES_TO_ADAPT = 20
        private const val FADE = 0.6
    }
}
