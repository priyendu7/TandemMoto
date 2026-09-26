package com.tandemmoto.ui

import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.LinkStatus.NotConnected.Reason
import com.tandemmoto.link.Partner
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.ride.partnerName
import com.tandemmoto.ui.ride.toUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkStatusMappingTest {
    private val partner = Partner("Redmi Y2", "addr", Partner.Role.Initiator, 0L)

    @Test
    fun everyLinkStatusMapsToAConnectionStatus() {
        val expected = mapOf(
            LinkStatus.NotPaired to ConnectionStatus.NotPaired,
            LinkStatus.Connecting(partner) to ConnectionStatus.Searching,
            LinkStatus.Reconnecting(partner) to ConnectionStatus.Reconnecting,
            LinkStatus.Connected(partner) to ConnectionStatus.Connected,
            LinkStatus.NotConnected(partner) to ConnectionStatus.Unreachable,
            LinkStatus.NotConnected(partner, Reason.Disconnected) to ConnectionStatus.NotConnected,
            LinkStatus.NotConnected(partner, Reason.MaybePairedElsewhere) to
                ConnectionStatus.PairedElsewhere,
            LinkStatus.NotConnected(partner, Reason.NoLongerPaired) to
                ConnectionStatus.NoLongerPaired,
            LinkStatus.NotConnected(partner, Reason.PartnerAppClosed) to
                ConnectionStatus.PartnerAppClosed,
            LinkStatus.NotConnected(partner, Reason.UpdateNeeded) to ConnectionStatus.UpdateNeeded,
            LinkStatus.NotConnected(partner, Reason.WifiOff) to ConnectionStatus.WifiOff,
            LinkStatus.NotConnected(partner, Reason.PartnerDisconnected) to
                ConnectionStatus.PartnerDisconnected
        )
        expected.forEach { (link, ui) -> assertEquals(link.toString(), ui, link.toUi()) }
    }

    @Test
    fun thePartnersNameIsShownWhenKnown() {
        assertNull(LinkStatus.NotPaired.partnerName())
        assertEquals("Redmi Y2", LinkStatus.Connected(partner).partnerName())
        assertEquals(
            "Redmi Y2",
            LinkStatus.NotConnected(partner, Reason.PartnerAppClosed).partnerName()
        )
    }
}
