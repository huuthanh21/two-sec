package dev.twosec.app.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryBlocklistStoreMasterTest {

    @Test
    fun `masterEnabled defaults to false`() = runTest {
        val store = InMemoryBlocklistStore()
        store.masterEnabled().test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `setMasterEnabled true then read returns true`() = runTest {
        val store = InMemoryBlocklistStore()
        store.masterEnabled().test {
            assertEquals(false, awaitItem())
            store.setMasterEnabled(true)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `setMasterEnabled false after true returns false`() = runTest {
        val store = InMemoryBlocklistStore()
        store.setMasterEnabled(true)
        store.masterEnabled().test {
            assertEquals(true, awaitItem())
            store.setMasterEnabled(false)
            assertEquals(false, awaitItem())
        }
    }
}
