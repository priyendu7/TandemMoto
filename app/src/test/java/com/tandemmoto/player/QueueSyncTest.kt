package com.tandemmoto.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSyncTest {
    private fun check(queue: List<String>, target: List<String>) {
        val steps = QueueSync.steps(queue, target)
        assertEquals(target, QueueSync.apply(queue, steps))
    }

    @Test
    fun addsRemovesAndMovesEndInTheTargetOrder() {
        check(emptyList(), listOf("a", "b", "c"))
        check(listOf("a", "b", "c"), emptyList())
        check(listOf("a", "b", "c"), listOf("a", "c"))
        check(listOf("a", "b"), listOf("a", "x", "b"))
        check(listOf("a", "b", "c", "d"), listOf("d", "a", "b", "c"))
        check(listOf("a", "b", "c", "d"), listOf("c", "x", "a", "y"))
    }

    @Test
    fun aMoveIsOneStepNotAWholeNewQueue() {
        // The current song keeps playing: the player isn't given a new queue.
        val steps = QueueSync.steps(listOf("a", "b", "c", "d"), listOf("a", "d", "b", "c"))
        assertEquals(listOf(QueueStep.Move(3, 1)), steps)
    }

    @Test
    fun anUnchangedQueueNeedsNoSteps() {
        assertTrue(QueueSync.steps(listOf("a", "b"), listOf("a", "b")).isEmpty())
    }

    @Test
    fun randomEditsAlwaysEndInTheTargetOrder() {
        val random = Random(51)
        repeat(500) {
            val pool = (0 until 15).map { "s$it" }
            val queue = pool.shuffled(random).take(random.nextInt(10))
            val target = pool.shuffled(random).take(random.nextInt(12))
            check(queue, target)
        }
    }
}
