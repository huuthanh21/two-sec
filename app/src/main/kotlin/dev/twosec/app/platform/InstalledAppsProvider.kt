package dev.twosec.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class InstalledApp(
    val packageName: String,
    val label: String,
)

class InstalledAppsProvider(
    private val context: Context? = null,
    private val packageManager: PackageManager? = context?.let {
        ContextCompat.getSystemService(it, PackageManager::class.java) ?: it.packageManager
    },
    private val ownPackageName: String = context?.packageName ?: "",
    private val fetchLaunchableApps: () -> List<InstalledApp> = {
        val pm = packageManager ?: throw IllegalStateException("PackageManager not available")
        val ctx = context ?: throw IllegalStateException("Context not available")
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0).orEmpty()
        resolveInfos.asSequence()
            .map { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@map null
                if (packageName == ctx.packageName) return@map null
                val appInfo: ApplicationInfo = resolveInfo.activityInfo?.applicationInfo ?: return@map null
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                ) {
                    return@map null
                }
                InstalledApp(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(pm).toString(),
                )
            }
            .filterNotNull()
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    },
    private val fetchAllLaunchablePackages: () -> Set<String> = {
        queryPackagesWithCategory(packageManager, Intent.CATEGORY_LAUNCHER)
    },
    private val fetchHomePackages: () -> Set<String> = {
        queryPackagesWithCategory(packageManager, Intent.CATEGORY_HOME)
    },
    private val fetchEnabledImePackages: () -> Set<String> = {
        val ctx = context ?: throw IllegalStateException("Context not available")
        val pm = packageManager ?: throw IllegalStateException("PackageManager not available")
        val imm = ContextCompat.getSystemService(ctx, InputMethodManager::class.java)
        imm?.enabledInputMethodList.orEmpty().mapNotNull { imeInfo ->
            val packageName = imeInfo.packageName
            runCatching {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                    packageName
                } else {
                    null
                }
            }.getOrNull()
        }.toSet()
    }
) {
    private val lock = Any()

    @Volatile
    private var cachedLaunchableApps: List<InstalledApp>? = null

    @Volatile
    private var cachedAllLaunchablePackages: Set<String>? = null

    @Volatile
    private var cachedHomePackages: Set<String>? = null

    @Volatile
    private var cachedImePackages: Set<String>? = null

    private fun <T> getOrFetch(getter: () -> T?, fetcher: () -> T, setter: (T) -> Unit): T {
        return getter() ?: synchronized(lock) {
            getter() ?: fetcher().also(setter)
        }
    }

    fun getHomePackages(): Set<String> =
        getOrFetch({ cachedHomePackages }, fetchHomePackages) { cachedHomePackages = it }

    fun getEnabledImePackages(): Set<String> =
        getOrFetch({ cachedImePackages }, fetchEnabledImePackages) { cachedImePackages = it }

    private fun getAllLaunchablePackages(): Set<String> =
        getOrFetch({ cachedAllLaunchablePackages }, fetchAllLaunchablePackages) { cachedAllLaunchablePackages = it }

    fun listUserLaunchableApps(): List<InstalledApp> =
        getOrFetch({ cachedLaunchableApps }, fetchLaunchableApps) { cachedLaunchableApps = it }

    fun isUserFacing(packageName: String): Boolean {
        return packageName == ownPackageName ||
                getHomePackages().contains(packageName) ||
                getAllLaunchablePackages().contains(packageName)
    }

    fun refresh() {
        synchronized(lock) {
            cachedLaunchableApps = null
            cachedAllLaunchablePackages = null
            cachedHomePackages = null
            cachedImePackages = null
        }
    }

    fun rebuildCacheAsync(coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        refresh()
        coroutineScope.launch {
            getHomePackages()
            getEnabledImePackages()
            listUserLaunchableApps()
            getAllLaunchablePackages()
        }
    }

    private companion object {
        private fun queryPackagesWithCategory(pm: PackageManager?, category: String): Set<String> {
            val packageMgr = pm ?: return emptySet()
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            return packageMgr.queryIntentActivities(intent, 0).orEmpty()
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }
    }
}
