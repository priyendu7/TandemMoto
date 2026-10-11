package com.tandemmoto.cues

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** The tones for each [Cue] (#86), generated, no assets. */
object Tones {
    const val SAMPLE_RATE = 48_000
    private const val AMPLITUDE = 0.3
    private const val FADE_MS = 10

    /** (Hz, ms) notes, about 400 ms in all. */
    fun notes(cue: Cue): List<Pair<Int, Int>> = when (cue) {
        Cue.PartnerConnected -> listOf(660 to 200, 880 to 200)
        Cue.PartnerDisconnected -> listOf(880 to 140, 523 to 140, 392 to 140)
        Cue.MusicOn -> listOf(1_000 to 300)
        Cue.MusicOff -> listOf(500 to 300)
    }

    /** 16-bit mono PCM; each note fades in and out so it doesn't click. */
    fun pcm(cue: Cue): ShortArray {
        val out = ArrayList<Short>()
        for ((hz, ms) in notes(cue)) {
            val count = SAMPLE_RATE * ms / 1_000
            val fade = SAMPLE_RATE * FADE_MS / 1_000
            for (i in 0 until count) {
                val envelope = min(1.0, min(i, count - 1 - i).toDouble() / fade)
                val value = sin(2 * PI * hz * i / SAMPLE_RATE) * AMPLITUDE * envelope
                out.add((value * Short.MAX_VALUE).toInt().toShort())
            }
        }
        return out.toShortArray()
    }
}

/**
 * Plays cues one after another on a static [AudioTrack]. It never asks for audio focus: that would
 * pause the music (and the partner's, through the mirror). [usage] follows the intercom route, as
 * for the partner's voice.
 */
class TonePlayer(private val usage: () -> Int, private val log: (String) -> Unit = {}) {
    private val queue = Executors.newSingleThreadExecutor { Thread(it, "SoundCues") }
    private val pcm = Cue.entries.associateWith(Tones::pcm)

    fun play(cue: Cue) {
        val usage = usage()
        queue.execute { playNow(cue, usage) }
    }

    private fun playNow(cue: Cue, usage: Int) {
        val samples = pcm.getValue(cue)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(Tones.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: IllegalArgumentException) {
            log("Can't play $cue: ${e.message}")
            return
        } catch (e: UnsupportedOperationException) {
            log("Can't play $cue: ${e.message}")
            return
        }
        try {
            if (track.state == AudioTrack.STATE_NO_STATIC_DATA) {
                track.write(samples, 0, samples.size)
            }
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                log("Can't play $cue: track state ${track.state}")
                return
            }
            track.play()
            Thread.sleep(samples.size * 1_000L / Tones.SAMPLE_RATE + TAIL_MS)
            runCatching { track.stop() }
        } catch (e: IllegalStateException) {
            log("Can't play $cue: ${e.message}")
        } finally {
            track.release()
        }
    }

    private companion object {
        /** Lets the output drain before the track is released. */
        const val TAIL_MS = 50L
    }
}
