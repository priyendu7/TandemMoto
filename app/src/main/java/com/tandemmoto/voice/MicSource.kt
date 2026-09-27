package com.tandemmoto.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
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
 * The phone's mic with the voice-call source, which gets the processing the maker tuned for
 * calls (#73 builds on it).
 */
class AudioRecordSource(private val context: Context) : MicSource {
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
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
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
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return null
        }
        return object : MicCapture {
            override fun read(buffer: ShortArray) = record.read(buffer, 0, buffer.size)

            // Android doesn't report it; the minimum buffer is a fair guess.
            override val latencyMs = minBuffer / 2 * 1_000 / MicSource.SAMPLE_RATE

            override fun close() {
                runCatching { record.stop() }
                record.release()
            }
        }
    }
}
