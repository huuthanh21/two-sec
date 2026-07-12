package dev.twosec.app.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class InstalledApp(
    val packageName: String,
    val label: String,
)

class InstalledAppsProvider(
    private val context: Context,
    private val packageManager: PackageManager = ContextCompat.getSystemService(context, PackageManager::class.java)
        ?: context.packageManager,
) {
    fun listUserLaunchableApps(): List<InstalledApp> {
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(
            android.content.Intent.CATEGORY_LAUNCHER,
        )
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
        return apps
            .asSequence()
            .map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName) return@map null
                val appInfo: ApplicationInfo = resolveInfo.activityInfo.applicationInfo
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                ) {
                    return@map null
                }
                InstalledApp(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                )
            }
            .filterNotNull()
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
