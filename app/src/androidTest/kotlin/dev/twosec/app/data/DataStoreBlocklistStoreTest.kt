package dev.twosec.app.data

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runers.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreBlocklistStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val storeNames = mutableSetOf<String>()

    @After
    fun cleanup() {
        storeNames.forEach { name ->
            context.preferencesDataStoreFile(name).delete()
        }
        storeNames.clear()
    }

    private fun newStore(name: String): DataStoreBlocklistStore {
        storeNames += name
        return DataStoreBlocklistStore(context, storeName = name)
    }

    @Test
    fun masterEnabled_roundTripsAcrossReopen() = runTest {
        val name = "test-master-roundtrip"
        val storeA = newStore(name)
        storeA.masterEnabled().test {
            assertEquals(false, awaitItem())
            storeA.setMasterEnabled(true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val storeB = newStore(name)
        assertEquals(true, storeB.masterEnabled().first())
    }

    @Test
    fun blocklist_roundTripsAcrossReopen() = runTest {
        val name = "test-blocklist-roundtrip"
        val storeA = newStore(name)
        storeA.addToBlocklist("com.example.alpha")
        storeA.addToBlocklist("com.example.beta")
        assertEquals(
            setOf("com.example.alpha", "com.example.beta"),
            storeA.blocklist().first(),
        )

        val storeB = newStore(name)
        assertEquals(
            setOf("com.example.alpha", "com.example.beta"),
            storeB.blocklist().first(),
        )
    }

    @Test
    fun whitelistExpiry_roundTripsAcrossReopen() = runTest {
        val name = "test-whitelist-roundtrip"
        val storeA = newStore(name)
        storeA.setWhitelistExpiry("com.example.alpha", 5_000L)
        assertEquals(
            mapOf("com.example.alpha" to 5_000L),
            storeA.whitelistExpiries().first(),
        )

        val storeB = newStore(name)
        assertEquals(
            mapOf("com.example.alpha" to 5_000L),
            storeB.whitelistExpiries().first(),
        )
    }
}
