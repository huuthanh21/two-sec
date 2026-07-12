package dev.twosec.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryBlocklistStore : BlocklistStore {

    private val master = MutableStateFlow(false)
    private val blocklist = MutableStateFlow<Set<String>>(emptySet())
    private val whitelist = MutableStateFlow<Map<String, Long>>(emptyMap())

    override fun masterEnabled(): Flow<Boolean> = master

    override suspend fun setMasterEnabled(enabled: Boolean) {
        master.value = enabled
    }

    override fun blocklist(): Flow<Set<String>> = blocklist

    override suspend fun addToBlocklist(packageName: String) {
        blocklist.update { it + packageName }
    }

    override suspend fun removeFromBlocklist(packageName: String) {
        blocklist.update { it - packageName }
    }

    override fun whitelistExpiries(): Flow<Map<String, Long>> = whitelist

    override suspend fun setWhitelistExpiry(packageName: String, untilMs: Long) {
        whitelist.update { it + (packageName to untilMs) }
    }

    override suspend fun clearExpiredWhitelist(nowMs: Long) {
        whitelist.update { current -> current.filterValues { expiry -> expiry > nowMs } }
    }
}
