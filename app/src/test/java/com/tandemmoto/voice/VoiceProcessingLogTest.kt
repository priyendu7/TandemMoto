package com.tandemmoto.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceProcessingLogTest {
    @Test
    fun saysWhichEffectsTheMicHas() {
        val line = VoiceProcessingLog.describe(
            processing = true,
            effects = mapOf(
                VoiceEffect.NoiseSuppressor to EffectState.On,
                VoiceEffect.GainControl to EffectState.Failed,
                VoiceEffect.EchoCanceller to EffectState.NotOnThisPhone
            )
        )
        assertEquals(
            "Mic open with the voice-call source; noise suppressor on, " +
                "gain control failed to start, no echo canceller on this phone",
            line
        )
    }

    @Test
    fun processingOffIsTheRawMic() {
        assertEquals(
            "Mic open, voice processing off (Settings → Advanced): raw mic, no effects",
            VoiceProcessingLog.describe(processing = false, effects = emptyMap())
        )
    }
}
