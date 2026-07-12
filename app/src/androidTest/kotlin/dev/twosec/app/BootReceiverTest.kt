package dev.twosec.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.twosec.app.platform.BootReceiver
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val serviceComponent = ComponentName(context, "dev.twosec.app.platform.BootReceiver")

    @Test
    fun bootReceiver_isRegisteredForBootCompleted() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        val matches = receivers.any { it.activityInfo.name == "dev.twosec.app.platform.BootReceiver" }
        assertTrue("BootReceiver should be registered for BOOT_COMPLETED", matches)
    }

    @Test
    fun bootReceiver_isRegisteredForLockedBootCompleted() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        val matches = receivers.any { it.activityInfo.name == "dev.twosec.app.platform.BootReceiver" }
        assertTrue("BootReceiver should be registered for LOCKED_BOOT_COMPLETED", matches)
    }

    @Test
    fun bootReceiver_onBootCompleted_keepsServiceComponentEnabled() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val state = context.packageManager.getComponentEnabledSetting(serviceComponent)
        assertTrue(
            "Service component should be enabled after BOOT_COMPLETED; was $state",
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
    }

    @Test
    fun bootReceiver_onLockedBootCompleted_keepsServiceComponentEnabled() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        val state = context.packageManager.getComponentEnabledSetting(serviceComponent)
        assertTrue(
            "Service component should be enabled after LOCKED_BOOT_COMPLETED; was $state",
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
    }
}
