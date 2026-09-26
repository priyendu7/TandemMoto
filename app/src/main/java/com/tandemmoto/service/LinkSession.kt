package com.tandemmoto.service

import com.tandemmoto.link.LinkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Decides when the foreground service runs (#40, #51), so it's tested on the JVM. [start] and
 * [stop] start and stop `LinkService`. It runs while the link wants it **or** music is playing
 * ([playing]), so a Disconnect doesn't stop the music and a pause doesn't drop the link.
 *
 * The link wants it:
 * - from the first time the link is Connected. That happens with the app open, and Android 12+
 *   only lets an app start a foreground service from the foreground;
 * - through drops shorter than [idleStopMs]: a background reconnect (#27) couldn't start it again;
 * - until [idleStopMs] not connected, [stopNow] (Disconnect), or not paired (Forget partner).
 */
class LinkSession(
    private val status: StateFlow<LinkStatus>,
    private val scope: CoroutineScope,
    private val start: () -> Unit,
    private val stop: () -> Unit,
    private val log: (String) -> Unit = {},
    private val idleStopMs: Long = IDLE_STOP_MS,
    private val playing: StateFlow<Boolean> = MutableStateFlow(false)
) {
    /** The service is running. */
    var running = false
        private set

    /** The link wants the service (see the class comment). */
    var linkWanted = false
        private set
    private var idleTimer: Job? = null

    fun begin() {
        scope.launch { status.collect(::onStatus) }
        scope.launch { playing.collect { update() } }
    }

    /** Disconnect: the link no longer wants it (music may keep it running). */
    fun stopNow() {
        idleTimer?.cancel()
        if (!linkWanted) return
        linkWanted = false
        log("Link no longer needs the service")
        update()
    }

    private fun onStatus(status: LinkStatus) {
        when (status) {
            is LinkStatus.Connected -> {
                idleTimer?.cancel()
                if (!linkWanted) {
                    linkWanted = true
                    update()
                }
            }
            // Trying to (re)connect: the service must stay up for it (#27).
            is LinkStatus.Connecting, is LinkStatus.Reconnecting -> idleTimer?.cancel()
            is LinkStatus.NotConnected -> if (linkWanted && idleTimer?.isActive != true) {
                idleTimer = scope.launch {
                    delay(idleStopMs)
                    log("Not connected for ${idleStopMs / 1000} s")
                    stopNow()
                }
            }
            LinkStatus.NotPaired -> stopNow()
        }
    }

    private fun update() {
        val wanted = linkWanted || playing.value
        if (wanted && !running) {
            running = true
            start()
            log("Link service started")
        } else if (!wanted && running) {
            running = false
            stop()
            log("Link service stopped")
        }
    }

    companion object {
        /** Matches the give-up window planned for auto-reconnect (#27). */
        const val IDLE_STOP_MS = 120_000L
    }
}
