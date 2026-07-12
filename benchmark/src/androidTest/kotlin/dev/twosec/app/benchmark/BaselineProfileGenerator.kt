package dev.twosec.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = "dev.twosec.app",
            maxIterations = 5,
            stableIterations = 3,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }
}


