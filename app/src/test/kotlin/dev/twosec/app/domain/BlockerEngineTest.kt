package dev.twosec.app.domain

import dev.twosec.app.data.FakeClock
import dev.twosec.app.data.InMemoryBlocklistStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockerEngineTest {

    private data class DecideCase(
        val masterOn: Boolean,
        val inBlocklist: Boolean,
        val whitelisted: Boolean,
        val inIgnoreSet: Boolean,
        val isOwnPackage: Boolean,
        val expected: Decision,
    )

    @Test
    fun `decide truth table across all 32 combinations`() = runTest {
        val cases = listOf(
            DecideCase(false, false, false, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(false, false, false, true, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(false, false, true, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(false, false, true, true, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(false, true, false, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(false, true, false, true, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(false, true, true, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(false, true, true, true, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, false, false, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, false, false, true, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, false, true, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, false, true, true, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, true, false, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, true, false, true, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, true, true, false, true, Decision.Skip(SkipReason.OwnPackage)),
            DecideCase(true, true, true, true, true, Decision.Skip(SkipReason.OwnPackage)),

            DecideCase(false, false, false, true, false, Decision.Skip(SkipReason.IgnoredPackage)),
            DecideCase(false, false, true, true, false, Decision.Skip(SkipReason.IgnoredPackage)),
            DecideCase(false, true, false, true, false, Decision.Skip(SkipReason.IgnoredPackage)),
            DecideCase(false, true, true, true, false, Decision.Skip(SkipReason.IgnoredPackage)),
            DecideCase(true, false, false, true, false, Decision.Skip(SkipReason.IgnoredPackage)),
            DecideCase(true, false, true, true, false, Decision.Skip(SkipReason.IgnoredPackage)),
            DecideCase(true, true, false, true, false, Decision.Skip(SkipReason.IgnoredPackage)),
            DecideCase(true, true, true, true, false, Decision.Skip(SkipReason.IgnoredPackage)),

            DecideCase(false, false, false, false, false, Decision.Skip(SkipReason.MasterOff)),
            DecideCase(false, false, true, false, false, Decision.Skip(SkipReason.MasterOff)),
            DecideCase(false, true, false, false, false, Decision.Skip(SkipReason.MasterOff)),
            DecideCase(false, true, true, false, false, Decision.Skip(SkipReason.MasterOff)),

            DecideCase(true, false, false, false, false, Decision.Skip(SkipReason.NotInBlocklist)),
            DecideCase(true, false, true, false, false, Decision.Skip(SkipReason.NotInBlocklist)),

            DecideCase(true, true, true, false, false, Decision.Skip(SkipReason.Whitelisted)),

            DecideCase(true, true, false, false, false, Decision.Intervene),
        )

        assertEquals(32, cases.size)

        for (case in cases) {
            val store = InMemoryBlocklistStore()
            if (case.masterOn) store.setMasterEnabled(true)
            if (case.inBlocklist) store.addToBlocklist(TARGET)
            if (case.whitelisted) store.setWhitelistExpiry(TARGET, EXPIRY)

            val ignoredPackages = if (case.inIgnoreSet) setOf(TARGET) else emptySet()
            val ownPackage = if (case.isOwnPackage) TARGET else OTHER

            val engine = BlockerEngine(store, ignoredPackages, ownPackage)

            assertEquals(
                "case masterOn=${case.masterOn} inBlocklist=${case.inBlocklist} " +
                    "whitelisted=${case.whitelisted} inIgnoreSet=${case.inIgnoreSet} " +
                    "isOwnPackage=${case.isOwnPackage}",
                case.expected,
                engine.decide(TARGET, NOW),
            )
        }
    }

    private companion object {
        const val TARGET = "com.example.target"
        const val OTHER = "com.example.other"
        const val NOW = 5_000L
        const val EXPIRY = 10_000L
    }
}
