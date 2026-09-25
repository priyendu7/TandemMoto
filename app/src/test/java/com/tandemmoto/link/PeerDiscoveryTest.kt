package com.tandemmoto.link

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeerDiscoveryTest {
    private val driver = FakeWifiP2pDriver()
    private val preconditions = FakePreconditions()

    private fun TestScope.discovery(driver: WifiP2pDriver = this@PeerDiscoveryTest.driver) =
        PeerDiscovery(driver, preconditions, backgroundScope)

    @Test
    fun unsupportedPhoneNeverAsksAndroid() = runTest {
        val discovery = discovery(FakeWifiP2pDriver(supported = false))
        discovery.start()
        runCurrent()
        assertEquals(DiscoveryState.Unsupported, discovery.state.value)
    }

    @Test
    fun missingPermissionIsReportedBeforeCallingAndroid() = runTest {
        preconditions.granted = false
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertEquals(DiscoveryState.PermissionMissing, discovery.state.value)
        assertEquals(0, driver.discoverCalls)
    }

    @Test
    fun locationOffDoesNotBlockTheSearch() = runTest {
        // The Redmi Y2 (Android 9) discovers with Location off.
        preconditions.locationOff = true
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        assertEquals(1, driver.discoverCalls)
    }

    @Test
    fun nothingFoundWithLocationOffSuggestsLocation() = runTest {
        preconditions.locationOff = true
        val discovery = discovery()
        discovery.start()
        advanceTimeBy(60_001)
        assertEquals(
            DiscoveryState.Finished(emptyList(), suggestLocation = true),
            discovery.state.value
        )
    }

    @Test
    fun devicesFoundWithLocationOffDoesNotSuggestLocation() = runTest {
        preconditions.locationOff = true
        val discovery = discovery()
        discovery.start()
        runCurrent()
        driver.peers.value = listOf(phone("Galaxy S25"))
        advanceTimeBy(60_001)
        assertEquals(DiscoveryState.Finished(listOf(phone("Galaxy S25"))), discovery.state.value)
    }

    @Test
    fun unknownWifiStateIsNotTreatedAsOff() = runTest {
        // The Redmi Y2 never reported its initial Wi-Fi Direct state.
        driver.enabled.value = null
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        assertEquals(1, driver.discoverCalls)
    }

    @Test
    fun scansForTheWholeWindowThenFinishesWithWhatItFound() = runTest {
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        driver.peers.value = listOf(tv("TV"), phone("Redmi Y2"))
        advanceTimeBy(59_000)
        assertEquals(
            DiscoveryState.Scanning(listOf(phone("Redmi Y2"), tv("TV"))),
            discovery.state.value
        )
        advanceTimeBy(1_001)
        assertEquals(
            DiscoveryState.Finished(listOf(phone("Redmi Y2"), tv("TV"))),
            discovery.state.value
        )
        assertEquals(1, driver.stopCalls)
    }

    @Test
    fun finishingWithNothingFoundGivesAnEmptyResult() = runTest {
        val discovery = discovery()
        discovery.start()
        advanceTimeBy(60_001) // one full search window
        assertEquals(DiscoveryState.Finished(emptyList()), discovery.state.value)
    }

    @Test
    fun restartsDiscoveryWhenAndroidStopsIt() = runTest {
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertEquals(1, driver.discoverCalls)
        driver.discovering.value = false // Android stopped discovery on its own
        runCurrent()
        assertEquals(2, driver.discoverCalls)
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
    }

    @Test
    fun busyIsRetriedWithBackoffThenReportedAsStuck() = runTest {
        repeat(PeerDiscovery.MAX_ATTEMPTS) { driver.discoverResults.addLast(P2pResult.Busy) }
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertEquals(1, driver.discoverCalls)
        advanceTimeBy(1_001) // retry after 1 s
        assertEquals(2, driver.discoverCalls)
        advanceTimeBy(2_001) // then 2 s
        assertEquals(3, driver.discoverCalls)
        advanceTimeBy(4_001) // then 4 s
        assertEquals(4, driver.discoverCalls)
        assertEquals(DiscoveryState.Stuck, discovery.state.value)
    }

    @Test
    fun oneBusyThenSuccessKeepsScanning() = runTest {
        driver.discoverResults.addLast(P2pResult.Busy)
        val discovery = discovery()
        discovery.start()
        advanceTimeBy(1_001)
        assertEquals(2, driver.discoverCalls)
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
    }

    @Test
    fun errorWithPermissionRevokedMeansPermissionMissing() = runTest {
        driver.discoverResults.addLast(P2pResult.Error)
        preconditions.granted = true
        val discovery = discovery()
        discovery.start()
        preconditions.granted = false // revoked between the check and Android's answer
        runCurrent()
        assertEquals(DiscoveryState.PermissionMissing, discovery.state.value)
    }

    @Test
    fun wifiOffWaitsAndResumesByItselfWhenWifiReturns() = runTest {
        driver.enabled.value = false
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertEquals(DiscoveryState.WifiOff, discovery.state.value)
        assertEquals(0, driver.discoverCalls)
        driver.enabled.value = true
        runCurrent()
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        assertEquals(1, driver.discoverCalls)
    }

    @Test
    fun wifiTurnedOffMidScanWaitsThenStartsAFreshWindow() = runTest {
        val discovery = discovery()
        discovery.start()
        advanceTimeBy(40_000)
        driver.enabled.value = false
        runCurrent()
        assertEquals(DiscoveryState.WifiOff, discovery.state.value)
        advanceTimeBy(60_000) // stays waiting, no timeout while Wi-Fi is off
        assertEquals(DiscoveryState.WifiOff, discovery.state.value)
        driver.enabled.value = true
        advanceTimeBy(59_000)
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
    }

    @Test
    fun stopEndsTheSearchAndAsksAndroidToStop() = runTest {
        val discovery = discovery()
        discovery.start()
        runCurrent()
        discovery.stop()
        runCurrent()
        assertEquals(DiscoveryState.Idle, discovery.state.value)
        assertEquals(1, driver.stopCalls)
        advanceTimeBy(60_000)
        assertEquals(1, driver.discoverCalls)
    }

    @Test
    fun searchAgainStartsANewWindow() = runTest {
        val discovery = discovery()
        discovery.start()
        advanceTimeBy(60_001) // one full search window
        assertTrue(discovery.state.value is DiscoveryState.Finished)
        discovery.start()
        runCurrent()
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        assertEquals(2, driver.discoverCalls)
    }

    @Test
    fun phonesAreListedFirstThenByName() {
        assertEquals(
            listOf(phone("alpha"), phone("Bravo"), tv("Aardvark TV")),
            PeerDiscovery.sorted(listOf(tv("Aardvark TV"), phone("Bravo"), phone("alpha")))
        )
    }

    @Test
    fun continuousSearchNeverFinishesByItself() = runTest {
        val discovery = discovery()
        discovery.start(continuous = true)
        advanceTimeBy(10 * 60_000L)
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        driver.discovering.value = false // Android stopped; it restarts
        runCurrent()
        assertEquals(2, driver.discoverCalls)
    }

    @Test
    fun pauseEndsTheLoopWithoutAskingAndroidToStop() = runTest {
        val discovery = discovery()
        discovery.start()
        runCurrent()
        discovery.pause()
        runCurrent()
        assertEquals(DiscoveryState.Idle, discovery.state.value)
        assertEquals(0, driver.stopCalls)
        advanceTimeBy(120_000)
        assertEquals(1, driver.discoverCalls)
    }
}
