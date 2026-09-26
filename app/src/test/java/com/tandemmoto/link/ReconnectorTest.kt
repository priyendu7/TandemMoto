package com.tandemmoto.link

import com.tandemmoto.link.Partner.Role.Acceptor
import com.tandemmoto.link.Partner.Role.Initiator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectorTest {
    private val driver = FakeWifiP2pDriver()
    private val redmi = phone("Redmi Y2")
    private val initiator = Partner(redmi.name, redmi.address, Initiator, 0L)
    private val acceptor = Partner(redmi.name, redmi.address, Acceptor, 0L)
    private val logs = mutableListOf<String>()

    private fun TestScope.reconnector(): Pair<Reconnector, PeerDiscovery> {
        val discovery = PeerDiscovery(driver, FakePreconditions(), backgroundScope)
        return Reconnector(driver, discovery, { logs += it }) to discovery
    }

    @Test
    fun theInitiatorConnectsOnceThePartnerIsVisible() = runTest {
        val (reconnector, _) = reconnector()
        val result = async { reconnector.run(initiator) }
        advanceTimeBy(10_000)
        assertTrue(driver.connectCalls.isEmpty())
        driver.peers.value = listOf(redmi)
        runCurrent()
        assertEquals(listOf(redmi.address), driver.connectCalls)
        driver.formGroupWith(redmi)
        runCurrent()
        assertTrue(result.await())
    }

    @Test
    fun theInitiatorBacksOffAfterAttemptsThatFormNoGroup() = runTest {
        val (reconnector, _) = reconnector()
        driver.peers.value = listOf(redmi)
        backgroundScope.async { reconnector.run(initiator) }
        runCurrent()
        val attemptTimes = mutableListOf(testScheduler.currentTime)
        var seen = 1
        while (attemptTimes.size < 6) {
            advanceTimeBy(100)
            if (driver.connectCalls.size > seen) {
                seen = driver.connectCalls.size
                attemptTimes += testScheduler.currentTime
            }
        }
        val gaps = attemptTimes.zipWithNext { a, b -> (b - a) / 1_000 }
        // 15 s without a group, then 1, 2, 4, 8, 8 s of backoff.
        assertEquals(listOf(16L, 17L, 19L, 23L, 23L), gaps)
        assertTrue(driver.cancelConnectCalls >= 5)
    }

    @Test
    fun theAcceptorNeverConnectsAndKeepsSearching() = runTest {
        val (reconnector, discovery) = reconnector()
        driver.peers.value = listOf(redmi)
        val result = async { reconnector.run(acceptor) }
        advanceTimeBy(90_000)
        assertTrue(driver.connectCalls.isEmpty())
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        driver.formGroupWith(redmi, isGroupOwner = false)
        runCurrent()
        assertTrue(result.await())
    }

    @Test
    fun givesUpAfterTheWindowAndStopsSearching() = runTest {
        val (reconnector, discovery) = reconnector()
        val result = async { reconnector.run(acceptor) }
        advanceTimeBy(Reconnector.WINDOW_MS - 1)
        assertFalse(result.isCompleted)
        advanceTimeBy(2)
        assertFalse(result.await())
        assertEquals(DiscoveryState.Idle, discovery.state.value)
    }

    @Test
    fun neverConnectsOverAnExistingGroup() = runTest {
        val (reconnector, _) = reconnector()
        driver.formGroupWith(tv("Some TV"))
        driver.peers.value = listOf(redmi)
        backgroundScope.async { reconnector.run(initiator) }
        advanceTimeBy(5_000)
        assertTrue(driver.connectCalls.isEmpty())
        driver.group.value = null
        runCurrent()
        assertEquals(listOf(redmi.address), driver.connectCalls)
    }
}
