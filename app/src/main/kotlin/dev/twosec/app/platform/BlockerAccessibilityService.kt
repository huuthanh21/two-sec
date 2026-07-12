package dev.twosec.app.platform

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.twosec.app.TwoSecApp
import dev.twosec.app.ui.InterventionActivity

class BlockerAccessibilityService : AccessibilityService() {

    private var launcher: InterventionLauncher? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as TwoSecApp
        launcher = InterventionLauncher(
            engine = app.blockerEngine,
            clock = app.clock,
            onIntervene = ::startInterventionActivity,
        )
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        launcher?.onForegroundApp(packageName)
    }

    private fun startInterventionActivity(packageName: String) {
        val intent = Intent(this, InterventionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(InterventionActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivity(intent)
    }
}
