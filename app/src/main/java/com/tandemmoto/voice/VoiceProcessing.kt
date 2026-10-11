package com.tandemmoto.voice

import android.content.Context
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.edit
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android's voice effects, which a phone may or may not have (#73). */
enum class VoiceEffect(val label: String) {
    NoiseSuppressor("noise suppressor"),
    GainControl("gain control"),
    EchoCanceller("echo canceller");

    companion object {
        /** The ones this phone has; no mic needed to ask. */
        fun available(): Set<VoiceEffect> = entries.filterTo(mutableSetOf()) { it.isAvailable() }
    }
}

private fun VoiceEffect.isAvailable(): Boolean = runCatching {
    when (this) {
        VoiceEffect.NoiseSuppressor -> NoiseSuppressor.isAvailable()
        VoiceEffect.GainControl -> AutomaticGainControl.isAvailable()
        VoiceEffect.EchoCanceller -> AcousticEchoCanceler.isAvailable()
    }
}.getOrDefault(false)

private fun VoiceEffect.create(sessionId: Int): AudioEffect? = when (this) {
    VoiceEffect.NoiseSuppressor -> NoiseSuppressor.create(sessionId)
    VoiceEffect.GainControl -> AutomaticGainControl.create(sessionId)
    VoiceEffect.EchoCanceller -> AcousticEchoCanceler.create(sessionId)
}

/** What happened to each effect when the mic opened. */
enum class EffectState { On, NotOnThisPhone, Failed }

object VoiceProcessingLog {
    /** One log line for an opened mic, e.g. to fill in `docs/TEST_MATRIX.md`. */
    fun describe(processing: Boolean, effects: Map<VoiceEffect, EffectState>): String {
        if (!processing) {
            return "Mic open, voice processing off (Settings → Advanced): raw mic, no effects"
        }
        val parts = VoiceEffect.entries.map { effect ->
            when (effects[effect]) {
                EffectState.On -> "${effect.label} on"
                EffectState.Failed -> "${effect.label} failed to start"
                EffectState.NotOnThisPhone, null -> "no ${effect.label} on this phone"
            }
        }
        return "Mic open with the voice-call source; " + parts.joinToString(", ")
    }
}

/**
 * The effects on one open mic (#73). They're tied to its audio session, so they run only while
 * that mic is open (mic mode, the Talk test or the Mic test) and [close] releases them.
 */
internal class MicEffects(sessionId: Int) : Closeable {
    private val opened = mutableListOf<AudioEffect>()
    val states: Map<VoiceEffect, EffectState> =
        VoiceEffect.entries.associateWith { attach(it, sessionId) }

    private fun attach(effect: VoiceEffect, sessionId: Int): EffectState {
        if (!effect.isAvailable()) return EffectState.NotOnThisPhone
        val created = runCatching { effect.create(sessionId) }.getOrNull()
            ?: return EffectState.Failed
        opened += created
        val on = runCatching {
            created.setEnabled(true) == AudioEffect.SUCCESS && created.enabled
        }.getOrDefault(false)
        return if (on) EffectState.On else EffectState.Failed
    }

    override fun close() {
        opened.forEach { runCatching { it.release() } }
        opened.clear()
    }
}

/**
 * Settings → Advanced → Voice processing (#73): a test switch to compare the processed voice with
 * the raw mic. On by default; it applies the next time the mic opens.
 */
class VoiceProcessingSetting(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("intercom", Context.MODE_PRIVATE)
    private val _on = MutableStateFlow(prefs.getBoolean(KEY, true))
    val on: StateFlow<Boolean> = _on.asStateFlow()

    fun set(on: Boolean) {
        prefs.edit { putBoolean(KEY, on) }
        _on.value = on
    }

    private companion object {
        const val KEY = "voice_processing"
    }
}
