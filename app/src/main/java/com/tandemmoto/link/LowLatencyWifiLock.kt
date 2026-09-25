package com.tandemmoto.link

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Asks Wi-Fi not to doze while the command channel is open: the spike measured ~20–30% lower
 * latency with it. Android only honours the low-latency mode while the app is in the foreground
 * with the screen on; the heartbeat covers the rest.
 */
class LowLatencyWifiLock(context: Context) {
    private val lock: WifiManager.WifiLock? =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(mode(), "TandemMoto:link")
            ?.apply { setReferenceCounted(false) }

    fun hold(on: Boolean) {
        val lock = lock ?: return
        runCatching {
            if (on &&
                !lock.isHeld
            ) {
                lock.acquire()
            } else if (!on && lock.isHeld) {
                lock.release()
            }
        }
    }

    @Suppress("DEPRECATION") // HIGH_PERF is the closest mode before Android 10
    private fun mode() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
    } else {
        WifiManager.WIFI_MODE_FULL_HIGH_PERF
    }
}
