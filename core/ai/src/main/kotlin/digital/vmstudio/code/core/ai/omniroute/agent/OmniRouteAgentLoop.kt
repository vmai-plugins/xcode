package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.model.ConversationTurn
import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import digital.vmstudio.code.core.ai.omniroute.applyOmniRouteAuth
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.network.http.VmHttpException
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
    private val webFetcher: WebFetcher,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Finished conversations by session id, so a follow-up message reaches the
     * model with everything said before it. In memory only and capped at
     * [MAX_SESSIONS]; a conversation reopened after a restart starts fresh.
     */
    private val sessions = SessionMemory(MAX_SESSIONS)

    /** Gateway+model pairs whose streaming failed once; they go straight to plain requests. */
    private val nonStreaming: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

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
        // In-memory history keeps tool calls; after a restart only the visible
        // transcript the chat screen sends along is left, which is still enough
        // for the model to follow the conversation.
        val previous = sessions.get(sessionId) ?: config.history.toMessages()

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
            history += OmniRouteMessage.User(config.prompt, config.images)
            var inputTokensTotal = 0L
            var outputTokensTotal = 0L

            repeat(MAX_ITERATIONS) {
                currentCoroutineContext().ensureActive()

                val budget = config.contextChars ?: MAX_HISTORY_CHARS
                val target = GatewayTarget(baseUrl, key, dialect, model, budget)
                val (turn, streamedText) =
                    when (val next = nextTurn(target, workingDirectory, allowedTools, history)) {
                        is VmResult.Failure -> {
                            emit(AgentEvent.Failed(next.error))
                            return@flow
                        }
                        is VmResult.Success -> next.value
                    }

                when (turn) {
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
                        // Images are sent once; later turns keep only the text so the
                        // conversation does not re-upload them on every request.
                        sessions.put(
                            sessionId,
                            history.map { it.withoutImages() },
                        )
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
                        // Close the streamed draft before the tool rows appear under it.
                        if (streamedText.isNotBlank()) emit(AgentEvent.AssistantMessage(streamedText))
                        history += OmniRouteMessage.Assistant(
                            text = streamedText.ifBlank { null },
                            toolCalls = turn.calls,
                        )

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

    /** Where and how one turn's request is sent. */
    private data class GatewayTarget(
        val baseUrl: String,
        val key: Secret,
        val dialect: OmniRouteDialect,
        val model: String,
        val historyBudget: Int,
    )

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
        // Read-only, but it sends a model-chosen URL off the device, so a
        // restricted run does not get it.
        if (!config.restricted) add(OmniRouteTool.WEB_FETCH)
        if (config.permissionMode != AgentPermissionMode.PLAN) {
            add(OmniRouteTool.WRITE_FILE)
            add(OmniRouteTool.EDIT_FILE)
            if (!config.restricted) add(OmniRouteTool.RUN_COMMAND)
        }
    }

    private fun buildRequest(
        target: GatewayTarget,
        workingDirectory: String,
        allowedTools: Set<OmniRouteTool>,
        history: List<OmniRouteMessage>,
        stream: Boolean,
    ): Request {
        val body = OmniRouteToolCodec.buildRequestBody(
            dialect = target.dialect,
            model = target.model,
            systemPrompt = systemPrompt(workingDirectory, allowedTools),
            history = trimHistory(history, budget = target.historyBudget),
            maxTokens = DEFAULT_MAX_TOKENS,
            tools = allowedTools.toList(),
        ).let { if (stream) OmniRouteStreamAssembler.streamingBody(target.dialect, it) else it }
        return Request.Builder()
            .url("${target.baseUrl}/${target.dialect.chatPath}")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .applyOmniRouteAuth(target.key, target.dialect)
            .apply { if (stream) header("Accept", "text/event-stream") }
            .build()
    }

    /**
     * Asks the model for its next turn, streamed so the reply appears as it is
     * written. Gateways and models differ in how well they stream tool calls, so a
     * stream that fails or carries nothing usable is retried once without
     * streaming. Returns the turn and any text already shown while streaming.
     */
    private suspend fun FlowCollector<AgentEvent>.nextTurn(
        target: GatewayTarget,
        workingDirectory: String,
        allowedTools: Set<OmniRouteTool>,
        history: List<OmniRouteMessage>,
    ): VmResult<Pair<OmniRouteTurn, String>> {
        val assembler = OmniRouteStreamAssembler(target.dialect)
        val streamKey = target.baseUrl + "|" + target.model
        val streamed = if (streamKey in nonStreaming) null else try {
            httpClient.stream(buildRequest(target, workingDirectory, allowedTools, history, stream = true))
                .collect { event ->
                    assembler.accept(event.data)?.takeIf { it.isNotEmpty() }?.let {
                        emit(AgentEvent.AssistantDelta(it))
                    }
                }
            assembler.finish()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (streamFailure: VmHttpException) {
            VmLog.i(
                LogCategory.AI,
                TAG,
                "Streaming turn failed, retrying without: ${streamFailure.error.summary}",
            )
            null
        }
        if (streamed != null && streamed !is OmniRouteTurn.ParseFailed) {
            return VmResult.Success(streamed to assembler.textSoFar)
        }
        // Don't pay for a failing stream attempt on every later turn.
        if (streamKey !in nonStreaming) nonStreaming += streamKey

        val request = buildRequest(target, workingDirectory, allowedTools, history, stream = false)
        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success ->
                VmResult.Success(OmniRouteToolCodec.parseResponse(target.dialect, result.value) to "")
        }
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
        val target = path ?: argument(call.argumentsJson, "url")
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
                summary = summarize(tool, call.name, target),
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
            content = capToolOutput(outcome.output),
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

        if (tool in FILE_TOOLS) {
            val default = if (tool == OmniRouteTool.LIST_DIRECTORY) "." else null
            val path = argument(call.argumentsJson, "path") ?: default
            val problem = path?.let { PathGuard.problem(workingDirectory, it) }
            if (problem != null) return ToolOutcome(problem, isError = true)
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
            OmniRouteTool.WEB_FETCH -> webFetch(call)
        }
    }

    private suspend fun webFetch(call: OmniRouteToolCall): ToolOutcome {
        val url = argument(call.argumentsJson, "url")
            ?: return ToolOutcome("Missing required argument \"url\".", isError = true)
        return when (val result = webFetcher.fetch(url)) {
            is VmResult.Success -> ToolOutcome(result.value, isError = false)
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
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

        return when (val result = remoteFileSystem.readText(serverId, resolved, MAX_READ_BYTES)) {
            is VmResult.Success -> ToolOutcome(result.value, isError = false)
            is VmResult.Failure -> ToolOutcome(
                result.error.summaryWithReason() +
                    ". For a large file, use grep_search, or run_command with head, tail or sed " +
                    "to read the part you need.",
                isError = true,
            )
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
        OmniRouteTool.WEB_FETCH -> "Fetch ${path ?: "web page"}"
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
        if (OmniRouteTool.WEB_FETCH in allowedTools) {
            append("Use web_fetch to read documentation or other public pages when you need them. ")
        }
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
        const val TAG = "OmniRouteAgentLoop"
        val FILE_TOOLS = setOf(
            OmniRouteTool.READ_FILE,
            OmniRouteTool.LIST_DIRECTORY,
            OmniRouteTool.GREP_SEARCH,
            OmniRouteTool.WRITE_FILE,
            OmniRouteTool.EDIT_FILE,
        )
        const val SESSION_PREFIX = "omniroute-"
        const val MAX_ITERATIONS = 25
        const val MAX_SESSIONS = 16
        const val MAX_READ_BYTES = 256L * 1024
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

private fun OmniRouteMessage.withoutImages(): OmniRouteMessage =
    if (this is OmniRouteMessage.User) copy(images = emptyList()) else this

/**
 * Rebuilds plain-text history. Consecutive turns from the same side are merged,
 * because the Anthropic dialect rejects two user (or two assistant) messages in a
 * row, which a failed or stopped run leaves behind.
 */
internal fun List<ConversationTurn>.toMessages(): List<OmniRouteMessage> =
    filter { it.text.isNotBlank() }
        .fold(mutableListOf<ConversationTurn>()) { merged, turn ->
            val last = merged.lastOrNull()
            if (last != null && last.fromUser == turn.fromUser) {
                merged[merged.lastIndex] = last.copy(text = last.text + "\n\n" + turn.text)
            } else {
                merged += turn
            }
            merged
        }
        .map { turn ->
            if (turn.fromUser) {
                OmniRouteMessage.User(turn.text)
            } else {
                OmniRouteMessage.Assistant(text = turn.text, toolCalls = emptyList())
            }
        }

/** One tool result is never allowed to dominate the model's context. */
internal const val MAX_TOOL_OUTPUT_CHARS = 30_000

/**
 * Rough context budget in characters (about 4 per token). Small enough for free
 * models with 32k-token windows to keep room for the reply.
 */
internal const val MAX_HISTORY_CHARS = 100_000

/** Older tool results are shortened to this once the history is over budget. */
internal const val TRIMMED_TOOL_OUTPUT_CHARS = 1_500

internal fun capToolOutput(output: String, limit: Int = MAX_TOOL_OUTPUT_CHARS): String =
    if (output.length <= limit) {
        output
    } else {
        output.take(limit) + "\n\n[output truncated: ${output.length - limit} more characters]"
    }

private fun OmniRouteMessage.size(): Int = when (this) {
    is OmniRouteMessage.User -> text.length
    is OmniRouteMessage.Assistant -> text.orEmpty().length + toolCalls.sumOf { it.argumentsJson.length }
    is OmniRouteMessage.ToolResult -> content.length
}

/**
 * Keeps a long conversation inside the model's context window.
 *
 * First shortens old tool results (the bulk of most histories), keeping the last
 * [keepRecent] messages intact. If that is not enough, drops whole turns from the
 * front, always restarting at a user message so a tool result never appears
 * without the call that produced it.
 */
internal fun trimHistory(
    history: List<OmniRouteMessage>,
    budget: Int = MAX_HISTORY_CHARS,
    keepRecent: Int = 6,
): List<OmniRouteMessage> {
    if (history.sumOf { it.size() } <= budget) return history

    val recentFrom = (history.size - keepRecent).coerceAtLeast(0)
    var trimmed = history.mapIndexed { index, message ->
        if (index < recentFrom && message is OmniRouteMessage.ToolResult) {
            message.copy(content = capToolOutput(message.content, TRIMMED_TOOL_OUTPUT_CHARS))
        } else {
            message
        }
    }

    while (trimmed.sumOf { it.size() } > budget) {
        val nextUser = trimmed.withIndex()
            .drop(1)
            .firstOrNull { it.value is OmniRouteMessage.User }
            ?.index ?: break
        trimmed = trimmed.drop(nextUser)
    }
    return trimmed
}
