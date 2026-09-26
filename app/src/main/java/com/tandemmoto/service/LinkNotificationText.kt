package com.tandemmoto.service

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tandemmoto.R
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.link.LinkStatus.NotConnected.Reason

/** A button on the link notification. */
enum class NotificationAction(@StringRes val label: Int, @DrawableRes val icon: Int) {
    /** Drop the link and stop the service. */
    Disconnect(R.string.notification_disconnect, R.drawable.ic_close),

    /** Still searching: stop trying (same as Disconnect underneath). */
    Stop(R.string.notification_stop, R.drawable.ic_close),

    /** Try to reach the partner again. */
    Connect(R.string.notification_connect, R.drawable.ic_notification_link),

    /** Open the Wi-Fi panel. */
    TurnOnWifi(R.string.notification_turn_on_wifi, R.drawable.ic_notification_link),

    /** Not connected anyway: just stop the service (same as Disconnect underneath). */
    Close(R.string.notification_close, R.drawable.ic_close)
}

/**
 * The notification's line and buttons for a link status; [name] fills `%1$s` when the string
 * takes it. Buttons follow the state: phone test on #40 showed "Disconnect" after the partner had
 * already disconnected.
 */
data class NotificationText(
    @StringRes val text: Int,
    val name: String? = null,
    val actions: List<NotificationAction> = listOf(NotificationAction.Disconnect)
)

private val reconnect = listOf(NotificationAction.Connect, NotificationAction.Close)

fun LinkStatus.notificationText(): NotificationText = when (this) {
    LinkStatus.NotPaired ->
        NotificationText(
            R.string.notification_not_paired,
            actions = listOf(NotificationAction.Close)
        )
    is LinkStatus.Connecting -> NotificationText(
        R.string.notification_connecting,
        partner.name,
        listOf(NotificationAction.Stop)
    )
    is LinkStatus.Connected -> NotificationText(R.string.notification_connected, partner.name)
    is LinkStatus.Reconnecting -> NotificationText(
        R.string.notification_reconnecting,
        partner.name,
        listOf(NotificationAction.Stop)
    )
    is LinkStatus.NotConnected -> when (reason) {
        Reason.WifiOff -> NotificationText(
            R.string.notification_wifi_off,
            actions = listOf(NotificationAction.TurnOnWifi, NotificationAction.Close)
        )
        // Still in a group: the partner's app may come back on its own.
        Reason.PartnerAppClosed ->
            NotificationText(R.string.notification_partner_app_closed, partner.name)
        // This phone is listening; only the partner's phone can reconnect (#27), so no Connect.
        Reason.PartnerDisconnected -> NotificationText(
            R.string.notification_partner_disconnected,
            partner.name,
            listOf(NotificationAction.Close)
        )
        // Only the app can fix these (pair again, update): tapping the notification opens it.
        Reason.NoLongerPaired, Reason.UpdateNeeded -> NotificationText(
            R.string.notification_not_connected,
            partner.name,
            listOf(NotificationAction.Close)
        )
        Reason.Unreachable ->
            NotificationText(R.string.notification_unreachable, partner.name, reconnect)
        Reason.Disconnected, Reason.MaybePairedElsewhere ->
            NotificationText(R.string.notification_not_connected, partner.name, reconnect)
    }
}
