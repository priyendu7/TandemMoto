package com.tandemmoto.link

import com.tandemmoto.link.LinkStatus.NotConnected.Reason
import com.tandemmoto.link.Partner.Role.Acceptor
import com.tandemmoto.link.Partner.Role.Initiator
import com.tandemmoto.state.Message
import com.tandemmoto.state.Message.Bye
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
    private companion object {
        const val MY_INSTALL = "my-install"
    }

    private val driver = FakeWifiP2pDriver()
    private val redmi = phone("Redmi Y2")
    private val s25 = phone("Galaxy S25")

    private lateinit var transport: FakeTransport
    private lateinit var partnerApp: FakePartnerApp

    /** [partnerAppRunning]: the partner phone's app answers on the command channel. */
    private fun TestScope.link(
        store: PartnerStore = InMemoryPartnerStore(),
        partnerAppRunning: Boolean = true
    ): Link {
        partnerApp = FakePartnerApp(backgroundScope)
        transport = FakeTransport(partnerApp.takeIf { partnerAppRunning })
        return Link(
            driver = driver,
            preconditions = FakePreconditions(),
            store = store,
            scope = backgroundScope,
            now = { 1_000L },
            transport = transport,
            installId = { MY_INSTALL },
            appVersion = "0.1.0",
            nanoTime = { testScheduler.currentTime * 1_000_000 }
        ).also {
            it.start()
            runCurrent()
        }
    }

    private fun saved(device: NearbyDevice, role: Partner.Role) =
        Partner(device.name, device.address, role, 1_000L)

    /** The partner after its app's first Hello. */
    private fun Partner.learned() = copy(installId = partnerApp.hello.installId)

    /** The partner's app is closed: its connections drop and nothing answers any more. */
    private fun closePartnerApp() {
        transport.partnerApp.value = null
        partnerApp.quit()
    }

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
        assertEquals(saved(redmi, Initiator).learned(), store.saved)
        assertEquals(LinkStatus.Connected(saved(redmi, Initiator).learned()), link.status.value)
    }

    @Test
    fun acceptingAnInvitationOnThePairScreenPairsAsAcceptor() = runTest {
        val store = InMemoryPartnerStore()
        val link = link(store)
        link.openPairScreen()
        driver.formGroupWith(s25, isGroupOwner = false)
        runCurrent()
        assertEquals(PairingState.Paired(saved(s25, Acceptor)), link.pairing.value)
        assertEquals(saved(s25, Acceptor).learned(), store.saved)
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
    fun busyEveryTimeFailsWithBusyAfterRetries() = runTest {
        repeat(Link.CONNECT_ATTEMPTS) { driver.connectResults.addLast(P2pResult.Busy) }
        val link = link()
        link.openPairScreen()
        link.invite(redmi)
        advanceTimeBy(Link.CONNECT_RETRY_MS * Link.CONNECT_ATTEMPTS)
        assertEquals(PairingState.Failed(PairingState.Failed.Reason.Busy), link.pairing.value)
        assertEquals(Link.CONNECT_ATTEMPTS, driver.connectCalls.size)
    }

    @Test
    fun aRefusedConnectIsRetriedAndThenInvites() = runTest {
        // Phone test on #24: connect() failed with ERROR right after tapping.
        driver.connectResults.addLast(P2pResult.Error)
        val link = link()
        link.openPairScreen()
        link.invite(redmi)
        advanceTimeBy(Link.CONNECT_RETRY_MS + 1)
        assertEquals(PairingState.Inviting(redmi), link.pairing.value)
        assertEquals(2, driver.connectCalls.size)
    }

    @Test
    fun invitingDoesNotAskAndroidToStopDiscovery() = runTest {
        // Stopping discovery and connecting at once made connect() fail on both phones.
        val link = link()
        link.openPairScreen()
        runCurrent()
        val stopsBefore = driver.stopCalls
        link.invite(redmi)
        runCurrent()
        assertEquals(stopsBefore, driver.stopCalls)
        assertEquals(listOf(redmi.address), driver.connectCalls)
    }

    @Test
    fun thePairScreenKeepsListeningWhileOpen() = runTest {
        // A phone only receives an invitation while it's discovering.
        val link = link()
        link.openPairScreen()
        advanceTimeBy(5 * 60_000L)
        assertTrue(link.discovery.state.value is DiscoveryState.Scanning)
        link.closePairScreen()
        runCurrent()
        assertEquals(DiscoveryState.Idle, link.discovery.state.value)
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
        // Its app was told why before the group went.
        assertEquals(listOf(Message.Bye(Bye.Reason.NotYourPartner)), partnerApp.byesReceived)
    }

    @Test
    fun theGuardStillRemovesTheGroupWhenTheOtherAppDoesNotAnswer() = runTest {
        link(InMemoryPartnerStore(saved(s25, Acceptor)), partnerAppRunning = false)
        advanceTimeBy(PeerDiscovery.SCAN_DURATION_MS + 1)
        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(0, driver.removeGroupCalls)
        advanceTimeBy(Link.GUARD_TIMEOUT_MS + 1)
        assertEquals(1, driver.removeGroupCalls)
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
        assertEquals(LinkStatus.Connected(partner.learned()), link.status.value)
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
        assertEquals(LinkStatus.Connected(partner.learned()), link.status.value)
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
        assertEquals(LinkStatus.Connected(partner.learned()), link.status.value)
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
            LinkStatus.NotConnected(partner.learned(), Reason.MaybePairedElsewhere),
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
        assertEquals(LinkStatus.NotConnected(partner.learned()), link.status.value)
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

    // ---- Command channel (#25) ----

    @Test
    fun aGroupAloneIsNotConnectedUntilThePartnersAppAnswers() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner), partnerAppRunning = false)
        driver.formGroupWith(redmi) // Android keeps the group even with the app closed
        runCurrent()
        assertEquals(LinkStatus.Connecting(partner), link.status.value)

        advanceTimeBy(Link.PARTNER_APP_TIMEOUT_MS + 1)
        assertEquals(LinkStatus.NotConnected(partner, Reason.PartnerAppClosed), link.status.value)

        transport.partnerApp.value = partnerApp // the partner opens the app
        advanceTimeBy(CommandChannel.SLOW_RETRY_MS + 1)
        assertEquals(LinkStatus.Connected(partner.learned()), link.status.value)
    }

    @Test
    fun closingThePartnersAppIsNoticed() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(LinkStatus.Connected(partner.learned()), link.status.value)

        closePartnerApp()
        runCurrent()
        assertEquals(LinkStatus.Connecting(partner.learned()), link.status.value)
        advanceTimeBy(Link.PARTNER_APP_TIMEOUT_MS + 1)
        assertEquals(
            LinkStatus.NotConnected(partner.learned(), Reason.PartnerAppClosed),
            link.status.value
        )
    }

    @Test
    fun aPartnerThatGoesSilentIsNotConnectedNotAClosedApp() = runTest {
        // #25 phone test: Wi-Fi off on the S25; the Redmi's Android kept the group ~13 s longer.
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        driver.formGroupWith(redmi)
        runCurrent()
        partnerApp.answersPings = false // gone: silent, and unreachable
        transport.partnerApp.value = null
        advanceTimeBy(CommandChannel.SILENCE_TIMEOUT_MS + CommandChannel.HEARTBEAT_MS)
        assertEquals(LinkStatus.NotConnected(partner.learned()), link.status.value)
        advanceTimeBy(Link.PARTNER_APP_TIMEOUT_MS * 2)
        assertEquals(LinkStatus.NotConnected(partner.learned()), link.status.value)

        partnerApp.answersPings = true // back in range: the channel is still retrying
        transport.partnerApp.value = partnerApp
        advanceTimeBy(CommandChannel.SLOW_RETRY_MS + 1)
        assertEquals(LinkStatus.Connected(partner.learned()), link.status.value)
    }

    @Test
    fun ourHelloCarriesOurIdAndWhoWeThinkThePartnerIs() = runTest {
        val partner = saved(redmi, Initiator).copy(installId = "partner-install")
        link(InMemoryPartnerStore(partner))
        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(
            listOf(Message.Hello("0.1.0", MY_INSTALL, "partner-install", "Initiator")),
            partnerApp.hellosReceived
        )
    }

    @Test
    fun aDifferentInstallAtThePartnersAddressIsRefused() = runTest {
        // e.g. the partner reinstalled the app, or another phone took over its name
        val partner = saved(redmi, Initiator).copy(installId = "someone-else")
        val store = InMemoryPartnerStore(partner)
        val link = link(store)
        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(LinkStatus.NotConnected(partner, Reason.NoLongerPaired), link.status.value)
        assertEquals(listOf(Message.Bye(Bye.Reason.NotYourPartner)), partnerApp.byesReceived)
        assertEquals("someone-else", store.saved?.installId)
    }

    @Test
    fun aPartnerThatPairedWithAnotherPhoneSaysSo() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        partnerApp.refuseWith = Bye.Reason.NotYourPartner
        driver.formGroupWith(redmi)
        runCurrent()
        val expected = LinkStatus.NotConnected(partner.learned(), Reason.NoLongerPaired)
        assertEquals(expected, link.status.value)

        driver.group.value = null // then it removes the group: the reason stays
        runCurrent()
        assertEquals(expected, link.status.value)
    }

    @Test
    fun aHelloNamingAnotherPartnerIsRefused() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        transport.partnerApp.value = FakePartnerApp(
            backgroundScope,
            hello = Message.Hello("0.1.0", "partner-install", "a-third-phone", "Acceptor")
        )
        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(LinkStatus.NotConnected(partner, Reason.NoLongerPaired), link.status.value)
    }

    @Test
    fun aProtocolMismatchAsksForAnUpdate() = runTest {
        val partner = saved(redmi, Initiator)
        val link = link(InMemoryPartnerStore(partner))
        partnerApp.refuseWith = Bye.Reason.ProtocolMismatch
        driver.formGroupWith(redmi)
        runCurrent()
        assertEquals(
            LinkStatus.NotConnected(partner.learned(), Reason.UpdateNeeded),
            link.status.value
        )
    }

    @Test
    fun repeatedGroupBroadcastsDoNotReopenTheChannel() = runTest {
        val partner = saved(redmi, Initiator)
        link(InMemoryPartnerStore(partner))
        driver.formGroupWith(redmi)
        runCurrent()
        driver.formGroupWith(redmi) // Android re-sends the same group
        runCurrent()
        assertEquals(1, partnerApp.hellosReceived.size)
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
        assertEquals(listOf(Message.Bye(Bye.Reason.NotYourPartner)), partnerApp.byesReceived)
    }

    @Test
    fun withoutAPartnerNothingConnectsOnStart() = runTest {
        val link = link()
        assertEquals(LinkStatus.NotPaired, link.status.value)
        assertEquals(0, driver.discoverCalls)
        assertTrue(driver.connectCalls.isEmpty())
    }
}
