package com.tandemmoto.ui.components

import androidx.annotation.StringRes
import com.tandemmoto.R

/** How serious a status is; drives its icon. */
enum class StatusKind { Neutral, InProgress, Ok, Info, Problem }

/** The connection bar's colour: green when connected, yellow while looking, orange otherwise. */
enum class BarTone { Connected, Looking, NotConnected }

/**
 * Every link/peripheral state the PRD asks the UI to surface (PRD §3 "clear error states").
 * [label] / [namedLabel] are the full sentences, read by TalkBack and used in the notification;
 * [namedLabel] takes the paired phone's name as `%1$s` ("Connected to Redmi Y2"). [short] is the
 * status card's first line; its `%1$s`, if any, is drawn as the partner's box. [subtitle] is the
 * second line, what to do next. [action] is the card's trailing chip ("Connect"), shown only when
 * tapping the card does something.
 */
enum class ConnectionStatus(
    @StringRes val label: Int,
    val kind: StatusKind,
    @StringRes val short: Int,
    @StringRes val subtitle: Int,
    @StringRes val namedLabel: Int? = null,
    @StringRes val action: Int? = null
) {
    NotPaired(
        R.string.status_not_paired,
        StatusKind.Neutral,
        R.string.status_not_paired_short,
        R.string.status_not_paired_subtitle,
        action = R.string.status_action_pair
    ),

    /** The user disconnected: tap to connect again. */
    NotConnected(
        R.string.status_not_connected,
        StatusKind.Neutral,
        R.string.status_not_connected_short,
        R.string.status_not_connected_subtitle,
        R.string.status_not_connected_named,
        R.string.status_action_connect
    ),

    /** Tried for the whole reconnect window without reaching the partner (#27). */
    Unreachable(
        R.string.status_unreachable,
        StatusKind.Problem,
        R.string.status_unreachable_short,
        R.string.status_unreachable_subtitle,
        R.string.status_unreachable_named,
        R.string.status_action_retry
    ),
    PairedElsewhere(
        R.string.status_paired_elsewhere,
        StatusKind.Problem,
        R.string.status_paired_elsewhere_short,
        R.string.status_pair_again_subtitle,
        R.string.status_paired_elsewhere_named,
        R.string.status_action_pair
    ),
    NoLongerPaired(
        R.string.status_no_longer_paired,
        StatusKind.Problem,
        R.string.status_no_longer_paired_short,
        R.string.status_pair_again_subtitle,
        R.string.status_no_longer_paired_named,
        R.string.status_action_pair
    ),
    PartnerAppClosed(
        R.string.status_partner_app_closed,
        StatusKind.Info,
        R.string.status_partner_app_closed_short,
        R.string.status_partner_app_closed_subtitle,
        R.string.status_partner_app_closed_named
    ),
    UpdateNeeded(
        R.string.status_update_needed,
        StatusKind.Problem,
        R.string.status_update_needed_short,
        R.string.status_update_needed_subtitle
    ),
    WifiOff(
        R.string.status_wifi_off,
        StatusKind.Problem,
        R.string.status_wifi_off_short,
        R.string.status_wifi_off_subtitle,
        action = R.string.status_action_wifi
    ),
    Searching(
        R.string.status_searching,
        StatusKind.InProgress,
        R.string.status_searching_short,
        R.string.status_searching_subtitle,
        R.string.status_searching_named
    ),
    Connected(
        R.string.status_connected,
        StatusKind.Ok,
        R.string.status_connected_short,
        R.string.status_connected_subtitle,
        R.string.status_connected_named
    ),
    Reconnecting(
        R.string.status_reconnecting,
        StatusKind.InProgress,
        R.string.status_reconnecting_short,
        R.string.status_searching_subtitle,
        R.string.status_reconnecting_named
    ),
    PartnerDisconnected(
        R.string.status_partner_disconnected,
        StatusKind.Problem,
        R.string.status_partner_disconnected_short,
        R.string.status_partner_disconnected_subtitle,
        R.string.status_partner_disconnected_named
    ),
    PartnerOnCall(
        R.string.status_partner_on_call,
        StatusKind.Info,
        R.string.status_partner_on_call_short,
        R.string.status_partner_on_call_subtitle,
        R.string.status_partner_on_call_named
    ),
    HeadsetDisconnected(
        R.string.status_headset_disconnected,
        StatusKind.Problem,
        R.string.status_headset_disconnected_short,
        R.string.status_headset_disconnected_subtitle
    ),
    MicPermissionMissing(
        R.string.status_mic_permission_missing,
        StatusKind.Problem,
        R.string.status_mic_permission_missing_short,
        R.string.status_mic_permission_missing_subtitle
    );

    val tone: BarTone
        get() = when (this) {
            Connected, PartnerOnCall -> BarTone.Connected
            Searching, Reconnecting, PartnerAppClosed -> BarTone.Looking
            NotPaired, NotConnected, Unreachable, PairedElsewhere, NoLongerPaired,
            PartnerDisconnected, WifiOff, UpdateNeeded, HeadsetDisconnected,
            MicPermissionMissing -> BarTone.NotConnected
        }
}
