package com.tandemmoto.link

import com.tandemmoto.state.Message
import com.tandemmoto.state.Message.Bye
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

    /** The partner's app answered on the command channel (#25), not just "in a group". */
    data class Connected(val partner: Partner) : LinkStatus

    data class NotConnected(val partner: Partner, val reason: Reason = Reason.Unreachable) :
        LinkStatus {
        enum class Reason {
            /** No group with the partner (not found, or the link dropped). */
            Unreachable,

            /**
             * The partner accepted and then at once dropped the group without a word, which is what
             * a phone that has since paired with someone else does. A guess.
             */
            MaybePairedElsewhere,

            /** The partner's app said so: it forgot us or paired with another phone. */
            NoLongerPaired,

            /** In a group, but the partner's app isn't answering (closed, or not started yet). */
            PartnerAppClosed,

            /** The apps speak different protocol versions. */
            UpdateNeeded,

            /** This phone's Wi-Fi is off. */
            WifiOff,

            /** The partner tapped Disconnect. */
            PartnerDisconnected
        }
    }
}

/**
 * The app's single Wi-Fi Direct link: pairing, connecting to the saved partner, and keeping any
 * other phone out. Rules come from the spike (docs/spikes/wifi-direct.md):
 * - one initiator: only the [Partner.Role.Initiator] calls connect(), never while a group exists
 *   or is forming;
 * - a stuck invitation survives restarts, so it's cancelled before connecting;
 * - a group can outlive the app, so an existing group with the partner is reused;
 * - Android silently accepts reconnections to a saved group, even from a phone that is no longer
 *   the partner, so a group with anyone else is removed (the wrong-device guard), after telling
 *   that phone's app over the channel.
 *
 * A group only means Android connected the phones; it outlives the apps. [LinkStatus.Connected]
 * needs the partner's app to answer on the [CommandChannel].
 */
class Link(
    private val driver: WifiP2pDriver,
    preconditions: DiscoveryPreconditions,
    private val store: PartnerStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    transport: FrameTransport,
    /** This installation's ID (see [InstallId]). */
    private val installId: suspend () -> String,
    private val appVersion: String,
    nanoTime: () -> Long = System::nanoTime,
    keepAwake: (Boolean) -> Unit = {},
    private val connectWindowMs: Long = PeerDiscovery.SCAN_DURATION_MS,
    private val inviteTimeoutMs: Long = INVITE_TIMEOUT_MS
) {
    val discovery = PeerDiscovery(driver, preconditions, scope, log = log)

    val channel = CommandChannel(transport, scope, nanoTime, log, keepAwake)

    private val _partner = MutableStateFlow<Partner?>(null)
    val partner: StateFlow<Partner?> = _partner.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    private val _status = MutableStateFlow<LinkStatus>(LinkStatus.NotPaired)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private var pairScreenOpen = false
    private var inviteJob: Job? = null
    private var connectJob: Job? = null
    private var groupWithPartner = false
    private var myInstallId = ""

    /** Set right after a group with the partner forms; a quick silent drop suggests rejection. */
    private var justFormed = false

    /** The partner's app answered in this group, so a drop isn't a rejection. */
    private var answeredInGroup = false
    private var removingOurselves = false

    /** The group the channel was opened for, so repeated group broadcasts don't reopen it. */
    private var channelGroup: GroupInfo? = null
    private var appTimer: Job? = null
    private var guardJob: Job? = null

    /** Loads the saved partner, watches the group and channel, and makes one connection attempt. */
    fun start() {
        scope.launch {
            myInstallId = installId()
            _partner.value = store.partner.first()
            _partner.value?.let {
                log("Saved partner ${it.logId} (${it.role})")
                _status.value = LinkStatus.NotConnected(it, unreachable())
            }
            scope.launch { channel.state.collect { onChannelState(it) } }
            scope.launch { driver.enabled.collect { onWifi(it) } }
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

    /**
     * One attempt of [connectWindowMs] at forming the group; the channel takes it from there.
     * Automatic retrying after a drop is #27.
     */
    fun connectToPartner() {
        val partner = _partner.value ?: return
        if (groupWithPartner || connectJob?.isActive == true) return
        if (_pairing.value is PairingState.Inviting) return
        if (driver.enabled.value == false) {
            _status.value = LinkStatus.NotConnected(partner, LinkStatus.NotConnected.Reason.WifiOff)
            return
        }
        connectJob = scope.launch {
            // The group watcher may already have found an existing group with the partner.
            if (groupWithPartner) return@launch
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
                driver.group.first { group -> group?.peer?.let(partner::matches) == true }
                initiating?.cancel()
            }
            discovery.stop()
            if (connected == null && !groupWithPartner) {
                _status.value = LinkStatus.NotConnected(partner, unreachable())
                log("Partner not reached")
            }
        }
    }

    /**
     * The user's Disconnect (notification action): tell the partner's app, drop the group and stop
     * trying. Home then offers Tap to connect.
     */
    fun disconnect() {
        val partner = _partner.value ?: return
        scope.launch {
            connectJob?.cancel()
            discovery.stop()
            channel.send(Bye(Bye.Reason.Disconnected))
            channel.close()
            removingOurselves = true
            driver.cancelConnect()
            if (driver.group.value != null) driver.removeGroup()
            _status.value = LinkStatus.NotConnected(partner, unreachable())
            log("Disconnected by the user")
        }
    }

    fun forgetPartner() {
        val partner = _partner.value ?: return
        scope.launch {
            inviteJob?.cancel()
            connectJob?.cancel()
            channel.send(Bye(Bye.Reason.NotYourPartner)) // so its app knows, if it's listening
            channel.close()
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
                completePairing(peer, Partner.Role.Initiator, group)
            partner != null && partner.matches(peer) -> onPartnerGroup(partner, peer, group)
            pairScreenOpen -> completePairing(peer, Partner.Role.Acceptor, group)
            else -> guard(peer, group)
        }
    }

    private suspend fun completePairing(peer: NearbyDevice, role: Partner.Role, group: GroupInfo) {
        inviteJob?.cancel()
        inviteJob = null
        val partner = Partner(peer.name, peer.address, role, now())
        store.save(partner)
        _partner.value = partner
        _pairing.value = PairingState.Paired(partner)
        groupWithPartner = false // a new partner: start over
        channelGroup = null
        log("Paired with ${partner.logId} as $role")
        onGroupWithPartner(partner, group)
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
        if (!groupWithPartner) {
            log(
                "In a group with ${current.logId} " +
                    "(${if (group.isGroupOwner) "group owner" else "client"})"
            )
        }
        onGroupWithPartner(current, group)
    }

    /** The group is up; the partner counts as connected once its app answers on the channel. */
    private fun onGroupWithPartner(partner: Partner, group: GroupInfo) {
        if (!groupWithPartner) {
            groupWithPartner = true
            answeredInGroup = false
            _status.value = LinkStatus.Connecting(partner)
            justFormed = true
            scope.launch {
                delay(REJECTION_WINDOW_MS)
                justFormed = false
            }
        }
        openChannel(group, check = ::checkPartnerHello)
    }

    private fun openChannel(group: GroupInfo, check: suspend (Message.Hello) -> Bye.Reason?) {
        if (channelGroup?.sameGroupAs(group) == true) return
        channelGroup = group
        channel.open(Endpoint(group.isGroupOwner, group.ownerAddress), ::hello, check)
    }

    private fun hello() = _partner.value.let {
        Message.Hello(appVersion, myInstallId, it?.installId, it?.role?.name ?: "None")
    }

    /** Vets the partner app's Hello; learns its install ID the first time. */
    private suspend fun checkPartnerHello(remote: Message.Hello): Bye.Reason? {
        val partner = _partner.value ?: return Bye.Reason.NotYourPartner
        if (partner.installId != null && partner.installId != remote.installId) {
            log("A different TandemMoto install answered: not the partner")
            return Bye.Reason.NotYourPartner
        }
        if (remote.partnerInstallId != null && remote.partnerInstallId != myInstallId) {
            log("The partner's app is paired with another phone")
            return Bye.Reason.NotYourPartner
        }
        if (partner.installId == null) {
            val learned = partner.copy(installId = remote.installId)
            store.save(learned)
            _partner.value = learned
            log("Learned the partner's install ID")
        }
        if (remote.role == partner.role.name) log("Both phones are ${partner.role}")
        return null
    }

    private fun onChannelState(state: ChannelState) {
        if (!groupWithPartner) return
        val partner = _partner.value ?: return
        when (state) {
            ChannelState.Idle -> appTimer?.cancel()
            ChannelState.Opening -> if (channel.lastLoss == ChannelLoss.Vanished) {
                // The phone went quiet or the socket broke: it's gone (Wi-Fi off, out of range),
                // not a closed app. The channel keeps retrying while Android keeps the group,
                // which on the Redmi took ~13 s to notice (#25 phone test).
                appTimer?.cancel()
                _status.value = LinkStatus.NotConnected(partner, unreachable())
                log("Partner went away")
            } else {
                if (_status.value is LinkStatus.Connected) {
                    _status.value = LinkStatus.Connecting(partner)
                }
                startAppTimer()
            }
            is ChannelState.Open -> {
                appTimer?.cancel()
                answeredInGroup = true
                _status.value = LinkStatus.Connected(partner)
                log("Connected to ${partner.logId}")
            }
            is ChannelState.Refused -> {
                appTimer?.cancel()
                val reason = when (state.reason) {
                    Bye.Reason.ProtocolMismatch -> LinkStatus.NotConnected.Reason.UpdateNeeded
                    Bye.Reason.Disconnected -> LinkStatus.NotConnected.Reason.PartnerDisconnected
                    else -> LinkStatus.NotConnected.Reason.NoLongerPaired
                }
                _status.value = LinkStatus.NotConnected(partner, reason)
            }
        }
    }

    /** No answer from the partner's app for a while: it's probably not running. */
    private fun startAppTimer() {
        if (appTimer?.isActive == true) return
        appTimer = scope.launch {
            delay(PARTNER_APP_TIMEOUT_MS)
            val partner = _partner.value ?: return@launch
            if (groupWithPartner && channel.state.value == ChannelState.Opening) {
                _status.value =
                    LinkStatus.NotConnected(
                        partner,
                        LinkStatus.NotConnected.Reason.PartnerAppClosed
                    )
                log("The partner's app isn't answering")
            }
        }
    }

    /**
     * The wrong-device guard: a group with a phone that isn't the partner. Tell its app why over
     * the channel (so it stops trying), then remove the group.
     */
    private fun guard(peer: NearbyDevice, group: GroupInfo) {
        if (guardJob?.isActive == true) return
        log("Group with ${peer.logId}, not the partner: removing it")
        openChannel(group) { Bye.Reason.NotYourPartner }
        guardJob = scope.launch {
            withTimeoutOrNull(GUARD_TIMEOUT_MS) {
                channel.state.first { it is ChannelState.Refused }
            }
            channel.close()
            channelGroup = null
            removingOurselves = true
            driver.removeGroup()
        }
    }

    private fun onGroupRemoved() {
        val wasOurs = removingOurselves
        removingOurselves = false
        channel.close()
        channelGroup = null
        appTimer?.cancel()
        if (!groupWithPartner) return
        groupWithPartner = false
        val partner = _partner.value ?: return
        val current = (_status.value as? LinkStatus.NotConnected)?.reason
        val reason = when {
            // The partner's app already said why; keep that.
            current == LinkStatus.NotConnected.Reason.NoLongerPaired ||
                current == LinkStatus.NotConnected.Reason.UpdateNeeded ||
                current == LinkStatus.NotConnected.Reason.PartnerDisconnected -> current
            driver.enabled.value == false -> LinkStatus.NotConnected.Reason.WifiOff
            justFormed && !wasOurs && !answeredInGroup && partner.role == Partner.Role.Initiator ->
                LinkStatus.NotConnected.Reason.MaybePairedElsewhere
            else -> unreachable()
        }
        _status.value = LinkStatus.NotConnected(partner, reason)
        log(
            if (reason == LinkStatus.NotConnected.Reason.MaybePairedElsewhere) {
                "Partner dropped the group at once: maybe paired elsewhere"
            } else {
                "Group removed"
            }
        )
    }

    /** Not connected for no reason the partner gave: this phone's Wi-Fi, or just not reached. */
    private fun unreachable() = if (driver.enabled.value == false) {
        LinkStatus.NotConnected.Reason.WifiOff
    } else {
        LinkStatus.NotConnected.Reason.Unreachable
    }

    /**
     * Wi-Fi off stops any attempt and says so; back on, it's an ordinary "Not connected" (tap to
     * connect; reconnecting by itself is #27). A group, if any, is removed by Android.
     */
    private fun onWifi(enabled: Boolean?) {
        val partner = _partner.value ?: return
        when (enabled) {
            false -> {
                // Only our own attempt's search; the Pair screen shows Wi-Fi off itself.
                if (connectJob?.isActive == true) {
                    connectJob?.cancel()
                    discovery.stop()
                }
                _status.value =
                    LinkStatus.NotConnected(partner, LinkStatus.NotConnected.Reason.WifiOff)
                log("Wi-Fi off")
            }
            true -> {
                val status = _status.value
                if (status is LinkStatus.NotConnected &&
                    status.reason == LinkStatus.NotConnected.Reason.WifiOff
                ) {
                    _status.value = LinkStatus.NotConnected(partner)
                    log("Wi-Fi back on")
                }
            }
            null -> Unit
        }
    }

    private fun GroupInfo.sameGroupAs(other: GroupInfo) = isGroupOwner == other.isGroupOwner &&
        ownerAddress == other.ownerAddress &&
        peer?.address == other.peer?.address

    private fun NearbyDevice.isSamePhone(other: NearbyDevice) =
        address.isNotBlank() && address == other.address || name.isNotBlank() && name == other.name

    companion object {
        /** Time to accept the first pairing prompt (≈ 15 s in the spike) with margin. */
        const val INVITE_TIMEOUT_MS = 45_000L

        /** A group with the partner dropped this soon, by them, suggests a rejection. */
        const val REJECTION_WINDOW_MS = 10_000L

        /**
         * In a group, but no answer from the partner's app for this long: it isn't running. The
         * channel normally opens ~0.6 s after the group forms (spike).
         */
        const val PARTNER_APP_TIMEOUT_MS = 5_000L

        /** How long the wrong-device guard waits to tell the other app before removing the group. */
        const val GUARD_TIMEOUT_MS = 3_000L

        /** connect() attempts before giving up, [CONNECT_RETRY_MS] apart. */
        const val CONNECT_ATTEMPTS = 3
        const val CONNECT_RETRY_MS = 1_000L
    }
}
