package com.tandemmoto.link

import kotlinx.coroutines.flow.StateFlow

/** A Wi-Fi Direct device found by discovery. */
data class NearbyDevice(
    val name: String,
    val address: String,
    val status: Status,
    /** Wi-Fi Direct device category 10 ("Telephone"); TVs, printers etc. are listed separately. */
    val isPhone: Boolean
) {
    enum class Status { Available, Invited, Connected, Failed, Unavailable }

    /** Logged instead of the name or address: no partner device details in logs. */
    val logId: String get() = "peer-" + Integer.toHexString((name + address).hashCode()).takeLast(4)
}

/** Outcome of a WifiP2pManager request (its ActionListener result). */
enum class P2pResult { Ok, Busy, Error, Unsupported }

/**
 * The slice of Android's Wi-Fi Direct API that discovery needs. [AndroidWifiP2pDriver] wraps
 * WifiP2pManager; tests use a fake, so [PeerDiscovery]'s logic runs on the JVM.
 */
interface WifiP2pDriver {
    /** The phone declares Wi-Fi Direct and the system service exists. */
    val supported: Boolean

    /** Wi-Fi Direct enabled (false when Wi-Fi is off); null until Android first reports it. */
    val enabled: StateFlow<Boolean?>

    val peers: StateFlow<List<NearbyDevice>>

    /** Android is currently discovering (it stops on its own after a while). */
    val discovering: StateFlow<Boolean>

    suspend fun discoverPeers(): P2pResult

    suspend fun stopPeerDiscovery(): P2pResult

    fun close()
}

/** What has to be true before discovery can work, apart from Wi-Fi itself. */
interface DiscoveryPreconditions {
    /** Nearby devices (Android 13+) or precise location (12 and older) is granted. */
    fun nearbyGranted(): Boolean

    /** Android 12 and older only: Location is switched off, so discovery finds nothing. */
    fun locationOff(): Boolean
}
