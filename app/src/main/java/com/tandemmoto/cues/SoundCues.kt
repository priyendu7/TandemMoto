package com.tandemmoto.cues

import androidx.core.content.edit
import com.tandemmoto.link.LinkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** A short tone in the rider's earbuds (#86). */
enum class Cue { PartnerConnected, PartnerDisconnected, MusicOn, MusicOff }

/**
 * When to play a [Cue] (#86): the partner connecting or dropping, and the music stopping or
 * starting, from either phone. Nothing for the starting values, and a play/pause ping-pong (as on
 * a reconnect) settles for [SETTLE_MS] first, so it gives one cue or none.
 */
class SoundCues(
    private val status: Flow<LinkStatus>,
    private val playing: Flow<Boolean>,
    private val enabled: StateFlow<Boolean>,
    private val play: (Cue) -> Unit,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {}
) {
    fun start() {
        scope.launch {
            var previous: LinkStatus? = null
            status.collect { next ->
                val before = previous
                previous = next
                if (before != null) linkCue(before, next)?.let(::cue)
            }
        }
        scope.launch {
            var announced: Boolean? = null
            playing.distinctUntilChanged().collectLatest { now ->
                if (announced == null) {
                    announced = now
                    return@collectLatest
                }
                delay(SETTLE_MS)
                if (now != announced) {
                    announced = now
                    cue(if (now) Cue.MusicOn else Cue.MusicOff)
                }
            }
        }
    }

    private fun cue(cue: Cue) {
        if (enabled.value) {
            log("$cue")
            play(cue)
        } else {
            log("$cue (sound cues off)")
        }
    }

    companion object {
        const val SETTLE_MS = 250L

        /** Into Connected, or out of it (forgetting the partner isn't a drop). */
        fun linkCue(before: LinkStatus, next: LinkStatus): Cue? = when {
            before !is LinkStatus.Connected && next is LinkStatus.Connected -> Cue.PartnerConnected
            before is LinkStatus.Connected &&
                next !is LinkStatus.Connected &&
                next !is LinkStatus.NotPaired -> Cue.PartnerDisconnected
            else -> null
        }
    }
}

/** Settings → Sound cues (#86). On by default. */
class SoundCuesSetting(context: android.content.Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("cues", android.content.Context.MODE_PRIVATE)
    private val _on = MutableStateFlow(prefs.getBoolean(KEY, true))
    val on: StateFlow<Boolean> = _on.asStateFlow()

    fun set(on: Boolean) {
        prefs.edit { putBoolean(KEY, on) }
        _on.value = on
    }

    private companion object {
        const val KEY = "sound_cues"
    }
}
