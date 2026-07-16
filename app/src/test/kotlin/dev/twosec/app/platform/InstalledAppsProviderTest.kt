package dev.twosec.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class InstalledAppsProviderTest {

    private class TestProvider(
        val provider: InstalledAppsProvider,
        val fetchLaunchableCount: AtomicInteger,
        val fetchAllLaunchableCount: AtomicInteger,
        val fetchHomeCount: AtomicInteger,
        val fetchImeCount: AtomicInteger
    )

    private fun createTestProvider(
        ownPackage: String = "dev.twosec.app",
        launchableApp: String = "com.example.app",
        systemLaunchableApp: String = "com.android.settings",
        launcherApp: String = "com.example.launcher",
        imeApp: String = "com.example.ime",
        fetchDelayMs: Long = 0L
    ): TestProvider {
        val fetchLaunchableCount = AtomicInteger(0)
        val fetchAllLaunchableCount = AtomicInteger(0)
        val fetchHomeCount = AtomicInteger(0)
        val fetchImeCount = AtomicInteger(0)

        val provider = InstalledAppsProvider(
            ownPackageName = ownPackage,
            fetchLaunchableApps = {
                if (fetchDelayMs > 0) Thread.sleep(fetchDelayMs)
                fetchLaunchableCount.incrementAndGet()
                listOf(InstalledApp(launchableApp, "App"))
            },
            fetchAllLaunchablePackages = {
                if (fetchDelayMs > 0) Thread.sleep(fetchDelayMs)
                fetchAllLaunchableCount.incrementAndGet()
                setOf(launchableApp, systemLaunchableApp)
            },
            fetchHomePackages = {
                if (fetchDelayMs > 0) Thread.sleep(fetchDelayMs)
                fetchHomeCount.incrementAndGet()
                setOf(launcherApp)
            },
            fetchEnabledImePackages = {
                if (fetchDelayMs > 0) Thread.sleep(fetchDelayMs)
                fetchImeCount.incrementAndGet()
                setOf(imeApp)
            }
        )

        return TestProvider(
            provider = provider,
            fetchLaunchableCount = fetchLaunchableCount,
            fetchAllLaunchableCount = fetchAllLaunchableCount,
            fetchHomeCount = fetchHomeCount,
            fetchImeCount = fetchImeCount
        )
    }

    @Test
    fun `isUserFacing correctly identifies own package, launcher packages, and launchable apps including system ones`() {
        val ownPackage = "dev.twosec.app"
        val launchableApp = "com.example.app"
        val systemLaunchableApp = "com.android.settings"
        val launcherApp = "com.example.launcher"
        val imeApp = "com.example.ime"
        val normalApp = "com.example.other"

        val testProvider = createTestProvider(
            ownPackage = ownPackage,
            launchableApp = launchableApp,
            systemLaunchableApp = systemLaunchableApp,
            launcherApp = launcherApp,
            imeApp = imeApp
        )
        val provider = testProvider.provider

        // Own package is user-facing
        assertTrue(provider.isUserFacing(ownPackage))

        // Launcher package is user-facing
        assertTrue(provider.isUserFacing(launcherApp))

        // Non-system launchable app is user-facing
        assertTrue(provider.isUserFacing(launchableApp))

        // System launchable app is user-facing
        assertTrue(provider.isUserFacing(systemLaunchableApp))

        // IME is NOT user-facing per instructions
        assertFalse(provider.isUserFacing(imeApp))

        // Other non-matching apps are NOT user-facing
        assertFalse(provider.isUserFacing(normalApp))
    }

    @Test
    fun `caching mechanism avoids multiple fetcher invocations`() {
        val testProvider = createTestProvider()
        val provider = testProvider.provider

        // Initial queries
        provider.listUserLaunchableApps()
        provider.getHomePackages()
        provider.getEnabledImePackages()
        provider.isUserFacing("com.example.app")

        assertEquals(1, testProvider.fetchLaunchableCount.get())
        assertEquals(1, testProvider.fetchAllLaunchableCount.get())
        assertEquals(1, testProvider.fetchHomeCount.get())
        assertEquals(1, testProvider.fetchImeCount.get())

        // Repeated queries should hit cache
        provider.listUserLaunchableApps()
        provider.getHomePackages()
        provider.getEnabledImePackages()
        provider.isUserFacing("com.example.app")

        assertEquals(1, testProvider.fetchLaunchableCount.get())
        assertEquals(1, testProvider.fetchAllLaunchableCount.get())
        assertEquals(1, testProvider.fetchHomeCount.get())
        assertEquals(1, testProvider.fetchImeCount.get())
    }

    @Test
    fun `refresh invalidates caches`() {
        val testProvider = createTestProvider()
        val provider = testProvider.provider

        provider.listUserLaunchableApps()
        provider.getHomePackages()
        provider.getEnabledImePackages()
        provider.isUserFacing("com.example.app")

        assertEquals(1, testProvider.fetchLaunchableCount.get())
        assertEquals(1, testProvider.fetchAllLaunchableCount.get())
        assertEquals(1, testProvider.fetchHomeCount.get())
        assertEquals(1, testProvider.fetchImeCount.get())

        // Invalidate cache
        provider.refresh()

        // Queries after refresh should invoke fetchers again
        provider.listUserLaunchableApps()
        provider.getHomePackages()
        provider.getEnabledImePackages()
        provider.isUserFacing("com.example.app")

        assertEquals(2, testProvider.fetchLaunchableCount.get())
        assertEquals(2, testProvider.fetchAllLaunchableCount.get())
        assertEquals(2, testProvider.fetchHomeCount.get())
        assertEquals(2, testProvider.fetchImeCount.get())
    }

    @Test
    fun `concurrent queries are thread-safe and only invoke fetchers once`() {
        val testProvider = createTestProvider(fetchDelayMs = 10L)
        val provider = testProvider.provider

        val threadsCount = 10
        val latch = CountDownLatch(threadsCount)

        val threads = List(threadsCount) {
            thread(start = false) {
                provider.isUserFacing("com.example.app")
                provider.getEnabledImePackages()
                latch.countDown()
            }
        }

        threads.forEach { it.start() }
        val finished = latch.await(2, TimeUnit.SECONDS)

        assertTrue("Timed out waiting for concurrent threads to finish", finished)
        assertEquals(1, testProvider.fetchAllLaunchableCount.get())
        assertEquals(1, testProvider.fetchHomeCount.get())
        assertEquals(1, testProvider.fetchImeCount.get())
    }
}
