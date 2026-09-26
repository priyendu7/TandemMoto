package com.tandemmoto.player

import com.tandemmoto.link.ChannelState
import com.tandemmoto.playlist.Stamp
import com.tandemmoto.state.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** Where this phone's player is: its song, whether it's meant to play, and its position now. */
data class PlayerPosition(val songId: String?, val playing: Boolean, val positionMs: Long)

/** What [PlaybackMirror] needs from the local player ([Playback]); a fake in tests. */
interface LocalPlayer {
    fun position(): PlayerPosition

    fun hasSong(songId: String): Boolean

    /**
     * Brings the player to [songId] at [positionMs], playing or paused, as the partner's phone
     * is: quietly, not as a control (it's never sent back).
     */
    fun apply(songId: String, positionMs: Long, playing: Boolean)
}

/** How [Playback] tells the mirror about controls and queue changes. */
interface PlaybackListener {
    /** A control on this phone ([control] names it, for logs) has just changed the player. */
    fun onControl(control: String)

    /** The queue changed: a song the partner is on may be here now. */
    fun onQueueChanged()
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
        val by = me ?: return // a control before the install ID loads: nothing to mirror yet
        clock += 1
        val stamp = Stamp(clock, by)
        latest = stamp
        pending = null
        scope.launch { sendWhereWeAre(control, stamp) }
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
        val now = player.position()
        send(
            Message.PlaybackState(
                songId = now.songId,
                playing = now.playing,
                positionMs = now.positionMs,
                atNanos = nanoTime(),
                stamp = stamp ?: Stamp(0, by),
                control = control
            )
        )
    }

    private fun onReceived(state: Message.PlaybackState) {
        clock = maxOf(clock, state.stamp.clock)
        // Clock 0: the partner hasn't had a control since its app started; nothing to follow.
        if (state.stamp.clock == 0L) return
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

    private fun apply(state: Message.PlaybackState) {
        val songId = state.songId ?: return
        val offset = clockOffsetNanos.value
        // When the partner acted, on this phone's clock (unknown offset: as good as now).
        val sinceMs = offset?.let { ((nanoTime() - (state.atNanos - it)) / NANOS_PER_MS) }
            ?.coerceAtLeast(0)
        val positionMs = state.positionMs + if (state.playing) sinceMs ?: 0 else 0
        player.apply(songId, positionMs, state.playing)
        val after = sinceMs?.let { "in $it ms" } ?: "(clock offset unknown)"
        log(
            "Followed the partner's ${state.control} $after: song-${songId.take(8)} " +
                "${if (state.playing) "playing" else "paused"} at ${positionMs / 1_000} s"
        )
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
