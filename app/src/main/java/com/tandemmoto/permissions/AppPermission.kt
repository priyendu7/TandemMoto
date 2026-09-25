package com.tandemmoto.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import com.tandemmoto.R

/**
 * A runtime permission as the user sees it, which may map to several Android permissions
 * depending on the API level. Each one gates a single Home screen section and is asked for there,
 * so the rest of the app works without it.
 *
 * Permissions are added here in the PR that builds the feature using them: audio files with the
 * music library (Phase 2, if it scans the device instead of using the file picker), the
 * microphone with the intercom (Phase 4), phone state with call hold (Phase 5).
 */
enum class AppPermission {
    /** Connection bar: Wi-Fi Direct discovery (PRD §5.1); nearby devices, location before 13. */
    NEARBY,

    /**
     * Android 13+: shows the link's notification (the foreground service runs without it). Optional
     * and asked once, the first time the link connects; Settings can turn it on later.
     */
    NOTIFICATIONS;

    /**
     * Android permissions to request on [sdk], empty when none are needed there. The first entry
     * decides whether this counts as granted: for location that's FINE, because Wi-Fi Direct
     * discovery doesn't work with approximate location only.
     */
    fun permissionsFor(sdk: Int): List<String> = when (this) {
        NEARBY ->
            if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        NOTIFICATIONS ->
            if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
    }

    /**
     * One line shown in the section's prompt: what to allow and why. [approximateOnly] is the
     * Android 12-and-older case where location was granted, but not precise location.
     */
    @StringRes
    fun prompt(sdk: Int, approximateOnly: Boolean = false): Int = when (this) {
        NEARBY -> when {
            sdk >= Build.VERSION_CODES.TIRAMISU -> R.string.permission_nearby_prompt
            approximateOnly -> R.string.permission_precise_location_prompt
            else -> R.string.permission_location_prompt
        }
        NOTIFICATIONS -> R.string.permission_notifications_prompt
    }

    companion object {
        /** The permissions that apply on [sdk], in the order the screen lists them. */
        fun applicable(sdk: Int): List<AppPermission> =
            entries.filter { it.permissionsFor(sdk).isNotEmpty() }
    }
}
