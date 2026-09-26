package com.tandemmoto.link

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The search-and-connect loop to the saved partner (#27), for one window of [windowMs]. Android
 * never reconnects on its own (spike #22), and a phone only receives an invitation while it's
 * discovering, so both phones search the whole time:
 * - the [Partner.Role.Initiator] connects whenever the partner is visible, and after an attempt
 *   that forms no group within [attemptMs] backs off [BACKOFF_MS] before the next;
 * - the acceptor never calls connect() (both connecting deadlocks); it stays discoverable and
 *   Android accepts the saved group without a prompt.
 */
class Reconnector(
    private val driver: WifiP2pDriver,
    private val discovery: PeerDiscovery,
    private val log: (String) -> Unit = {},
    private val windowMs: Long = WINDOW_MS,
    private val attemptMs: Long = ATTEMPT_MS
) {
    /**
     * Tries until a group with [partner] forms (true) or the window ends (false). Cancelling
     * leaves discovery to the caller: the Pair screen or Wi-Fi-off handling takes it over.
     */
    suspend fun run(partner: Partner): Boolean {
        // A stuck invitation survives app restarts (spike); clear it before trying.
        if (driver.group.value == null) driver.cancelConnect()
        discovery.start(continuous = true)
        val reached = withTimeoutOrNull(windowMs) {
            coroutineScope {
                val initiating = if (partner.role == Partner.Role.Initiator) {
                    launch { initiate(partner) }
                } else {
                    null
                }
                driver.group.first { it?.peer?.let(partner::matches) == true }
                initiating?.cancel()
                true // not the line above: for the acceptor that's null, i.e. "timed out"
            }
        } ?: false
        discovery.stop()
        return reached
    }

    private suspend fun initiate(partner: Partner) {
        var failures = 0
        while (true) {
            val found = driver.peers.first { peers -> peers.any(partner::matches) }
                .first(partner::matches)
            // Wait out a group that exists or is forming (never connect over one).
            if (driver.group.value != null) {
                driver.group.first { it == null }
                continue
            }
            log("Partner visible, connecting")
            discovery.pause() // not stop(): see PeerDiscovery.pause
            val result = driver.connectWithRetry(found.address, log)
            val formed = result == P2pResult.Ok &&
                withTimeoutOrNull(attemptMs) {
                    driver.group.first { it?.peer?.let(partner::matches) == true }
                } != null
            if (formed) return
            driver.cancelConnect()
            val wait = BACKOFF_MS[failures.coerceAtMost(BACKOFF_MS.lastIndex)]
            failures++
            log("No group after attempt $failures; next in ${wait / 1_000} s")
            delay(wait)
            discovery.start(continuous = true)
        }
    }

    companion object {
        /** Give up after this long (the issue's ~2 min); a tap starts a fresh window. */
        const val WINDOW_MS = 120_000L

        /** A persistent-group reconnection took 1.4–7 s in the spike. */
        const val ATTEMPT_MS = 15_000L

        val BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L)
    }
}

/** connect(), retried a few times: Android can refuse it briefly (BUSY/ERROR). */
internal suspend fun WifiP2pDriver.connectWithRetry(
    address: String,
    log: (String) -> Unit
): P2pResult {
    var result = P2pResult.Error
    repeat(Link.CONNECT_ATTEMPTS) { attempt ->
        result = connect(address)
        if (result == P2pResult.Ok || result == P2pResult.Unsupported) return result
        log("Connect refused: $result (attempt ${attempt + 1})")
        if (attempt < Link.CONNECT_ATTEMPTS - 1) delay(Link.CONNECT_RETRY_MS)
    }
    return result
}
