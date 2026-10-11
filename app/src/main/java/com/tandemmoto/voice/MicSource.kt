package com.tandemmoto.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.Closeable

/** An open mic: 16-bit mono samples at [MicSource.SAMPLE_RATE]. */
interface MicCapture : Closeable {
    /** Fills [buffer]; the number of samples read, or a negative error code. */
    fun read(buffer: ShortArray): Int

    /** Roughly how long a sample waits in the mic's buffer before [read] returns it. */
    val latencyMs: Int get() = 0
}

/** Opens the mic; a fake in tests. */
fun interface MicSource {
    /** Null when it can't: no permission, or Android refused it. */
    fun open(): MicCapture?

    companion object {
        /** Wideband voice, the intercom's format (Phase 4 planning, #71). */
        const val SAMPLE_RATE = 16_000
    }
}

/**
 * The phone's mic. With [processing] on (the default, #73) it uses the voice-call source, which
 * gets the processing the maker tuned for calls, plus Android's noise suppressor, gain control
 * and echo canceller where the phone has them. Off (Settings → Advanced, for A/B tests) it uses
 * the voice-recognition source, which Android requires to be unprocessed, and no effects.
 */
class AudioRecordSource(
    private val context: Context,
    private val processing: () -> Boolean = { true },
    private val log: (String) -> Unit = {}
) : MicSource {
    @SuppressLint("MissingPermission") // checked just before
    override fun open(): MicCapture? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            MicSource.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val processed = processing()
        val record = try {
            AudioRecord(
                if (processed) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                },
                MicSource.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 4
            )
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        val effects = if (processed) MicEffects(record.audioSessionId) else null
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            effects?.close()
            record.release()
            return null
        }
        log(VoiceProcessingLog.describe(processed, effects?.states.orEmpty()))
        return object : MicCapture {
            private var samplesRead = 0L
            private val timestamp = AudioTimestamp()
            private val guessMs = minBuffer / 2 * 1_000 / MicSource.SAMPLE_RATE

            override fun read(buffer: ShortArray) =
                record.read(buffer, 0, buffer.size).also { if (it > 0) samplesRead += it }

            // Measured from Android's capture timestamp where it gives one; else the minimum
            // buffer is a fair guess.
            override val latencyMs: Int
                get() {
                    val ok = record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) ==
                        AudioRecord.SUCCESS
                    if (!ok || samplesRead == 0L) return guessMs
                    val capturedAt = timestamp.nanoTime +
                        (samplesRead - timestamp.framePosition) * 1_000_000_000 /
                        MicSource.SAMPLE_RATE
                    val ms = (System.nanoTime() - capturedAt) / 1_000_000
                    return if (ms in 0..MAX_PLAUSIBLE_MS) ms.toInt() else guessMs
                }

            override fun close() {
                runCatching { record.stop() }
                effects?.close()
                record.release()
            }
        }
    }

    private companion object {
        const val MAX_PLAUSIBLE_MS = 1_000L
    }
}
