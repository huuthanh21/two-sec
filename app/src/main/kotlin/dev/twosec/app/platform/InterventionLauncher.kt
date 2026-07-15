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
        val decision = engine.decide(packageName, clock.now())
        when (decision) {
            is Decision.Intervene -> {
                lastForegroundPackage = null
                onIntervene(packageName)
            }
            is Decision.Skip -> when (decision.reason) {
                SkipReason.Whitelisted -> lastForegroundPackage = packageName
                SkipReason.NotInBlocklist -> lastForegroundPackage = null
                SkipReason.IgnoredPackage,
                SkipReason.MasterOff,
                SkipReason.OwnPackage,
                -> Unit
                SkipReason.AlreadyInForeground,
                -> Unit
            }
        }
        Timber.d("onForegroundApp old=%s new=%s decision=%s", previous, packageName, decision)
        return decision
    }
}
