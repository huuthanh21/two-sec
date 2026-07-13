package dev.twosec.app

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.twosec.app.data.Clock
import dev.twosec.app.data.InMemoryBlocklistStore
import dev.twosec.app.data.SystemClock
import dev.twosec.app.domain.BlockerEngine
import dev.twosec.app.domain.Decision
import dev.twosec.app.domain.InterventionLifecycle
import dev.twosec.app.domain.SessionState
import dev.twosec.app.platform.SystemPackages
import dev.twosec.app.ui.InterventionActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterventionFlowTest {

    private val instrumentation: Instrumentation =
        InstrumentationRegistry.getInstrumentation()

    private val mainContext: Context = instrumentation.context
        .createPackageContext(
            "dev.twosec.app",
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )

    private val testContext: Context = instrumentation.context

    private val uiAutomation: UiAutomation = instrumentation.uiAutomation

    @After
    fun cleanup() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mainContext.startActivity(home)
    }

    @Test
    fun launchingBlockedTargetApp_triggersInterventionActivity() {
        val lifecycle = lifecycleFor(blocked = setOf(BLOCKED_PACKAGE))

        launchTarget()

        val decision = lifecycle.onForegroundApp(BLOCKED_PACKAGE)
        assertTrue(
            "Engine should Intervene for blocked package; got $decision",
            decision is Decision.Intervene,
        )

        startInterventionActivity(BLOCKED_PACKAGE)
        lifecycle.onInterventionShown(BLOCKED_PACKAGE)

        waitForTopPackage("dev.twosec.app", timeoutMs = 5_000L)
    }

    @Test
    fun lifecycle_returnsIntervene_andTransitionsToPendingIntervention_forBlockedPackage() {
        val lifecycle = lifecycleFor(blocked = setOf(BLOCKED_PACKAGE))

        val decision = lifecycle.onForegroundApp(BLOCKED_PACKAGE)

        assertTrue("Engine should Intervene for blocked package; got $decision", decision is Decision.Intervene)
        assertEquals(SessionState.PendingIntervention(BLOCKED_PACKAGE), lifecycle.state)
    }

    @Test
    fun lifecycle_returnsSkip_andTransitionsToInForeground_forUnblockedPackage() {
        val lifecycle = lifecycleFor(blocked = emptySet())

        val decision = lifecycle.onForegroundApp(UNBLOCKED_PACKAGE)

        assertTrue("Engine should Skip for unblocked package; got $decision", decision is Decision.Skip)
        assertEquals(SessionState.InForeground(UNBLOCKED_PACKAGE), lifecycle.state)
    }

    @Test
    fun interventionActivity_isRegisteredInMainManifest() {
        val intent = Intent().setClassName(mainContext, "dev.twosec.app.ui.InterventionActivity")
        val activities = mainContext.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue("InterventionActivity should be registered in main manifest", activities.isNotEmpty())
    }

    private fun lifecycleFor(
        blocked: Set<String>,
    ): InterventionLifecycle {
        val store = InMemoryBlocklistStore()
        runBlocking {
            store.setMasterEnabled(true)
            for (pkg in blocked) store.addToBlocklist(pkg)
        }
        val clock: Clock = SystemClock()
        val engine = BlockerEngine(
            store = store,
            ignoredPackages = SystemPackages.ignored,
            ownPackage = mainContext.packageName,
        )
        return InterventionLifecycle(
            engine = engine,
            clock = clock,
        )
    }

    private fun startInterventionActivity(packageName: String) {
        val intent = Intent(mainContext, InterventionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(InterventionActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        mainContext.startActivity(intent)
    }

    private fun launchTarget() {
        val targetIntent = Intent().setClassName(testContext, TestTargetActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        testContext.startActivity(targetIntent)
    }

    private fun waitForTopPackage(expected: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val top = currentTopPackage()
            if (top == expected) return
            Thread.sleep(200L)
        }
        val top = currentTopPackage()
        assertEquals(
            "Expected $expected to be on top, but was $top",
            expected, top,
        )
    }

    private fun currentTopPackage(): String? {
        val pfd = uiAutomation.executeShellCommand("dumpsys activity activities")
        val text = pfd.fileDescriptor.let { fd ->
            java.io.FileInputStream(fd).bufferedReader().use { it.readText() }
        }
        pfd.close()
        val match = Regex("topResumedActivity=ActivityRecord\\{[^}]+ u0 (\\S+)/").find(text)
        return match?.groupValues?.get(1)
    }

    private companion object {
        const val BLOCKED_PACKAGE = "com.example.blocked"
        const val UNBLOCKED_PACKAGE = "com.example.unblocked"
    }
}
