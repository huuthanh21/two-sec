package dev.twosec.app.data

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class LogSharerTest {

    private class CapturingFactory : ShareIntentFactory {
        var action: String? = null
        var mimeType: String? = null
        var uris: List<String>? = null
        var flags: Int = -1
        var callCount: Int = 0

        override fun create(action: String, mimeType: String, streamUris: List<String>, flags: Int): Intent {
            this.action = action
            this.mimeType = mimeType
            this.uris = streamUris
            this.flags = flags
            this.callCount += 1
            return Intent(action)
        }
    }

    @Test
    fun `buildShareIntent passes SEND_MULTIPLE action to the factory`() {
        val factory = CapturingFactory()
        LogSharer.buildShareIntent(listOf("content://a"), factory)
        assertEquals("android.intent.action.SEND_MULTIPLE", factory.action)
    }

    @Test
    fun `buildShareIntent passes text plain mime type to the factory`() {
        val factory = CapturingFactory()
        LogSharer.buildShareIntent(listOf("content://a"), factory)
        assertEquals("text/plain", factory.mimeType)
    }

    @Test
    fun `buildShareIntent grants read uri permission flag`() {
        val factory = CapturingFactory()
        LogSharer.buildShareIntent(listOf("content://a"), factory)
        assertEquals(0x00000001, factory.flags)
    }

    @Test
    fun `buildShareIntent preserves uris in order`() {
        val factory = CapturingFactory()
        val uris = listOf(
            "content://dev.twosec.app.logfileprovider/logs/two-sec.log",
            "content://dev.twosec.app.logfileprovider/logs/two-sec.log.1",
        )
        LogSharer.buildShareIntent(uris, factory)
        assertEquals(uris, factory.uris)
    }

    @Test
    fun `buildShareIntent calls the factory exactly once`() {
        val factory = CapturingFactory()
        LogSharer.buildShareIntent(listOf("content://a", "content://b"), factory)
        assertEquals(1, factory.callCount)
    }

    @Test
    fun `buildShareIntent with empty uris still delegates`() {
        val factory = CapturingFactory()
        LogSharer.buildShareIntent(emptyList(), factory)
        assertEquals(emptyList<String>(), factory.uris)
    }
}
