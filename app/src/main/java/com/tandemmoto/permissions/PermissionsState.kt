package com.tandemmoto.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class PermissionStatus { Granted, Denied, PermanentlyDenied }

/**
 * The status of every permission that applies on [sdk]. Permissions that don't apply there
 * (e.g. notifications before Android 13) are absent and count as granted.
 */
data class PermissionsState(
    val sdk: Int,
    val statuses: Map<AppPermission, PermissionStatus>,
    /** Android 12 and older: approximate location was granted, but discovery needs precise. */
    val approximateLocationOnly: Boolean = false
) {
    fun isGranted(permission: AppPermission): Boolean =
        status(permission) == PermissionStatus.Granted

    /** Permissions that don't apply on [sdk] count as granted. */
    fun status(permission: AppPermission): PermissionStatus =
        statuses[permission] ?: PermissionStatus.Granted

    companion object {
        /**
         * Pure mapping from Android's answers to [PermissionsState].
         *
         * Android has no "permanently denied" flag: a permission counts as permanently denied
         * once it has been requested ([askedBefore]), is still denied, and the system no longer
         * allows a rationale, meaning the dialog won't show again.
         */
        fun from(
            sdk: Int,
            isGranted: (String) -> Boolean,
            askedBefore: Set<AppPermission> = emptySet(),
            shouldShowRationale: (String) -> Boolean = { false }
        ): PermissionsState {
            val statuses = AppPermission.applicable(sdk).associateWith { permission ->
                val key = permission.permissionsFor(sdk).first()
                when {
                    isGranted(key) -> PermissionStatus.Granted
                    permission in askedBefore && !shouldShowRationale(key) ->
                        PermissionStatus.PermanentlyDenied
                    else -> PermissionStatus.Denied
                }
            }
            val approximateOnly = sdk < Build.VERSION_CODES.TIRAMISU &&
                !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
                isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
            return PermissionsState(sdk, statuses, approximateOnly)
        }
    }
}

fun Context.isPermissionGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Granted/denied only; screens that need "permanently denied" use [PermissionsState.from]. */
fun Context.currentPermissionsState(): PermissionsState =
    PermissionsState.from(Build.VERSION.SDK_INT, ::isPermissionGranted)
