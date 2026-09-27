package com.tandemmoto.voice

import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the mic test last found. */
sealed interface MicTestResult {
    /** Loudness each second, in dBFS (0 is the loudest; quiet rooms are around -50). */
    data class Heard(val levelsDb: List<Double>) : MicTestResult

    /** Every sample was zero: Android gave the app silence, i.e. blocked the mic. */
    data object Silent : MicTestResult

    /** The mic couldn't be opened (no permission, or Android refused). */
    data object CouldNotOpen : MicTestResult
}

data class MicTestState(
    /** Counting down to the start, in seconds; null when not waiting. */
    val startsIn: Int? = null,
    val recording: Boolean = false,
    val result: MicTestResult? = null
) {
    val busy: Boolean get() = startsIn != null || recording
}

/**
 * Settings → Diagnostics → Mic test (#70): records a few seconds after a delay, so the tester can
 * lock the screen or leave the app first, and logs the loudness every second. It checks that the
 * phone lets the app use the mic in the background before the intercom depends on it: Android
 * gives an app that isn't allowed silence (all zeros), not an error. Nothing is kept.
 */
class MicTest(
    private val record: MicSource,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    /** Where the app is, for the log ("app in background", the service's mic access…). */
    private val context: () -> String = { "" },
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow(MicTestState())
    val state: StateFlow<MicTestState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(delaySeconds: Int) {
        if (_state.value.busy) return
        job = scope.launch {
            for (left in delaySeconds downTo 1) {
                _state.value = MicTestState(startsIn = left, result = _state.value.result)
                delay(1_000)
            }
            _state.value = MicTestState(recording = true)
            log("Recording $SECONDS s (${context()})")
            val result = withContext(io) { recordAndMeasure() }
            log(
                when (result) {
                    is MicTestResult.Heard ->
                        "Heard sound: " +
                            result.levelsDb.joinToString(", ") { "%.0f dBFS".format(it) }
                    MicTestResult.Silent -> "Silent: every sample was zero (the mic is blocked)"
                    MicTestResult.CouldNotOpen -> "Couldn't open the mic"
                }
            )
            _state.value = MicTestState(result = result)
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = MicTestState(result = _state.value.result)
    }

    private fun recordAndMeasure(): MicTestResult {
        val capture = record.open() ?: return MicTestResult.CouldNotOpen
        val levels = mutableListOf<Double>()
        var anySound = false
        capture.use { mic ->
            val second = ShortArray(MicSource.SAMPLE_RATE)
            repeat(SECONDS) {
                val read = readFully(mic, second)
                if (read <= 0) return@use
                if (MicLevels.anyNonZero(second, read)) anySound = true
                levels += MicLevels.dbfs(second, read)
            }
        }
        return when {
            levels.isEmpty() -> MicTestResult.CouldNotOpen
            !anySound -> MicTestResult.Silent
            else -> MicTestResult.Heard(levels)
        }
    }

    /** Reads until [buffer] is full or the mic stops; the samples read. */
    private fun readFully(capture: MicCapture, buffer: ShortArray): Int {
        var total = 0
        val chunk = ShortArray(MicSource.SAMPLE_RATE / 10)
        while (total < buffer.size) {
            val read = capture.read(chunk)
            if (read <= 0) break
            val take = minOf(read, buffer.size - total)
            chunk.copyInto(buffer, total, 0, take)
            total += take
        }
        return total
    }

    companion object {
        const val SECONDS = 5
    }
}

/** Loudness of 16-bit samples. */
object MicLevels {
    /** RMS level in dBFS; -96 for digital silence. */
    fun dbfs(samples: ShortArray, count: Int = samples.size): Double {
        if (count <= 0) return FLOOR_DB
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i] / 32_768.0
            sum += s * s
        }
        val rms = sqrt(sum / count)
        return if (rms <= 0) FLOOR_DB else maxOf(FLOOR_DB, 20 * log10(rms))
    }

    fun anyNonZero(samples: ShortArray, count: Int = samples.size): Boolean =
        (0 until count).any { samples[it].toInt() != 0 }

    const val FLOOR_DB = -96.0
}
