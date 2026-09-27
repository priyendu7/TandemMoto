package com.tandemmoto.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.Closeable

/** An open speaker (or earphones) for the partner's voice. */
interface SpeakerOutput : Closeable {
    /** Plays [samples]; blocks while the output's buffer is full, which paces the caller. */
    fun write(samples: ShortArray): Int

    /** How long a written sample waits before it's heard (the output's buffer). */
    val latencyMs: Int
}

/** Opens the output; a fake in tests. */
fun interface SpeakerSource {
    fun open(): SpeakerOutput?
}

/**
 * The partner's voice: 16 kHz mono, low-latency mode, a small buffer (a bigger one is more
 * delay). [usage] is the voice-call path, or media while the earbuds stay in music mode (#72).
 */
class AudioTrackSource(
    private val usage: () -> Int = { AudioAttributes.USAGE_VOICE_COMMUNICATION }
) : SpeakerSource {
    override fun open(): SpeakerOutput? {
        val minBuffer = AudioTrack.getMinBufferSize(
            MicSource.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage())
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(MicSource.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer, JitterBuffer.FRAME_SAMPLES * 2 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        } catch (e: UnsupportedOperationException) {
            return null
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        track.play()
        return object : SpeakerOutput {
            override fun write(samples: ShortArray) = track.write(samples, 0, samples.size)

            override val latencyMs = track.bufferSizeInFrames * 1_000 / MicSource.SAMPLE_RATE

            override fun close() {
                runCatching { track.stop() }
                track.release()
            }
        }
    }
}
