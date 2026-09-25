package com.tandemmoto.ui.ride

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tandemmoto.link
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.ui.components.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the Ride (Home) screen state: the link status comes from the shared [link]; playback
 * (Phase 2–3) still has its fixed initial state.
 */
class RideViewModel(app: Application) : AndroidViewModel(app) {
    private val link = app.link
    private val playback = MutableStateFlow(RideUiState())

    val uiState: StateFlow<RideUiState> =
        combine(playback, link.status) { state, status ->
            state.copy(connection = status.toUi(), partnerName = status.partnerName())
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RideUiState())

    /** "Not connected · Tap to connect". */
    fun onConnect() = link.connectToPartner()

    // TODO(Phase 3): send play/pause/skip through the MediaSession and mirror them to the partner.
    fun onPlayPause() = Unit

    fun onNext() = Unit

    fun onPrevious() = Unit
}

internal fun LinkStatus.partnerName(): String? = when (this) {
    LinkStatus.NotPaired -> null
    is LinkStatus.Connecting -> partner.name
    is LinkStatus.Connected -> partner.name
    is LinkStatus.NotConnected -> partner.name
}

internal fun LinkStatus.toUi(): ConnectionStatus = when (this) {
    LinkStatus.NotPaired -> ConnectionStatus.NotPaired
    is LinkStatus.Connecting -> ConnectionStatus.Searching
    is LinkStatus.Connected -> ConnectionStatus.Connected
    is LinkStatus.NotConnected -> when {
        maybePairedElsewhere -> ConnectionStatus.PairedElsewhere
        else -> ConnectionStatus.NotConnected
    }
}
