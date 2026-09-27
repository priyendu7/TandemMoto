package com.tandemmoto.voice

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Settings → Talk test: whether this phone is sending, and for how much longer. */
data class TalkTestState(val on: Boolean = false, val secondsLeft: Int = 0)

/**
 * Settings → Diagnostics → Talk test (#71): sends this phone's mic to the partner for a bench
 * test before mic mode (#72) exists. Turn it on on both phones for two-way voice. The phone's
 * audio is in call mode while it runs, and it turns itself off after [LIMIT_S], so a mic can't
 * be left open.
 */
class TalkTest(
    private val setSending: (Boolean) -> Unit,
    private val callMode: (Boolean) -> Unit,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {}
) {
    private val _state = MutableStateFlow(TalkTestState())
    val state: StateFlow<TalkTestState> = _state.asStateFlow()
    private var timer: Job? = null

    fun set(on: Boolean) {
        if (on == _state.value.on) return
        timer?.cancel()
        if (!on) {
            stopNow("turned off")
            return
        }
        log("Talk test on")
        _state.value = TalkTestState(on = true, secondsLeft = LIMIT_S)
        callMode(true)
        setSending(true)
        timer = scope.launch {
            for (left in LIMIT_S downTo 1) {
                _state.value = TalkTestState(on = true, secondsLeft = left)
                delay(1_000)
            }
            stopNow("after ${LIMIT_S / 60} min")
        }
    }

    private fun stopNow(why: String) {
        setSending(false)
        callMode(false)
        _state.value = TalkTestState()
        log("Talk test off ($why)")
    }

    companion object {
        const val LIMIT_S = 120
    }
}

/** Android's call mode, which the mic's voice processing and voice routing expect. */
class AudioModeControl(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var before: Int? = null

    fun set(inCall: Boolean) {
        if (inCall) {
            if (before == null) before = audio.mode
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            before?.let { audio.mode = it }
            before = null
        }
    }
}
