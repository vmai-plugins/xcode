package digital.vmstudio.code.core.ai.claudecode

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.provider.AiProvider
import digital.vmstudio.code.core.ai.provider.AiProviderHealth
import digital.vmstudio.code.core.ai.provider.AiProviderKind
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.command.CommandOutputChunk
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.CoroutineDispatcher
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the Claude Code CLI on the development machine over SSH.
 *
 * The agent executes where the code is. Nothing about the repository crosses the
 * mobile connection: only the prompt goes out and only the event stream comes back.
 * The CLI owns planning, tool selection and context management, so this class is a
 * transport and a translator rather than an agent implementation.
 *
 * **The app's command-safety layer does not extend inside a run.** Tools the CLI
 * invokes are its own subprocesses on the server, invisible to `CommandGuard`.
 * Constraint comes from the CLI's own controls, set per run through
 * [AgentRunConfig]: permission mode, tool allow and deny lists, and restricted mode.
 * Every tool call is surfaced as an event so the user can see what happened, but the
 * app cannot gate them individually. This is stated in SECURITY.md and is the main
 * trade-off of delegating the agent.
 */
@Singleton
class ClaudeCodeCliProvider @Inject constructor(
    private val connectionManager: SshConnectionManager,
    private val parser: ClaudeCodeStreamParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AiProvider {

    override val kind: AiProviderKind = AiProviderKind.CLAUDE_CODE_CLI

    override suspend fun checkHealth(serverId: String?): VmResult<AiProviderHealth> {
        if (serverId == null) {
            return VmResult.Success(
                AiProviderHealth(
                    kind = kind,
                    isAvailable = false,
                    diagnosis = "Choose a server. The Claude Code agent runs on your " +
                        "development machine, not on this device.",
                ),
            )
        }

        val startedAt = System.currentTimeMillis()
        return connectionManager.withSession(serverId) { session ->
            val versionResult = session.execute(
                ClaudeCodeCommandBuilder.versionCommand(),
                CommandLimits.QUICK,
            )
            val version = when (versionResult) {
                is VmResult.Failure -> return@withSession versionResult
                is VmResult.Success -> versionResult.value.combinedOutput().trim()
            }

            if (version.contains(ClaudeCodeCommandBuilder.MISSING_MARKER) || version.isEmpty()) {
                return@withSession VmResult.Success(
                    AiProviderHealth(
                        kind = kind,
                        isAvailable = false,
                        latencyMillis = System.currentTimeMillis() - startedAt,
                        diagnosis = "The claude command was not found on this server. " +
                            "Install Claude Code there, or check it is on the PATH of " +
                            "non-interactive shells.",
                    ),
                )
            }

            val authOutput = session.execute(
                ClaudeCodeCommandBuilder.authStatusCommand(),
                CommandLimits.QUICK,
            ).let { result ->
                when (result) {
                    is VmResult.Failure -> ""
                    is VmResult.Success -> result.value.combinedOutput()
                }
            }

            // The CLI reports auth as JSON. Matched loosely so a formatting change
            // degrades to "unknown" rather than to a false "signed in".
            val loggedIn = authOutput.contains("\"loggedIn\": true") ||
                authOutput.contains("\"loggedIn\":true")

            VmResult.Success(
                AiProviderHealth(
                    kind = kind,
                    isAvailable = true,
                    version = version.lineSequence().firstOrNull()?.trim(),
                    endpoint = "ssh://$serverId",
                    isAuthenticated = loggedIn,
                    latencyMillis = System.currentTimeMillis() - startedAt,
                    diagnosis = if (loggedIn) {
                        null
                    } else {
                        "Claude Code is installed but not signed in. On the server run " +
                            "\"claude auth login\", or set ANTHROPIC_BASE_URL and an auth " +
                            "token in its environment to use a gateway. Credentials belong " +
                            "on the server; this app never stores them."
                    },
                ),
            )
        }
    }

    override fun run(config: AgentRunConfig): Flow<AgentEvent> = flow {
        val command = ClaudeCodeCommandBuilder.build(config)
        VmLog.i(
            LogCategory.AI,
            TAG,
            "Starting agent run in ${config.workingDirectory} " +
                "(mode=${config.permissionMode}, resume=${config.resumeSessionId != null})",
        )

        when (val session = connectionManager.session(config.serverId)) {
            is VmResult.Failure -> emit(AgentEvent.Failed(session.error))
            is VmResult.Success -> {
                var sawTerminalEvent = false
                emitAll(
                    session.value.executeStreaming(command, CommandLimits.LONG_RUNNING)
                        .toAgentEvents { sawTerminalEvent = it },
                )
                if (!sawTerminalEvent) {
                    // A run that ends without a result envelope produced nothing
                    // usable. Reporting it explicitly beats a chat that just stops.
                    emit(
                        AgentEvent.Failed(
                            VmError.Ai(
                                summary = "The agent stopped without finishing",
                                reason = "Claude Code exited before reporting a result.",
                                suggestedAction = "Check that it is signed in on the server, " +
                                    "and open the AI connection test for details.",
                                provider = "claude-code",
                            ),
                        ),
                    )
                }
            }
        }
    }.flowOn(ioDispatcher)

    /**
     * Maps raw output into agent events.
     *
     * stderr is surfaced as diagnostics rather than dropped: an unauthenticated or
     * misconfigured CLI reports on stderr and exits, and without this the run would
     * look like an agent that simply did nothing.
     */
    private fun Flow<CommandOutputChunk>.toAgentEvents(
        onTerminal: (Boolean) -> Unit,
    ): Flow<AgentEvent> = flow {
        var terminal = false
        collect { chunk ->
            when (chunk) {
                is CommandOutputChunk.Stdout -> parser.parseLine(chunk.line).forEach { event ->
                    if (event is AgentEvent.Completed || event is AgentEvent.Failed) {
                        terminal = true
                        onTerminal(true)
                    }
                    emit(event)
                }

                is CommandOutputChunk.Stderr -> {
                    if (chunk.line.isNotBlank()) {
                        emit(AgentEvent.Diagnostic(chunk.line, isStderr = true))
                    }
                }

                is CommandOutputChunk.Exited -> {
                    if (!terminal && !chunk.isSuccess) {
                        terminal = true
                        onTerminal(true)
                        emit(AgentEvent.Failed(exitError(chunk)))
                    }
                }
            }
        }
    }

    private fun exitError(chunk: CommandOutputChunk.Exited) = VmError.Ai(
        summary = "The agent could not run",
        reason = "Claude Code exited with status ${chunk.exitCode ?: "unknown"}" +
            (chunk.signal?.let { ", terminated by $it" } ?: "") + ".",
        suggestedAction = "Run the AI connection test to check the CLI is installed, " +
            "signed in, and able to reach its gateway.",
        retryable = true,
        provider = "claude-code",
    )

    private companion object {
        const val TAG = "ClaudeCodeCliProvider"
    }
}
