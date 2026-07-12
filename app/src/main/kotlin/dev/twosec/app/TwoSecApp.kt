package dev.twosec.app

import android.app.Application
import dev.twosec.app.data.BlocklistStore
import dev.twosec.app.data.DataStoreBlocklistStore
import dev.twosec.app.platform.InstalledAppsProvider

class TwoSecApp : Application() {

    lateinit var blocklistStore: BlocklistStore
        private set

    lateinit var installedAppsProvider: InstalledAppsProvider
        private set

    override fun onCreate() {
        super.onCreate()
        blocklistStore = DataStoreBlocklistStore(this)
        installedAppsProvider = InstalledAppsProvider(this)
    }
}
