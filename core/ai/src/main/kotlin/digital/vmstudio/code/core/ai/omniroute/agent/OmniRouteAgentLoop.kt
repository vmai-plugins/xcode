package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import digital.vmstudio.code.core.ai.omniroute.applyOmniRouteAuth
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * A real, tool-using agent loop driven by an OmniRoute model instead of Claude Code
 * CLI - the path that lets a free/cheap model edit files and run commands, not just
 * chat.
 *
 * Owns everything Claude Code CLI would otherwise own for its own run: building the
 * multi-turn conversation, dispatching tool calls, and deciding when to ask a human
 * first. Emits the same [AgentEvent] sequence the CLI path does, so nothing above
 * this class (the chat screen, task tracking, diff review) needs to know which
 * backend actually ran.
 */
@Singleton
class OmniRouteAgentLoop @Inject constructor(
    private val httpClient: VmHttpClient,
    private val remoteFileSystem: RemoteFileSystem,
    private val commandGuard: CommandGuard,
    private val fileEditApprovalGate: FileEditApprovalGate,
    private val serverRepository: ServerRepository,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Finished conversations by session id, so a follow-up message reaches the
     * model with everything said before it. In memory only and capped at
     * [MAX_SESSIONS]; a conversation reopened after a restart starts fresh.
     */
    private val sessions = SessionMemory(MAX_SESSIONS)

    fun run(
        baseUrl: String,
        key: Secret,
        dialect: OmniRouteDialect,
        model: String,
        config: AgentRunConfig,
        autonomyLevel: AgentAutonomyLevel,
    ): Flow<AgentEvent> = flow {
        val serverId = config.serverId
        val workingDirectory = config.workingDirectory
        val startedAt = System.currentTimeMillis()

        val allowedTools = allowedToolsFor(config)
        // Continue the conversation the chat screen asks to resume, if this process
        // still holds it; otherwise start fresh, exactly as a new chat would.
        val sessionId = config.resumeSessionId?.takeIf { sessions.get(it) != null }
            ?: (SESSION_PREFIX + UUID.randomUUID())
        val previous = sessions.get(sessionId)

        try {
            emit(
                AgentEvent.SessionStarted(
                    sessionId = sessionId,
                    workingDirectory = workingDirectory,
                    model = model,
                    availableTools = allowedTools.map { it.toolName },
                ),
            )

            val history = previous.orEmpty().toMutableList()
            history += OmniRouteMessage.User(config.prompt)
            var inputTokensTotal = 0L
            var outputTokensTotal = 0L

            repeat(MAX_ITERATIONS) {
                currentCoroutineContext().ensureActive()

                val request =
                    buildRequest(baseUrl, key, dialect, model, workingDirectory, allowedTools, history)
                val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))
                val responseBody = when (result) {
                    is VmResult.Failure -> {
                        emit(AgentEvent.Failed(result.error))
                        return@flow
                    }
                    is VmResult.Success -> result.value
                }

                when (val turn = OmniRouteToolCodec.parseResponse(dialect, responseBody)) {
                    is OmniRouteTurn.ParseFailed -> {
                        emit(
                            AgentEvent.Failed(
                                VmError.Ai(
                                    summary = "Could not read the model's response",
                                    reason = turn.reason,
                                    provider = "omniroute",
                                ),
                            ),
                        )
                        return@flow
                    }

                    is OmniRouteTurn.Text -> {
                        turn.usage?.let { (input, output) ->
                            inputTokensTotal += input
                            outputTokensTotal += output
                        }
                        if (turn.text.isNotBlank()) emit(AgentEvent.AssistantMessage(turn.text))
                        // An assistant turn with no content is rejected by the
                        // Anthropic dialect, so a blank reply is stored as a marker.
                        history += OmniRouteMessage.Assistant(
                            text = turn.text.ifBlank { "(no reply)" },
                            toolCalls = emptyList(),
                        )
                        sessions.put(sessionId, history.toList())
                        emit(
                            AgentEvent.Completed(
                                sessionId = sessionId,
                                resultText = turn.text.takeIf { it.isNotBlank() },
                                durationMillis = System.currentTimeMillis() - startedAt,
                                costUsd = 0.0,
                                inputTokens = inputTokensTotal,
                                outputTokens = outputTokensTotal,
                                isError = false,
                            ),
                        )
                        return@flow
                    }

                    is OmniRouteTurn.ToolCalls -> {
                        turn.usage?.let { (input, output) ->
                            inputTokensTotal += input
                            outputTokensTotal += output
                        }
                        history += OmniRouteMessage.Assistant(text = null, toolCalls = turn.calls)

                        for (call in turn.calls) {
                            currentCoroutineContext().ensureActive()
                            history += runToolCall(call, config, autonomyLevel, allowedTools)
                        }
                    }
                }
            }

            emit(
                AgentEvent.Failed(
                    VmError.Ai(
                        summary = "Reached the tool-call limit",
                        reason = "Stopped after $MAX_ITERATIONS model turns to avoid a runaway loop.",
                        suggestedAction = "Ask a narrower question, or continue in a new message.",
                        retryable = false,
                        provider = "omniroute",
                    ),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            emit(
                AgentEvent.Failed(
                    VmError.Ai(
                        summary = "The agent loop failed",
                        reason = throwable.message,
                        provider = "omniroute",
                        cause = throwable,
                    ),
                ),
            )
        }
    }

    // --- tool dispatch ---------------------------------------------------------------

    private data class ToolOutcome(val output: String, val isError: Boolean)

    /**
     * The same permission-mode selector the Claude Code CLI path honors
     * (--permission-mode plan / --restricted) must mean the same thing here:
     * PLAN offers no tool that changes anything, and restricted drops the tool
     * that runs commands, regardless of the user's standing autonomy level.
     */
    private fun allowedToolsFor(config: AgentRunConfig): Set<OmniRouteTool> = buildSet {
        add(OmniRouteTool.READ_FILE)
        add(OmniRouteTool.LIST_DIRECTORY)
        add(OmniRouteTool.GREP_SEARCH)
        if (config.permissionMode != AgentPermissionMode.PLAN) {
            add(OmniRouteTool.WRITE_FILE)
            add(OmniRouteTool.EDIT_FILE)
            if (!config.restricted) add(OmniRouteTool.RUN_COMMAND)
        }
    }

    private fun buildRequest(
        baseUrl: String,
        key: Secret,
        dialect: OmniRouteDialect,
        model: String,
        workingDirectory: String,
        allowedTools: Set<OmniRouteTool>,
        history: List<OmniRouteMessage>,
    ): Request {
        val requestBody = OmniRouteToolCodec.buildRequestBody(
            dialect = dialect,
            model = model,
            systemPrompt = systemPrompt(workingDirectory, allowedTools),
            history = history,
            maxTokens = DEFAULT_MAX_TOKENS,
            tools = allowedTools.toList(),
        )
        return Request.Builder()
            .url("$baseUrl/${dialect.chatPath}")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .applyOmniRouteAuth(key, dialect)
            .build()
    }

    /** Runs one tool call, emitting its start and finish, and returns the result for the history. */
    private suspend fun FlowCollector<AgentEvent>.runToolCall(
        call: OmniRouteToolCall,
        config: AgentRunConfig,
        autonomyLevel: AgentAutonomyLevel,
        allowedTools: Set<OmniRouteTool>,
    ): OmniRouteMessage.ToolResult {
        val tool = OmniRouteTool.fromToolName(call.name)
        val path = argument(call.argumentsJson, "path")
        // Resolved to an absolute path, not the raw model argument:
        // a model editing a repo-root file may well call this with
        // just "README.md", and DiffReviewViewModel derives the
        // directory to run git in from this path's dirname - a bare
        // filename with no "/" would make it try to cd into the
        // filename itself instead of the actual working directory.
        val resolvedPath = path?.let { resolvePath(config.workingDirectory, it) }

        emit(
            AgentEvent.ToolStarted(
                toolUseId = call.id,
                name = call.name,
                summary = summarize(tool, call.name, path),
                argumentsJson = call.argumentsJson,
                affectedPath = resolvedPath,
            ),
        )

        val outcome = dispatch(
            tool = tool,
            call = call,
            serverId = config.serverId,
            workingDirectory = config.workingDirectory,
            autonomyLevel = autonomyLevel,
            permissionMode = config.permissionMode,
            allowedTools = allowedTools,
        )

        emit(
            AgentEvent.ToolFinished(
                toolUseId = call.id,
                isError = outcome.isError,
                output = outcome.output,
            ),
        )
        return OmniRouteMessage.ToolResult(
            toolCallId = call.id,
            toolName = call.name,
            content = outcome.output,
            isError = outcome.isError,
        )
    }

    private suspend fun dispatch(
        tool: OmniRouteTool?,
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
        autonomyLevel: AgentAutonomyLevel,
        permissionMode: AgentPermissionMode,
        allowedTools: Set<OmniRouteTool>,
    ): ToolOutcome {
        if (tool == null) {
            return ToolOutcome("Unknown tool \"${call.name}\".", isError = true)
        }
        // Belt and suspenders: the model was not offered this tool (PLAN mode, or
        // restricted dropping run_command), but a call naming it anyway must still
        // be refused rather than trusting the request body kept it out.
        if (tool !in allowedTools) {
            return ToolOutcome(
                "\"${tool.toolName}\" is not available in this run's permission mode.",
                isError = true,
            )
        }

        return when (tool) {
            OmniRouteTool.READ_FILE -> readFile(call, serverId, workingDirectory)
            OmniRouteTool.LIST_DIRECTORY -> listDirectory(call, serverId, workingDirectory)
            OmniRouteTool.GREP_SEARCH -> grepSearch(call, serverId, workingDirectory)
            OmniRouteTool.WRITE_FILE ->
                writeFile(call, serverId, workingDirectory, autonomyLevel, permissionMode)
            OmniRouteTool.EDIT_FILE ->
                editFile(call, serverId, workingDirectory, autonomyLevel, permissionMode)
            OmniRouteTool.RUN_COMMAND -> runCommand(call, serverId)
        }
    }

    private suspend fun readFile(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val path = argument(call.argumentsJson, "path")
            ?: return ToolOutcome("Missing required argument \"path\".", isError = true)
        val resolved = resolvePath(workingDirectory, path)

        return when (val result = remoteFileSystem.readText(serverId, resolved)) {
            is VmResult.Success -> ToolOutcome(result.value, isError = false)
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
        }
    }

    private suspend fun listDirectory(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val path = argument(call.argumentsJson, "path") ?: "."
        val resolved = resolvePath(workingDirectory, path)

        return when (val result = remoteFileSystem.list(serverId, resolved)) {
            is VmResult.Success -> {
                val listing = result.value.joinToString("\n") { entry ->
                    val kind = if (entry.isDirectory) "d" else "-"
                    "$kind ${entry.sizeBytes.toString().padStart(SIZE_COLUMN_WIDTH)}  ${entry.name}"
                }
                ToolOutcome(listing.ifBlank { "(empty directory)" }, isError = false)
            }
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
        }
    }

    private suspend fun grepSearch(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val query = argument(call.argumentsJson, "query")
            ?: return ToolOutcome("Missing required argument \"query\".", isError = true)
        val path = argument(call.argumentsJson, "path") ?: "."
        val resolved = resolvePath(workingDirectory, path)
        val caseSensitive = argument(call.argumentsJson, "case_sensitive")?.toBooleanStrictOrNull() ?: false

        val flags = if (caseSensitive) "-rn" else "-rni"
        val escapedQuery = query.replace("'", "'\\''")
        val escapedPath = resolved.replace("'", "'\\''")
        val command = "grep $flags --exclude-dir={.git,node_modules,build,.gradle} " +
            "'$escapedQuery' '$escapedPath' | head -n 50"

        return when (val result = commandGuard.run(serverId, command, requestedByAgent = true)) {
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
            is VmResult.Success -> {
                val output = result.value.combinedOutput()
                ToolOutcome(
                    output = output.ifBlank { "(no matches found for '$query')" },
                    isError = false,
                )
            }
        }
    }

    /**
     * Edits apply without asking only off production, never in MANUAL mode, and only
     * when the run or the standing autonomy level already permits edits.
     */
    private fun canAutoApply(
        server: Server,
        permissionMode: AgentPermissionMode,
        autonomyLevel: AgentAutonomyLevel,
    ): Boolean = server.environment != ServerEnvironment.PRODUCTION &&
        permissionMode != AgentPermissionMode.MANUAL &&
        (
            permissionMode == AgentPermissionMode.ACCEPT_EDITS ||
                permissionMode == AgentPermissionMode.BYPASS ||
                autonomyLevel.allows(AgentAutonomyLevel.DEVELOPER)
        )

    /** Why [target] cannot be replaced in [content], or null when it occurs exactly once. */
    private fun uniqueMatchProblem(content: String, target: String, resolved: String): String? {
        val occurrences = content.split(target).size - 1
        return when {
            occurrences == 0 ->
                "Could not find the target_content in \"$resolved\". " +
                    "Ensure target_content exactly matches the existing file contents."
            occurrences > 1 ->
                "target_content matches $occurrences times in \"$resolved\". " +
                    "Please provide more surrounding context so the match is unique."
            else -> null
        }
    }

    private suspend fun editFile(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
        autonomyLevel: AgentAutonomyLevel,
        permissionMode: AgentPermissionMode,
    ): ToolOutcome {
        val path = argument(call.argumentsJson, "path")
        val target = argument(call.argumentsJson, "target_content")
        val replacement = argument(call.argumentsJson, "replacement_content")
        if (path == null || target == null || replacement == null) {
            val missing = when {
                path == null -> "path"
                target == null -> "target_content"
                else -> "replacement_content"
            }
            return ToolOutcome("Missing required argument \"$missing\".", isError = true)
        }
        val resolved = resolvePath(workingDirectory, path)

        val server = when (val result = serverRepository.get(serverId)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return ToolOutcome(result.error.summaryWithReason(), isError = true)
        }

        val existingContent = when (val result = remoteFileSystem.readText(serverId, resolved)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return ToolOutcome(
                "Failed to read file before edit: ${result.error.summaryWithReason()}",
                isError = true,
            )
        }

        uniqueMatchProblem(existingContent, target, resolved)?.let { problem ->
            return ToolOutcome(problem, isError = true)
        }

        val newContent = existingContent.replace(target, replacement)

        val autoApply = canAutoApply(server, permissionMode, autonomyLevel)

        if (!autoApply) {
            val approved = fileEditApprovalGate.request(
                FileEditApprovalRequest(
                    path = resolved,
                    isNewFile = false,
                    oldContent = existingContent,
                    newContent = newContent,
                    serverId = server.id,
                    serverName = server.name,
                    environment = server.environment,
                ),
            )
            if (!approved) {
                return ToolOutcome("The edit to \"$resolved\" was not approved.", isError = true)
            }
        }

        return when (val result = remoteFileSystem.writeText(serverId, resolved, newContent)) {
            is VmResult.Success -> ToolOutcome("Successfully edited $resolved.", isError = false)
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
        }
    }

    private suspend fun writeFile(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
        autonomyLevel: AgentAutonomyLevel,
        permissionMode: AgentPermissionMode,
    ): ToolOutcome {
        val path = argument(call.argumentsJson, "path")
            ?: return ToolOutcome("Missing required argument \"path\".", isError = true)
        val content = argument(call.argumentsJson, "content")
            ?: return ToolOutcome("Missing required argument \"content\".", isError = true)
        val resolved = resolvePath(workingDirectory, path)

        val server = when (val result = serverRepository.get(serverId)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return ToolOutcome(result.error.summaryWithReason(), isError = true)
        }

        // Production always requires a human decision, exactly as UserPreferences
        // documents for AgentAutonomyLevel: "the level raises the floor, never the
        // ceiling." MANUAL asks for everything with an effect, by the user's own
        // per-run choice, regardless of the standing autonomy level. Everything else
        // (ACCEPT_EDITS, BYPASS, or DEVELOPER+/FULL_AGENT autonomy) may auto-apply.
        val autoApply = canAutoApply(server, permissionMode, autonomyLevel)

        if (!autoApply) {
            // Existence is checked directly rather than inferred from whether the
            // content could be read: a file that exists but is too large to preview
            // is still an overwrite, not a creation, and must not be shown as one.
            val fileExists = when (val result = remoteFileSystem.exists(serverId, resolved)) {
                is VmResult.Success -> result.value
                is VmResult.Failure -> return ToolOutcome(result.error.summaryWithReason(), isError = true)
            }
            val existingContent = if (fileExists) {
                (remoteFileSystem.readText(serverId, resolved) as? VmResult.Success)?.value
            } else {
                null
            }

            val approved = fileEditApprovalGate.request(
                FileEditApprovalRequest(
                    path = resolved,
                    isNewFile = !fileExists,
                    oldContent = existingContent,
                    newContent = content,
                    serverId = server.id,
                    serverName = server.name,
                    environment = server.environment,
                ),
            )
            if (!approved) {
                return ToolOutcome("The write to \"$resolved\" was not approved.", isError = true)
            }
        }

        return when (val result = remoteFileSystem.writeText(serverId, resolved, content)) {
            is VmResult.Success ->
                ToolOutcome("Wrote ${content.length} characters to $resolved.", isError = false)
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
        }
    }

    private suspend fun runCommand(call: OmniRouteToolCall, serverId: String): ToolOutcome {
        val command = argument(call.argumentsJson, "command")
            ?: return ToolOutcome("Missing required argument \"command\".", isError = true)

        return when (val result = commandGuard.run(serverId, command, requestedByAgent = true)) {
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
            is VmResult.Success -> {
                val output = result.value.combinedOutput()
                ToolOutcome(
                    output = output.ifBlank { "(no output)" } +
                        if (!result.value.isSuccess) "\n[exit code ${result.value.exitCode}]" else "",
                    isError = !result.value.isSuccess,
                )
            }
        }
    }

    // --- helpers -----------------------------------------------------------------

    private fun summarize(tool: OmniRouteTool?, toolName: String, path: String?): String = when (tool) {
        OmniRouteTool.READ_FILE -> "Read ${path ?: "?"}"
        OmniRouteTool.LIST_DIRECTORY -> "List ${path ?: "?"}"
        OmniRouteTool.GREP_SEARCH -> "Search in ${path ?: "?"}"
        OmniRouteTool.WRITE_FILE -> "Write ${path ?: "?"}"
        OmniRouteTool.EDIT_FILE -> "Edit ${path ?: "?"}"
        OmniRouteTool.RUN_COMMAND -> "Run command"
        null -> toolName
    }

    /** Absolute and home-relative paths pass through; anything else is relative to [workingDirectory]. */
    private fun resolvePath(workingDirectory: String, path: String): String =
        if (path.startsWith("/") || path.startsWith("~")) path else "${workingDirectory.trimEnd('/')}/$path"

    private fun argument(argumentsJson: String, key: String): String? {
        val root = runCatching { json.parseToJsonElement(argumentsJson) }.getOrNull()
            as? JsonObject ?: return null
        return (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun VmError.summaryWithReason(): String = reason?.let { "$summary: $it" } ?: summary

    private fun systemPrompt(
        workingDirectory: String,
        allowedTools: Set<OmniRouteTool>,
    ): String = buildString {
        append(
            "You are an autonomous AI coding agent working in $workingDirectory on a remote " +
                "server, reached through tools. First inspect the project structure with " +
                "list_directory, read_file, or grep_search. ",
        )
        if (OmniRouteTool.EDIT_FILE in allowedTools) {
            append(
                "When modifying existing files, always prefer edit_file to replace targeted " +
                    "code sections precisely. ",
            )
        }
        if (OmniRouteTool.WRITE_FILE in allowedTools) {
            append(
                "Use write_file to create new files or rewrite small files entirely. ",
            )
        }
        if (OmniRouteTool.RUN_COMMAND in allowedTools) {
            append(
                "Use run_command to run build, test, git, and lint commands, inspect outputs, " +
                    "and self-correct any errors. ",
            )
        }
        if (OmniRouteTool.WRITE_FILE !in allowedTools) {
            append(
                "This run only proposes a plan: you cannot write files or run " +
                    "commands, so describe what you would do instead of attempting it. ",
            )
        }
        append(
            "Prefer relative paths under $workingDirectory. When done, reply " +
                "with a clear summary of findings or modifications.",
        )
    }

    private companion object {
        const val SESSION_PREFIX = "omniroute-"
        const val MAX_ITERATIONS = 25
        const val MAX_SESSIONS = 16
        const val DEFAULT_MAX_TOKENS = 4096
        const val SIZE_COLUMN_WIDTH = 10
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** A small least-recently-used map of conversation histories. */
private class SessionMemory(private val capacity: Int) {
    private val entries =
        object : LinkedHashMap<String, List<OmniRouteMessage>>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<OmniRouteMessage>>?,
            ): Boolean = size > capacity
        }

    @Synchronized
    fun get(sessionId: String): List<OmniRouteMessage>? = entries[sessionId]

    @Synchronized
    fun put(sessionId: String, history: List<OmniRouteMessage>) {
        entries[sessionId] = history
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
