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
import dev.twosec.app.platform.InterventionLauncher
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
        val launcher = launcherFor(
            blocked = setOf(BLOCKED_PACKAGE),
            onIntervene = { packageName -> startInterventionActivity(packageName) },
        )

        launchTarget()

        val decision = launcher.onForegroundApp(BLOCKED_PACKAGE)
        assertTrue(
            "Engine should Intervene for blocked package; got $decision",
            decision is Decision.Intervene,
        )

        waitForTopPackage("dev.twosec.app", timeoutMs = 5_000L)
    }

    @Test
    fun launcher_returnsIntervene_andCallsOnIntervene_withBlockedPackageName() {
        var startedPackage: String? = null
        val launcher = launcherFor(
            blocked = setOf(BLOCKED_PACKAGE),
            onIntervene = { packageName -> startedPackage = packageName },
        )

        val decision = launcher.onForegroundApp(BLOCKED_PACKAGE)

        assertTrue("Engine should Intervene for blocked package; got $decision", decision is Decision.Intervene)
        assertEquals(BLOCKED_PACKAGE, startedPackage)
    }

    @Test
    fun launcher_returnsSkip_andDoesNotInvokeOnIntervene_forUnblockedPackage() {
        val launcher = launcherFor(
            blocked = emptySet(),
            onIntervene = { error("Should not be called for unblocked package") },
        )

        val decision = launcher.onForegroundApp(UNBLOCKED_PACKAGE)
        assertTrue("Engine should Skip for unblocked package; got $decision", decision is Decision.Skip)
    }

    @Test
    fun interventionActivity_isRegisteredInMainManifest() {
        val intent = Intent().setClassName(mainContext, "dev.twosec.app.ui.InterventionActivity")
        val activities = mainContext.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue("InterventionActivity should be registered in main manifest", activities.isNotEmpty())
    }

    private fun launcherFor(
        blocked: Set<String>,
        onIntervene: (String) -> Unit,
    ): InterventionLauncher {
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
        return InterventionLauncher(
            engine = engine,
            clock = clock,
            onIntervene = onIntervene,
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
