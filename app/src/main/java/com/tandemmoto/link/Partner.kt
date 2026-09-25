package com.tandemmoto.link

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The one phone this phone is paired with (PRD: a known, deliberately paired partner).
 *
 * [role] implements the spike's one-initiator rule. An app can't read its own Wi-Fi Direct
 * address (it's anonymised), so the phones can't compare IDs; instead the phone where the user
 * tapped the partner is the [Role.Initiator] and makes every later connection, while the
 * [Role.Acceptor] only makes itself visible and waits. Both calling connect() deadlocks.
 */
data class Partner(
    val name: String,
    val address: String,
    val role: Role,
    val pairedAtMillis: Long,
    /** The partner app's install ID, learned from its first Hello (#25); null until then. */
    val installId: String? = null
) {
    enum class Role { Initiator, Acceptor }

    /**
     * Same phone: the Wi-Fi Direct address first (stable across sessions, Wi-Fi toggles and
     * airplane mode on P1 and P4 in the spike), else the device name, in case Android 10+
     * randomised the address.
     */
    fun matches(device: NearbyDevice): Boolean = device.address.isNotBlank() &&
        device.address == address ||
        device.name.isNotBlank() &&
        device.name == name

    val logId: String get() = "peer-" + Integer.toHexString((name + address).hashCode()).takeLast(4)
}

interface PartnerStore {
    val partner: Flow<Partner?>

    suspend fun save(partner: Partner)

    suspend fun clear()
}

/** [PartnerStore] in app-private storage (removed by Forget partner or uninstalling). */
class DataStorePartnerStore(private val dataStore: DataStore<Preferences>) : PartnerStore {
    override val partner: Flow<Partner?> = dataStore.data.map { prefs ->
        val name = prefs[NAME] ?: return@map null
        val address = prefs[ADDRESS] ?: return@map null
        val role = prefs[ROLE]?.let { runCatching { Partner.Role.valueOf(it) }.getOrNull() }
            ?: return@map null
        Partner(name, address, role, prefs[PAIRED_AT] ?: 0L, prefs[INSTALL_ID])
    }

    override suspend fun save(partner: Partner) {
        dataStore.edit {
            it[NAME] = partner.name
            it[ADDRESS] = partner.address
            it[ROLE] = partner.role.name
            it[PAIRED_AT] = partner.pairedAtMillis
            if (partner.installId !=
                null
            ) {
                it[INSTALL_ID] = partner.installId
            } else {
                it.remove(INSTALL_ID)
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val NAME = stringPreferencesKey("name")
        val ADDRESS = stringPreferencesKey("address")
        val ROLE = stringPreferencesKey("role")
        val PAIRED_AT = longPreferencesKey("paired_at")
        val INSTALL_ID = stringPreferencesKey("install_id")
    }
}
