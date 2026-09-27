package com.tandemmoto.voice

import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MicTestTest {
    /** A mic that plays [sample] for every position; counts how often it was closed. */
    private class FakeMic(private val sample: (Int) -> Short) : MicSource {
        var closed = 0
        private var position = 0

        override fun open() = object : MicCapture {
            override fun read(buffer: ShortArray): Int {
                for (i in buffer.indices) buffer[i] = sample(position++)
                return buffer.size
            }

            override fun close() {
                closed++
            }
        }
    }

    private val logs = mutableListOf<String>()

    private fun TestScope.test(source: MicSource) = MicTest(
        record = source,
        scope = backgroundScope,
        log = { logs += it },
        io = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun aToneIsHeardEverySecond() = runTest {
        // A 440 Hz tone at half scale: about -9 dBFS.
        val mic = FakeMic { i -> (16_384 * sin(2 * PI * 440 * i / 16_000)).toInt().toShort() }
        val micTest = test(mic)
        micTest.start(delaySeconds = 10)
        runCurrent()
        assertEquals(10, micTest.state.value.startsIn)
        advanceTimeBy(10_001)
        runCurrent()
        val result = micTest.state.value.result as MicTestResult.Heard
        assertEquals(MicTest.SECONDS, result.levelsDb.size)
        result.levelsDb.forEach { assertEquals(-9.0, it, 0.5) }
        assertEquals(1, mic.closed)
        assertFalse(micTest.state.value.busy)
        assertTrue(logs.any { it.startsWith("Heard sound") })
    }

    @Test
    fun allZerosMeansTheMicIsBlocked() = runTest {
        val micTest = test(FakeMic { 0 })
        micTest.start(delaySeconds = 0)
        runCurrent()
        assertEquals(MicTestResult.Silent, micTest.state.value.result)
    }

    @Test
    fun aMicThatWontOpenSaysSo() = runTest {
        val micTest = test { null }
        micTest.start(delaySeconds = 0)
        runCurrent()
        assertEquals(MicTestResult.CouldNotOpen, micTest.state.value.result)
    }

    @Test
    fun cancellingDuringTheCountdownRecordsNothing() = runTest {
        val mic = FakeMic { 1 }
        val micTest = test(mic)
        micTest.start(delaySeconds = 10)
        advanceTimeBy(3_000)
        micTest.cancel()
        advanceTimeBy(20_000)
        assertEquals(null, micTest.state.value.result)
        assertEquals(0, mic.closed)
    }

    @Test
    fun levels() {
        assertEquals(MicLevels.FLOOR_DB, MicLevels.dbfs(ShortArray(100)), 0.0)
        assertEquals(0.0, MicLevels.dbfs(ShortArray(100) { Short.MIN_VALUE }), 0.01)
        assertFalse(MicLevels.anyNonZero(ShortArray(10)))
        assertTrue(MicLevels.anyNonZero(ShortArray(10) { if (it == 9) 1 else 0 }))
    }
}
