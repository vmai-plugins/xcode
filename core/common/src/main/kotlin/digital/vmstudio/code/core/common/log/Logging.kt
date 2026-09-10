package digital.vmstudio.code.core.common.log

/** Structured log channels. Diagnostics export and log filtering both key off these. */
enum class LogCategory {
    AUTH,
    SSH,
    SFTP,
    AI,
    AGENT,
    GIT,
    CONNECTOR,
    PROJECT,
    NETWORK,
    DATABASE,
    UI,
    SECURITY,
    TERMINAL,
    EDITOR,
    TRANSFER,
}

enum class LogLevel(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
}

data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val category: LogCategory,
    val tag: String,
    val message: String,
    val throwableText: String? = null,
)

/**
 * A logging destination. Implementations must assume [entry] has already passed
 * through [SecretRedactor]; they must never re-derive text from raw inputs.
 */
interface LogSink {
    fun write(entry: LogEntry)
}
