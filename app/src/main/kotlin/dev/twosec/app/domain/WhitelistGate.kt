package dev.twosec.app.domain

import dev.twosec.app.data.BlocklistStore

class WhitelistGate(
    private val store: BlocklistStore,
) {
    suspend fun setWhitelist(packageName: String, untilMillis: Long) {
        store.setWhitelistExpiry(packageName, untilMillis)
    }

    fun isWhitelisted(packageName: String, now: Long): Boolean {
        val expiry = store.snapshot().whitelistExpiries[packageName] ?: return false
        return now < expiry
    }

    suspend fun clearExpired(now: Long) {
        store.clearExpiredWhitelist(now)
    }
}
