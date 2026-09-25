package com.tandemmoto.link

import kotlinx.coroutines.flow.MutableStateFlow

/** Scriptable [WifiP2pDriver]: tests set the flows and queue results, then check the calls. */
class FakeWifiP2pDriver(override val supported: Boolean = true) : WifiP2pDriver {
    override val enabled = MutableStateFlow<Boolean?>(true)
    override val peers = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val discovering = MutableStateFlow(false)
    override val group = MutableStateFlow<GroupInfo?>(null)

    /** Results for successive discoverPeers calls; Ok once they run out. */
    val discoverResults = ArrayDeque<P2pResult>()

    /** Results for successive connect calls; Ok once they run out. */
    val connectResults = ArrayDeque<P2pResult>()

    var discoverCalls = 0
    var stopCalls = 0
    val connectCalls = mutableListOf<String>()
    var cancelConnectCalls = 0
    var removeGroupCalls = 0

    override suspend fun discoverPeers(): P2pResult {
        discoverCalls++
        val result = discoverResults.removeFirstOrNull() ?: P2pResult.Ok
        if (result == P2pResult.Ok) discovering.value = true
        return result
    }

    override suspend fun stopPeerDiscovery(): P2pResult {
        stopCalls++
        discovering.value = false
        return P2pResult.Ok
    }

    override suspend fun connect(address: String): P2pResult {
        connectCalls += address
        return connectResults.removeFirstOrNull() ?: P2pResult.Ok
    }

    override suspend fun cancelConnect(): P2pResult {
        cancelConnectCalls++
        return P2pResult.Ok
    }

    override suspend fun removeGroup(): P2pResult {
        removeGroupCalls++
        group.value = null
        return P2pResult.Ok
    }

    override fun close() = Unit

    /** Android reporting a formed group with [peer]. */
    fun formGroupWith(peer: NearbyDevice, isGroupOwner: Boolean = true) {
        group.value =
            GroupInfo(
                isGroupOwner,
                "192.168.49.1",
                peer.copy(status = NearbyDevice.Status.Connected)
            )
    }
}

class FakePreconditions(var granted: Boolean = true, var locationOff: Boolean = false) :
    DiscoveryPreconditions {
    override fun nearbyGranted() = granted

    override fun locationOff() = locationOff
}

class InMemoryPartnerStore(initial: Partner? = null) : PartnerStore {
    private val state = MutableStateFlow(initial)
    override val partner = state
    val saved get() = state.value

    override suspend fun save(partner: Partner) {
        state.value = partner
    }

    override suspend fun clear() {
        state.value = null
    }
}

fun phone(name: String, address: String = "addr-$name") =
    NearbyDevice(name, address, NearbyDevice.Status.Available, isPhone = true)

fun tv(name: String) = NearbyDevice(name, name, NearbyDevice.Status.Available, isPhone = false)
