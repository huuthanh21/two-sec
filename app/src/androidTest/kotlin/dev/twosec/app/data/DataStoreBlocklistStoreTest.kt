package dev.twosec.app.data

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreBlocklistStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun cleanup() = runBlocking {
        listOf(
            "test-master-roundtrip",
            "test-blocklist-roundtrip",
            "test-whitelist-roundtrip",
        ).forEach { name ->
            context.preferencesDataStoreFile(name).delete()
        }
    }

    @Test
    fun masterEnabled_roundTripsAcrossReopen() {
        DataStoreBlocklistStore(context, storeName = "test-master-roundtrip").run {
            runBlocking { setMasterEnabled(true) }
            close()
        }
        DataStoreBlocklistStore(context, storeName = "test-master-roundtrip").run {
            assertEquals(true, runBlocking { masterEnabled().first() })
            close()
        }
    }

    @Test
    fun blocklist_roundTripsAcrossReopen() {
        DataStoreBlocklistStore(context, storeName = "test-blocklist-roundtrip").run {
            runBlocking {
                addToBlocklist("com.example.alpha")
                addToBlocklist("com.example.beta")
            }
            close()
        }
        DataStoreBlocklistStore(context, storeName = "test-blocklist-roundtrip").run {
            assertEquals(
                setOf("com.example.alpha", "com.example.beta"),
                runBlocking { blocklist().first() },
            )
            close()
        }
    }

    @Test
    fun whitelistExpiry_roundTripsAcrossReopen() {
        DataStoreBlocklistStore(context, storeName = "test-whitelist-roundtrip").run {
            runBlocking { setWhitelistExpiry("com.example.alpha", 5_000L) }
            close()
        }
        DataStoreBlocklistStore(context, storeName = "test-whitelist-roundtrip").run {
            assertEquals(
                mapOf("com.example.alpha" to 5_000L),
                runBlocking { whitelistExpiries().first() },
            )
            close()
        }
    }
}
