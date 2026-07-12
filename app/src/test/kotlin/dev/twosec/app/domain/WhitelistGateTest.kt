package dev.twosec.app.domain

import dev.twosec.app.data.InMemoryBlocklistStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistGateTest {

    @Test
    fun `setWhitelist then isWhitelisted returns true within window`() = runTest {
        val gate = WhitelistGate(InMemoryBlocklistStore())

        gate.setWhitelist("com.example.alpha", 10_000L)

        assertTrue(gate.isWhitelisted("com.example.alpha", 5_000L))
    }

    @Test
    fun `isWhitelisted returns false at and after expiry`() = runTest {
        val gate = WhitelistGate(InMemoryBlocklistStore())

        gate.setWhitelist("com.example.alpha", 10_000L)

        assertFalse(gate.isWhitelisted("com.example.alpha", 10_000L))
        assertFalse(gate.isWhitelisted("com.example.alpha", 15_000L))
    }

    @Test
    fun `isWhitelisted returns false for a package that was never whitelisted`() = runTest {
        val gate = WhitelistGate(InMemoryBlocklistStore())

        assertFalse(gate.isWhitelisted("com.example.alpha", 0L))
    }

    @Test
    fun `clearExpired removes only the expired entries`() = runTest {
        val gate = WhitelistGate(InMemoryBlocklistStore())

        gate.setWhitelist("com.example.expired", 5_000L)
        gate.setWhitelist("com.example.active", 15_000L)

        gate.clearExpired(10_000L)

        assertFalse(gate.isWhitelisted("com.example.expired", 10_000L))
        assertTrue(gate.isWhitelisted("com.example.active", 10_000L))
    }

    @Test
    fun `distinct packages have independent windows`() = runTest {
        val gate = WhitelistGate(InMemoryBlocklistStore())

        gate.setWhitelist("com.example.alpha", 10_000L)
        gate.setWhitelist("com.example.beta", 20_000L)

        assertTrue(gate.isWhitelisted("com.example.alpha", 5_000L))
        assertTrue(gate.isWhitelisted("com.example.beta", 5_000L))
        assertFalse(gate.isWhitelisted("com.example.alpha", 15_000L))
        assertTrue(gate.isWhitelisted("com.example.beta", 15_000L))
        assertFalse(gate.isWhitelisted("com.example.beta", 20_000L))
    }
}
