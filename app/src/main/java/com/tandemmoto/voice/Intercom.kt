package com.tandemmoto.voice

import androidx.core.content.edit
import com.tandemmoto.service.MicAccess
import com.tandemmoto.state.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Home's intercom line says (#72). */
enum class IntercomLine {
    /** Not linked with the partner. */
    NotLinked,

    /** Linked, nothing playing, never started this ride: Home offers Start intercom. */
    Ready,

    /** Music is playing: it turns on when the music is paused. */
    WaitingForPause,

    /** Mic mode is starting: switching the earbuds to call mode. */
    Connecting,

    /** Talking. */
    On,

    /** Talking on the phone's own mic and speaker: the earbuds didn't switch. */
    OnPhone
}

data class IntercomState(
    val line: IntercomLine = IntercomLine.NotLinked,
    val muted: Boolean = false,
    val partnerMuted: Boolean = false,
    val route: RouteKind? = null
) {
    val micMode: Boolean
        get() = line == IntercomLine.Connecting ||
            line == IntercomLine.On ||
            line == IntercomLine.OnPhone
}

/** The intercom's rules, on the JVM (#72). */
object IntercomRules {
    /**
     * Mic mode: linked, the intercom wanted (music has played this ride, or Start intercom), and
     * the music not playing. A song held while it's fetched counts as playing.
     */
    fun micMode(linked: Boolean, wanted: Boolean, playing: Boolean) = linked && wanted && !playing

    /** This phone sends its voice: mic mode, the route up, not muted, and the mic usable. */
    fun sending(micMode: Boolean, routeReady: Boolean, muted: Boolean, micAccess: MicAccess) =
        micMode && routeReady && !muted && micAccess == MicAccess.Ready

    fun line(linked: Boolean, wanted: Boolean, playing: Boolean, route: RouteState): IntercomLine =
        when {
            !linked -> IntercomLine.NotLinked
            micMode(linked, wanted, playing) -> when (route) {
                RouteState.Closed, RouteState.Opening -> IntercomLine.Connecting
                is RouteState.Open ->
                    if (route.result.fellBack) IntercomLine.OnPhone else IntercomLine.On
            }
            playing -> IntercomLine.WaitingForPause
            else -> IntercomLine.Ready
        }
}

/** Where the intercom's audio is, while mic mode switches it. */
sealed interface RouteState {
    data object Closed : RouteState

    data object Opening : RouteState

    data class Open(val result: RouteResult) : RouteState
}

/**
 * Mic mode and self-mute (#72). Pausing the music on either phone, or Start intercom, opens the
 * intercom on both: the audio route switches to call mode (wired headset, or the earbuds' hands-
 * free link), then this phone sends its voice unless muted. Playing music, Stop intercom, a link
 * drop or Disconnect close it: sending stops and the route goes back, so **no mic is left open**.
 *
 * Mute is this phone's alone and only the user changes it ([MuteStore]); it releases the mic
 * but keeps the route, so the partner is still heard. The partner learns it from a
 * [Message.Muted], sent on every change and every connection.
 */
class Intercom(
    private val linked: StateFlow<Boolean>,
    private val wanted: StateFlow<Boolean>,
    private val playing: StateFlow<Boolean>,
    private val micAccess: StateFlow<MicAccess>,
    private val mute: MuteStore,
    private val incoming: Flow<Message>,
    private val send: suspend (Message) -> Boolean,
    private val route: AudioRoute,
    private val setSending: (Boolean) -> Unit,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val routeState = MutableStateFlow<RouteState>(RouteState.Closed)
    private val partnerMuted = MutableStateFlow(false)
    private val _state = MutableStateFlow(IntercomState())
    val state: StateFlow<IntercomState> = _state.asStateFlow()

    fun setMuted(muted: Boolean) {
        if (muted == mute.muted.value) return
        mute.set(muted)
        log(if (muted) "Muted" else "Unmuted")
        scope.launch { if (linked.value) send(Message.Muted(muted)) }
    }

    fun start() {
        scope.launch {
            incoming.filterIsInstance<Message.Muted>().collect {
                partnerMuted.value = it.muted
                log("Partner ${if (it.muted) "muted" else "unmuted"}")
            }
        }
        scope.launch {
            linked.collect { isLinked ->
                if (isLinked) send(Message.Muted(mute.muted.value)) else partnerMuted.value = false
            }
        }
        scope.launch {
            combine(linked, wanted, playing, IntercomRules::micMode)
                .distinctUntilChanged()
                .collectLatest { on -> if (on) openMicMode() else closeMicMode() }
        }
        scope.launch {
            combine(linked, wanted, playing, routeState) { l, w, p, r ->
                IntercomRules.line(l, w, p, r)
            }
                .combine(mute.muted) { line, muted -> line to muted }
                .combine(partnerMuted) { (line, muted), partner ->
                    IntercomState(
                        line = line,
                        muted = muted,
                        partnerMuted = partner && line != IntercomLine.NotLinked,
                        route = (routeState.value as? RouteState.Open)?.result?.kind
                    )
                }
                .collect { _state.value = it }
        }
        scope.launch {
            combine(
                combine(linked, wanted, playing, IntercomRules::micMode),
                routeState,
                mute.muted,
                micAccess
            ) { micMode, route, muted, access ->
                IntercomRules.sending(micMode, route is RouteState.Open, muted, access)
            }.distinctUntilChanged().collect(setSending)
        }
    }

    private suspend fun openMicMode() {
        val pausedAt = nowMs()
        log("Mic mode on")
        routeState.value = RouteState.Opening
        val result = route.open()
        routeState.value = RouteState.Open(result)
        log(
            "Intercom on ${result.kind}" +
                (if (result.fellBack) " (earbuds didn't switch: phone mic and speaker)" else "") +
                ", earbuds switch ${result.switchMs} ms, talking ${nowMs() - pausedAt} ms after the pause"
        )
    }

    private fun closeMicMode() {
        if (routeState.value == RouteState.Closed) return
        setSending(false)
        route.close()
        routeState.value = RouteState.Closed
        log("Mic mode off: mic released, audio back to normal")
    }
}

/**
 * Settings → Advanced → Use earbud mic for the intercom (#72). Off by default: the earbuds stay
 * in music mode and you talk into the phone.
 */
class EarbudMicSetting(context: android.content.Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("intercom", android.content.Context.MODE_PRIVATE)
    private val _on = MutableStateFlow(prefs.getBoolean(KEY, false))
    val on: StateFlow<Boolean> = _on.asStateFlow()

    fun set(on: Boolean) {
        prefs.edit { putBoolean(KEY, on) }
        _on.value = on
    }

    private companion object {
        const val KEY = "earbud_mic"
    }
}

/** This phone's mute (#72): only the user changes it, and it's remembered across restarts. */
interface MuteStore {
    val muted: StateFlow<Boolean>

    fun set(muted: Boolean)
}

class SharedPrefsMuteStore(context: android.content.Context) : MuteStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("intercom", android.content.Context.MODE_PRIVATE)
    private val _muted = MutableStateFlow(prefs.getBoolean(KEY, false))
    override val muted: StateFlow<Boolean> = _muted.asStateFlow()

    override fun set(muted: Boolean) {
        prefs.edit { putBoolean(KEY, muted) }
        _muted.update { muted }
    }

    private companion object {
        const val KEY = "muted"
    }
}
