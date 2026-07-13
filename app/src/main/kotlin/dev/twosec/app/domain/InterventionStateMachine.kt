package dev.twosec.app.domain

import dev.twosec.app.data.Clock

class InterventionStateMachine(
    private val packageName: String,
    private val clock: Clock,
) {
    private val startTimeMs: Long = clock.now()

    var state: InterventionViewState = InterventionViewState.Counting(DURATION_MS)
        private set

    val initialEffects: List<InterventionEffect> = listOf(InterventionEffect.HideButtons)

    fun process(event: InterventionEvent): List<InterventionEffect> = when (event) {
        is InterventionEvent.Tick -> onTick(event)
        InterventionEvent.UserTappedContinue -> onTappedContinue()
        InterventionEvent.UserTappedClose -> onTappedCloseOrBack()
        InterventionEvent.BackPressed -> onTappedCloseOrBack()
        InterventionEvent.ScreenDestroyed -> emptyList()
    }

    private fun onTick(event: InterventionEvent.Tick): List<InterventionEffect> {
        if (state !is InterventionViewState.Counting) return emptyList()

        val elapsed = event.now - startTimeMs
        if (elapsed >= DURATION_MS) {
            state = InterventionViewState.AwaitingChoice
            return listOf(InterventionEffect.ShowButtons)
        }
        state = InterventionViewState.Counting(DURATION_MS - elapsed)
        return emptyList()
    }

    private fun onTappedContinue(): List<InterventionEffect> {
        if (state !is InterventionViewState.AwaitingChoice) return emptyList()
        return listOf(
            InterventionEffect.WhitelistPackage(packageName, clock.now() + WHITELIST_MS),
            InterventionEffect.FinishActivity,
        )
    }

    private fun onTappedCloseOrBack(): List<InterventionEffect> {
        if (state !is InterventionViewState.AwaitingChoice) return emptyList()
        return listOf(
            InterventionEffect.GoHome,
            InterventionEffect.FinishActivity,
        )
    }

    private companion object {
        const val DURATION_MS: Long = 5_000L
        const val WHITELIST_MS: Long = 30_000L
    }
}
