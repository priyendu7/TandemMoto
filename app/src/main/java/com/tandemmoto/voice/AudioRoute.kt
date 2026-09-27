package com.tandemmoto.voice

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Where the intercom's voice goes in and out (#72). */
enum class RouteKind { WiredHeadset, BluetoothEarbuds, Phone }

/** [fellBack]: earbuds were there but didn't switch to call mode, so it's the phone's own. */
data class RouteResult(val kind: RouteKind, val fellBack: Boolean = false, val switchMs: Long = 0)

/** Switches the phone's audio into call mode for mic mode and back; a fake in tests. */
interface AudioRoute {
    /** Call mode, and the best device for it; waits (up to a few seconds) for earbuds. */
    suspend fun open(): RouteResult

    /** Back to normal: the music returns to the earbuds' or earphones' music mode. */
    fun close()
}

/**
 * Android's call-mode routing (#72).
 * - Android 12+ (the rider's S25): pick the communication device: Bluetooth earbuds, else a
 *   wired headset, else the phone.
 * - Before 12 (the pillion's Redmi on 9): start the Bluetooth hands-free link (SCO) and wait
 *   for it to connect, up to [SCO_TIMEOUT_MS]; a wired headset is used by itself in call mode.
 * Earbuds that don't switch in time fall back to the phone's mic and speaker.
 */
class AndroidAudioRoute(private val context: Context, private val log: (String) -> Unit = {}) :
    AudioRoute {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null
    private var scoStarted = false

    override suspend fun open(): RouteResult = withContext(Dispatchers.Main) {
        if (previousMode == null) previousMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) openModern() else openLegacy()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun openModern(): RouteResult {
        val devices = audio.availableCommunicationDevices
        val earbuds = devices.firstOrNull { it.type in BLUETOOTH_CALL }
        val wired = devices.firstOrNull { it.type in WIRED }
        val pick = earbuds ?: wired ?: return RouteResult(RouteKind.Phone)
        val started = System.currentTimeMillis()
        if (!audio.setCommunicationDevice(pick)) {
            log("Couldn't route the intercom to ${typeName(pick.type)}")
            return RouteResult(RouteKind.Phone, fellBack = earbuds != null)
        }
        if (pick == earbuds) {
            val switched = withTimeoutOrNull(SCO_TIMEOUT_MS) {
                while (audio.communicationDevice?.id != pick.id) delay(POLL_MS)
                true
            } ?: false
            val switchMs = System.currentTimeMillis() - started
            if (!switched) {
                audio.clearCommunicationDevice()
                return RouteResult(RouteKind.Phone, fellBack = true, switchMs = switchMs)
            }
            return RouteResult(RouteKind.BluetoothEarbuds, switchMs = switchMs)
        }
        return RouteResult(RouteKind.WiredHeadset)
    }

    @Suppress("DEPRECATION") // the only way to reach earbuds' hands-free link before Android 12
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private suspend fun openLegacy(): RouteResult {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val earbuds = outputs.any { it.type in BLUETOOTH_ANY }
        val wired = outputs.any { it.type in WIRED }
        if (!earbuds || !audio.isBluetoothScoAvailableOffCall) {
            return RouteResult(if (wired) RouteKind.WiredHeadset else RouteKind.Phone)
        }
        val connected = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> connected.complete(true)
                    AudioManager.SCO_AUDIO_STATE_ERROR -> connected.complete(false)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val started = System.currentTimeMillis()
        try {
            audio.startBluetoothSco()
            scoStarted = true
            audio.isBluetoothScoOn = true
            val ok = withTimeoutOrNull(SCO_TIMEOUT_MS) { connected.await() } ?: false
            val switchMs = System.currentTimeMillis() - started
            if (!ok) {
                stopSco()
                return RouteResult(RouteKind.Phone, fellBack = true, switchMs = switchMs)
            }
            return RouteResult(RouteKind.BluetoothEarbuds, switchMs = switchMs)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    override fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice()
        stopSco()
        previousMode?.let { audio.mode = it }
        previousMode = null
    }

    @Suppress("DEPRECATION")
    private fun stopSco() {
        if (!scoStarted) return
        scoStarted = false
        audio.isBluetoothScoOn = false
        audio.stopBluetoothSco()
    }

    private fun typeName(type: Int) = when (type) {
        in BLUETOOTH_ANY -> "Bluetooth"
        in WIRED -> "wired headset"
        else -> "device type $type"
    }

    companion object {
        const val SCO_TIMEOUT_MS = 4_000L
        private const val POLL_MS = 50L

        @SuppressLint("InlinedApi") // BLE headsets only show up on Android 12+
        private val BLUETOOTH_CALL =
            setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
        private val BLUETOOTH_ANY = BLUETOOTH_CALL + AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        private val WIRED = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )
    }
}
