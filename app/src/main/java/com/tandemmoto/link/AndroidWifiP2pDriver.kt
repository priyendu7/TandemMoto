package com.tandemmoto.link

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.IntentCompat
import androidx.core.location.LocationManagerCompat
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.currentPermissionsState
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** [WifiP2pDriver] over the platform WifiP2pManager and its broadcasts. */
class AndroidWifiP2pDriver(context: Context) : WifiP2pDriver {
    private val appContext = context.applicationContext
    private val manager: WifiP2pManager? = appContext.getSystemService(WifiP2pManager::class.java)
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(appContext, Looper.getMainLooper(), null)

    override val supported: Boolean =
        manager != null &&
            channel != null &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    // Seeded from Wi-Fi's current state: Android is supposed to send the Wi-Fi Direct state as soon
    // as the receiver registers, but the Redmi Y2 (MIUI, Android 9) never did.
    private val _enabled = MutableStateFlow(
        appContext.getSystemService(WifiManager::class.java)?.isWifiEnabled
    )
    override val enabled: StateFlow<Boolean?> = _enabled.asStateFlow()

    private val _peers = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val peers: StateFlow<List<NearbyDevice>> = _peers.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    override val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val _group = MutableStateFlow<GroupInfo?>(null)
    override val group: StateFlow<GroupInfo?> = _group.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION ->
                    _enabled.value = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val list = IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_P2P_DEVICE_LIST,
                        WifiP2pDeviceList::class.java
                    )
                    _peers.value = list?.deviceList.orEmpty().map { it.toNearbyDevice() }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> onConnectionChanged(
                    IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                        WifiP2pInfo::class.java
                    ),
                    IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_WIFI_P2P_GROUP,
                        WifiP2pGroup::class.java
                    )
                )
                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION ->
                    _discovering.value =
                        intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1) ==
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
            }
        }
    }

    init {
        if (supported) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
            // These actions are protected: only the system can send them. On Android 13+ the flag
            // is declared explicitly. Below that, ContextCompat's NOT_EXPORTED emulation guards the
            // receiver with an app-only permission, which can also filter the sticky state Android
            // replays on registration (the Redmi Y2 never got it), so register plainly there.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
            readCurrentGroup()
        }
    }

    /** A group can outlive the app (spike); read the current one instead of waiting. */
    private fun readCurrentGroup() {
        runCatching {
            manager!!.requestConnectionInfo(channel) { info ->
                if (info?.groupFormed == true) requestGroup { onConnectionChanged(info, it) }
            }
        }
    }

    // Needs the Nearby/location permission; without it the SecurityException is swallowed and
    // the group is picked up from the next connection broadcast instead.
    @SuppressLint("MissingPermission")
    private fun requestGroup(onGroup: (WifiP2pGroup?) -> Unit) {
        runCatching { manager!!.requestGroupInfo(channel, onGroup) }
    }

    private fun onConnectionChanged(info: WifiP2pInfo?, group: WifiP2pGroup?) {
        if (info?.groupFormed != true) {
            _group.value = null
            return
        }
        if (group == null) {
            // Some phones leave the group out of the broadcast; ask for it.
            _group.value = GroupInfo(info.isGroupOwner, info.groupOwnerAddress?.hostAddress, null)
            requestGroup { requested ->
                if (requested !=
                    null
                ) {
                    onConnectionChanged(info, requested)
                }
            }
            return
        }
        val peer = if (info.isGroupOwner) group.clientList.firstOrNull() else group.owner
        _group.value = GroupInfo(
            isGroupOwner = info.isGroupOwner,
            ownerAddress = info.groupOwnerAddress?.hostAddress,
            peer = peer?.toNearbyDevice()?.copy(status = NearbyDevice.Status.Connected)
        )
    }

    // Callers check the permission first (DiscoveryPreconditions); an exception maps to Error.
    @SuppressLint("MissingPermission")
    override suspend fun discoverPeers(): P2pResult =
        request { listener -> manager!!.discoverPeers(channel, listener) }

    override suspend fun stopPeerDiscovery(): P2pResult =
        request { listener -> manager!!.stopPeerDiscovery(channel, listener) }

    // The first pairing fixes the group owner (spike), so groupOwnerIntent is left to Android.
    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): P2pResult = request { listener ->
        manager!!.connect(channel, WifiP2pConfig().apply { deviceAddress = address }, listener)
    }

    override suspend fun cancelConnect(): P2pResult =
        request { listener -> manager!!.cancelConnect(channel, listener) }

    override suspend fun removeGroup(): P2pResult =
        request { listener -> manager!!.removeGroup(channel, listener) }

    override fun close() {
        if (!supported) return
        runCatching { appContext.unregisterReceiver(receiver) }
        // Channel.close() throws if the channel never fully connected; closing must never crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) runCatching { channel?.close() }
    }

    private suspend fun request(call: (WifiP2pManager.ActionListener) -> Unit): P2pResult {
        if (!supported) return P2pResult.Unsupported
        return suspendCancellableCoroutine { continuation ->
            val listener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (continuation.isActive) continuation.resume(P2pResult.Ok)
                }

                override fun onFailure(reason: Int) {
                    if (continuation.isActive) continuation.resume(reason.toResult())
                }
            }
            try {
                call(listener)
            } catch (e: RuntimeException) {
                // SecurityException without the permission; OEM Wi-Fi stacks can throw others.
                // A failed request is reported, never allowed to crash the app.
                if (continuation.isActive) continuation.resume(P2pResult.Error)
            }
        }
    }

    private fun Int.toResult() = when (this) {
        WifiP2pManager.BUSY -> P2pResult.Busy
        WifiP2pManager.P2P_UNSUPPORTED -> P2pResult.Unsupported
        else -> P2pResult.Error
    }

    private fun WifiP2pDevice.toNearbyDevice() = NearbyDevice(
        name = deviceName.orEmpty(),
        address = deviceAddress.orEmpty(),
        status = when (status) {
            WifiP2pDevice.CONNECTED -> NearbyDevice.Status.Connected
            WifiP2pDevice.INVITED -> NearbyDevice.Status.Invited
            WifiP2pDevice.FAILED -> NearbyDevice.Status.Failed
            WifiP2pDevice.AVAILABLE -> NearbyDevice.Status.Available
            else -> NearbyDevice.Status.Unavailable
        },
        isPhone = primaryDeviceType.orEmpty().startsWith("10-")
    )
}

class AndroidDiscoveryPreconditions(context: Context) : DiscoveryPreconditions {
    private val appContext = context.applicationContext

    override fun nearbyGranted(): Boolean =
        appContext.currentPermissionsState().isGranted(AppPermission.NEARBY)

    override fun locationOff(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false
        val location = appContext.getSystemService(LocationManager::class.java) ?: return false
        return !LocationManagerCompat.isLocationEnabled(location)
    }
}
