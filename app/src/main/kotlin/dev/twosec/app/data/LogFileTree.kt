package dev.twosec.app.data

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import timber.log.Timber

class LogFileTree(
    private val logsDir: File,
    private val clock: Clock = SystemClock(),
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : Timber.Tree() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "two-sec-log-writer").apply { isDaemon = true }
    }

    private val isoFormatter: SimpleDateFormat = SimpleDateFormat(ISO_PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    val currentFile: File get() = File(logsDir, CURRENT_NAME)
    val backupFile: File get() = File(logsDir, BACKUP_NAME)

    internal fun flush(timeoutMs: Long = 5_000L) {
        val latch = CountDownLatch(1)
        executor.execute { latch.countDown() }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val line = formatLine(priority, tag, message, t)
        executor.execute { writeLine(line) }
    }

    private fun writeLine(line: String) {
        try {
            if (!logsDir.exists() && !logsDir.mkdirs()) return
            val target = currentFile
            val lineBytes = line.toByteArray(Charsets.UTF_8).size + 1L
            if (target.exists() && target.length() + lineBytes > maxBytes) {
                rotate()
            }
            FileWriter(currentFile, true).use { writer ->
                writer.append(line)
                writer.append('\n')
            }
        } catch (e: Exception) {
            Log.e("LogFileTree", "write failed", e)
        }
    }

    private fun rotate() {
        backupFile.delete()
        currentFile.renameTo(backupFile)
    }

    private fun formatLine(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ): String {
        val timestamp = isoFormatter.format(Date(clock.now()))
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
        val safeTag = tag ?: "-"
        val safeMessage = if (t == null) message else "$message\n${t.stackTraceToString()}"
        return "$timestamp [$level] [$safeTag] $safeMessage"
    }

    companion object {
        const val CURRENT_NAME: String = "two-sec.log"
        const val BACKUP_NAME: String = "two-sec.log.1"
        const val DEFAULT_MAX_BYTES: Long = 1_000_000L
        private const val ISO_PATTERN: String = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    }
}
