package com.tandemmoto.spike

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tandemmoto.diagnostics.AppLog
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.currentPermissionsState
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LabPeer(val name: String, val address: String, val status: String) {
    /** Logged instead of the name or address (no partner device details in logs). */
    val logId: String get() = "peer-" + Integer.toHexString((name + address).hashCode()).takeLast(4)
}

data class LabEvent(val atMillis: Long, val text: String)

data class LabState(
    val supported: Boolean = false,
    val p2pEnabled: Boolean? = null,
    val locationOn: Boolean = false,
    val nearbyGranted: Boolean = false,
    val thisDevice: String? = null,
    val discovering: Boolean = false,
    val peers: List<LabPeer> = emptyList(),
    val goIntent: Int = 15,
    val groupFormed: Boolean = false,
    val isGroupOwner: Boolean? = null,
    val groupOwnerAddress: String? = null,
    val socketState: String = "Not connected",
    val discoveryToFirstPeerMs: Long? = null,
    val connectToGroupMs: Long? = null,
    val groupToSocketMs: Long? = null,
    val ping: PingSnapshot = PingSnapshot(),
    val events: List<LabEvent> = emptyList()
)

/**
 * Spike-only (#22): drives WifiP2pManager directly and records what happens, with timings, so the
 * real link layer can be designed from measurements. Not production code.
 */
class WifiDirectLab(app: Application) : AndroidViewModel(app) {
    private val manager: WifiP2pManager? = app.getSystemService(WifiP2pManager::class.java)
    private val channel: WifiP2pManager.Channel? = manager?.initialize(
        app,
        Looper.getMainLooper()
    ) {
        event("Channel lost: Android dropped the Wi-Fi Direct framework connection")
    }

    private val _state = MutableStateFlow(
        LabState(
            supported = app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        )
    )
    val state: StateFlow<LabState> = _state.asStateFlow()

    private val stats = PingStats()
    private var discoverStartedAt = 0L
    private var connectStartedAt = 0L
    private var groupFormedAt = 0L
    private var session: Job? = null

    @Volatile private var socket: Socket? = null

    @Volatile private var server: ServerSocket? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBroadcast(intent)
    }

    init {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        event("Lab opened: ${AppLog.environmentSummary()}")
        event(
            "Wi-Fi Direct feature declared: ${_state.value.supported}, " +
                "manager available: ${manager != null}"
        )
        refreshEnvironment()
        viewModelScope.launch(Dispatchers.IO) { loopbackSelfTest() }
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000)
                _state.update { it.copy(ping = stats.snapshot(now())) }
                if (++tick % 10 == 0 &&
                    _state.value.ping.sent > 0
                ) {
                    event("Ping ${_state.value.ping}")
                }
            }
        }
    }

    /** Location and permission can change in system settings; re-read them on resume. */
    fun refreshEnvironment() {
        val app = getApplication<Application>()
        val location = app.getSystemService(LocationManager::class.java)
        val locationOn = location != null && LocationManagerCompat.isLocationEnabled(location)
        val nearby = app.currentPermissionsState().isGranted(AppPermission.NEARBY)
        val before = _state.value
        if (before.locationOn != locationOn || before.nearbyGranted != nearby) {
            event("Environment: location on=$locationOn, nearby/location permission=$nearby")
        }
        _state.update { it.copy(locationOn = locationOn, nearbyGranted = nearby) }
    }

    fun setGoIntent(intent: Int) {
        _state.update { it.copy(goIntent = intent) }
        event("Group owner intent set to $intent")
    }

    @SuppressLint("MissingPermission") // Checked via nearbyGranted; a SecurityException is logged.
    fun discover() {
        discoverStartedAt = now()
        _state.update { it.copy(discoveryToFirstPeerMs = null) }
        guarded("Discover") { manager!!.discoverPeers(channel, listener("Discover")) }
    }

    fun stopDiscovery() {
        guarded("Stop discovery") {
            manager!!.stopPeerDiscovery(channel, listener("Stop discovery"))
        }
    }

    @SuppressLint("MissingPermission") // Checked via nearbyGranted; a SecurityException is logged.
    fun connect(peer: LabPeer) {
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.address
            groupOwnerIntent = _state.value.goIntent
        }
        connectStartedAt = now()
        event("Connect to ${peer.logId} with group owner intent ${config.groupOwnerIntent}")
        guarded("Connect") { manager!!.connect(channel, config, listener("Connect")) }
    }

    fun disconnect() {
        closeSocket()
        guarded("Cancel connect") { manager!!.cancelConnect(channel, listener("Cancel connect")) }
        guarded("Remove group") { manager!!.removeGroup(channel, listener("Remove group")) }
    }

    private fun onBroadcast(intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) ==
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED
                _state.update { it.copy(p2pEnabled = enabled) }
                event("Wi-Fi Direct ${if (enabled) "enabled" else "disabled"}")
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                val list = IntentCompat.getParcelableExtra(
                    intent,
                    WifiP2pManager.EXTRA_P2P_DEVICE_LIST,
                    WifiP2pDeviceList::class.java
                )
                onPeers(list?.deviceList.orEmpty())
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> onConnectionInfo(
                IntentCompat.getParcelableExtra(
                    intent,
                    WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                    WifiP2pInfo::class.java
                )
            )
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device = IntentCompat.getParcelableExtra(
                    intent,
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                    WifiP2pDevice::class.java
                )
                val text = device?.let { "${it.deviceName} (${statusName(it.status)})" }
                _state.update { it.copy(thisDevice = text) }
                event("This device: ${device?.let { statusName(it.status) }}")
            }
            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                val started = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1) ==
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                _state.update { it.copy(discovering = started) }
                event("Discovery ${if (started) "started" else "stopped"}")
            }
        }
    }

    private fun onPeers(devices: Collection<WifiP2pDevice>) {
        val peers = devices.map { LabPeer(it.deviceName, it.deviceAddress, statusName(it.status)) }
        val firstPeerMs = if (
            _state.value.discoveryToFirstPeerMs == null &&
            discoverStartedAt > 0 &&
            peers.isNotEmpty()
        ) {
            (now() - discoverStartedAt) / 1_000_000
        } else {
            null
        }
        _state.update {
            it.copy(
                peers = peers,
                discoveryToFirstPeerMs = firstPeerMs ?: it.discoveryToFirstPeerMs
            )
        }
        firstPeerMs?.let { event("First peer after $it ms") }
        event("Peers (${peers.size}): " + peers.joinToString { "${it.logId}=${it.status}" })
    }

    private fun onConnectionInfo(info: WifiP2pInfo?) {
        val wasFormed = _state.value.groupFormed
        if (info?.groupFormed == true && !wasFormed) {
            groupFormedAt = now()
            val connectMs = connectStartedAt.takeIf { it > 0 }?.let {
                (groupFormedAt - it) /
                    1_000_000
            }
            val address = info.groupOwnerAddress?.hostAddress
            _state.update {
                it.copy(
                    groupFormed = true,
                    isGroupOwner = info.isGroupOwner,
                    groupOwnerAddress = address,
                    connectToGroupMs = connectMs,
                    groupToSocketMs = null
                )
            }
            event(
                "Group formed" +
                    (connectMs?.let { " $it ms after Connect" } ?: " (initiated by partner)") +
                    ": this phone is ${if (info.isGroupOwner) "GROUP OWNER" else "client"}, owner at $address"
            )
            info.groupOwnerAddress?.let { startSocket(info.isGroupOwner, it) }
        } else if (info?.groupFormed != true && wasFormed) {
            event("Group removed")
            session?.cancel()
            closeSocket()
            connectStartedAt = 0
            _state.update {
                it.copy(
                    groupFormed = false,
                    isGroupOwner = null,
                    groupOwnerAddress = null,
                    socketState = "Not connected"
                )
            }
        }
    }

    private fun startSocket(isGroupOwner: Boolean, owner: InetAddress) {
        session?.cancel()
        stats.reset()
        session = viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = if (isGroupOwner) {
                    setSocketState("Waiting for partner on port $PORT")
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(PORT))
                        server = this
                    }.accept()
                } else {
                    connectWithRetry(owner)
                }
                socket = s
                s.tcpNoDelay = true
                val socketMs = (now() - groupFormedAt) / 1_000_000
                _state.update { it.copy(groupToSocketMs = socketMs) }
                setSocketState("Connected, pinging")
                event("Socket connected $socketMs ms after group formed")
                runPing(s)
            } catch (e: IOException) {
                if (isActive) {
                    setSocketState("Error: ${e.message}")
                    event("Socket ended: ${e.javaClass.simpleName}: ${e.message}")
                }
            } finally {
                closeSocket()
            }
        }
    }

    private suspend fun connectWithRetry(owner: InetAddress): Socket {
        repeat(CONNECT_ATTEMPTS) { attempt ->
            setSocketState("Connecting to owner (attempt ${attempt + 1})")
            try {
                return Socket().apply { connect(InetSocketAddress(owner, PORT), 2_000) }
            } catch (e: IOException) {
                event("Socket connect attempt ${attempt + 1} failed: ${e.message}")
                delay(500)
            }
        }
        throw IOException("Gave up after $CONNECT_ATTEMPTS attempts")
    }

    private suspend fun runPing(s: Socket) = coroutineScope {
        val out = s.getOutputStream().bufferedWriter()
        val lock = Any()
        fun send(line: String) = synchronized(lock) {
            out.write(line)
            out.write("\n")
            out.flush()
        }
        launch {
            var seq = 0
            while (isActive) {
                seq++
                val at = now()
                stats.recordSent(seq, at)
                send(PingProtocol.ping(seq, at))
                delay(1_000)
            }
        }
        val reader = s.getInputStream().bufferedReader()
        while (isActive) {
            val line = reader.readLine() ?: throw IOException("Partner closed the connection")
            when (val message = PingProtocol.parse(line)) {
                is PingMessage.Ping -> send(PingProtocol.pong(message.seq, message.sentAtNanos))
                is PingMessage.Pong -> {
                    val at = now()
                    val gap = stats.recordPong(message.seq, at - message.sentAtNanos, at)
                    if (gap != null && gap > GAP_WORTH_LOGGING_NANOS) {
                        event("Ping gap: ${gap / 1_000_000} ms without a pong")
                    }
                }
                null -> event("Unknown line from partner (${line.length} chars)")
            }
        }
    }

    /**
     * Opens a TCP server and client on 127.0.0.1. No Wi-Fi Direct involved: it shows whether this
     * app may create sockets at all, which Android ties to the INTERNET permission.
     */
    private fun loopbackSelfTest() {
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                Socket(InetAddress.getLoopbackAddress(), listener.localPort).use { }
            }
            event("Loopback socket self-test: OK")
        } catch (e: Exception) {
            event("Loopback socket self-test FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun setSocketState(text: String) = _state.update { it.copy(socketState = text) }

    private fun closeSocket() {
        runCatching { socket?.close() }
        runCatching { server?.close() }
        socket = null
        server = null
    }

    private inline fun guarded(action: String, block: () -> Unit) {
        if (manager == null || channel == null) {
            event("$action: Wi-Fi Direct isn't available on this phone")
            return
        }
        try {
            block()
        } catch (e: SecurityException) {
            event("$action: permission missing (${e.message}). Grant it on Home first")
        }
    }

    private fun listener(action: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() = event("$action: accepted")

        override fun onFailure(reason: Int) = event("$action failed: ${reasonName(reason)}")
    }

    private fun event(text: String) {
        AppLog.i(TAG, text)
        _state.update {
            it.copy(
                events = (listOf(LabEvent(System.currentTimeMillis(), text)) + it.events).take(300)
            )
        }
    }

    override fun onCleared() {
        session?.cancel()
        closeSocket()
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) channel?.close()
        AppLog.i(TAG, "Lab closed")
    }

    private companion object {
        const val TAG = "Spike"
        const val PORT = 8988
        const val CONNECT_ATTEMPTS = 20
        const val GAP_WORTH_LOGGING_NANOS = 3_000_000_000L

        fun now() = SystemClock.elapsedRealtimeNanos()

        fun statusName(status: Int) = when (status) {
            WifiP2pDevice.CONNECTED -> "connected"
            WifiP2pDevice.INVITED -> "invited"
            WifiP2pDevice.FAILED -> "failed"
            WifiP2pDevice.AVAILABLE -> "available"
            WifiP2pDevice.UNAVAILABLE -> "unavailable"
            else -> "unknown($status)"
        }

        fun reasonName(reason: Int) = when (reason) {
            WifiP2pManager.ERROR -> "ERROR"
            WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
            WifiP2pManager.BUSY -> "BUSY"
            WifiP2pManager.NO_SERVICE_REQUESTS -> "NO_SERVICE_REQUESTS"
            else -> "reason $reason"
        }
    }
}
