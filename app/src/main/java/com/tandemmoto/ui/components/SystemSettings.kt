package com.tandemmoto.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Opens the Wi-Fi quick panel (Android 10+) or the Wi-Fi settings screen. */
fun Context.openWifiSettings() {
    startActivity(
        Intent(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_WIFI
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
        )
    )
}
