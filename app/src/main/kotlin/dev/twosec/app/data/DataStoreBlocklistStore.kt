package dev.twosec.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreBlocklistStore(
    context: Context,
    private val storeName: String = DEFAULT_STORE_NAME,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : BlocklistStore {

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(storeName) },
    )

    override fun masterEnabled(): Flow<Boolean> = store.data.map { it[KEY_MASTER] ?: false }

    override suspend fun setMasterEnabled(enabled: Boolean) {
        store.edit { it[KEY_MASTER] = enabled }
    }

    override fun blocklist(): Flow<Set<String>> =
        store.data.map { prefs -> prefs[KEY_BLOCKLIST] ?: emptySet() }

    override suspend fun addToBlocklist(packageName: String) {
        store.edit { prefs ->
            val current = prefs[KEY_BLOCKLIST] ?: emptySet()
            prefs[KEY_BLOCKLIST] = current + packageName
        }
    }

    override suspend fun removeFromBlocklist(packageName: String) {
        store.edit { prefs ->
            val current = prefs[KEY_BLOCKLIST] ?: emptySet()
            prefs[KEY_BLOCKLIST] = current - packageName
        }
    }

    override fun whitelistExpiries(): Flow<Map<String, Long>> =
        store.data.map { prefs -> decodeWhitelist(prefs[KEY_WHITELIST]) }

    override suspend fun setWhitelistExpiry(packageName: String, untilMs: Long) {
        store.edit { prefs ->
            val current = decodeWhitelist(prefs[KEY_WHITELIST])
            val updated = current + (packageName to untilMs)
            prefs[KEY_WHITELIST] = encodeWhitelist(updated)
        }
    }

    override suspend fun clearExpiredWhitelist(nowMs: Long) {
        store.edit { prefs ->
            val current = decodeWhitelist(prefs[KEY_WHITELIST])
            val kept = current.filterValues { expiry -> expiry > nowMs }
            prefs[KEY_WHITELIST] = encodeWhitelist(kept)
        }
    }

    private fun encodeWhitelist(map: Map<String, Long>): Set<String> =
        map.map { (pkg, expiry) -> "$pkg$SEPARATOR$expiry" }.toSet()

    private fun decodeWhitelist(set: Set<String>?): Map<String, Long> {
        if (set.isNullOrEmpty()) return emptyMap()
        val out = mutableMapOf<String, Long>()
        for (entry in set) {
            val idx = entry.lastIndexOf(SEPARATOR)
            if (idx <= 0) continue
            val pkg = entry.substring(0, idx)
            val expiry = entry.substring(idx + 1).toLongOrNull() ?: continue
            out[pkg] = expiry
        }
        return out
    }

    companion object {
        const val DEFAULT_STORE_NAME: String = "two_sec"
        private const val SEPARATOR: String = "|"
        private val KEY_MASTER = booleanPreferencesKey("master_enabled")
        private val KEY_BLOCKLIST = stringSetPreferencesKey("blocklist")
        private val KEY_WHITELIST = stringSetPreferencesKey("whitelist")
    }
}
