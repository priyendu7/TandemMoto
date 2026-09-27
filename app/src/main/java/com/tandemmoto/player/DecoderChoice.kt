package com.tandemmoto.player

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Which decoder plays a song (#62). FLAC goes to Android's own software decoder first: the Redmi
 * Y2's Qualcomm FLAC decoder (OMX.qti.audio.decoder.flac) stuck at 0 s on 24-bit, 48 kHz files
 * its Files app plays, and FLAC is cheap to decode in software. Everything else keeps the phone's
 * usual order (hardware first).
 */
@OptIn(UnstableApi::class)
object DecoderChoice {
    fun selector(
        usual: MediaCodecSelector = MediaCodecSelector.DEFAULT,
        software: MediaCodecSelector = MediaCodecSelector.PREFER_SOFTWARE
    ) = MediaCodecSelector { mimeType, secure, tunneling ->
        val choice = if (mimeType == MimeTypes.AUDIO_FLAC) software else usual
        choice.getDecoderInfos(mimeType, secure, tunneling)
    }
}
