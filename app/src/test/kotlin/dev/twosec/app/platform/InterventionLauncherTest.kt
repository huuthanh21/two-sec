package dev.twosec.app.platform

import dev.twosec.app.data.Clock
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
    private val interveneCalls = mutableListOf<String>()
    private val launcher = buildLauncher(
        store = store,
        clock = clock,
        ignoredPackages = emptySet(),
        onIntervene = { interveneCalls.add(it) },
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

    @Test
    fun `ignored foreground event between blocked-app events does not break AlreadyInForeground dedup`() = runTest {
        val imeInterveneCalls = mutableListOf<String>()
        val imeLauncher = buildLauncher(
            store = store,
            clock = clock,
            ignoredPackages = setOf(IME_PACKAGE),
            onIntervene = { imeInterveneCalls.add(it) },
        )
        imeLauncher.onForegroundApp(BLOCKED_PACKAGE)
        imeInterveneCalls.clear()
        clock.set(1_000L)
        store.setWhitelistExpiry(BLOCKED_PACKAGE, untilMs = 30_000L)
        clock.set(2_000L)
        val whitelisted = imeLauncher.onForegroundApp(BLOCKED_PACKAGE)
        assertEquals(Decision.Skip(SkipReason.Whitelisted), whitelisted)

        clock.set(3_000L)
        val ime = imeLauncher.onForegroundApp(IME_PACKAGE)
        assertEquals(Decision.Skip(SkipReason.IgnoredPackage), ime)

        val inWhitelist = imeLauncher.onForegroundApp(BLOCKED_PACKAGE)
        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), inWhitelist)
        assertEquals(emptyList<String>(), imeInterveneCalls)

        clock.set(40_000L)
        val postExpiry = imeLauncher.onForegroundApp(BLOCKED_PACKAGE)
        assertEquals(Decision.Skip(SkipReason.AlreadyInForeground), postExpiry)
        assertEquals(emptyList<String>(), imeInterveneCalls)
    }

    private companion object {
        const val BLOCKED_PACKAGE = "com.example.blocked"
        const val UNBLOCKED_PACKAGE = "com.example.unblocked"
        const val IME_PACKAGE = "com.example.ime"
        const val OWN_PACKAGE = "dev.twosec.app"

        fun buildLauncher(
            store: InMemoryBlocklistStore,
            clock: Clock,
            ignoredPackages: Set<String>,
            onIntervene: (String) -> Unit,
        ): InterventionLauncher {
            val engine = BlockerEngine(
                store = store,
                ignoredPackages = ignoredPackages,
                ownPackage = OWN_PACKAGE,
            )
            return InterventionLauncher(
                engine = engine,
                clock = clock,
                onIntervene = onIntervene,
            )
        }
    }
}
