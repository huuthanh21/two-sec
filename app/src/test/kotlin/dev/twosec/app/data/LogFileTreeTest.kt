package dev.twosec.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.util.TimeZone

class LogFileTreeTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val fixedNowMs: Long = 1_700_000_000_000L
    private val expectedTimestamp: String

    init {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        expectedTimestamp = fmt.format(java.util.Date(fixedNowMs))
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `first write creates the logs directory and current log file`() {
        val tree = plantInTemp()

        Timber.i("hello")
        tree.flush()

        assertTrue(tree.currentFile.exists())
        assertTrue(
            "expected file to end with the hello message: ${tree.currentFile.readText()}",
            tree.currentFile.readText().trimEnd().endsWith("hello"),
        )
    }

    @Test
    fun `log line has ISO timestamp level tag and message in that order`() {
        val tree = plantInTemp()

        Timber.tag("TwoSec").i("ready")
        tree.flush()

        val line = tree.currentFile.readText().trim()
        val match = LINE_REGEX.matchEntire(line)
        assertTrue("line did not match format: $line", match != null)
        val (timestamp, level, tag, message) = match!!.destructured
        assertEquals(expectedTimestamp, timestamp)
        assertEquals("I", level)
        assertEquals("TwoSec", tag)
        assertEquals("ready", message)
    }

    @Test
    fun `write past maxBytes rotates current to backup and starts a fresh current`() {
        val logsDir = tempFolder.newFolder("logs")
        val tree = LogFileTree(logsDir, clock = FakeClock(fixedNowMs), maxBytes = 64L)
        Timber.plant(tree)

        Timber.tag("T").i("first")
        tree.flush()
        assertTrue(
            "expected current to contain first message: ${tree.currentFile.readText()}",
            tree.currentFile.readText().contains("first"),
        )

        Timber.tag("T").i("second line that pushes us over the cap")
        tree.flush()

        assertTrue("backup should exist after rotation", tree.backupFile.exists())
        assertTrue("current should exist after rotation", tree.currentFile.exists())
        assertTrue(
            "backup should contain first message: ${tree.backupFile.readText()}",
            tree.backupFile.readText().contains("first"),
        )
        val newCurrent = tree.currentFile.readText()
        assertTrue("current should contain second line: $newCurrent", newCurrent.contains("second line that pushes us over the cap"))
        assertFalse("current should not contain the first line: $newCurrent", newCurrent.contains("first\n"))
    }

    @Test
    fun `consecutive small writes accumulate into the same file without rotating`() {
        val tree = plantInTemp(maxBytes = 10_000L)

        repeat(10) { i -> Timber.tag("T").i("m$i") }
        tree.flush()

        assertFalse("backup should not exist with no rotation", tree.backupFile.exists())
        val content = tree.currentFile.readText()
        val lines = content.lines().filter { it.isNotEmpty() }
        assertEquals(10, lines.size)
        assertTrue(lines.first().contains("m0"))
        assertTrue(lines.last().contains("m9"))
    }

    @Test
    fun `log with a throwable appends the stack trace after the message`() {
        val tree = plantInTemp()

        Timber.tag("T").e(RuntimeException("nope"), "boom")
        tree.flush()

        val content = tree.currentFile.readText()
        assertTrue("expected boom in content: $content", content.contains("boom"))
        assertTrue("expected RuntimeException in stack: $content", content.contains("RuntimeException"))
        assertTrue("expected stack frame in content: $content", content.contains("at "))
    }

    @Test
    fun `rotation triggers based on UTF-8 byte count not UTF-16 char count`() {
        val logsDir = tempFolder.newFolder("logs")
        val tree = LogFileTree(logsDir, clock = FakeClock(fixedNowMs), maxBytes = 100L)
        Timber.plant(tree)

        val emoji = "\uD83D\uDE00".repeat(10)
        Timber.tag("T").i(emoji)
        tree.flush()
        Timber.tag("T").i(emoji)
        tree.flush()

        assertTrue("backup should exist after rotation", tree.backupFile.exists())
        assertTrue(
            "current file should be at or below the cap, was ${tree.currentFile.length()}",
            tree.currentFile.length() <= 100L,
        )
    }

    @Test
    fun `write that would exceed maxBytes still succeeds even for a single long line`() {
        val logsDir = tempFolder.newFolder("logs")
        val tree = LogFileTree(logsDir, clock = FakeClock(fixedNowMs), maxBytes = 16L)
        Timber.plant(tree)

        val longMessage = "X".repeat(200)
        Timber.tag("T").i(longMessage)
        tree.flush()

        assertTrue(tree.currentFile.exists())
        assertTrue(tree.currentFile.readText().contains(longMessage))
    }

    private fun plantInTemp(maxBytes: Long = LogFileTree.DEFAULT_MAX_BYTES): LogFileTree {
        val logsDir = tempFolder.newFolder("logs")
        val tree = LogFileTree(logsDir, clock = FakeClock(fixedNowMs), maxBytes = maxBytes)
        Timber.plant(tree)
        return tree
    }

    private companion object {
        val LINE_REGEX: Regex = Regex("""^(\S+) \[(\w)] \[([^]]+)] (.+)$""")
    }
}
