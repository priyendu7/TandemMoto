package com.tandemmoto.service

import com.tandemmoto.R
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.LinkStatus.NotConnected.Reason
import com.tandemmoto.link.Partner
import com.tandemmoto.service.NotificationAction.Close
import com.tandemmoto.service.NotificationAction.Connect
import com.tandemmoto.service.NotificationAction.Disconnect
import com.tandemmoto.service.NotificationAction.Stop
import com.tandemmoto.service.NotificationAction.TurnOnWifi
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkNotificationTextTest {
    private val partner = Partner("Redmi Y2", "addr", Partner.Role.Initiator, 0L)
    private val name = partner.name

    private fun notConnected(reason: Reason) = LinkStatus.NotConnected(partner, reason)

    @Test
    fun everyStatusHasALineAndTheRightButtons() {
        val expected = mapOf(
            LinkStatus.NotPaired to
                NotificationText(R.string.notification_not_paired, null, listOf(Close)),
            LinkStatus.Connecting(partner) to
                NotificationText(R.string.notification_connecting, name, listOf(Stop)),
            LinkStatus.Connected(partner) to
                NotificationText(R.string.notification_connected, name, listOf(Disconnect)),
            notConnected(Reason.Unreachable) to
                NotificationText(R.string.notification_unreachable, name, listOf(Connect, Close)),
            notConnected(Reason.Disconnected) to
                NotificationText(R.string.notification_not_connected, name, listOf(Connect, Close)),
            notConnected(Reason.MaybePairedElsewhere) to
                NotificationText(R.string.notification_not_connected, name, listOf(Connect, Close)),
            notConnected(Reason.WifiOff) to
                NotificationText(R.string.notification_wifi_off, null, listOf(TurnOnWifi, Close)),
            notConnected(Reason.PartnerAppClosed) to NotificationText(
                R.string.notification_partner_app_closed,
                name,
                listOf(Disconnect)
            ),
            // Listening: only the partner's phone can reconnect, so no Connect (#27).
            notConnected(Reason.PartnerDisconnected) to NotificationText(
                R.string.notification_partner_disconnected,
                name,
                listOf(Close)
            ),
            LinkStatus.Reconnecting(partner) to
                NotificationText(R.string.notification_reconnecting, name, listOf(Stop)),
            notConnected(Reason.NoLongerPaired) to
                NotificationText(R.string.notification_not_connected, name, listOf(Close)),
            notConnected(Reason.UpdateNeeded) to
                NotificationText(R.string.notification_not_connected, name, listOf(Close))
        )
        expected.forEach { (status, text) ->
            assertEquals(status.toString(), text, status.notificationText())
        }
    }
}
