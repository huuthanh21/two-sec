package dev.twosec.app.domain

import dev.twosec.app.data.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Test

class InterventionStateMachineTest {

    private fun newMachine(
        packageName: String = PACKAGE,
        startTimeMs: Long = 0L,
    ): Pair<InterventionStateMachine, FakeClock> {
        val clock = FakeClock(startTimeMs)
        return InterventionStateMachine(packageName = packageName, clock = clock) to clock
    }

    @Test
    fun `starting state is Counting with full duration remaining`() {
        val (sm, _) = newMachine()

        assertEquals(InterventionViewState.Counting(5_000L), sm.state)
    }

    @Test
    fun `initialEffects is a single HideButtons emitted on entry to Counting`() {
        val (sm, _) = newMachine()

        assertEquals(
            listOf<InterventionEffect>(InterventionEffect.HideButtons),
            sm.initialEffects,
        )
    }

    @Test
    fun `Tick before duration elapses updates millisRemaining and emits no effects`() {
        val (sm, _) = newMachine()

        val effects = sm.process(InterventionEvent.Tick(now = 100L))

        assertEquals(emptyList<InterventionEffect>(), effects)
        assertEquals(InterventionViewState.Counting(4_900L), sm.state)
    }

    @Test
    fun `Tick at multiple intermediate points decreases the remaining time monotonically`() {
        val (sm, _) = newMachine()

        sm.process(InterventionEvent.Tick(now = 100L))
        assertEquals(InterventionViewState.Counting(4_900L), sm.state)

        sm.process(InterventionEvent.Tick(now = 2_500L))
        assertEquals(InterventionViewState.Counting(2_500L), sm.state)

        sm.process(InterventionEvent.Tick(now = 4_900L))
        assertEquals(InterventionViewState.Counting(100L), sm.state)
    }

    @Test
    fun `Tick at exactly duration transitions to AwaitingChoice and emits ShowButtons`() {
        val (sm, _) = newMachine()

        val effects = sm.process(InterventionEvent.Tick(now = 5_000L))

        assertEquals(listOf<InterventionEffect>(InterventionEffect.ShowButtons), effects)
        assertEquals(InterventionViewState.AwaitingChoice, sm.state)
    }

    @Test
    fun `Tick past duration transitions to AwaitingChoice and emits ShowButtons`() {
        val (sm, _) = newMachine()

        val effects = sm.process(InterventionEvent.Tick(now = 5_100L))

        assertEquals(listOf<InterventionEffect>(InterventionEffect.ShowButtons), effects)
        assertEquals(InterventionViewState.AwaitingChoice, sm.state)
    }

    @Test
    fun `Tick after AwaitingChoice is a no-op and does not re-emit ShowButtons`() {
        val (sm, _) = newMachine()
        sm.process(InterventionEvent.Tick(now = 5_000L))

        val effects = sm.process(InterventionEvent.Tick(now = 10_000L))

        assertEquals(emptyList<InterventionEffect>(), effects)
        assertEquals(InterventionViewState.AwaitingChoice, sm.state)
    }

    @Test
    fun `BackPressed in Counting is a no-op`() {
        val (sm, _) = newMachine()

        val effects = sm.process(InterventionEvent.BackPressed)

        assertEquals(emptyList<InterventionEffect>(), effects)
        assertEquals(InterventionViewState.Counting(5_000L), sm.state)
    }

    @Test
    fun `BackPressed in AwaitingChoice produces the same effects as UserTappedClose`() {
        val (sm, _) = newMachine()
        sm.process(InterventionEvent.Tick(now = 5_000L))

        val effects = sm.process(InterventionEvent.BackPressed)

        assertEquals(
            listOf<InterventionEffect>(
                InterventionEffect.FinishActivity,
                InterventionEffect.GoHome,
            ),
            effects,
        )
    }

    @Test
    fun `UserTappedContinue in AwaitingChoice emits FinishActivity and WhitelistPackage with clock now plus 30s`() {
        val (sm, clock) = newMachine()
        sm.process(InterventionEvent.Tick(now = 5_000L))
        clock.advance(7_000L)

        val effects = sm.process(InterventionEvent.UserTappedContinue)

        assertEquals(
            listOf<InterventionEffect>(
                InterventionEffect.FinishActivity,
                InterventionEffect.WhitelistPackage(PACKAGE, until = 37_000L),
            ),
            effects,
        )
    }

    @Test
    fun `UserTappedClose in AwaitingChoice emits FinishActivity and GoHome`() {
        val (sm, _) = newMachine()
        sm.process(InterventionEvent.Tick(now = 5_000L))

        val effects = sm.process(InterventionEvent.UserTappedClose)

        assertEquals(
            listOf<InterventionEffect>(
                InterventionEffect.FinishActivity,
                InterventionEffect.GoHome,
            ),
            effects,
        )
    }

    @Test
    fun `UserTappedContinue while still Counting is ignored`() {
        val (sm, _) = newMachine()

        val effects = sm.process(InterventionEvent.UserTappedContinue)

        assertEquals(emptyList<InterventionEffect>(), effects)
        assertEquals(InterventionViewState.Counting(5_000L), sm.state)
    }

    @Test
    fun `UserTappedClose while still Counting is ignored`() {
        val (sm, _) = newMachine()

        val effects = sm.process(InterventionEvent.UserTappedClose)

        assertEquals(emptyList<InterventionEffect>(), effects)
        assertEquals(InterventionViewState.Counting(5_000L), sm.state)
    }

    @Test
    fun `ScreenDestroyed in Counting is a no-op`() {
        val (sm, _) = newMachine()

        val effects = sm.process(InterventionEvent.ScreenDestroyed)

        assertEquals(emptyList<InterventionEffect>(), effects)
        assertEquals(InterventionViewState.Counting(5_000L), sm.state)
    }

    @Test
    fun `ScreenDestroyed in AwaitingChoice is a no-op`() {
        val (sm, _) = newMachine()
        sm.process(InterventionEvent.Tick(now = 5_000L))

        val effects = sm.process(InterventionEvent.ScreenDestroyed)

        assertEquals(emptyList<InterventionEffect>(), effects)
        assertEquals(InterventionViewState.AwaitingChoice, sm.state)
    }

    @Test
    fun `effects from successive events are returned per event not accumulated`() {
        val (sm, _) = newMachine()

        val first = sm.process(InterventionEvent.Tick(now = 100L))
        val second = sm.process(InterventionEvent.Tick(now = 5_000L))

        assertEquals(emptyList<InterventionEffect>(), first)
        assertEquals(listOf<InterventionEffect>(InterventionEffect.ShowButtons), second)
    }

    private companion object {
        const val PACKAGE = "com.example.target"
    }
}
