package com.tandemmoto.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TalkTestTest {
    private val sending = mutableListOf<Boolean>()
    private val callMode = mutableListOf<Boolean>()

    @Test
    fun onSendsInCallModeAndOffUndoesIt() = runTest {
        val test = TalkTest({ sending += it }, { callMode += it }, backgroundScope)
        test.set(true)
        runCurrent()
        assertTrue(test.state.value.on)
        assertEquals(TalkTest.LIMIT_S, test.state.value.secondsLeft)
        test.set(false)
        assertEquals(listOf(true, false), sending)
        assertEquals(listOf(true, false), callMode)
        assertFalse(test.state.value.on)
    }

    @Test
    fun itTurnsItselfOffAfterTwoMinutes() = runTest {
        val test = TalkTest({ sending += it }, { callMode += it }, backgroundScope)
        test.set(true)
        advanceTimeBy(TalkTest.LIMIT_S * 1_000L - 1_000)
        runCurrent()
        assertTrue(test.state.value.on)
        assertEquals(1, test.state.value.secondsLeft)
        advanceTimeBy(1_001)
        runCurrent()
        assertFalse(test.state.value.on)
        assertEquals(listOf(true, false), sending)
    }

    @Test
    fun turningItOnTwiceIsOneTest() = runTest {
        val test = TalkTest({ sending += it }, { callMode += it }, backgroundScope)
        test.set(true)
        test.set(true)
        assertEquals(listOf(true), sending)
    }
}
