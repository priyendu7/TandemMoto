package com.tandemmoto.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import com.tandemmoto.R

/**
 * A runtime permission as the user sees it: one row on the permissions screen, which may map to
 * several Android permissions depending on the API level.
 *
 * Permissions are added here in the PR that builds the feature using them: the microphone with
 * the intercom (Phase 4, optional: music sharing works without it), notifications with the
 * foreground service, phone state with call hold (Phase 5).
 */
enum class AppPermission(val required: Boolean) {
    /** Wi-Fi Direct discovery (PRD §5.1): nearby Wi-Fi devices on 13+, location before that. */
    NEARBY(required = true);

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
    }

    @StringRes
    fun title(sdk: Int): Int = when (this) {
        NEARBY ->
            if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                R.string.permission_nearby_title
            } else {
                R.string.permission_location_title
            }
    }

    @StringRes
    fun reason(sdk: Int): Int = when (this) {
        NEARBY ->
            if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                R.string.permission_nearby_reason
            } else {
                R.string.permission_location_reason
            }
    }

    companion object {
        /** The permissions that apply on [sdk], in the order the screen lists them. */
        fun applicable(sdk: Int): List<AppPermission> =
            entries.filter { it.permissionsFor(sdk).isNotEmpty() }
    }
}
