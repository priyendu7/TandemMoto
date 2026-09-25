package com.tandemmoto.ui.components

import androidx.annotation.StringRes
import com.tandemmoto.R

/** How serious a status is; drives its colour and icon. */
enum class StatusKind { Neutral, InProgress, Ok, Info, Problem }

/**
 * Every link/peripheral state the PRD asks the UI to surface (PRD §3 "clear error states").
 * Phase 1 will drive this from the real connection manager.
 */
enum class ConnectionStatus(@StringRes val label: Int, val kind: StatusKind) {
    NotPaired(R.string.status_not_paired, StatusKind.Neutral),
    NotConnected(R.string.status_not_connected, StatusKind.Neutral),
    PairedElsewhere(R.string.status_paired_elsewhere, StatusKind.Problem),
    Searching(R.string.status_searching, StatusKind.InProgress),
    Connected(R.string.status_connected, StatusKind.Ok),
    Reconnecting(R.string.status_reconnecting, StatusKind.InProgress),
    PartnerDisconnected(R.string.status_partner_disconnected, StatusKind.Problem),
    PartnerOnCall(R.string.status_partner_on_call, StatusKind.Info),
    HeadsetDisconnected(R.string.status_headset_disconnected, StatusKind.Problem),
    MicPermissionMissing(R.string.status_mic_permission_missing, StatusKind.Problem)
}
