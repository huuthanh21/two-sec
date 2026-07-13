package dev.twosec.app.domain

import dev.twosec.app.data.FakeClock
import dev.twosec.app.data.InMemoryBlocklistStore
import dev.twosec.app.domain.SessionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterventionLifecycleTest {

    private val store = InMemoryBlocklistStore()
    private val clock = FakeClock(initialNow = 0L)
    private val engine = BlockerEngine(
        store = store,
        ignoredPackages = emptySet(),
        ownPackage = OWN_PACKAGE,
    )
    private lateinit var lifecycle: InterventionLifecycle

    @Before
    fun setUp() = runTest {
        store.setMasterEnabled(true)
        store.addToBlocklist(BLOCKED)
        lifecycle = InterventionLifecycle(engine = engine, clock = clock)
    }

    @Test
    fun `first call for blocked package returns Intervene and transitions to PendingIntervention`() = runTest {
        val decision = lifecycle.onForegroundApp(BLOCKED)

        assertTrue("expected Intervene; got $decision", decision is Decision.Intervene)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `repeated call for same blocked package returns AlreadyInForeground`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)

        val decision = lifecycle.onForegroundApp(BLOCKED)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), decision)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `three consecutive calls for same package only return Intervene once`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)
        lifecycle.onForegroundApp(BLOCKED)
        val decision = lifecycle.onForegroundApp(BLOCKED)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), decision)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `different package after blocked runs engine and does not intervene for unblocked`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)

        val decision = lifecycle.onForegroundApp(UNBLOCKED)

        assertEquals(Decision.Skip(SkipReason.NotInBlocklist), decision)
        assertEquals(SessionState.InForeground(UNBLOCKED), lifecycle.state)
    }

    @Test
    fun `returning to blocked package after another package invokes Intervene again`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)
        lifecycle.onForegroundApp(UNBLOCKED)

        val decision = lifecycle.onForegroundApp(BLOCKED)

        assertTrue("expected Intervene; got $decision", decision is Decision.Intervene)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `whitelisted blocked package is skipped by engine and stays in InForeground`() = runTest {
        clock.set(1_000L)
        store.setWhitelistExpiry(BLOCKED, untilMs = 5_000L)
        clock.set(2_000L)

        val decision = lifecycle.onForegroundApp(BLOCKED)

        assertEquals(Decision.Skip(SkipReason.Whitelisted), decision)
        assertEquals(SessionState.InForeground(BLOCKED), lifecycle.state)
    }

    @Test
    fun `in-app nav after whitelisted Continue is filtered as AlreadyInForeground`() = runTest {
        clock.set(1_000L)
        store.setWhitelistExpiry(BLOCKED, untilMs = 5_000L)
        clock.set(2_000L)
        lifecycle.onForegroundApp(BLOCKED)

        val repeat = lifecycle.onForegroundApp(BLOCKED)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), repeat)
        assertEquals(SessionState.InForeground(BLOCKED), lifecycle.state)
    }

    @Test
    fun `unblocked package returns SkipNotInBlocklist and records last foreground`() = runTest {
        val decision = lifecycle.onForegroundApp(UNBLOCKED)

        assertEquals(Decision.Skip(SkipReason.NotInBlocklist), decision)
        assertEquals(SessionState.InForeground(UNBLOCKED), lifecycle.state)
    }

    @Test
    fun `unblocked package repeat is filtered as AlreadyInForeground`() = runTest {
        lifecycle.onForegroundApp(UNBLOCKED)

        val repeat = lifecycle.onForegroundApp(UNBLOCKED)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), repeat)
        assertEquals(SessionState.InForeground(UNBLOCKED), lifecycle.state)
    }

    @Test
    fun `PR5 regression full sequence Close then reopen triggers Intervene again`() = runTest {
        assertTrue(lifecycle.onForegroundApp(BLOCKED) is Decision.Intervene)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)

        lifecycle.onInterventionShown(BLOCKED)
        assertEquals(SessionState.InIntervention(BLOCKED), lifecycle.state)

        lifecycle.onInterventionDismissed(BLOCKED)
        assertEquals(SessionState.Idle, lifecycle.state)

        val reopen = lifecycle.onForegroundApp(BLOCKED)
        assertTrue("expected Intervene; got $reopen", reopen is Decision.Intervene)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `onInterventionShown from Idle is a no-op`() {
        lifecycle.onInterventionShown(BLOCKED)

        assertEquals(SessionState.Idle, lifecycle.state)
    }

    @Test
    fun `onInterventionShown from InForeground is a no-op`() = runTest {
        lifecycle.onForegroundApp(UNBLOCKED)

        lifecycle.onInterventionShown(BLOCKED)

        assertEquals(SessionState.InForeground(UNBLOCKED), lifecycle.state)
    }

    @Test
    fun `onInterventionShown from PendingIntervention transitions to InIntervention`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)

        lifecycle.onInterventionShown(BLOCKED)

        assertEquals(SessionState.InIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `onInterventionShown from PendingIntervention with mismatched package is a no-op`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)

        lifecycle.onInterventionShown(OTHER)

        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `onInterventionShown from InIntervention is a no-op`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)
        lifecycle.onInterventionShown(BLOCKED)

        lifecycle.onInterventionShown(BLOCKED)

        assertEquals(SessionState.InIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `onInterventionDismissed from Idle is a no-op`() {
        lifecycle.onInterventionDismissed(BLOCKED)

        assertEquals(SessionState.Idle, lifecycle.state)
    }

    @Test
    fun `onInterventionDismissed from InForeground is a no-op`() = runTest {
        lifecycle.onForegroundApp(UNBLOCKED)

        lifecycle.onInterventionDismissed(BLOCKED)

        assertEquals(SessionState.InForeground(UNBLOCKED), lifecycle.state)
    }

    @Test
    fun `onInterventionDismissed from PendingIntervention is a no-op`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)

        lifecycle.onInterventionDismissed(BLOCKED)

        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `onInterventionDismissed from InIntervention transitions to Idle`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)
        lifecycle.onInterventionShown(BLOCKED)

        lifecycle.onInterventionDismissed(BLOCKED)

        assertEquals(SessionState.Idle, lifecycle.state)
    }

    @Test
    fun `onInterventionDismissed from InIntervention with mismatched package is a no-op`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)
        lifecycle.onInterventionShown(BLOCKED)

        lifecycle.onInterventionDismissed(OTHER)

        assertEquals(SessionState.InIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `PendingIntervention short-circuits the engine`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)

        store.setMasterEnabled(false)

        val decision = lifecycle.onForegroundApp(BLOCKED)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), decision)
        assertEquals(SessionState.PendingIntervention(BLOCKED), lifecycle.state)
    }

    @Test
    fun `InIntervention also short-circuits the engine`() = runTest {
        lifecycle.onForegroundApp(BLOCKED)
        lifecycle.onInterventionShown(BLOCKED)
        assertEquals(SessionState.InIntervention(BLOCKED), lifecycle.state)

        store.setMasterEnabled(false)

        val decision = lifecycle.onForegroundApp(BLOCKED)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), decision)
        assertEquals(SessionState.InIntervention(BLOCKED), lifecycle.state)
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
        const val UNBLOCKED = "com.example.unblocked"
        const val OTHER = "com.example.other"
        const val OWN_PACKAGE = "dev.twosec.app"
    }
}
