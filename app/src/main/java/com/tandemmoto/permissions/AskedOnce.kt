package com.tandemmoto.permissions

import android.content.Context

/**
 * Remembers across restarts that an optional permission was offered automatically, so it's offered
 * only once (notifications, on the first connection). Settings can still ask again.
 */
class AskedOnce(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("permissions", Context.MODE_PRIVATE)

    fun wasAsked(permission: AppPermission): Boolean = prefs.getBoolean(key(permission), false)

    fun markAsked(permission: AppPermission) {
        prefs.edit().putBoolean(key(permission), true).apply()
    }

    private fun key(permission: AppPermission) = "auto_asked_${permission.name.lowercase()}"
}
