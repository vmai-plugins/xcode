package digital.vmstudio.code.core.ssh.command

/**
 * Outcome of a non-interactive remote command.
 *
 * [exitCode] is nullable on purpose: a command killed by a signal never reports
 * one, and treating that as exit 0 would make a killed build look successful.
 */
data class CommandResult(
    val command: String,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val durationMillis: Long,
    /** Set when the command was terminated by a signal rather than exiting. */
    val terminatingSignal: String? = null,
    /** True when output was cut off at the configured limit. */
    val outputTruncated: Boolean = false,
) {
    val isSuccess: Boolean get() = exitCode == 0

    /** Combined output for display, with stderr marked so it is distinguishable. */
    fun combinedOutput(): String = buildString {
        if (stdout.isNotEmpty()) append(stdout)
        if (stderr.isNotEmpty()) {
            if (isNotEmpty() && !endsWith('\n')) append('\n')
            append(stderr)
        }
    }
}

/**
 * One piece of output from a still-running command.
 *
 * Line-oriented rather than byte-oriented because the consumers that need streaming
 * are line-delimited protocols: the Claude Code CLI's `stream-json`, build output,
 * and log tails. A byte stream would push reassembly onto every caller.
 */
sealed interface CommandOutputChunk {

    data class Stdout(val line: String) : CommandOutputChunk

    data class Stderr(val line: String) : CommandOutputChunk

    /** Terminal event. [exitCode] is null when the process was killed by a signal. */
    data class Exited(
        val exitCode: Int?,
        val signal: String? = null,
        val durationMillis: Long = 0,
    ) : CommandOutputChunk {
        val isSuccess: Boolean get() = exitCode == 0
    }
}

/** Bounds applied to a single command so one runaway process cannot exhaust memory. */
data class CommandLimits(
    val timeoutMillis: Long = 60_000,
    /** Output beyond this is discarded and the result marked truncated. */
    val maxOutputBytes: Int = 1 shl 20,
) {
    companion object {
        /** For probes such as `uname -a` that should answer immediately. */
        val QUICK = CommandLimits(timeoutMillis = 10_000, maxOutputBytes = 64 * 1024)

        /** For builds and test runs. */
        val LONG_RUNNING = CommandLimits(timeoutMillis = 30 * 60_000, maxOutputBytes = 4 shl 20)
    }
}
