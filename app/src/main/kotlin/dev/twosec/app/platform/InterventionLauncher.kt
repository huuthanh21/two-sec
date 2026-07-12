package dev.twosec.app.platform

import android.content.Context
import android.content.Intent
import dev.twosec.app.data.Clock
import dev.twosec.app.domain.BlockerEngine
import dev.twosec.app.domain.Decision
import dev.twosec.app.ui.InterventionActivity

class InterventionLauncher(
    private val context: Context,
    private val engine: BlockerEngine,
    private val clock: Clock,
    private val activityStarter: (Intent) -> Unit = { context.startActivity(it) },
) {

    fun onForegroundApp(packageName: String): Decision {
        val decision = engine.decide(packageName, clock.now())
        if (decision !is Decision.Intervene) return decision
        val intent = Intent(context, InterventionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(InterventionActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        activityStarter(intent)
        return decision
    }
}
