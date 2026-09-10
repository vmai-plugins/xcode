package digital.vmstudio.code.core.ssh.connection

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.command.CommandOutputChunk
import digital.vmstudio.code.core.ssh.command.CommandResult
import digital.vmstudio.code.core.ssh.model.Server
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * A live, authenticated connection to one server.
 *
 * Owns the sshj client. Callers obtain one from [SshConnectionManager] and must not
 * close it directly — the manager owns the lifecycle so several features (terminal,
 * file browser, agent) can share a single TCP connection and multiplex channels
 * over it, which is the whole point of SSH.
 */
class SshSession internal constructor(
    val server: Server,
    val serverInfo: ServerInfo,
    private val client: SSHClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : Closeable {

    val isConnected: Boolean
        get() = client.isConnected && client.isAuthenticated

    /**
     * Runs a non-interactive command and collects its output.
     *
     * stdout and stderr are drained concurrently: reading them in sequence
     * deadlocks as soon as a command fills the pipe of whichever stream is read
     * second, which is a classic way to hang on a verbose build.
     */
    suspend fun execute(
        command: String,
        limits: CommandLimits = CommandLimits(),
    ): VmResult<CommandResult> = withContext(ioDispatcher) {
        val startedAt = System.currentTimeMillis()
        var session: Session? = null
        try {
            session = client.startSession()
            val cmd = session.exec(command)

            // The whole read-and-wait is bounded here rather than relying on
            // cmd.join's timeout: a process that keeps stdout open but idle (or
            // trickling) never lets the drain loops reach EOF, so a join placed
            // after them would never run. On expiry the finally block closes the
            // session, which terminates the remote process.
            val drained = withTimeoutOrNull(limits.timeoutMillis) {
                val pair = coroutineScope {
                    val outDeferred = async { drain(cmd.inputStream, limits.maxOutputBytes) }
                    val errDeferred = async { drain(cmd.errorStream, limits.maxOutputBytes) }
                    outDeferred.await() to errDeferred.await()
                }
                runInterruptible { cmd.join(limits.timeoutMillis, TimeUnit.MILLISECONDS) }
                pair
            } ?: return@withContext VmResult.Failure(
                mapCommandFailure(command, CommandTimeoutException(limits.timeoutMillis), startedAt, limits),
            )
            val (stdout, stderr) = drained

            val result = CommandResult(
                command = command,
                exitCode = cmd.exitStatus,
                stdout = stdout.text,
                stderr = stderr.text,
                durationMillis = System.currentTimeMillis() - startedAt,
                terminatingSignal = cmd.exitSignal?.toString(),
                outputTruncated = stdout.truncated || stderr.truncated,
            )
            VmLog.d(
                LogCategory.SSH,
                TAG,
                "exec on ${server.name}: exit=${result.exitCode} " +
                    "in ${result.durationMillis}ms",
            )
            VmResult.Success(result)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            VmResult.Failure(mapCommandFailure(command, throwable, startedAt, limits))
        } finally {
            runCatching { session?.close() }
        }
    }

    /**
     * Runs a command and emits its output as it arrives.
     *
     * Distinct from [execute], which collects everything and returns once. An agent
     * run streaming JSON events, a build printing progress, or a log tail all need
     * output while the process is still alive; buffering to completion would make
     * them look frozen for minutes and then arrive all at once.
     *
     * No PTY is allocated. Programs that detect a TTY will correctly report they are
     * being piped, which is what makes `--output-format stream-json` emit machine
     * output rather than a rendered UI.
     *
     * The channel is closed when the flow completes or is cancelled, so abandoning
     * collection terminates the remote process rather than leaking it.
     */
    fun executeStreaming(
        command: String,
        limits: CommandLimits = CommandLimits.LONG_RUNNING,
    ): Flow<CommandOutputChunk> = channelFlow {
        val startedAt = System.currentTimeMillis()
        var session: Session? = null
        try {
            val opened = client.startSession()
            session = opened
            val cmd = opened.exec(command)

            // stdout and stderr are drained by separate coroutines. Reading them in
            // sequence deadlocks as soon as the process fills the pipe of whichever
            // is read second.
            val stdout = launch {
                readLines(cmd.inputStream) { send(CommandOutputChunk.Stdout(it)) }
            }
            val stderr = launch {
                readLines(cmd.errorStream) { send(CommandOutputChunk.Stderr(it)) }
            }

            // Bounded so a command that never closes its streams (a hung build, a
            // forgotten `tail -f`) cannot pin this coroutine forever. On expiry the
            // readers are cancelled and the finally block closes the session.
            val finished = withTimeoutOrNull(limits.timeoutMillis) {
                stdout.join()
                stderr.join()
                runInterruptible { cmd.join(limits.timeoutMillis, TimeUnit.MILLISECONDS) }
                true
            }

            if (finished == null) {
                stdout.cancel()
                stderr.cancel()
                send(
                    CommandOutputChunk.Exited(
                        exitCode = null,
                        signal = "TIMEOUT",
                        durationMillis = System.currentTimeMillis() - startedAt,
                    ),
                )
            } else {
                send(
                    CommandOutputChunk.Exited(
                        exitCode = cmd.exitStatus,
                        signal = cmd.exitSignal?.toString(),
                        durationMillis = System.currentTimeMillis() - startedAt,
                    ),
                )
            }
        } finally {
            // NonCancellable: on cancellation this still has to run, or the remote
            // process survives the coroutine that owned it.
            withContext(NonCancellable) {
                runCatching { session?.close() }
            }
        }
    }.flowOn(ioDispatcher)

    /**
     * Reads a stream line by line, emitting each through [onLine].
     *
     * A manual reader rather than `bufferedReader().lineSequence()` because the
     * sequence blocks on `hasNext()` and cannot be interrupted; this loop yields
     * between lines so a cancelled collector actually stops.
     */
    private suspend fun readLines(stream: InputStream, onLine: suspend (String) -> Unit) {
        val reader = stream.bufferedReader()
        while (true) {
            val line = try {
                runInterruptible(ioDispatcher) { reader.readLine() }
            } catch (_: java.io.IOException) {
                // The channel closing under a blocking read is a normal end of output.
                null
            } ?: break
            onLine(line)
        }
    }

    /**
     * Opens an interactive shell with a PTY.
     *
     * The caller owns the returned channel and must close it. `xterm-256color` is
     * requested because the terminal emulator renders 256-colour SGR sequences, and
     * claiming a lesser terminal would make remote tools emit degraded output.
     */
    suspend fun openShell(
        columns: Int,
        rows: Int,
        termType: String = DEFAULT_TERM,
    ): VmResult<ShellChannel> = withContext(ioDispatcher) {
        var session: Session? = null
        try {
            session = client.startSession()
            session.allocatePTY(termType, columns, rows, 0, 0, emptyMap())
            val shell = session.startShell()
            VmLog.i(LogCategory.TERMINAL, TAG, "Opened shell on ${server.name} (${columns}x$rows)")
            VmResult.Success(ShellChannel(session, shell))
        } catch (cancellation: CancellationException) {
            runCatching { session?.close() }
            throw cancellation
        } catch (throwable: Throwable) {
            runCatching { session?.close() }
            VmResult.Failure(
                VmError.Ssh(
                    summary = "Could not open a shell",
                    reason = throwable.message,
                    suggestedAction = "The account may have a restricted shell, or the server " +
                        "may not permit PTY allocation.",
                    host = server.host,
                    port = server.port,
                    username = server.username,
                    cause = throwable,
                ),
            )
        }
    }

    /**
     * Opens an SFTP channel. The caller owns it and must close it; each concurrent
     * browse or transfer takes its own channel so a slow transfer cannot block
     * directory listings.
     */
    suspend fun openSftp(): VmResult<SFTPClient> = withContext(ioDispatcher) {
        try {
            VmResult.Success(client.newSFTPClient())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            VmResult.Failure(
                VmError.Ssh(
                    summary = "Could not open SFTP",
                    reason = throwable.message,
                    suggestedAction = "The server may not have the SFTP subsystem enabled. " +
                        "Check for a Subsystem sftp line in sshd_config.",
                    host = server.host,
                    port = server.port,
                    cause = throwable,
                ),
            )
        }
    }

    override fun close() {
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }

    // --- internals -------------------------------------------------------------

    private data class Drained(val text: String, val truncated: Boolean)

    /**
     * Reads a stream to EOF, stopping at [maxBytes].
     *
     * Once the cap is hit the remainder is still consumed but discarded, rather
     * than closing the stream early: abandoning it would leave the remote process
     * blocked on a full pipe instead of exiting.
     */
    private suspend fun drain(stream: InputStream, maxBytes: Int): Drained =
        runInterruptible(ioDispatcher) {
            val buffer = ByteArray(READ_CHUNK)
            val collected = java.io.ByteArrayOutputStream()
            var truncated = false
            while (true) {
                val read = try {
                    stream.read(buffer)
                } catch (_: java.io.IOException) {
                    // The channel closing mid-read is a normal end of output.
                    break
                }
                if (read < 0) break
                val remaining = maxBytes - collected.size()
                if (remaining > 0) {
                    collected.write(buffer, 0, minOf(read, remaining))
                    if (read > remaining) truncated = true
                } else {
                    truncated = true
                }
            }
            Drained(collected.toString(Charsets.UTF_8.name()), truncated)
        }

    private fun mapCommandFailure(
        command: String,
        throwable: Throwable,
        startedAt: Long,
        limits: CommandLimits,
    ): VmError {
        val elapsed = System.currentTimeMillis() - startedAt
        val timedOut = throwable is CommandTimeoutException || elapsed >= limits.timeoutMillis
        return VmError.Command(
            summary = if (timedOut) "Command timed out" else "Command failed to run",
            reason = if (timedOut) {
                "No result after ${limits.timeoutMillis / 1000} seconds."
            } else {
                throwable.message
            },
            suggestedAction = if (timedOut) {
                "Long-running commands should be started in the background, " +
                    "or run from a terminal session where you can watch them."
            } else {
                "Check the connection to ${server.name} and try again."
            },
            retryable = true,
            command = command,
            cause = throwable,
        )
    }

    private companion object {
        const val TAG = "SshSession"
        const val DEFAULT_TERM = "xterm-256color"
        const val READ_CHUNK = 8 * 1024
    }
}

/** Thrown internally when a command exceeds its [CommandLimits.timeoutMillis] wall-clock bound. */
private class CommandTimeoutException(timeoutMillis: Long) :
    java.io.IOException("Command exceeded ${timeoutMillis}ms")

/**
 * An interactive shell channel. Wraps the session so closing the channel also
 * releases the underlying SSH session rather than leaking it.
 */
class ShellChannel internal constructor(
    private val session: Session,
    private val shell: Session.Shell,
) : Closeable {

    val input: InputStream get() = shell.inputStream

    val errorStream: InputStream get() = shell.errorStream

    val output: java.io.OutputStream get() = shell.outputStream

    val isOpen: Boolean get() = shell.isOpen

    /** Tells the remote side the window changed so full-screen programs redraw. */
    fun resize(columns: Int, rows: Int) {
        runCatching { shell.changeWindowDimensions(columns, rows, 0, 0) }
    }

    override fun close() {
        runCatching { shell.close() }
        runCatching { session.close() }
    }
}
