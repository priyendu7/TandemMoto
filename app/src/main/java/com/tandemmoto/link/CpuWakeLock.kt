package com.tandemmoto.link

import android.content.Context
import android.os.PowerManager

/**
 * Keeps the CPU running while the link waits for its next reconnect slot (#85): with the screen
 * off and no music, Android can otherwise sleep through the slot.
 */
class CpuWakeLock(context: Context) {
    private val lock: PowerManager.WakeLock? =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TandemMoto:reconnect")
            ?.apply { setReferenceCounted(false) }

    // Released by Link's finally: when reconnected, or on Disconnect / Forget / Wi-Fi off.
    @Suppress("Wakelock", "WakelockTimeout")
    fun hold(on: Boolean) {
        val lock = lock ?: return
        runCatching {
            if (on && !lock.isHeld) {
                lock.acquire()
            } else if (!on && lock.isHeld) {
                lock.release()
            }
        }
    }
}
