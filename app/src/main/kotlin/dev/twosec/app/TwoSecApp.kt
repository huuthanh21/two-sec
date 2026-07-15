package dev.twosec.app

import android.app.Application
import android.os.Build
import dev.twosec.app.data.BlocklistStore
import dev.twosec.app.data.Clock
import dev.twosec.app.data.DataStoreBlocklistStore
import dev.twosec.app.data.LogFileTree
import dev.twosec.app.data.SystemClock
import dev.twosec.app.domain.BlockerEngine
import dev.twosec.app.domain.WhitelistGate
import dev.twosec.app.platform.InstalledAppsProvider
import dev.twosec.app.platform.SystemPackages
import timber.log.Timber
import java.io.File

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

    lateinit var logFileTree: LogFileTree
        private set

    override fun onCreate() {
        super.onCreate()
        logFileTree = LogFileTree(logsDir = File(filesDir, "logs"))
        Timber.plant(logFileTree)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        blocklistStore = DataStoreBlocklistStore(this)
        installedAppsProvider = InstalledAppsProvider(this)
        clock = SystemClock()
        blockerEngine = BlockerEngine(
            store = blocklistStore,
            ignoredPackages = SystemPackages.ignored,
            ownPackage = packageName,
        )
        whitelistGate = WhitelistGate(blocklistStore)
        writeSessionHeader()
    }

    private fun writeSessionHeader() {
        val snapshot = blocklistStore.snapshot()
        Timber.i(
            """---- session start ----
            |app: two-sec v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})
            |device: ${Build.MODEL} / Android ${Build.VERSION.SDK_INT}
            |blocklist: ${snapshot.blocklist.sorted().joinToString(", ")}
            """.trimMargin(),
        )
    }
}
