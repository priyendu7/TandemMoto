package com.tandemmoto.transfer

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings → Songs from partner (#50): per phone, not shared with the partner. */
class WindowSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "transfer",
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(
        WindowSettings(
            behind = prefs.getInt(BEHIND, WindowSettings.DEFAULT_BEHIND)
                .coerceIn(WindowSettings.BEHIND_RANGE),
            ahead = prefs.getInt(AHEAD, WindowSettings.DEFAULT_AHEAD)
                .coerceIn(WindowSettings.AHEAD_RANGE)
        )
    )
    val settings: StateFlow<WindowSettings> = _settings.asStateFlow()

    fun update(settings: WindowSettings) {
        val clean = WindowSettings(
            settings.behind.coerceIn(WindowSettings.BEHIND_RANGE),
            settings.ahead.coerceIn(WindowSettings.AHEAD_RANGE)
        )
        prefs.edit {
            putInt(BEHIND, clean.behind)
            putInt(AHEAD, clean.ahead)
        }
        _settings.value = clean
    }

    private companion object {
        const val BEHIND = "window_behind"
        const val AHEAD = "window_ahead"
    }
}
