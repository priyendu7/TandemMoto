package com.tandemmoto.playlist

import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionalIndexTest {
    private fun assertBetween(a: String?, b: String?) {
        val key = FractionalIndex.between(a, b)
        assertTrue("$a < $key", a == null || a < key)
        assertTrue("$key < $b", b == null || key < b)
        assertTrue("$key ends in 0", !key.endsWith('0'))
    }

    @Test
    fun keysFitBetweenAnyTwo() {
        assertBetween(null, null)
        assertBetween("V", null)
        assertBetween(null, "V")
        assertBetween("V", "W")
        assertBetween("V", "V1")
        assertBetween("Vz", "W")
        assertBetween("1", "2")
    }

    @Test
    fun appendingManyStaysSorted() {
        var last: String? = null
        repeat(1_000) {
            val next = FractionalIndex.between(last, null)
            assertTrue(last == null || last!! < next)
            last = next
        }
    }

    @Test
    fun movingIntoTheSameGapAgainAndAgainAlwaysFindsAKey() {
        // The worst case for position keys: every move lands just after the first song.
        val first = FractionalIndex.between(null, null)
        var upper = FractionalIndex.between(first, null)
        repeat(500) {
            val key = FractionalIndex.between(first, upper)
            assertTrue(first < key && key < upper)
            upper = key
        }
        var lower = FractionalIndex.between(null, null)
        val top = FractionalIndex.between(lower, null)
        repeat(500) {
            val key = FractionalIndex.between(lower, top)
            assertTrue(lower < key && key < top)
            lower = key
        }
    }

    @Test
    fun randomInsertsKeepAStrictOrder() {
        val random = Random(49)
        val keys = mutableListOf(FractionalIndex.between(null, null))
        repeat(2_000) {
            val at = random.nextInt(keys.size + 1)
            keys.add(at, FractionalIndex.between(keys.getOrNull(at - 1), keys.getOrNull(at)))
        }
        assertTrue(keys.zipWithNext().all { (a, b) -> a < b })
    }
}
