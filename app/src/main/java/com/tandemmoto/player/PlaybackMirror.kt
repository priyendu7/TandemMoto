package com.tandemmoto.player

import com.tandemmoto.link.ChannelState
import com.tandemmoto.playlist.Stamp
import com.tandemmoto.state.Message
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Where this phone's player is: its song, whether it's meant to play, and its position now.
 * [waiting]: it wants to play but holds until both phones have the song (#61).
 */
data class PlayerPosition(
    val songId: String?,
    val playing: Boolean,
    val positionMs: Long,
    val waiting: Boolean = false
)

/** What [PlaybackMirror] needs from the local player ([Playback]); a fake in tests. */
interface LocalPlayer {
    fun position(): PlayerPosition

    fun hasSong(songId: String): Boolean

    /**
     * Brings the player to [songId] at [positionMs], playing or paused, as the partner's phone
     * is: quietly, not as a control (it's never sent back). [startInMs] > 0: a start both phones
     * make at the same moment, that far ahead. [partnerPlaying]: the partner is playing it (not
     * holding), so it has the song.
     */
    fun apply(
        songId: String,
        positionMs: Long,
        playing: Boolean,
        startInMs: Long = 0,
        partnerPlaying: Boolean = false
    )
}

/** How [Playback] tells the mirror about controls and queue changes. */
interface PlaybackListener {
    /** A control on this phone ([control] names it, for logs) has just changed the player. */
    fun onControl(control: String)

    /** The queue changed: a song the partner is on may be here now. */
    fun onQueueChanged()

    /**
     * Both phones have the held song (#61): tell the partner to start at a moment shortly
     * ahead. Returns how far ahead, in ms, so this phone starts then too (0: start now).
     */
    fun onStartTogether(): Long
}

/**
 * Keeps the two players in step (#60). After a control on either phone, that phone sends the
 * result ([Message.PlaybackState]: song, playing or paused, position, when) with a Lamport
 * [Stamp]; the newest stamp wins on both phones, and a state that isn't newer changes nothing, so
 * two controls at the same moment still leave both phones in the same state. Applying the
 * partner's state is quiet: it's never sent back.
 *
 * Link down, each phone plays on its own; on every connection both send where they are with the
 * stamp of their latest control, and the newer one wins. A phone that was restarted starts at
 * clock 0, so it joins what the partner is playing. A state whose song isn't in this phone's
 * queue yet (the playlist edit is still on its way) is applied when the song arrives.
 *
 * Starting together (#61): when a held song reaches both phones, the phone that notices sends a
 * start [START_LEAD_MS] ahead and both start then. Staying in step: every [SYNC_EVERY_MS] while
 * playing, the phone that made the latest control sends where it is; only the other phone
 * corrects, and only when it's more than [DRIFT_MS] off (a smaller jump would be heard for
 * nothing).
 */
class PlaybackMirror(
    private val player: LocalPlayer,
    private val installId: suspend () -> String,
    private val channelState: StateFlow<ChannelState>,
    private val incoming: Flow<Message>,
    private val send: suspend (Message) -> Boolean,
    /** The partner's clock minus this phone's, from the heartbeat; null while unknown. */
    private val clockOffsetNanos: StateFlow<Long?>,
    private val scope: CoroutineScope,
    private val nanoTime: () -> Long = System::nanoTime,
    private val log: (String) -> Unit = {}
) : PlaybackListener {
    /** Lamport clock: one past the newest stamp seen from either phone. */
    private var clock = 0L

    /** The stamp of the state both phones should be in (the newest control either made). */
    private var latest: Stamp? = null

    /** The partner's newest state, waiting for its song to reach the queue. */
    private var pending: Message.PlaybackState? = null

    private var me: String? = null

    fun start() {
        scope.launch {
            while (true) {
                delay(SYNC_EVERY_MS)
                sendSync()
            }
        }
        scope.launch {
            me = installId()
            incoming.filterIsInstance<Message.PlaybackState>().collect(::onReceived)
        }
        scope.launch {
            channelState.collect { state ->
                if (state is ChannelState.Open) sendWhereWeAre("Connect", latest)
            }
        }
    }

    override fun onControl(control: String) {
        val stamp = newStamp() ?: return
        val state = whereWeAre(control, stamp, nanoTime())
        scope.launch { send(state) }
    }

    override fun onStartTogether(): Long {
        if (channelState.value !is ChannelState.Open) return 0
        val stamp = newStamp() ?: return 0
        val state = whereWeAre(START_TOGETHER, stamp, nanoTime() + START_LEAD_MS * NANOS_PER_MS)
            .copy(playing = true, waiting = false)
        scope.launch { send(state) }
        return START_LEAD_MS
    }

    private fun newStamp(): Stamp? {
        val by = me ?: return null // before the install ID loads: nothing to mirror yet
        clock += 1
        return Stamp(clock, by).also {
            latest = it
            pending = null
        }
    }

    private fun whereWeAre(control: String, stamp: Stamp, atNanos: Long): Message.PlaybackState {
        val now = player.position()
        return Message.PlaybackState(
            songId = now.songId,
            playing = now.playing,
            positionMs = now.positionMs,
            atNanos = atNanos,
            stamp = stamp,
            control = control,
            waiting = now.waiting
        )
    }

    /** The latest control was this phone's: tell the partner where it is, to stay in step. */
    private suspend fun sendSync() {
        val stamp = latest ?: return
        if (stamp.by != me || channelState.value !is ChannelState.Open) return
        val now = player.position()
        if (!now.playing || now.waiting || now.songId == null) return
        send(whereWeAre(SYNC, stamp, nanoTime()))
    }

    override fun onQueueChanged() {
        val waiting = pending ?: return
        if (waiting.songId != null && player.hasSong(waiting.songId)) {
            pending = null
            apply(waiting)
        }
    }

    private suspend fun sendWhereWeAre(control: String, stamp: Stamp?) {
        val by = me ?: installId().also { me = it }
        send(whereWeAre(control, stamp ?: Stamp(0, by), nanoTime()))
    }

    private fun onReceived(state: Message.PlaybackState) {
        clock = maxOf(clock, state.stamp.clock)
        // Clock 0: the partner hasn't had a control since its app started; nothing to follow.
        if (state.stamp.clock == 0L) return
        if (state.control == SYNC && state.stamp == latest) {
            keepInStep(state)
            return
        }
        val current = latest
        if (current != null && state.stamp <= current) return
        latest = state.stamp
        pending = null
        val songId = state.songId ?: return
        if (!player.hasSong(songId)) {
            log("Partner is on song-${songId.take(8)}, not in the queue yet: waiting for it")
            pending = state
            return
        }
        apply(state)
    }

    /** How long ago the partner's state was made, on this phone's clock; negative: ahead. */
    private fun sinceMs(state: Message.PlaybackState): Long? = clockOffsetNanos.value?.let {
        (nanoTime() - (state.atNanos - it)) / NANOS_PER_MS
    }

    private fun apply(state: Message.PlaybackState) {
        val songId = state.songId ?: return
        // Unknown clock offset: as good as now.
        val sinceMs = sinceMs(state)
        val elapsed = if (state.playing && !state.waiting) (sinceMs ?: 0).coerceAtLeast(0) else 0
        val startInMs = (-(sinceMs ?: 0)).coerceAtLeast(0)
        val positionMs = state.positionMs + elapsed
        player.apply(
            songId,
            positionMs,
            state.playing,
            startInMs,
            partnerPlaying = state.playing && !state.waiting
        )
        val after = when {
            sinceMs == null -> "(clock offset unknown)"
            sinceMs < 0 -> "to start in ${-sinceMs} ms"
            else -> "in $sinceMs ms"
        }
        val what = when {
            !state.playing -> "paused"
            state.waiting -> "waiting for the song"
            else -> "playing"
        }
        log(
            "Followed the partner's ${state.control} $after: song-${songId.take(8)} $what " +
                "at ${positionMs / 1_000} s"
        )
    }

    /** The partner (which made the latest control) says where it is: jump if we drifted. */
    private fun keepInStep(state: Message.PlaybackState) {
        val songId = state.songId ?: return
        val sinceMs = sinceMs(state) ?: return
        val here = player.position()
        if (here.songId != songId || !here.playing || here.waiting) return
        if (!state.playing || state.waiting) return
        val expected = state.positionMs + sinceMs.coerceAtLeast(0)
        val drift = here.positionMs - expected
        if (abs(drift) <= DRIFT_MS) {
            // Every check, not just the jumps: the phone test measures how close they stay (#62).
            log("In step with the partner: $drift ms on song-${songId.take(8)}")
            return
        }
        log("Drifted $drift ms from the partner on song-${songId.take(8)}: back in step")
        player.apply(songId, expected, playing = true, partnerPlaying = true)
    }

    companion object {
        private const val NANOS_PER_MS = 1_000_000L

        /** How far ahead a start together is planned: well over a message's trip (~10 ms). */
        const val START_LEAD_MS = 300L

        const val SYNC_EVERY_MS = 5_000L

        /** Further apart than this, the following phone jumps back in step. */
        const val DRIFT_MS = 500L

        const val START_TOGETHER = "StartTogether"
        const val SYNC = "Sync"
    }
}
