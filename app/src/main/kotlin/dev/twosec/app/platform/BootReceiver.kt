package dev.twosec.app.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> renewAccessibilitySubscription(context)
        }
    }

    private fun renewAccessibilitySubscription(context: Context) {
        val service = ComponentName(context, BlockerAccessibilityService::class.java)
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val isStillEnabled = enabled.any { info ->
            val resolved = info.resolveInfo.serviceInfo
            resolved.packageName == service.packageName &&
                resolved.name == service.className
        }
        if (!isStillEnabled) {
            val newState = context.packageManager
                .getComponentEnabledSetting(service)
            if (newState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                context.packageManager.setComponentEnabledSetting(
                    service,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
