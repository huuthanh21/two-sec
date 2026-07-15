package dev.twosec.app.platform

import dev.twosec.app.data.Clock
import dev.twosec.app.domain.BlockerEngine
import dev.twosec.app.domain.Decision
import dev.twosec.app.domain.SkipReason
import timber.log.Timber

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
        val previous = lastForegroundPackage
        lastForegroundPackage = packageName
        val decision = engine.decide(packageName, clock.now())
        when {
            decision is Decision.Intervene -> {
                lastForegroundPackage = null
                onIntervene(packageName)
            }
            decision is Decision.Skip && decision.reason == SkipReason.IgnoredPackage -> {
                lastForegroundPackage = previous
            }
        }
        Timber.d("onForegroundApp old=%s new=%s decision=%s", previous, packageName, decision)
        return decision
    }
}
