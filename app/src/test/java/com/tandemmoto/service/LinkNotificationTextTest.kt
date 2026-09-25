package com.tandemmoto.service

import com.tandemmoto.R
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.LinkStatus.NotConnected.Reason
import com.tandemmoto.link.Partner
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkNotificationTextTest {
    private val partner = Partner("Redmi Y2", "addr", Partner.Role.Initiator, 0L)
    private val name = partner.name

    @Test
    fun everyStatusHasALine() {
        val expected = mapOf(
            LinkStatus.NotPaired to NotificationText(R.string.notification_not_paired),
            LinkStatus.Connecting(partner) to
                NotificationText(R.string.notification_connecting, name),
            LinkStatus.Connected(
                partner
            ) to NotificationText(R.string.notification_connected, name),
            LinkStatus.NotConnected(partner) to
                NotificationText(R.string.notification_not_connected, name),
            LinkStatus.NotConnected(partner, Reason.WifiOff) to
                NotificationText(R.string.notification_wifi_off),
            LinkStatus.NotConnected(partner, Reason.PartnerAppClosed) to
                NotificationText(R.string.notification_partner_app_closed, name),
            LinkStatus.NotConnected(partner, Reason.PartnerDisconnected) to
                NotificationText(R.string.notification_partner_disconnected, name),
            LinkStatus.NotConnected(partner, Reason.NoLongerPaired) to
                NotificationText(R.string.notification_not_connected, name),
            LinkStatus.NotConnected(partner, Reason.UpdateNeeded) to
                NotificationText(R.string.notification_not_connected, name),
            LinkStatus.NotConnected(partner, Reason.MaybePairedElsewhere) to
                NotificationText(R.string.notification_not_connected, name)
        )
        expected.forEach { (status, text) ->
            assertEquals(status.toString(), text, status.notificationText())
        }
    }
}
