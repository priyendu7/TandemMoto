package com.tandemmoto.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The Pair screen's pairing flow. */
sealed interface PairingState {
    data object Idle : PairingState

    /** Already paired with [current]; pairing with [device] would replace it. */
    data class ConfirmReplace(val device: NearbyDevice, val current: Partner) : PairingState

    data class Inviting(val device: NearbyDevice) : PairingState

    data class Paired(val partner: Partner) : PairingState

    data class Failed(val reason: Reason) : PairingState {
        enum class Reason { NoAnswer, Busy, Error }
    }
}

/** Home's view of the link to the saved partner. */
sealed interface LinkStatus {
    data object NotPaired : LinkStatus

    data class Connecting(val partner: Partner) : LinkStatus

    /** In a Wi-Fi Direct group with the partner (the command channel arrives with #25). */
    data class Connected(val partner: Partner) : LinkStatus

    /**
     * Not connected. [maybePairedElsewhere]: the partner accepted and then immediately dropped
     * the group, which is what a phone that has since paired with someone else does.
     */
    data class NotConnected(val partner: Partner, val maybePairedElsewhere: Boolean = false) :
        LinkStatus
}

/**
 * The app's single Wi-Fi Direct link: pairing, connecting to the saved partner, and keeping any
 * other phone out. Rules come from the spike (docs/spikes/wifi-direct.md):
 * - one initiator: only the [Partner.Role.Initiator] calls connect(), never while a group exists
 *   or is forming;
 * - a stuck invitation survives restarts, so it's cancelled before connecting;
 * - a group can outlive the app, so an existing group with the partner is reused;
 * - Android silently accepts reconnections to a saved group, even from a phone that is no longer
 *   the partner, so a group with anyone else is removed (the wrong-device guard).
 */
class Link(
    private val driver: WifiP2pDriver,
    preconditions: DiscoveryPreconditions,
    private val store: PartnerStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    private val connectWindowMs: Long = PeerDiscovery.SCAN_DURATION_MS,
    private val inviteTimeoutMs: Long = INVITE_TIMEOUT_MS
) {
    val discovery = PeerDiscovery(driver, preconditions, scope, log = log)

    private val _partner = MutableStateFlow<Partner?>(null)
    val partner: StateFlow<Partner?> = _partner.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.NotPaired)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private var pairScreenOpen = false
    private var inviteJob: Job? = null
    private var connectJob: Job? = null
    private var connectedWithPartner = false

    /** Set right after a group with the partner forms; a quick drop then means rejection. */
    private var justConnected = false
    private var removingOurselves = false

    /** Loads the saved partner, watches the group, and makes one connection attempt. */
    fun start() {
        scope.launch {
            _partner.value = store.partner.first()
            _partner.value?.let {
                log("Saved partner ${it.logId} (${it.role})")
                _status.value = LinkStatus.NotConnected(it)
            }
            scope.launch { driver.group.collect { onGroup(it) } }
            connectToPartner()
        }
    }

    // ---- Pair screen ----

    /** The Pair screen listens for as long as it's open, so an invitation can always arrive. */
    fun openPairScreen() {
        pairScreenOpen = true
        if (_pairing.value !is PairingState.Inviting) discovery.start(continuous = true)
    }

    fun closePairScreen() {
        pairScreenOpen = false
        if (_pairing.value is PairingState.Inviting) cancelInvite() else discovery.stop()
        if (_pairing.value !is PairingState.Inviting) _pairing.value = PairingState.Idle
    }

    fun invite(device: NearbyDevice) {
        if (driver.group.value != null || _pairing.value is PairingState.Inviting) {
            log("Invite to ${device.logId} ignored: a group is formed or forming")
            return
        }
        val current = _partner.value
        if (current != null && !current.matches(device)) {
            _pairing.value = PairingState.ConfirmReplace(device, current)
            return
        }
        startInvite(device)
    }

    fun confirmReplace() {
        (_pairing.value as? PairingState.ConfirmReplace)?.let { startInvite(it.device) }
    }

    fun cancelInvite() {
        inviteJob?.cancel()
        inviteJob = null
        scope.launch { driver.cancelConnect() }
        _pairing.value = PairingState.Idle
        log("Invitation cancelled")
        if (pairScreenOpen) discovery.start(continuous = true)
    }

    /** Clears a finished, failed or declined pairing step. */
    fun dismissPairing() {
        if (_pairing.value !is PairingState.Inviting) _pairing.value = PairingState.Idle
    }

    private fun startInvite(device: NearbyDevice) {
        _pairing.value = PairingState.Inviting(device)
        discovery.pause() // not stop(): see PeerDiscovery.pause
        log("Inviting ${device.logId}")
        inviteJob = scope.launch {
            val result = connectWithRetry(device.address)
            when (result) {
                P2pResult.Ok -> Unit
                P2pResult.Busy -> return@launch failPairing(PairingState.Failed.Reason.Busy)
                else -> return@launch failPairing(PairingState.Failed.Reason.Error)
            }
            // onGroup() completes the pairing and cancels this job when the group forms.
            delay(inviteTimeoutMs)
            driver.cancelConnect()
            failPairing(PairingState.Failed.Reason.NoAnswer)
        }
    }

    /** connect(), retried a few times: Android can refuse it briefly (BUSY/ERROR). */
    private suspend fun connectWithRetry(address: String): P2pResult {
        var result = P2pResult.Error
        repeat(CONNECT_ATTEMPTS) { attempt ->
            result = driver.connect(address)
            if (result == P2pResult.Ok || result == P2pResult.Unsupported) return result
            log("Connect refused: $result (attempt ${attempt + 1})")
            if (attempt < CONNECT_ATTEMPTS - 1) delay(CONNECT_RETRY_MS)
        }
        return result
    }

    private fun failPairing(reason: PairingState.Failed.Reason) {
        _pairing.value = PairingState.Failed(reason)
        log("Pairing failed: $reason")
        if (pairScreenOpen) discovery.start(continuous = true)
    }

    // ---- Connecting to the saved partner ----

    /** One attempt of [connectWindowMs]; automatic retrying after a drop is #27. */
    fun connectToPartner() {
        val partner = _partner.value ?: return
        if (_status.value is LinkStatus.Connected || connectJob?.isActive == true) return
        if (_pairing.value is PairingState.Inviting) return
        connectJob = scope.launch {
            // The group watcher may already have found an existing group with the partner.
            if (_status.value is LinkStatus.Connected) return@launch
            _status.value = LinkStatus.Connecting(partner)
            log("Connecting to ${partner.logId} as ${partner.role}")
            if (driver.group.value == null) driver.cancelConnect()
            discovery.start()
            val connected = withTimeoutOrNull(connectWindowMs) {
                // Acceptors just stay visible; Android accepts the saved group without a prompt.
                val initiating = if (partner.role == Partner.Role.Initiator) {
                    launch {
                        val found = driver.peers.first { peers -> peers.any(partner::matches) }
                            .first(partner::matches)
                        if (driver.group.value == null) {
                            log("Partner visible, connecting")
                            discovery.pause() // see PeerDiscovery.pause
                            connectWithRetry(found.address)
                        }
                    }
                } else {
                    null
                }
                _status.first { it is LinkStatus.Connected }
                initiating?.cancel()
            }
            discovery.stop()
            if (connected == null && _status.value !is LinkStatus.Connected) {
                _status.value = LinkStatus.NotConnected(partner)
                log("Partner not reached")
            }
        }
    }

    fun forgetPartner() {
        val partner = _partner.value ?: return
        scope.launch {
            inviteJob?.cancel()
            connectJob?.cancel()
            removingOurselves = true
            driver.cancelConnect()
            if (driver.group.value != null) driver.removeGroup()
            store.clear()
            _partner.value = null
            _status.value = LinkStatus.NotPaired
            _pairing.value = PairingState.Idle
            log("Forgot partner ${partner.logId}")
        }
    }

    // ---- Group changes ----

    private suspend fun onGroup(group: GroupInfo?) {
        if (group == null) {
            onGroupRemoved()
            return
        }
        val peer = group.peer ?: return // Android hasn't said who it is yet
        val partner = _partner.value
        val inviting = _pairing.value as? PairingState.Inviting
        when {
            inviting != null && inviting.device.isSamePhone(peer) ->
                completePairing(peer, Partner.Role.Initiator)
            partner != null && partner.matches(peer) -> onPartnerGroup(partner, peer, group)
            pairScreenOpen -> completePairing(peer, Partner.Role.Acceptor)
            else -> {
                log("Group with ${peer.logId}, not the partner: removing it")
                removingOurselves = true
                driver.removeGroup()
            }
        }
    }

    private suspend fun completePairing(peer: NearbyDevice, role: Partner.Role) {
        inviteJob?.cancel()
        inviteJob = null
        val partner = Partner(peer.name, peer.address, role, now())
        store.save(partner)
        _partner.value = partner
        _pairing.value = PairingState.Paired(partner)
        markConnected(partner)
        log("Paired with ${partner.logId} as $role")
    }

    private suspend fun onPartnerGroup(partner: Partner, peer: NearbyDevice, group: GroupInfo) {
        // Matched by name after Android changed the address: remember the new one.
        val current = if (partner.address != peer.address && peer.address.isNotBlank()) {
            partner.copy(address = peer.address).also {
                store.save(it)
                _partner.value = it
                log("Partner address changed; updated")
            }
        } else {
            partner
        }
        markConnected(current)
        log(
            "Connected to ${current.logId} (${if (group.isGroupOwner) "group owner" else "client"})"
        )
    }

    private fun markConnected(partner: Partner) {
        connectedWithPartner = true
        _status.value = LinkStatus.Connected(partner)
        justConnected = true
        scope.launch {
            delay(REJECTION_WINDOW_MS)
            justConnected = false
        }
    }

    private fun onGroupRemoved() {
        val wasOurs = removingOurselves
        removingOurselves = false
        if (!connectedWithPartner) return
        connectedWithPartner = false
        val partner = _partner.value ?: return
        val rejected = justConnected && !wasOurs && partner.role == Partner.Role.Initiator
        _status.value = LinkStatus.NotConnected(partner, maybePairedElsewhere = rejected)
        log(
            if (rejected) {
                "Partner dropped the group at once: maybe paired elsewhere"
            } else {
                "Group removed"
            }
        )
    }

    private fun NearbyDevice.isSamePhone(other: NearbyDevice) =
        address.isNotBlank() && address == other.address || name.isNotBlank() && name == other.name

    companion object {
        /** Time to accept the first pairing prompt (≈ 15 s in the spike) with margin. */
        const val INVITE_TIMEOUT_MS = 45_000L

        /** A group with the partner dropped this soon, by them, suggests a rejection. */
        const val REJECTION_WINDOW_MS = 10_000L

        /** connect() attempts before giving up, [CONNECT_RETRY_MS] apart. */
        const val CONNECT_ATTEMPTS = 3
        const val CONNECT_RETRY_MS = 1_000L
    }
}
