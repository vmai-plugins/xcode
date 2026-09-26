package digital.vmstudio.code.core.ai.background

import digital.vmstudio.code.core.ai.claudecode.ClaudeCodeCommandBuilder
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.terminal.emulator.TerminalEmulator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs Claude Code detached on the server, so work survives the app being closed.
 *
 * This is a different transport from [digital.vmstudio.code.core.ai.claudecode.ClaudeCodeCliProvider],
 * not a variant of it. A foreground run is `--print --output-format stream-json` and
 * yields parseable events; the CLI rejects that combination with `--bg`, because
 * `--print` never starts the session that `claude agents` attaches to. A background
 * run is therefore a real TUI session: it is started, polled by id, and its output
 * read back as rendered terminal text.
 *
 * Every command shape here was verified against Claude Code 2.1.261 rather than taken
 * from documentation.
 */
@Singleton
class BackgroundAgentRunner @Inject constructor(
    private val connectionManager: SshConnectionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Starts a detached run and returns its short id.
     *
     * The id is what `logs`, `stop` and `rm` take; the full session UUID needed for
     * `--resume` arrives later via [list], once the CLI has registered the session.
     */
    suspend fun start(
        serverId: String,
        workingDirectory: String,
        prompt: String,
        permissionMode: AgentPermissionMode = AgentPermissionMode.PLAN,
    ): VmResult<String> = withContext(ioDispatcher) {
        val command = ClaudeCodeCommandBuilder.backgroundStartCommand(
            workingDirectory = workingDirectory,
            prompt = prompt,
            permissionMode = permissionMode,
        )

        connectionManager.withSession(serverId) { session ->
            when (val result = session.execute(command, CommandLimits.QUICK)) {
                is VmResult.Failure -> result
                is VmResult.Success -> {
                    val output = result.value.combinedOutput()
                    val id = parseStartedId(output)
                    if (id == null) {
                        VmResult.Failure(startFailed(output))
                    } else {
                        VmLog.i(LogCategory.AI, TAG, "Background run $id started in $workingDirectory")
                        VmResult.Success(id)
                    }
                }
            }
        }
    }

    /**
     * Lists sessions, optionally scoped to one project directory.
     *
     * Includes finished runs, which is the point: a run that completed while the app
     * was closed must still be reportable.
     */
    suspend fun list(
        serverId: String,
        workingDirectory: String? = null,
    ): VmResult<List<BackgroundRun>> = withContext(ioDispatcher) {
        val command = ClaudeCodeCommandBuilder.agentsJsonCommand(workingDirectory)
        connectionManager.withSession(serverId) { session ->
            when (val result = session.execute(command, CommandLimits.QUICK)) {
                is VmResult.Failure -> result
                is VmResult.Success ->
                    VmResult.Success(BackgroundRunParser.parse(result.value.combinedOutput()))
            }
        }
    }

    /** One run by short id, or null when the server no longer knows about it. */
    suspend fun get(serverId: String, runId: String): VmResult<BackgroundRun?> =
        when (val listed = list(serverId)) {
            is VmResult.Failure -> listed
            is VmResult.Success -> VmResult.Success(listed.value.firstOrNull { it.id == runId })
        }

    /**
     * A run's recent output as plain text.
     *
     * `claude logs` emits the session's TUI verbatim — cursor addressing, colours,
     * box drawing — so it is replayed through the terminal emulator and read back as
     * the rendered screen. Stripping escape sequences with a regex would mangle a
     * screen that positions text rather than printing it in order.
     */
    suspend fun logs(serverId: String, runId: String): VmResult<String> =
        withContext(ioDispatcher) {
            val command = ClaudeCodeCommandBuilder.logsCommand(runId)
            connectionManager.withSession(serverId) { session ->
                when (val result = session.execute(command, CommandLimits.QUICK)) {
                    is VmResult.Failure -> result
                    is VmResult.Success -> VmResult.Success(render(result.value.combinedOutput()))
                }
            }
        }

    /** Stops a run. Its conversation is kept, so it can still be resumed. */
    suspend fun stop(serverId: String, runId: String): VmResult<Unit> = withContext(ioDispatcher) {
        issueCommand(serverId, ClaudeCodeCommandBuilder.stopCommand(runId))
            .flatMap { verifyStopped(serverId, runId) }
    }

    /** Deletes a run and its record. Only meaningful once it has stopped. */
    suspend fun remove(serverId: String, runId: String): VmResult<Unit> = withContext(ioDispatcher) {
        issueCommand(serverId, ClaudeCodeCommandBuilder.removeCommand(runId))
            .flatMap { verifyRemoved(serverId, runId) }
    }

    // --- internals -----------------------------------------------------------------

    private suspend fun issueCommand(serverId: String, command: String): VmResult<Unit> =
        connectionManager.withSession(serverId) { session ->
            when (val result = session.execute(command, CommandLimits.QUICK)) {
                is VmResult.Failure -> result
                is VmResult.Success -> VmResult.Success(Unit)
            }
        }

    /**
     * Confirms the run actually stopped rather than trusting `claude stop`'s text
     * output: its exact wording varies by CLI version, and it is blank both on some
     * successful exits and on some silently-ignored commands (a stale short id after
     * the CLI restarts, for instance) - treating blank as success let a failed stop
     * report itself as done while the run kept going on the server.
     */
    private suspend fun verifyStopped(serverId: String, runId: String): VmResult<Unit> =
        when (val after = get(serverId, runId)) {
            is VmResult.Failure -> after
            is VmResult.Success -> {
                val run = after.value
                if (run == null || !run.isRunning) {
                    VmResult.Success(Unit)
                } else {
                    VmResult.Failure(
                        VmError.Ai(
                            summary = "The run did not stop",
                            reason = "The server still reports it as running after 'claude stop'.",
                            suggestedAction = "Try again, or check the server directly if this repeats.",
                            retryable = true,
                            provider = "claude-code",
                        ),
                    )
                }
            }
        }

    /** Same reasoning as [verifyStopped]: confirms the run is actually gone. */
    private suspend fun verifyRemoved(serverId: String, runId: String): VmResult<Unit> =
        when (val after = get(serverId, runId)) {
            is VmResult.Failure -> after
            is VmResult.Success -> if (after.value == null) {
                VmResult.Success(Unit)
            } else {
                VmResult.Failure(
                    VmError.Ai(
                        summary = "The run could not be removed",
                        reason = "The server still lists it after 'claude rm'.",
                        retryable = true,
                        provider = "claude-code",
                    ),
                )
            }
        }

    /**
     * Renders ANSI output through the emulator and returns the visible screen.
     *
     * Sized generously: the log is a snapshot of the server's terminal, whose width
     * the app does not control, and a narrow emulator would wrap lines the server
     * never wrapped.
     */
    private fun render(ansi: String): String {
        val emulator = TerminalEmulator(columns = RENDER_COLUMNS, rows = RENDER_ROWS)
        emulator.write(ansi)
        return emulator.buffer.allText().trimEnd()
    }

    /**
     * Reads the short id out of the CLI's start banner.
     *
     * Verified output shape:
     * ```
     * Starting background service…
     * backgrounded · 3f8b10c3
     * ```
     */
    private fun parseStartedId(output: String): String? =
        STARTED_ID.find(output)?.groupValues?.getOrNull(1)

    private fun startFailed(output: String) = VmError.Ai(
        summary = "The agent could not be started in the background",
        reason = output.take(MAX_ERROR_EXCERPT).ifBlank { null },
        suggestedAction = "Check Claude Code is installed and signed in on the server.",
        retryable = true,
        provider = "claude-code",
    )

    private companion object {
        const val TAG = "BackgroundAgentRunner"

        /**
         * Matches `backgrounded · 3f8b10c3`, tolerating the separator changing.
         *
         * The separator is `\W*` rather than `\S*`: a greedy `\S*` backtracks into
         * the id itself, so `backgrounded 3f8b10c3` captured `8b10c3` — a truncated
         * id that `stop` or `rm` would aim at the wrong session. Word boundaries
         * pin the capture to the whole token.
         */
        val STARTED_ID = Regex("""backgrounded\b\W*\b([0-9a-f]{6,})\b""", RegexOption.IGNORE_CASE)

        const val RENDER_COLUMNS = 200
        const val RENDER_ROWS = 200
        const val MAX_ERROR_EXCERPT = 400
    }
}
