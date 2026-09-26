package com.tandemmoto.ui

import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.LinkStatus.NotConnected.Reason
import com.tandemmoto.link.Partner
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.ride.CONNECTED_BLIP_MS
import com.tandemmoto.ui.ride.RideViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RideViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val partner = Partner("Redmi Y2", "addr", Partner.Role.Initiator, 0L)
    private val status = MutableStateFlow<LinkStatus>(LinkStatus.NotPaired)
    private var connects = 0
    private var disconnects = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RideViewModel(status, { connects++ }, { disconnects++ })

    private fun TestScope.set(value: LinkStatus) {
        status.value = value
        runCurrent()
    }

    @Test
    fun followsTheLinkStatusAndPartnersName() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        assertEquals(ConnectionStatus.NotPaired, vm.uiState.value.connection)
        assertNull(vm.uiState.value.partnerName)

        set(LinkStatus.Connecting(partner))
        assertEquals(ConnectionStatus.Searching, vm.uiState.value.connection)
        assertEquals("Redmi Y2", vm.uiState.value.partnerName)
        set(LinkStatus.Connected(partner))
        assertEquals(ConnectionStatus.Connected, vm.uiState.value.connection)
        set(LinkStatus.NotConnected(partner))
        assertEquals(ConnectionStatus.Unreachable, vm.uiState.value.connection)
    }

    @Test
    fun aBriefBlipAwayFromConnectedIsNeverShown() = runTest(dispatcher) {
        val vm = viewModel()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.Connecting(partner)) // the socket reopening inside the group
        advanceTimeBy(CONNECTED_BLIP_MS / 2)
        set(LinkStatus.Connected(partner))
        advanceTimeBy(CONNECTED_BLIP_MS * 2)
        assertEquals(ConnectionStatus.Connected, vm.uiState.value.connection)
    }

    @Test
    fun aLastingChangeFromConnectedIsShownAfterTheHold() = runTest(dispatcher) {
        val vm = viewModel()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.Reconnecting(partner))
        assertEquals(ConnectionStatus.Connected, vm.uiState.value.connection)
        advanceTimeBy(CONNECTED_BLIP_MS + 1)
        assertEquals(ConnectionStatus.Reconnecting, vm.uiState.value.connection)
    }

    @Test
    fun otherChangesAreShownAtOnce() = runTest(dispatcher) {
        val vm = viewModel()
        set(LinkStatus.Connected(partner))
        set(LinkStatus.NotConnected(partner, Reason.WifiOff)) // not a blip: say it now
        assertEquals(ConnectionStatus.WifiOff, vm.uiState.value.connection)
        set(LinkStatus.Reconnecting(partner))
        assertEquals(ConnectionStatus.Reconnecting, vm.uiState.value.connection)
    }

    @Test
    fun connectAndDisconnectGoToTheLink() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onConnect()
        vm.onDisconnect()
        assertEquals(1, connects)
        assertEquals(1, disconnects)
    }

    @Test
    fun playbackStateIsUntouchedByTheLink() = runTest(dispatcher) {
        val vm = viewModel()
        set(LinkStatus.Connected(partner))
        assertEquals(null, vm.uiState.value.nowPlaying)
        assertEquals(false, vm.uiState.value.isPlaying)
    }
}
