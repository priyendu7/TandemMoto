package com.tandemmoto.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tandemmoto.link
import com.tandemmoto.link.DiscoveryState
import com.tandemmoto.link.NearbyDevice
import com.tandemmoto.link.PairingState
import kotlinx.coroutines.flow.StateFlow

/**
 * The Pair screen's view of the shared link: discovery plus pairing. The screen opens the
 * search on resume and closes it on pause (which also withdraws a pending invitation).
 */
class PairViewModel(app: Application) : AndroidViewModel(app) {
    private val link = app.link

    val discovery: StateFlow<DiscoveryState> = link.discovery.state
    val pairing: StateFlow<PairingState> = link.pairing

    fun onScreenVisible() = link.openPairScreen()

    fun onScreenHidden() = link.closePairScreen()

    fun searchAgain() = link.discovery.start()

    fun invite(device: NearbyDevice) = link.invite(device)

    fun confirmReplace() = link.confirmReplace()

    fun cancelInvite() = link.cancelInvite()

    fun dismissPairing() = link.dismissPairing()
}
