package com.tandemmoto.link

import com.tandemmoto.link.NearbyDevice.Status.Available
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeerDiscoveryTest {
    private class FakeDriver(override val supported: Boolean = true) : WifiP2pDriver {
        override val enabled = MutableStateFlow<Boolean?>(true)
        override val peers = MutableStateFlow<List<NearbyDevice>>(emptyList())
        override val discovering = MutableStateFlow(false)

        /** Results for successive discoverPeers calls; Ok once they run out. */
        val discoverResults = ArrayDeque<P2pResult>()
        var discoverCalls = 0
        var stopCalls = 0

        override suspend fun discoverPeers(): P2pResult {
            discoverCalls++
            val result = discoverResults.removeFirstOrNull() ?: P2pResult.Ok
            if (result == P2pResult.Ok) discovering.value = true
            return result
        }

        override suspend fun stopPeerDiscovery(): P2pResult {
            stopCalls++
            discovering.value = false
            return P2pResult.Ok
        }

        override fun close() = Unit
    }

    private class FakePreconditions(var granted: Boolean = true, var locationOff: Boolean = false) :
        DiscoveryPreconditions {
        override fun nearbyGranted() = granted

        override fun locationOff() = locationOff
    }

    private val driver = FakeDriver()
    private val preconditions = FakePreconditions()

    private fun TestScope.discovery(driver: WifiP2pDriver = this@PeerDiscoveryTest.driver) =
        PeerDiscovery(driver, preconditions, backgroundScope, scanDurationMs = 30_000)

    private fun phone(name: String) = NearbyDevice(name, name, Available, isPhone = true)

    private fun tv(name: String) = NearbyDevice(name, name, Available, isPhone = false)

    @Test
    fun unsupportedPhoneNeverAsksAndroid() = runTest {
        val discovery = discovery(FakeDriver(supported = false))
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
    fun locationOffIsReported() = runTest {
        preconditions.locationOff = true
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertEquals(DiscoveryState.LocationOff, discovery.state.value)
        assertEquals(0, driver.discoverCalls)
    }

    @Test
    fun scansForThirtySecondsThenFinishesWithWhatItFound() = runTest {
        val discovery = discovery()
        discovery.start()
        runCurrent()
        assertTrue(discovery.state.value is DiscoveryState.Scanning)
        driver.peers.value = listOf(tv("TV"), phone("Redmi Y2"))
        advanceTimeBy(29_000)
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
        advanceTimeBy(30_001) // one full search window
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
        advanceTimeBy(20_000)
        driver.enabled.value = false
        runCurrent()
        assertEquals(DiscoveryState.WifiOff, discovery.state.value)
        advanceTimeBy(60_000) // stays waiting, no timeout while Wi-Fi is off
        assertEquals(DiscoveryState.WifiOff, discovery.state.value)
        driver.enabled.value = true
        advanceTimeBy(29_000)
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
        advanceTimeBy(30_001) // one full search window
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
}
