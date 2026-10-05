package digital.vmstudio.code.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash so the user can hand it over.
 *
 * A crash on a phone used to leave nothing behind: no logcat access, no report,
 * just "the app closed". The handler writes the stack trace and the tail of the
 * (already redacted) diagnostics log to app-private storage, then lets the
 * platform's own handler finish the crash as before. The next launch offers it.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_TRACE_CHARS = 12_000
    private const val MAX_LOG_CHARS = 6_000

    fun install(context: Context, versionName: String, recentLogs: () -> String) {
        val file = File(context.filesDir, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Never let the reporter itself mask the original crash.
            runCatching {
                val logs = runCatching(recentLogs).getOrDefault("")
                file.writeText(
                    report(
                        versionName = versionName,
                        device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
                            "(SDK ${Build.VERSION.SDK_INT})",
                        threadName = thread.name,
                        error = error,
                        timeMillis = System.currentTimeMillis(),
                        recentLogs = logs,
                    ),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report, or null when the last run did not crash. */
    fun pending(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    @Suppress("LongParameterList")
    internal fun report(
        versionName: String,
        device: String,
        threadName: String,
        error: Throwable,
        timeMillis: Long,
        recentLogs: String,
    ): String = buildString {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(timeMillis))
        appendLine("X-Codes crash report")
        appendLine("App: $versionName")
        appendLine("Device: $device")
        appendLine("Time: $time")
        appendLine("Thread: $threadName")
        appendLine()
        appendLine(error.stackTraceToString().take(MAX_TRACE_CHARS))
        if (recentLogs.isNotBlank()) {
            appendLine("Recent log (secrets removed):")
            appendLine(recentLogs.takeLast(MAX_LOG_CHARS))
        }
    }
}
