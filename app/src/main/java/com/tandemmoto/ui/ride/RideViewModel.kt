package com.tandemmoto.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tandemmoto.TandemMotoApp
import com.tandemmoto.link.LinkStatus
import com.tandemmoto.player.PlaybackState
import com.tandemmoto.ui.components.ConnectionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Holds the Ride (Home) screen state: the link status comes from the shared link; playback
 * (Phase 2–3) still has its fixed initial state. [connect] and [disconnect] are the link's, passed
 * in so tests can use a fake status flow.
 */
class RideViewModel(
    linkStatus: Flow<LinkStatus>,
    private val connect: () -> Unit,
    private val disconnect: () -> Unit,
    playback: Flow<PlaybackState> = flowOf(PlaybackState()),
    private val controls: PlayerControls = PlayerControls()
) : ViewModel() {
    val uiState: StateFlow<RideUiState> =
        combine(playback, linkStatus.holdingConnected()) { player, status ->
            RideUiState(
                connection = status.toUi(),
                partnerName = status.partnerName(),
                nowPlaying = player.title,
                artist = player.artist,
                isPlaying = player.playWhenReady,
                gettingSong = player.gettingSong,
                hasSongs = player.hasSongs
            )
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RideUiState())

    /** "Tap to connect" / "Tap to try again". */
    fun onConnect() = connect()

    /** Tapping the connected bar, after confirming: same as the notification's Disconnect. */
    fun onDisconnect() = disconnect()

    // Local in Phase 2 (#51); Phase 3 mirrors them to the partner.
    fun onPlayPause() = controls.playPause()

    fun onNext() = controls.next()

    fun onPrevious() = controls.previous()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as TandemMotoApp
                RideViewModel(
                    app.link.status,
                    app.link::connectToPartner,
                    app::disconnect,
                    app.playback.state,
                    PlayerControls(
                        app.playback::togglePlay,
                        app.playback::next,
                        app.playback::previous
                    )
                )
            }
        }
    }
}

/** The player's buttons, passed in so tests don't need a real player. */
class PlayerControls(
    val playPause: () -> Unit = {},
    val next: () -> Unit = {},
    val previous: () -> Unit = {}
)

/** How long a blip away from Connected is held back before it's shown (and announced). */
const val CONNECTED_BLIP_MS = 1_500L

/**
 * Holds back a change from Connected to Connecting/Reconnecting for [holdMs]; if Connected comes
 * back meanwhile, the blip is never shown. The socket reopening inside a group flips the status
 * for under a second, and TalkBack would read each flip out. Every other change passes at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<LinkStatus>.holdingConnected(holdMs: Long = CONNECTED_BLIP_MS): Flow<LinkStatus> {
    var shown: LinkStatus? = null
    return transformLatest { status ->
        val blip = status is LinkStatus.Connecting || status is LinkStatus.Reconnecting
        if (shown is LinkStatus.Connected && blip) delay(holdMs)
        shown = status
        emit(status)
    }
}

internal fun LinkStatus.partnerName(): String? = when (this) {
    LinkStatus.NotPaired -> null
    is LinkStatus.Connecting -> partner.name
    is LinkStatus.Reconnecting -> partner.name
    is LinkStatus.Connected -> partner.name
    is LinkStatus.NotConnected -> partner.name
}

internal fun LinkStatus.toUi(): ConnectionStatus = when (this) {
    LinkStatus.NotPaired -> ConnectionStatus.NotPaired
    is LinkStatus.Connecting -> ConnectionStatus.Searching
    is LinkStatus.Reconnecting -> ConnectionStatus.Reconnecting
    is LinkStatus.Connected -> ConnectionStatus.Connected
    is LinkStatus.NotConnected -> when (reason) {
        LinkStatus.NotConnected.Reason.Unreachable -> ConnectionStatus.Unreachable
        LinkStatus.NotConnected.Reason.Disconnected -> ConnectionStatus.NotConnected
        LinkStatus.NotConnected.Reason.MaybePairedElsewhere -> ConnectionStatus.PairedElsewhere
        LinkStatus.NotConnected.Reason.NoLongerPaired -> ConnectionStatus.NoLongerPaired
        LinkStatus.NotConnected.Reason.PartnerAppClosed -> ConnectionStatus.PartnerAppClosed
        LinkStatus.NotConnected.Reason.UpdateNeeded -> ConnectionStatus.UpdateNeeded
        LinkStatus.NotConnected.Reason.WifiOff -> ConnectionStatus.WifiOff
        LinkStatus.NotConnected.Reason.PartnerDisconnected -> ConnectionStatus.PartnerDisconnected
    }
}
