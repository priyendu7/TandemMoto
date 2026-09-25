package com.tandemmoto.ui.components

import androidx.annotation.StringRes
import com.tandemmoto.R

/** How serious a status is; drives its colour and icon. */
enum class StatusKind { Neutral, InProgress, Ok, Info, Problem }

/**
 * Every link/peripheral state the PRD asks the UI to surface (PRD §3 "clear error states").
 * [namedLabel] is used instead of [label] when the paired phone's name is known ("Connected to
 * Redmi Y2"); it takes the name as `%1$s`.
 */
enum class ConnectionStatus(
    @StringRes val label: Int,
    val kind: StatusKind,
    @StringRes val namedLabel: Int? = null
) {
    NotPaired(R.string.status_not_paired, StatusKind.Neutral),
    NotConnected(
        R.string.status_not_connected,
        StatusKind.Neutral,
        R.string.status_not_connected_named
    ),
    PairedElsewhere(
        R.string.status_paired_elsewhere,
        StatusKind.Problem,
        R.string.status_paired_elsewhere_named
    ),
    NoLongerPaired(
        R.string.status_no_longer_paired,
        StatusKind.Problem,
        R.string.status_no_longer_paired_named
    ),
    PartnerAppClosed(
        R.string.status_partner_app_closed,
        StatusKind.Info,
        R.string.status_partner_app_closed_named
    ),
    UpdateNeeded(R.string.status_update_needed, StatusKind.Problem),
    Searching(R.string.status_searching, StatusKind.InProgress, R.string.status_searching_named),
    Connected(R.string.status_connected, StatusKind.Ok, R.string.status_connected_named),
    Reconnecting(
        R.string.status_reconnecting,
        StatusKind.InProgress,
        R.string.status_reconnecting_named
    ),
    PartnerDisconnected(
        R.string.status_partner_disconnected,
        StatusKind.Problem,
        R.string.status_partner_disconnected_named
    ),
    PartnerOnCall(
        R.string.status_partner_on_call,
        StatusKind.Info,
        R.string.status_partner_on_call_named
    ),
    HeadsetDisconnected(R.string.status_headset_disconnected, StatusKind.Problem),
    MicPermissionMissing(R.string.status_mic_permission_missing, StatusKind.Problem)
}
