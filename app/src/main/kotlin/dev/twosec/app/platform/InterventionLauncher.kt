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

    private data class ForegroundState(val packageName: String, val atMs: Long)

    private var lastForeground: ForegroundState? = null

    fun onForegroundApp(packageName: String): Decision {
        val now = clock.now()
        val previous = lastForeground
        if (previous != null &&
            previous.packageName == packageName &&
            now - previous.atMs < GRACE_MS
        ) {
            return Decision.Skip(SkipReason.AlreadyInForeground)
        }
        val decision = engine.decide(packageName, now)
        when (decision) {
            is Decision.Intervene -> {
                lastForeground = null
                onIntervene(packageName)
            }
            is Decision.Skip -> when (decision.reason) {
                SkipReason.Whitelisted -> lastForeground = ForegroundState(packageName, now)
                SkipReason.NotInBlocklist,
                SkipReason.IgnoredPackage,
                SkipReason.MasterOff,
                SkipReason.OwnPackage,
                -> Unit
                SkipReason.AlreadyInForeground,
                -> Unit
            }
        }
        Timber.d("onForegroundApp old=%s new=%s decision=%s", previous?.packageName, packageName, decision)
        return decision
    }

    private companion object {
        const val GRACE_MS = 60_000L
    }
}
