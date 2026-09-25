package com.tandemmoto.link

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * A random ID for this installation, sent only to the partner in Hello so each phone knows
 * exactly which app it paired with. Not derived from any device identifier; reinstalling the app
 * makes a new one (and the phones pair again). Kept apart from the partner, so Forget partner
 * doesn't change it.
 */
class InstallId(private val dataStore: DataStore<Preferences>) {
    suspend fun get(): String {
        dataStore.data.first()[KEY]?.let { return it }
        var id = ""
        dataStore.edit { prefs ->
            id =
                prefs[KEY] ?: UUID.randomUUID().toString().also { prefs[KEY] = it }
        }
        return id
    }

    private companion object {
        val KEY = stringPreferencesKey("install_id")
    }
}
