package com.tandemmoto.ui.ride

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the Ride screen state. Until the link (Phase 1) and player (Phase 2–3) exist it only
 * exposes the initial "not paired" state; those phases replace the source of [uiState].
 */
class RideViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RideUiState())
    val uiState: StateFlow<RideUiState> = _uiState.asStateFlow()

    // TODO(Phase 3): send play/pause/skip through the MediaSession and mirror them to the partner.
    fun onPlayPause() = Unit

    fun onNext() = Unit

    fun onPrevious() = Unit
}
