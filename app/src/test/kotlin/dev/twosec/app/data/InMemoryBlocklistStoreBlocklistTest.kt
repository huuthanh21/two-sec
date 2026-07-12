package dev.twosec.app.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryBlocklistStoreBlocklistTest {

    @Test
    fun `blocklist defaults to empty set`() = runTest {
        val store = InMemoryBlocklistStore()
        store.blocklist().test {
            assertEquals(emptySet<String>(), awaitItem())
        }
    }

    @Test
    fun `addToBlocklist then read returns the package`() = runTest {
        val store = InMemoryBlocklistStore()
        store.blocklist().test {
            assertEquals(emptySet<String>(), awaitItem())
            store.addToBlocklist("com.example.alpha")
            assertEquals(setOf("com.example.alpha"), awaitItem())
        }
    }

    @Test
    fun `addToBlocklist with multiple packages returns all`() = runTest {
        val store = InMemoryBlocklistStore()
        store.addToBlocklist("com.example.alpha")
        store.addToBlocklist("com.example.beta")
        store.blocklist().test {
            assertEquals(setOf("com.example.alpha", "com.example.beta"), awaitItem())
        }
    }

    @Test
    fun `addToBlocklist twice with same package is idempotent`() = runTest {
        val store = InMemoryBlocklistStore()
        store.addToBlocklist("com.example.alpha")
        store.addToBlocklist("com.example.alpha")
        store.blocklist().test {
            assertEquals(setOf("com.example.alpha"), awaitItem())
        }
    }

    @Test
    fun `removeFromBlocklist drops the package`() = runTest {
        val store = InMemoryBlocklistStore()
        store.addToBlocklist("com.example.alpha")
        store.addToBlocklist("com.example.beta")
        store.removeFromBlocklist("com.example.alpha")
        store.blocklist().test {
            assertEquals(setOf("com.example.beta"), awaitItem())
        }
    }

    @Test
    fun `removeFromBlocklist of missing package is a no-op`() = runTest {
        val store = InMemoryBlocklistStore()
        store.addToBlocklist("com.example.alpha")
        store.removeFromBlocklist("com.example.beta")
        store.blocklist().test {
            assertEquals(setOf("com.example.alpha"), awaitItem())
        }
    }
}
