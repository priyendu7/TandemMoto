package com.tandemmoto.service

import androidx.annotation.StringRes
import com.tandemmoto.R
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.LinkStatus.NotConnected.Reason

/** The notification's line for a link status; [name] fills `%1$s` when the string takes it. */
data class NotificationText(@StringRes val text: Int, val name: String? = null)

fun LinkStatus.notificationText(): NotificationText = when (this) {
    LinkStatus.NotPaired -> NotificationText(R.string.notification_not_paired)
    is LinkStatus.Connecting -> NotificationText(R.string.notification_connecting, partner.name)
    is LinkStatus.Connected -> NotificationText(R.string.notification_connected, partner.name)
    is LinkStatus.NotConnected -> when (reason) {
        Reason.WifiOff -> NotificationText(R.string.notification_wifi_off)
        Reason.PartnerAppClosed ->
            NotificationText(R.string.notification_partner_app_closed, partner.name)
        Reason.PartnerDisconnected ->
            NotificationText(R.string.notification_partner_disconnected, partner.name)
        else -> NotificationText(R.string.notification_not_connected, partner.name)
    }
}
