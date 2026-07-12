package dev.twosec.app

import android.app.Application
import dev.twosec.app.data.BlocklistStore
import dev.twosec.app.data.Clock
import dev.twosec.app.data.DataStoreBlocklistStore
import dev.twosec.app.data.SystemClock
import dev.twosec.app.domain.BlockerEngine
import dev.twosec.app.domain.WhitelistGate
import dev.twosec.app.platform.InstalledAppsProvider
import dev.twosec.app.platform.SystemPackages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TwoSecApp : Application() {

    lateinit var blocklistStore: BlocklistStore
        private set

    lateinit var installedAppsProvider: InstalledAppsProvider
        private set

    lateinit var clock: Clock
        private set

    lateinit var blockerEngine: BlockerEngine
        private set

    lateinit var whitelistGate: WhitelistGate
        private set

    lateinit var appScope: CoroutineScope
        private set

    override fun onCreate() {
        super.onCreate()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        blocklistStore = DataStoreBlocklistStore(this)
        installedAppsProvider = InstalledAppsProvider(this)
        clock = SystemClock()
        blockerEngine = BlockerEngine(
            store = blocklistStore,
            ignoredPackages = SystemPackages.ignored,
            ownPackage = packageName,
        )
        whitelistGate = WhitelistGate(blocklistStore)
    }
}
