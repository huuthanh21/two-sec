package dev.twosec.app.platform

import android.view.accessibility.AccessibilityEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.twosec.app.data.FakeClock
import dev.twosec.app.data.InMemoryBlocklistStore
import dev.twosec.app.domain.BlockerEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockerAccessibilityServiceIntegrationTest {

    private val store = InMemoryBlocklistStore()
    private val clock = FakeClock(initialNow = 0L)
    private val service = BlockerAccessibilityService()

    @Before
    fun setUp() = runBlocking {
        store.setMasterEnabled(true)
        store.addToBlocklist(BLOCKED_PACKAGE)
    }

    @Test
    fun onAccessibilityEvent_withNullPackageNameThenBlockedForeground_triggersIntervene() {
        val interveneCalls = mutableListOf<String>()
        service.setLauncherForTesting(launcherFor(interveneCalls))

        service.onAccessibilityEvent(windowStateChanged(BLOCKED_PACKAGE))
        interveneCalls.clear()

        service.onAccessibilityEvent(windowStateChanged(null))

        service.onAccessibilityEvent(windowStateChanged(BLOCKED_PACKAGE))

        assertEquals(listOf(BLOCKED_PACKAGE), interveneCalls)
    }

    @Test
    fun onAccessibilityEvent_withNonWindowStateChangedEvent_doesNotInvokeOnIntervene() {
        val interveneCalls = mutableListOf<String>()
        service.setLauncherForTesting(launcherFor(interveneCalls))
        service.onAccessibilityEvent(windowStateChanged(BLOCKED_PACKAGE))
        interveneCalls.clear()

        val event = AccessibilityEvent.obtain()
        event.eventType = AccessibilityEvent.TYPE_VIEW_CLICKED
        event.packageName = null
        service.onAccessibilityEvent(event)

        assertEquals(emptyList<String>(), interveneCalls)
    }

    @Test
    fun onAccessibilityEvent_withNullEvent_doesNotInvokeOnIntervene() {
        val interveneCalls = mutableListOf<String>()
        service.setLauncherForTesting(launcherFor(interveneCalls))

        service.onAccessibilityEvent(null)

        assertEquals(emptyList<String>(), interveneCalls)
    }

    private fun launcherFor(interveneCalls: MutableList<String>): InterventionLauncher {
        val engine = BlockerEngine(
            store = store,
            ignoredPackages = emptySet(),
            ownPackage = "dev.twosec.app",
        )
        return InterventionLauncher(
            engine = engine,
            clock = clock,
            onIntervene = { packageName -> interveneCalls.add(packageName) },
        )
    }

    private fun windowStateChanged(packageName: String?): AccessibilityEvent {
        val event = AccessibilityEvent.obtain()
        event.eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        event.packageName = packageName
        return event
    }

    private companion object {
        const val BLOCKED_PACKAGE = "com.example.blocked"
    }
}
