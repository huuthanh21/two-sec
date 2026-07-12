package dev.twosec.app.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryBlocklistStoreWhitelistTest {

    @Test
    fun `whitelistExpiries defaults to empty map`() = runTest {
        val store = InMemoryBlocklistStore()
        store.whitelistExpiries().test {
            assertEquals(emptyMap<String, Long>(), awaitItem())
        }
    }

    @Test
    fun `setWhitelistExpiry then read returns the entry`() = runTest {
        val store = InMemoryBlocklistStore()
        store.setWhitelistExpiry("com.example.alpha", 1_000L)
        store.whitelistExpiries().test {
            assertEquals(mapOf("com.example.alpha" to 1_000L), awaitItem())
        }
    }

    @Test
    fun `setWhitelistExpiry twice on different packages keeps both`() = runTest {
        val store = InMemoryBlocklistStore()
        store.setWhitelistExpiry("com.example.alpha", 1_000L)
        store.setWhitelistExpiry("com.example.beta", 2_000L)
        store.whitelistExpiries().test {
            assertEquals(
                mapOf("com.example.alpha" to 1_000L, "com.example.beta" to 2_000L),
                awaitItem(),
            )
        }
    }

    @Test
    fun `setWhitelistExpiry twice on the same package overwrites the timestamp`() = runTest {
        val store = InMemoryBlocklistStore()
        store.setWhitelistExpiry("com.example.alpha", 1_000L)
        store.setWhitelistExpiry("com.example.alpha", 2_500L)
        store.whitelistExpiries().test {
            assertEquals(mapOf("com.example.alpha" to 2_500L), awaitItem())
        }
    }

    @Test
    fun `clearExpiredWhitelist drops only entries with expiry at or before now`() = runTest {
        val store = InMemoryBlocklistStore()
        store.setWhitelistExpiry("com.example.expired", 500L)
        store.setWhitelistExpiry("com.example.active", 2_000L)
        store.clearExpiredWhitelist(1_000L)
        store.whitelistExpiries().test {
            val result = awaitItem()
            assertEquals(setOf("com.example.active"), result.keys)
            assertEquals(2_000L, result["com.example.active"])
        }
    }

    @Test
    fun `clearExpiredWhitelist with now=0 keeps entries with positive expiry`() = runTest {
        val store = InMemoryBlocklistStore()
        store.setWhitelistExpiry("com.example.alpha", 1L)
        store.clearExpiredWhitelist(0L)
        store.whitelistExpiries().test {
            assertTrue(awaitItem().containsKey("com.example.alpha"))
        }
    }
}
