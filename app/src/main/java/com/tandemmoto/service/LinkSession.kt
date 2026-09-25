package com.tandemmoto.service

import com.tandemmoto.link.LinkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Decides when the foreground service runs (#40), from the link status alone, so it's tested on
 * the JVM. [start] and [stop] start and stop `LinkService`.
 *
 * - Starts the first time the link is Connected. That happens with the app open, and Android 12+
 *   only lets an app start a foreground service from the foreground.
 * - Keeps running through drops shorter than [idleStopMs]: a background reconnect (#27) couldn't
 *   start it again.
 * - Stops after [idleStopMs] not connected, on [stopNow] (Disconnect), or when not paired
 *   (Forget partner).
 */
class LinkSession(
    private val status: StateFlow<LinkStatus>,
    private val scope: CoroutineScope,
    private val start: () -> Unit,
    private val stop: () -> Unit,
    private val log: (String) -> Unit = {},
    private val idleStopMs: Long = IDLE_STOP_MS
) {
    var running = false
        private set
    private var idleTimer: Job? = null

    fun begin() {
        scope.launch { status.collect(::onStatus) }
    }

    /** Disconnect: stop now rather than after the idle window. */
    fun stopNow() {
        if (!running) return
        idleTimer?.cancel()
        running = false
        stop()
        log("Link service stopped")
    }

    private fun onStatus(status: LinkStatus) {
        when (status) {
            is LinkStatus.Connected -> {
                idleTimer?.cancel()
                if (!running) {
                    running = true
                    start()
                    log("Link service started")
                }
            }
            is LinkStatus.Connecting -> idleTimer?.cancel()
            is LinkStatus.NotConnected -> if (running && idleTimer?.isActive != true) {
                idleTimer = scope.launch {
                    delay(idleStopMs)
                    log("Not connected for ${idleStopMs / 1000} s")
                    stopNow()
                }
            }
            LinkStatus.NotPaired -> stopNow()
        }
    }

    companion object {
        /** Matches the give-up window planned for auto-reconnect (#27). */
        const val IDLE_STOP_MS = 120_000L
    }
}
