package dev.twosec.app.platform

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dev.twosec.app.TwoSecApp

class BlockerAccessibilityService : AccessibilityService() {

    private var launcher: InterventionLauncher? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as TwoSecApp
        launcher = InterventionLauncher(this, app.blockerEngine, app.clock)
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        launcher?.onForegroundApp(packageName)
    }
}
