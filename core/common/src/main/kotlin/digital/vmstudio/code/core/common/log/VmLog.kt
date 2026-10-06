package digital.vmstudio.code.core.common.log

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide structured logger.
 *
 * Every message is routed through [SecretRedactor] before it reaches any sink, so
 * a careless `logger.d(SSH, "auth", "password=$password")` still cannot leak. Sinks
 * receive already-redacted entries and must not be given raw input.
 */
object VmLog {

    @Volatile
    private var minimumLevel: LogLevel = LogLevel.DEBUG

    private val sinks = mutableListOf<LogSink>()
    private val lock = Any()

    fun configure(minimumLevel: LogLevel) {
        this.minimumLevel = minimumLevel
    }

    fun addSink(sink: LogSink) = synchronized(lock) {
        if (sinks.none { it === sink }) sinks.add(sink)
    }

    fun removeSink(sink: LogSink) = synchronized(lock) {
        sinks.removeAll { it === sink }
    }

    fun v(category: LogCategory, tag: String, message: String) =
        log(LogLevel.VERBOSE, category, tag, message, null)

    fun d(category: LogCategory, tag: String, message: String) =
        log(LogLevel.DEBUG, category, tag, message, null)

    fun i(category: LogCategory, tag: String, message: String) =
        log(LogLevel.INFO, category, tag, message, null)

    fun w(category: LogCategory, tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, category, tag, message, throwable)

    fun e(category: LogCategory, tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, category, tag, message, throwable)

    private fun log(
        level: LogLevel,
        category: LogCategory,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (level.priority < minimumLevel.priority) return
        val entry = LogEntry(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            category = category,
            tag = tag,
            message = SecretRedactor.redact(message),
            throwableText = SecretRedactor.redactThrowable(throwable),
        )
        val snapshot = synchronized(lock) { sinks.toList() }
        snapshot.forEach { sink ->
            runCatching { sink.write(entry) }
        }
    }
}

/** Writes to logcat. Registered in debug builds only. */
class LogcatSink : LogSink {
    override fun write(entry: LogEntry) {
        val tag = "VM/${entry.category.name}"
        val text = "${entry.tag}: ${entry.message}" +
            (entry.throwableText?.let { "\n$it" } ?: "")
        when (entry.level) {
            LogLevel.VERBOSE -> Log.v(tag, text)
            LogLevel.DEBUG -> Log.d(tag, text)
            LogLevel.INFO -> Log.i(tag, text)
            LogLevel.WARN -> Log.w(tag, text)
            LogLevel.ERROR -> Log.e(tag, text)
        }
    }
}

/**
 * Bounded in-memory ring buffer backing the Diagnostics screen and its export.
 *
 * Bounded on purpose: an SSH session can emit thousands of lines a second, and an
 * unbounded buffer would be an OOM waiting to happen on a phone.
 */
@Singleton
class DiagnosticsLogSink @Inject constructor() : LogSink {

    private val buffer = ArrayDeque<LogEntry>(CAPACITY)
    private val lock = Any()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Live entries, filled from the buffer when collection starts. */
    val entries: Flow<List<LogEntry>> = _entries.onSubscription {
        _entries.value = synchronized(lock) { buffer.toList() }
    }

    override fun write(entry: LogEntry) {
        // Copying the whole buffer on every line is only worth it while someone
        // watches; otherwise each log call allocated a 2,000-entry list.
        val watched = _entries.subscriptionCount.value > 0
        val snapshot = synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
            if (watched) buffer.toList() else null
        }
        snapshot?.let { _entries.value = it }
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        _entries.value = emptyList()
    }

    /**
     * Renders the buffer as text for the user to share. Contents are already
     * redacted at write time, so no additional scrubbing is required here.
     */
    fun exportAsText(categories: Set<LogCategory>? = null): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val snapshot = synchronized(lock) { buffer.toList() }
        return buildString {
            appendLine("VMStudio Code diagnostics export")
            appendLine("Generated: ${formatter.format(Date())}")
            appendLine("Entries: ${snapshot.size}")
            appendLine("Secrets are removed at capture time; this file contains no credentials.")
            appendLine("-".repeat(72))
            snapshot
                .filter { categories == null || it.category in categories }
                .forEach { entry ->
                    append(formatter.format(Date(entry.timestampMillis)))
                    append(' ').append(entry.level.name.first())
                    append(" [").append(entry.category.name).append("] ")
                    append(entry.tag).append(": ").append(entry.message)
                    appendLine()
                    entry.throwableText?.let { appendLine(it.prependIndent("    ")) }
                }
        }
    }

    private companion object {
        const val CAPACITY = 2_000
    }
}
