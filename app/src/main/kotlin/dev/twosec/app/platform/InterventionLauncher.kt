package dev.twosec.app.platform

import dev.twosec.app.data.Clock
import dev.twosec.app.domain.BlockerEngine
import dev.twosec.app.domain.Decision
import dev.twosec.app.domain.SkipReason

class InterventionLauncher(
    private val engine: BlockerEngine,
    private val clock: Clock,
    private val onIntervene: (packageName: String) -> Unit,
) {

    private var lastForegroundPackage: String? = null

    fun onForegroundApp(packageName: String): Decision {
        if (packageName == lastForegroundPackage) {
            return Decision.Skip(SkipReason.AlreadyInForeground)
        }
        lastForegroundPackage = packageName
        val decision = engine.decide(packageName, clock.now())
        if (decision is Decision.Intervene) {
            clearLastForeground()
            onIntervene(packageName)
        }
        return decision
    }

    fun clearLastForeground() {
        lastForegroundPackage = null
    }
}
