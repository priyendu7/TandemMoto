package com.tandemmoto.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

sealed interface DiscoveryState {
    data object Idle : DiscoveryState

    data object Unsupported : DiscoveryState

    data object PermissionMissing : DiscoveryState

    data object WifiOff : DiscoveryState

    /** Android kept answering BUSY; only switching Wi-Fi off and on cleared it in the spike. */
    data object Stuck : DiscoveryState

    data class Scanning(val devices: List<NearbyDevice>) : DiscoveryState

    /**
     * The search window ended; [devices] are what was found. [suggestLocation]: nothing was found
     * and Location is off on Android 12 or older, which may be why.
     */
    data class Finished(val devices: List<NearbyDevice>, val suggestLocation: Boolean = false) :
        DiscoveryState
}

/**
 * Searches for nearby Wi-Fi Direct devices for [scanDurationMs], then stops (battery).
 *
 * Shaped by the spike (docs/spikes/wifi-direct.md): permission is checked before asking Android
 * (Android 16 only answers a generic ERROR), discovery is restarted whenever Android stops it (the
 * partner is only visible while it discovers too), BUSY is retried before giving up, and Wi-Fi
 * coming back on resumes the search by itself. Only an explicit "Wi-Fi Direct disabled" counts as
 * Wi-Fi off: the Redmi Y2 never reported its initial state.
 */
class PeerDiscovery(
    private val driver: WifiP2pDriver,
    private val preconditions: DiscoveryPreconditions,
    private val scope: CoroutineScope,
    private val scanDurationMs: Long = SCAN_DURATION_MS,
    private val log: (String) -> Unit = {}
) {
    private val _state = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Starts a fresh search; restarting an ongoing one is harmless. [continuous]: keep discovering
     * until [stop]/[pause] (the Pair screen, so the phone can always receive an invitation: a phone
     * only receives one while it's discovering); otherwise one [scanDurationMs] window.
     */
    fun start(continuous: Boolean = false) {
        job?.cancel()
        job = scope.launch { run(continuous) }
    }

    /**
     * Ends the app's search loop but leaves Android's discovery alone, for right before
     * connect(): asking Android to stop discovery and connect at once makes connect fail with
     * ERROR (seen on both P1 and P4). Android ends discovery itself when it connects.
     */
    fun pause() {
        job?.cancel()
        job = null
        _state.value = DiscoveryState.Idle
    }

    fun stop() {
        val wasRunning = job?.isActive == true
        job?.cancel()
        job = null
        _state.value = DiscoveryState.Idle
        if (wasRunning) {
            log("Search stopped")
            scope.launch { driver.stopPeerDiscovery() }
        }
    }

    private suspend fun run(continuous: Boolean) {
        when {
            !driver.supported -> return finish(
                DiscoveryState.Unsupported,
                "Wi-Fi Direct unsupported"
            )
            !preconditions.nearbyGranted() ->
                return finish(DiscoveryState.PermissionMissing, "Nearby permission missing")
        }
        while (true) {
            if (driver.enabled.value == false) {
                _state.value = DiscoveryState.WifiOff
                log("Wi-Fi Direct off, waiting for it to come back")
                driver.enabled.first { it == true }
            }
            when (scanOnce(continuous)) {
                Outcome.WifiLost -> continue
                Outcome.Finished -> {
                    val devices = sorted(driver.peers.value)
                    driver.stopPeerDiscovery()
                    val suggestLocation = devices.isEmpty() && preconditions.locationOff()
                    return finish(
                        DiscoveryState.Finished(devices, suggestLocation),
                        "Search finished: ${describe(devices)}" +
                            if (suggestLocation) " (Location is off)" else ""
                    )
                }
                Outcome.Stuck -> {
                    driver.stopPeerDiscovery()
                    return finish(DiscoveryState.Stuck, "Wi-Fi Direct stuck on BUSY")
                }
                Outcome.PermissionLost -> return finish(
                    DiscoveryState.PermissionMissing,
                    "Permission lost"
                )
                Outcome.Unsupported -> return finish(DiscoveryState.Unsupported, "Unsupported")
            }
        }
    }

    private suspend fun scanOnce(continuous: Boolean): Outcome = coroutineScope {
        _state.value = DiscoveryState.Scanning(sorted(driver.peers.value))
        log(
            if (continuous) {
                "Searching continuously"
            } else {
                "Searching for ${scanDurationMs / 1_000} s"
            }
        )
        val peersJob = launch {
            driver.peers.collect { peers ->
                _state.update {
                    if (it is DiscoveryState.Scanning) {
                        DiscoveryState.Scanning(
                            sorted(peers)
                        )
                    } else {
                        it
                    }
                }
            }
        }
        val outcome = withTimeoutOrNull(if (continuous) Long.MAX_VALUE else scanDurationMs) {
            val wifiLost = async { driver.enabled.first { it == false }.let { Outcome.WifiLost } }
            val discovery = async { keepDiscovering() }
            select {
                wifiLost.onAwait { it }
                discovery.onAwait { it }
            }.also {
                wifiLost.cancel()
                discovery.cancel()
            }
        } ?: Outcome.Finished
        peersJob.cancel()
        outcome
    }

    /** Keeps discovery running for the whole window; returns only if it can't. */
    private suspend fun keepDiscovering(): Outcome {
        while (true) {
            discoverWithRetry()?.let { return it }
            // Android stops discovery on its own; restart it so the partner can still see us.
            driver.discovering.first { it }
            driver.discovering.first { !it }
            log("Android stopped discovery, restarting")
        }
    }

    /** Null when discovery started; otherwise why it couldn't. */
    private suspend fun discoverWithRetry(): Outcome? {
        repeat(MAX_ATTEMPTS) { attempt ->
            when (driver.discoverPeers()) {
                P2pResult.Ok -> return null
                P2pResult.Unsupported -> return Outcome.Unsupported
                P2pResult.Busy, P2pResult.Error -> {
                    if (!preconditions.nearbyGranted()) return Outcome.PermissionLost
                    log("Discover refused (attempt ${attempt + 1})")
                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_BASE_MS shl attempt)
                }
            }
        }
        return Outcome.Stuck
    }

    private fun finish(state: DiscoveryState, message: String) {
        _state.value = state
        log(message)
    }

    private fun describe(devices: List<NearbyDevice>) = if (devices.isEmpty()) {
        "nothing found"
    } else {
        devices.joinToString {
            "${it.logId}=${it.status}"
        }
    }

    private enum class Outcome { Finished, WifiLost, Stuck, PermissionLost, Unsupported }

    companion object {
        /** One search window: 30 s felt too short in the first phone test. */
        const val SCAN_DURATION_MS = 60_000L

        /** discoverPeers attempts before [DiscoveryState.Stuck]; retries wait 1 s, 2 s, 4 s. */
        const val MAX_ATTEMPTS = 4
        const val RETRY_BASE_MS = 1_000L

        /** Phones first, then by name. */
        fun sorted(devices: List<NearbyDevice>): List<NearbyDevice> =
            devices.sortedWith(compareBy({ !it.isPhone }, { it.name.lowercase() }))
    }
}
