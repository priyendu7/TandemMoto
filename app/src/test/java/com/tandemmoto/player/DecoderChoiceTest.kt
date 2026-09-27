package com.tandemmoto.player

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class DecoderChoiceTest {
    private val asked = mutableListOf<String>()

    private fun named(name: String) = MediaCodecSelector { _, _, _ ->
        asked += name
        emptyList()
    }

    private val selector = DecoderChoice.selector(named("usual"), named("software"))

    @Test
    fun flacPrefersTheSoftwareDecoder() {
        selector.getDecoderInfos(MimeTypes.AUDIO_FLAC, false, false)
        assertEquals(listOf("software"), asked)
    }

    @Test
    fun otherFormatsKeepThePhonesUsualOrder() {
        selector.getDecoderInfos(MimeTypes.AUDIO_MPEG, false, false)
        selector.getDecoderInfos(MimeTypes.AUDIO_AAC, false, false)
        assertEquals(listOf("usual", "usual"), asked)
    }
}
