package com.tandemmoto.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tandemmoto.diagnostics.AppLog
import com.tandemmoto.link.AndroidDiscoveryPreconditions
import com.tandemmoto.link.AndroidWifiP2pDriver
import com.tandemmoto.link.DiscoveryState
import com.tandemmoto.link.PeerDiscovery
import kotlinx.coroutines.flow.StateFlow

/** Owns discovery for the Pair screen; the screen starts it on resume and stops it on pause. */
class PairViewModel(app: Application) : AndroidViewModel(app) {
    private val driver = AndroidWifiP2pDriver(app)
    private val discovery = PeerDiscovery(
        driver = driver,
        preconditions = AndroidDiscoveryPreconditions(app),
        scope = viewModelScope,
        log = { AppLog.i("Link", it) }
    )

    val state: StateFlow<DiscoveryState> = discovery.state

    fun startSearch() = discovery.start()

    fun stopSearch() = discovery.stop()

    override fun onCleared() {
        discovery.stop()
        driver.close()
    }
}
