package dev.twosec.app.domain

import dev.twosec.app.data.Clock

class InterventionLifecycle(
    private val engine: BlockerEngine,
    private val clock: Clock,
) {
    var state: SessionState = SessionState.Idle
        private set

    fun onForegroundApp(packageName: String): Decision {
        if (packageName == state.packageName) {
            return Decision.Skip(SkipReason.AlreadyInForeground)
        }

        state = SessionState.InForeground(packageName)

        val decision = engine.decide(packageName, clock.now())
        if (decision is Decision.Intervene) {
            state = SessionState.PendingIntervention(packageName)
        }

        return decision
    }

    fun onInterventionShown(packageName: String) {
        val s = state
        if (s is SessionState.PendingIntervention && s.packageName == packageName) {
            state = SessionState.InIntervention(packageName)
        }
    }

    fun onInterventionDismissed(packageName: String) {
        val s = state
        if (s is SessionState.InIntervention && s.packageName == packageName) {
            state = SessionState.Idle
        }
    }
}
