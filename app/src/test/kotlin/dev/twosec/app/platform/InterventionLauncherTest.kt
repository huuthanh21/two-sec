package dev.twosec.app.platform

import dev.twosec.app.data.FakeClock
import dev.twosec.app.data.InMemoryBlocklistStore
import dev.twosec.app.domain.BlockerEngine
import dev.twosec.app.domain.Decision
import dev.twosec.app.domain.SkipReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterventionLauncherTest {

    private val store = InMemoryBlocklistStore()
    private val clock = FakeClock(initialNow = 0L)
    private val engine = BlockerEngine(
        store = store,
        ignoredPackages = emptySet(),
        ownPackage = OWN_PACKAGE,
    )
    private val interveneCalls = mutableListOf<String>()
    private val launcher = InterventionLauncher(
        engine = engine,
        clock = clock,
        onIntervene = { packageName -> interveneCalls.add(packageName) },
    )

    @Before
    fun setUp() = runTest {
        store.setMasterEnabled(true)
        store.addToBlocklist(BLOCKED_PACKAGE)
    }

    @Test
    fun `first call for blocked package returns Intervene and invokes onIntervene`() = runTest {
        val decision = launcher.onForegroundApp(BLOCKED_PACKAGE)

        assertTrue("expected Intervene; got $decision", decision is Decision.Intervene)
        assertEquals(listOf(BLOCKED_PACKAGE), interveneCalls)
    }

    @Test
    fun `call for different package after blocked runs engine and does not invoke onIntervene for unblocked`() = runTest {
        launcher.onForegroundApp(BLOCKED_PACKAGE)
        interveneCalls.clear()

        val decision = launcher.onForegroundApp(UNBLOCKED_PACKAGE)

        assertEquals(Decision.Skip(SkipReason.NotInBlocklist), decision)
        assertEquals(emptyList<String>(), interveneCalls)
    }

    @Test
    fun `returning to blocked package after another package invokes onIntervene again`() = runTest {
        launcher.onForegroundApp(BLOCKED_PACKAGE)
        launcher.onForegroundApp(UNBLOCKED_PACKAGE)
        interveneCalls.clear()

        val decision = launcher.onForegroundApp(BLOCKED_PACKAGE)

        assertTrue("expected Intervene; got $decision", decision is Decision.Intervene)
        assertEquals(listOf(BLOCKED_PACKAGE), interveneCalls)
    }

    @Test
    fun `whitelisted blocked package is skipped by engine and does not invoke onIntervene`() = runTest {
        clock.set(1_000L)
        store.setWhitelistExpiry(BLOCKED_PACKAGE, untilMs = 5_000L)
        clock.set(2_000L)

        val decision = launcher.onForegroundApp(BLOCKED_PACKAGE)

        assertEquals(Decision.Skip(SkipReason.Whitelisted), decision)
        assertEquals(emptyList<String>(), interveneCalls)
    }

    @Test
    fun `whitelisted package is still recorded as last foreground so repeat is AlreadyInForeground`() = runTest {
        clock.set(1_000L)
        store.setWhitelistExpiry(BLOCKED_PACKAGE, untilMs = 5_000L)
        clock.set(2_000L)

        launcher.onForegroundApp(BLOCKED_PACKAGE)
        interveneCalls.clear()

        val decision = launcher.onForegroundApp(BLOCKED_PACKAGE)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), decision)
        assertEquals(emptyList<String>(), interveneCalls)
    }

    @Test
    fun `unblocked package does not invoke onIntervene and records last foreground`() = runTest {
        val decision = launcher.onForegroundApp(UNBLOCKED_PACKAGE)
        interveneCalls.clear()

        val repeat = launcher.onForegroundApp(UNBLOCKED_PACKAGE)

        assertEquals(Decision.Skip(SkipReason.NotInBlocklist), decision)
        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), repeat)
        assertEquals(emptyList<String>(), interveneCalls)
    }

    @Test
    fun `reopening blocked package after intervention invokes onIntervene again when no other foreground event fires`() = runTest {
        launcher.onForegroundApp(BLOCKED_PACKAGE)
        interveneCalls.clear()

        val decision = launcher.onForegroundApp(BLOCKED_PACKAGE)

        assertTrue(
            "Reopening blocked package after intervention should Intervene; got $decision",
            decision is Decision.Intervene,
        )
        assertEquals(listOf(BLOCKED_PACKAGE), interveneCalls)
    }

    @Test
    fun `in-app navigation after Continue is still filtered as AlreadyInForeground`() = runTest {
        launcher.onForegroundApp(BLOCKED_PACKAGE)
        clock.set(1_000L)
        store.setWhitelistExpiry(BLOCKED_PACKAGE, untilMs = 30_000L)
        clock.set(2_000L)

        val back = launcher.onForegroundApp(BLOCKED_PACKAGE)
        assertEquals(Decision.Skip(SkipReason.Whitelisted), back)
        interveneCalls.clear()

        val inApp = launcher.onForegroundApp(BLOCKED_PACKAGE)

        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), inApp)
        assertEquals(emptyList<String>(), interveneCalls)
    }

    private companion object {
        const val BLOCKED_PACKAGE = "com.example.blocked"
        const val UNBLOCKED_PACKAGE = "com.example.unblocked"
        const val OWN_PACKAGE = "dev.twosec.app"
    }
}
