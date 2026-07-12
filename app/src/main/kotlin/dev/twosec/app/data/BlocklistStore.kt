package dev.twosec.app.data

import kotlinx.coroutines.flow.Flow

interface BlocklistStore {
    fun masterEnabled(): Flow<Boolean>
    suspend fun setMasterEnabled(enabled: Boolean)

    fun blocklist(): Flow<Set<String>>
    suspend fun addToBlocklist(packageName: String)
    suspend fun removeFromBlocklist(packageName: String)

    fun whitelistExpiries(): Flow<Map<String, Long>>
    suspend fun setWhitelistExpiry(packageName: String, untilMs: Long)
    suspend fun clearExpiredWhitelist(nowMs: Long)

    fun snapshot(): BlocklistSnapshot
}
