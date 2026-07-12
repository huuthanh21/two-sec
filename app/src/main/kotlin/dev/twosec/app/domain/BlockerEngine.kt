package dev.twosec.app.domain

import dev.twosec.app.data.BlocklistStore

class BlockerEngine(
    private val store: BlocklistStore,
    private val ignoredPackages: Set<String>,
    private val ownPackage: String,
) {
    fun decide(packageName: String, now: Long): Decision {
        if (packageName == ownPackage) return Decision.Skip(SkipReason.OwnPackage)
        if (packageName in ignoredPackages) return Decision.Skip(SkipReason.IgnoredPackage)

        val snapshot = store.snapshot()
        if (!snapshot.masterEnabled) return Decision.Skip(SkipReason.MasterOff)
        if (packageName !in snapshot.blocklist) return Decision.Skip(SkipReason.NotInBlocklist)

        val expiry = snapshot.whitelistExpiries[packageName] ?: 0L
        if (now < expiry) return Decision.Skip(SkipReason.Whitelisted)

        return Decision.Intervene
    }
}
