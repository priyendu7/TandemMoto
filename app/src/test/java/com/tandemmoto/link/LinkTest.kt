package com.tandemmoto.link

import com.tandemmoto.link.Partner.Role.Acceptor
import com.tandemmoto.link.Partner.Role.Initiator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkTest {
    private val driver = FakeWifiP2pDriver()
    private val redmi = phone("Redmi Y2")
    private val s25 = phone("Galaxy S25")

    private fun TestScope.link(store: PartnerStore = InMemoryPartnerStore()) = Link(
        driver = driver,
        preconditions = FakePreconditions(),
        store = store,
        scope = backgroundScope,
        now = { 1_000L }
    ).also {
        it.start()
        runCurrent()
    }

    private fun saved(device: NearbyDevice, role: Partner.Role) =
        Partner(device.name, device.address, role, 1_000L)

    // ---- Pairing ----

    @Test
    fun tappingAPhoneInvitesItAndAcceptingPairsAsInitiator() = runTest {
        val store = InMemoryPartnerStore()
        val link = link(store)
        link.openPairScreen()
        link.invite(redmi)
        runCurrent()
        assertEquals(PairingState.Inviting(redmi), link.pairing.value)
        assertEquals(listOf(redmi.address), driver.connectCalls)

        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(PairingState.Paired(saved(redmi, Initiator)), link.pairing.value)
        assertEquals(saved(redmi, Initiator), store.saved)
        assertEquals(LinkStatus.Connected(saved(redmi, Initiator)), link.status.value)
    }

    @Test
    fun acceptingAnInvitationOnThePairScreenPairsAsAcceptor() = runTest {
        val store = InMemoryPartnerStore()
        val link = link(store)
        link.openPairScreen()
        driver.formGroupWith(s25, isGroupOwner = false)
        runCurrent()
        assertEquals(PairingState.Paired(saved(s25, Acceptor)), link.pairing.value)
        assertEquals(saved(s25, Acceptor), store.saved)
        assertTrue(driver.connectCalls.isEmpty())
    }

    @Test
    fun noAnswerWithinTheTimeoutCancelsTheInvitation() = runTest {
        val link = link()
        link.openPairScreen()
        link.invite(redmi)
        advanceTimeBy(Link.INVITE_TIMEOUT_MS - 1_000)
        assertEquals(PairingState.Inviting(redmi), link.pairing.value)
        advanceTimeBy(1_001)
        assertEquals(PairingState.Failed(PairingState.Failed.Reason.NoAnswer), link.pairing.value)
        assertTrue(driver.cancelConnectCalls >= 1)
    }

    @Test
    fun busyInvitationFailsWithBusy() = runTest {
        driver.connectResults.addLast(P2pResult.Busy)
        val link = link()
        link.openPairScreen()
        link.invite(redmi)
        runCurrent()
        assertEquals(PairingState.Failed(PairingState.Failed.Reason.Busy), link.pairing.value)
    }

    @Test
    fun invitingAgainOrWhileAGroupExistsIsIgnored() = runTest {
        val link = link()
        link.openPairScreen()
        link.invite(redmi)
        runCurrent()
        link.invite(s25) // still inviting the Redmi
        runCurrent()
        assertEquals(listOf(redmi.address), driver.connectCalls)

        link.cancelInvite()
        driver.formGroupWith(tv("Some TV"))
        link.invite(s25) // a group exists
        runCurrent()
        assertEquals(listOf(redmi.address), driver.connectCalls)
    }

    @Test
    fun pairingWithADifferentPhoneAsksBeforeReplacing() = runTest {
        val current = saved(s25, Initiator)
        val link = link(InMemoryPartnerStore(current))
        link.openPairScreen()
        link.invite(redmi)
        runCurrent()
        assertEquals(PairingState.ConfirmReplace(redmi, current), link.pairing.value)
        assertTrue(driver.connectCalls.isEmpty())

        link.confirmReplace()
        runCurrent()
        assertEquals(PairingState.Inviting(redmi), link.pairing.value)
        assertEquals(listOf(redmi.address), driver.connectCalls)
    }

    @Test
    fun cancellingAnInvitationWithdrawsIt() = runTest {
        val link = link()
        link.openPairScreen()
        link.invite(redmi)
        runCurrent()
        val before = driver.cancelConnectCalls
        link.cancelInvite()
        runCurrent()
        assertEquals(PairingState.Idle, link.pairing.value)
        assertEquals(before + 1, driver.cancelConnectCalls)
    }

    @Test
    fun leavingThePairScreenWhileInvitingCancelsTheInvitation() = runTest {
        val link = link()
        link.openPairScreen()
        link.invite(redmi)
        runCurrent()
        link.closePairScreen()
        runCurrent()
        assertEquals(PairingState.Idle, link.pairing.value)
        advanceTimeBy(Link.INVITE_TIMEOUT_MS * 2)
        assertEquals(PairingState.Idle, link.pairing.value)
    }

    // ---- Wrong-device guard ----

    @Test
    fun aGroupWithAnotherPhoneIsRemovedWhenNotPairing() = runTest {
        val link = link(InMemoryPartnerStore(saved(s25, Acceptor)))
        advanceTimeBy(PeerDiscovery.SCAN_DURATION_MS + 1) // let the startup attempt end
        driver.formGroupWith(redmi) // e.g. a phone that was once paired, reconnecting silently
        runCurrent()
        assertEquals(1, driver.removeGroupCalls)
        assertTrue(link.status.value !is LinkStatus.Connected)
    }

    // ---- Connecting to the saved partner ----

    @Test
    fun initiatorClearsStuckInvitationsFindsThePartnerAndConnects() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        assertEquals(LinkStatus.Connecting(partner), link.status.value)
        assertTrue(driver.cancelConnectCalls >= 1) // a stuck invitation survives restarts
        assertTrue(driver.connectCalls.isEmpty()) // not visible yet

        driver.peers.value = listOf(tv("TV"), redmi)
        runCurrent()
        assertEquals(listOf(redmi.address), driver.connectCalls)

        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(LinkStatus.Connected(partner), link.status.value)
    }

    @Test
    fun acceptorNeverCallsConnectAndWaitsForThePartner() = runTest {
        val partner = saved(s25, Acceptor)
        val link = link(InMemoryPartnerStore(partner))
        driver.peers.value = listOf(s25)
        runCurrent()
        assertTrue(driver.connectCalls.isEmpty())
        assertTrue(driver.discoverCalls >= 1) // stays visible

        driver.formGroupWith(s25, isGroupOwner = false)
        runCurrent()
        assertEquals(LinkStatus.Connected(partner), link.status.value)
    }

    @Test
    fun notReachedWithinTheWindowMeansNotConnected() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        advanceTimeBy(PeerDiscovery.SCAN_DURATION_MS + 1)
        assertEquals(LinkStatus.NotConnected(partner), link.status.value)
    }

    @Test
    fun anExistingGroupWithThePartnerIsReused() = runTest {
        val partner = saved(redmi, Initiator)
        driver.formGroupWith(redmi) // left over from before the app restarted
        val link = link(InMemoryPartnerStore(partner))
        assertEquals(LinkStatus.Connected(partner), link.status.value)
        assertTrue(driver.connectCalls.isEmpty())
    }

    @Test
    fun partnerMatchedByNameGetsItsNewAddressSaved() = runTest {
        val store = InMemoryPartnerStore(saved(redmi, Initiator))
        val link = link(store)
        val moved = redmi.copy(address = "new-address")
        driver.peers.value = listOf(moved)
        runCurrent()
        assertEquals(listOf("new-address"), driver.connectCalls)
        driver.formGroupWith(moved)
        runCurrent()
        assertEquals("new-address", store.saved?.address)
        assertEquals("new-address", (link.status.value as LinkStatus.Connected).partner.address)
    }

    @Test
    fun partnerDroppingTheGroupAtOnceSuggestsItPairedElsewhere() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        driver.peers.value = listOf(redmi)
        runCurrent()
        driver.formGroupWith(redmi)
        runCurrent()
        driver.group.value = null // the partner removed it straight away
        runCurrent()
        assertEquals(
            LinkStatus.NotConnected(partner, maybePairedElsewhere = true),
            link.status.value
        )
    }

    @Test
    fun aLaterDropIsAnOrdinaryDisconnect() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        driver.formGroupWith(redmi)
        runCurrent()
        advanceTimeBy(Link.REJECTION_WINDOW_MS + 1)
        driver.group.value = null
        runCurrent()
        assertEquals(LinkStatus.NotConnected(partner), link.status.value)
    }

    @Test
    fun tapToConnectStartsAnotherAttempt() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        advanceTimeBy(PeerDiscovery.SCAN_DURATION_MS + 1)
        assertEquals(LinkStatus.NotConnected(partner), link.status.value)
        link.connectToPartner()
        runCurrent()
        assertEquals(LinkStatus.Connecting(partner), link.status.value)
    }

    // ---- Forget ----

    @Test
    fun forgettingClearsThePartnerAndTheGroup() = runTest {
        val store = InMemoryPartnerStore(saved(redmi, Initiator))
        val link = link(store)
        driver.formGroupWith(redmi)
        runCurrent()
        link.forgetPartner()
        runCurrent()
        assertNull(store.saved)
        assertNull(link.partner.value)
        assertEquals(LinkStatus.NotPaired, link.status.value)
        assertEquals(1, driver.removeGroupCalls)
    }

    @Test
    fun withoutAPartnerNothingConnectsOnStart() = runTest {
        val link = link()
        assertEquals(LinkStatus.NotPaired, link.status.value)
        assertEquals(0, driver.discoverCalls)
        assertTrue(driver.connectCalls.isEmpty())
    }
}
