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
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.network.http.VmHttpException
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
    private val gitHubRepositoryClient: GitHubRepositoryClient,
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
        fallbackModel: String? = null,
    ): Flow<AgentEvent> = flow {
        val serverId = config.serverId
        val workingDirectory = config.workingDirectory
        val startedAt = System.currentTimeMillis()

        val allowedTools = allowedToolsFor(config)
        // If tools are required and auto/best-free was requested, transparently use the tool-capable free counterpart
        var currentModel = if (allowedTools.isNotEmpty() && (model == "auto/best-free" || model == "best-free")) {
            fallbackModel ?: "auto/coding:free"
        } else {
            model
        }
        // Continue the conversation the chat screen asks to resume, if this process
        // still holds it; otherwise start fresh, exactly as a new chat would.
        val sessionId = config.resumeSessionId?.takeIf { sessions.get(it) != null }
            ?: (SESSION_PREFIX + UUID.randomUUID())
        // In-memory history keeps tool calls; after a restart only the visible
        // transcript the chat screen sends along is left, which is still enough
        // for the model to follow the conversation.
        val previous = sessions.get(sessionId) ?: config.history.toMessages()
        val history = previous.orEmpty().toMutableList()
        history += OmniRouteMessage.User(config.prompt, config.images)

        try {
            emit(
                AgentEvent.SessionStarted(
                    sessionId = sessionId,
                    workingDirectory = workingDirectory,
                    model = currentModel,
                    availableTools = allowedTools.map { it.toolName },
                ),
            )

            var inputTokensTotal = 0L
            var outputTokensTotal = 0L
            val maxIterations = config.maxIterations ?: MAX_ITERATIONS
            val maxCommands = config.maxCommandsPerRun ?: 50
            val commandCount = java.util.concurrent.atomic.AtomicInteger(0)

            repeat(maxIterations) {
                currentCoroutineContext().ensureActive()

                val budget = config.contextChars ?: MAX_HISTORY_CHARS
                var target = GatewayTarget(baseUrl, key, dialect, currentModel, budget)
                var nextTurnResult = nextTurn(target, workingDirectory, allowedTools, history)

                val shouldFailover = when {
                    nextTurnResult is VmResult.Failure -> isFailoverCandidate(nextTurnResult.error)
                    nextTurnResult is VmResult.Success && nextTurnResult.value.first is OmniRouteTurn.GatewayError -> {
                        val gw = nextTurnResult.value.first as OmniRouteTurn.GatewayError
                        gw.retryable || isFailoverCandidate(gw.failure())
                    }
                    else -> false
                }

                if (shouldFailover && fallbackModel != null && currentModel != fallbackModel) {
                    val errMsg = when (nextTurnResult) {
                        is VmResult.Failure -> nextTurnResult.error.summary
                        is VmResult.Success -> (nextTurnResult.value.first as? OmniRouteTurn.GatewayError)?.message.orEmpty()
                    }
                    VmLog.i(LogCategory.AI, TAG, "Turn failed on $currentModel ($errMsg). Failing over to $fallbackModel")
                    emit(
                        AgentEvent.Diagnostic(
                            line = "[Model $currentModel unavailable ($errMsg); routing via fallback $fallbackModel...]",
                            isStderr = false,
                        ),
                    )
                    currentModel = fallbackModel
                    target = GatewayTarget(baseUrl, key, dialect, currentModel, budget)
                    nextTurnResult = nextTurn(target, workingDirectory, allowedTools, history)
                }

                val (turn, streamedText) =
                    when (nextTurnResult) {
                        is VmResult.Failure -> {
                            emit(AgentEvent.Failed(nextTurnResult.error))
                            return@flow
                        }
                        is VmResult.Success -> nextTurnResult.value
                    }

                when (turn) {
                    is OmniRouteTurn.ParseFailed, is OmniRouteTurn.GatewayError -> {
                        emit(AgentEvent.Failed(turn.failure()))
                        return@flow
                    }

                    is OmniRouteTurn.Text -> {
                        turn.usage?.let { (input, output) ->
                            inputTokensTotal += input
                            outputTokensTotal += output
                        }
                        val shown = if (turn.truncated) turn.text + TRUNCATED_NOTE else turn.text
                        if (shown.isNotBlank()) emit(AgentEvent.AssistantMessage(shown))
                        // An assistant turn with no content is rejected by the
                        // Anthropic dialect, so a blank reply is stored as a marker.
                        history += OmniRouteMessage.Assistant(
                            text = turn.text.ifBlank { "(no reply)" },
                            toolCalls = emptyList(),
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
                        val calls = turn.calls.withUniqueIds(usedToolCallIds(history)) {
                            "call_" + UUID.randomUUID().toString().take(TOOL_ID_LENGTH)
                        }
                        history += toolRound(turn.truncated, streamedText, calls) { call ->
                            runToolCall(call, config, autonomyLevel, allowedTools, commandCount, maxCommands)
                        }
                    }
                }
            }

            emit(
                AgentEvent.Failed(
                    VmError.Ai(
                        summary = "Reached the tool-call limit",
                        reason = "Stopped after $maxIterations model turns to avoid a runaway loop.",
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
        } finally {
            // Saved however the run ends (done, stopped, failed, out of turns), so
            // "continue" in the next message still knows what was already changed.
            // Images are sent once; later turns keep only the text.
            // Kept trimmed: up to 16 sessions of untrimmed tool output could hold tens
            // of MB for the life of the process. Twice the send budget keeps headroom.
            val budget = config.contextChars ?: MAX_HISTORY_CHARS
            sessions.put(sessionId, trimHistory(history.closedForResume(), budget * 2))
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
        val isLocalWorkspace = digital.vmstudio.code.core.sftp.fs.RemoteFileSystem.isLocalServer(config.serverId)
        val isGitHubWorkspace = config.workingDirectory.startsWith("/github/")
        add(OmniRouteTool.GREP_SEARCH)
        // Read-only, but it sends a model-chosen URL off the device, so a
        // restricted run or web access disabled does not get it.
        if (!config.restricted && config.webAccessEnabled) {
            add(OmniRouteTool.WEB_FETCH)
        }
        if (!isLocalWorkspace && !isGitHubWorkspace) add(OmniRouteTool.LIST_APKS)
        if (config.permissionMode != AgentPermissionMode.PLAN) {
            add(OmniRouteTool.WRITE_FILE)
            add(OmniRouteTool.EDIT_FILE)
            if (!config.restricted && !isLocalWorkspace && !isGitHubWorkspace) {
                add(OmniRouteTool.RUN_COMMAND)
                add(OmniRouteTool.RUN_BUILD)
            }
        }
    }

    private fun parseGitHubPath(resolvedPath: String): Pair<String, String>? {
        if (!resolvedPath.startsWith("/github/")) return null
        val parts = resolvedPath.removePrefix("/github/").split("/").filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val repoFullName = "${parts[0]}/${parts[1]}"
        val relativePath = parts.drop(2).joinToString("/")
        return repoFullName to relativePath
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
        var droppedConnection = false
        val streamed = if (streamKey in nonStreaming) null else try {
            httpClient.stream(buildRequest(target, workingDirectory, allowedTools, history, stream = true))
                .collect { event ->
                    val delta = assembler.accept(event.data)?.takeIf { it.isNotEmpty() }
                    // Thinking goes above the reply it led to, so it is shown the
                    // moment the reply starts.
                    if (delta != null) assembler.takeReasoning()?.let { emit(AgentEvent.Reasoning(it)) }
                    delta?.let { emit(AgentEvent.AssistantDelta(it)) }
                }
            // A turn that only thought and then called tools shows its thinking too.
            assembler.takeReasoning()?.let { emit(AgentEvent.Reasoning(it)) }
            assembler.finish()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (streamFailure: VmHttpException) {
            // A refused key, a missing model or a rate limit is not "streaming is
            // unsupported": retrying the same turn without streaming hammered a
            // rate-limited gateway and switched streaming off for good.
            if (streamFailure.error.isFinalForTurn()) return VmResult.Failure(streamFailure.error)
            VmLog.i(
                LogCategory.AI,
                TAG,
                "Streaming turn failed, retrying without: ${streamFailure.error.summary}",
            )
            null
        } catch (transport: IOException) {
            // A timeout or dropped connection mid-stream: retry this turn whole
            // rather than failing the run, but keep streaming for later turns.
            VmLog.i(LogCategory.AI, TAG, "Stream dropped, retrying without: ${transport.message}")
            droppedConnection = true
            null
        }
        if (streamed != null && streamed !is OmniRouteTurn.ParseFailed) {
            return VmResult.Success(streamed to assembler.textSoFar)
        }
        // Don't pay for a failing stream attempt on every later turn.
        if (!droppedConnection && streamKey !in nonStreaming) nonStreaming += streamKey

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
        commandCount: java.util.concurrent.atomic.AtomicInteger,
        maxCommands: Int,
    ): OmniRouteMessage.ToolResult {
        val tool = OmniRouteTool.fromToolName(call.name)
        val path = filePathArgument(call.argumentsJson)
        val target = path
            ?: argument(call.argumentsJson, "url")
            ?: argument(call.argumentsJson, "directory")
            ?: argument(call.argumentsJson, "tasks")
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

        val outcome = if (tool == OmniRouteTool.RUN_COMMAND || tool == OmniRouteTool.RUN_BUILD) {
            if (commandCount.get() >= maxCommands) {
                ToolOutcome(
                    "Command limit reached: maximum $maxCommands commands per run allowed. Command skipped.",
                    isError = true,
                )
            } else {
                commandCount.incrementAndGet()
                dispatch(
                    tool = tool,
                    call = call,
                    serverId = config.serverId,
                    workingDirectory = config.workingDirectory,
                    autonomyLevel = autonomyLevel,
                    permissionMode = config.permissionMode,
                    allowedTools = allowedTools,
                )
            }
        } else {
            dispatch(
                tool = tool,
                call = call,
                serverId = config.serverId,
                workingDirectory = config.workingDirectory,
                autonomyLevel = autonomyLevel,
                permissionMode = config.permissionMode,
                allowedTools = allowedTools,
            )
        }

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
            if (tool == OmniRouteTool.GOOGLE_SHEET_READ || tool == OmniRouteTool.GOOGLE_SHEET_APPEND) {
                return ToolOutcome(
                    "Google Sheets operations are unavailable because this agent session has no authenticated Sheets connection.",
                    isError = true,
                )
            }
            return ToolOutcome(
                "\"${tool.toolName}\" is not available in this run's permission mode.",
                isError = true,
            )
        }

        if (tool in FILE_TOOLS) {
            val default = if (tool == OmniRouteTool.LIST_DIRECTORY) "." else null
            val path = filePathArgument(call.argumentsJson) ?: default
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
            OmniRouteTool.RUN_COMMAND -> runCommand(call, serverId, workingDirectory)
            OmniRouteTool.RUN_BUILD -> runBuild(call, serverId, workingDirectory)
            OmniRouteTool.LIST_APKS -> listApks(call, serverId, workingDirectory)
            OmniRouteTool.WEB_FETCH -> webFetch(call)
            OmniRouteTool.GOOGLE_SHEET_READ,
            OmniRouteTool.GOOGLE_SHEET_APPEND -> ToolOutcome(
                "Google Sheets operations are unavailable because this agent session has no authenticated Sheets connection.",
                isError = true,
            )
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

    private suspend fun googleSheetRead(call: OmniRouteToolCall): ToolOutcome {
        val spreadsheetId = argument(call.argumentsJson, "spreadsheet_id")
            ?: return ToolOutcome("Missing required argument \"spreadsheet_id\".", isError = true)
        val range = argument(call.argumentsJson, "range")
            ?: return ToolOutcome("Missing required argument \"range\".", isError = true)
        val encodedRange = java.net.URLEncoder.encode(range, "UTF-8")
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
        return when (val result = webFetcher.fetch(url)) {
            is VmResult.Success -> ToolOutcome(result.value, isError = false)
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
        }
    }

    private suspend fun googleSheetAppend(call: OmniRouteToolCall): ToolOutcome {
        val spreadsheetId = argument(call.argumentsJson, "spreadsheet_id")
            ?: return ToolOutcome("Missing required argument \"spreadsheet_id\".", isError = true)
        val range = argument(call.argumentsJson, "range")
            ?: return ToolOutcome("Missing required argument \"range\".", isError = true)
        val values = argument(call.argumentsJson, "values")
            ?: return ToolOutcome("Missing required argument \"values\".", isError = true)
        return ToolOutcome(
            output = "Appended row to Google Sheet $spreadsheetId at $range with values: $values",
            isError = false,
        )
    }

    private suspend fun readFile(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val path = filePathArgument(call.argumentsJson)
            ?: return ToolOutcome("Missing required argument \"path\".", isError = true)
        val resolved = resolvePath(workingDirectory, path)

        parseGitHubPath(resolved)?.let { (repoFullName, relativePath) ->
            if (relativePath.isBlank()) {
                return ToolOutcome("Cannot read repository root as a file; use list_directory.", isError = true)
            }
            return when (val result = gitHubRepositoryClient.getFileContent(repoFullName, relativePath)) {
                is VmResult.Success -> ToolOutcome(result.value, isError = false)
                is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
            }
        }

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

        parseGitHubPath(resolved)?.let { (repoFullName, relativePath) ->
            return when (val result = gitHubRepositoryClient.getRepositoryTree(repoFullName)) {
                is VmResult.Success -> {
                    val prefix = if (relativePath.isBlank()) "" else "$relativePath/"
                    val immediateChildren = result.value.mapNotNull { item ->
                        if (prefix.isNotEmpty() && !item.path.startsWith(prefix)) return@mapNotNull null
                        val remainder = item.path.removePrefix(prefix)
                        if (remainder.isBlank() || '/' in remainder) return@mapNotNull null
                        val kind = if (item.type == "tree") "d" else "-"
                        "$kind ${item.size.toString().padStart(SIZE_COLUMN_WIDTH)}  $remainder"
                    }
                    ToolOutcome(immediateChildren.joinToString("\n").ifBlank { "(empty directory)" }, isError = false)
                }
                is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
            }
        }

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
        val caseSensitive = booleanArgument(call.argumentsJson, "case_sensitive") ?: false

        parseGitHubPath(resolved)?.let { (repoFullName, relativePath) ->
            return when (val treeResult = gitHubRepositoryClient.getRepositoryTree(repoFullName)) {
                is VmResult.Failure -> ToolOutcome(treeResult.error.summaryWithReason(), isError = true)
                is VmResult.Success -> {
                    val prefix = if (relativePath.isBlank()) "" else "$relativePath/"
                    val regex = runCatching {
                        if (caseSensitive) Regex(query) else Regex(query, RegexOption.IGNORE_CASE)
                    }.getOrElse {
                        Regex(Regex.escape(query), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                    }
                    val matchingFiles = treeResult.value.filter { item ->
                        item.type == "blob" &&
                            (prefix.isEmpty() || item.path.startsWith(prefix)) &&
                            regex.containsMatchIn(item.path)
                    }
                    val candidateBlobs = treeResult.value.filter { item ->
                        item.type == "blob" &&
                            item.size in 1..65_536 &&
                            (prefix.isEmpty() || item.path.startsWith(prefix))
                    }.take(15)
                    val lineMatches = mutableListOf<String>()
                    matchingFiles.forEach { item ->
                        lineMatches += "[file path match] ${item.path}"
                    }
                    for (blob in candidateBlobs) {
                        if (lineMatches.size >= GREP_MAX_LINES) break
                        val contentResult = gitHubRepositoryClient.getFileContent(repoFullName, blob.path)
                        if (contentResult is VmResult.Success) {
                            contentResult.value.lineSequence().forEachIndexed { idx, line ->
                                if (lineMatches.size < GREP_MAX_LINES && regex.containsMatchIn(line)) {
                                    lineMatches += "${blob.path}:${idx + 1}: ${line.trim()}"
                                }
                            }
                        }
                    }
                    ToolOutcome(
                        output = lineMatches.joinToString("\n").ifBlank { "(no matches found for '$query')" },
                        isError = false,
                    )
                }
            }
        }

        if (digital.vmstudio.code.core.sftp.fs.RemoteFileSystem.isLocalServer(serverId) || resolved.startsWith("/vmhub/")) {
            return localGrep(serverId, resolved, query, caseSensitive)
        }

        val command = grepCommand(
            query = query,
            path = resolved,
            caseSensitive = caseSensitive,
        )

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

    private suspend fun localGrep(
        serverId: String,
        rootPath: String,
        query: String,
        caseSensitive: Boolean,
    ): ToolOutcome {
        val regex = runCatching {
            if (caseSensitive) Regex(query) else Regex(query, RegexOption.IGNORE_CASE)
        }.getOrElse {
            Regex(Regex.escape(query), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        }
        val lineMatches = mutableListOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootPath)
        var filesInspected = 0
        while (queue.isNotEmpty() && lineMatches.size < GREP_MAX_LINES && filesInspected < 300) {
            val current = queue.removeFirst()
            when (val listResult = remoteFileSystem.list(serverId, current)) {
                is VmResult.Success -> {
                    for (entry in listResult.value) {
                        val name = entry.name
                        if (name.startsWith(".") || name == "build" || name == "node_modules" || name == ".git") continue
                        if (entry.isDirectory) {
                            queue.add(entry.path)
                        } else {
                            filesInspected++
                            if (regex.containsMatchIn(name)) {
                                lineMatches += "[file path match] ${entry.path}"
                            }
                            if (entry.sizeBytes in 1..65_536 && lineMatches.size < GREP_MAX_LINES) {
                                when (val textResult = remoteFileSystem.readText(serverId, entry.path, 65_536)) {
                                    is VmResult.Success -> {
                                        textResult.value.lineSequence().forEachIndexed { idx, line ->
                                            if (lineMatches.size < GREP_MAX_LINES && regex.containsMatchIn(line)) {
                                                lineMatches += "${entry.path}:${idx + 1}: ${line.trim()}"
                                            }
                                        }
                                    }
                                    is VmResult.Failure -> Unit
                                }
                            }
                        }
                    }
                }
                is VmResult.Failure -> break
            }
        }
        return ToolOutcome(
            output = lineMatches.joinToString("\n").ifBlank { "(no matches found for '$query')" },
            isError = false,
        )
    }

    /**
     * Edits apply without asking only off production, never in MANUAL mode, and only
     * when the run or the standing autonomy level already permits edits.
     */
    private fun canAutoApply(
        environment: ServerEnvironment,
        permissionMode: AgentPermissionMode,
        autonomyLevel: AgentAutonomyLevel,
    ): Boolean = environment != ServerEnvironment.PRODUCTION &&
        permissionMode != AgentPermissionMode.MANUAL &&
        (
            permissionMode == AgentPermissionMode.ACCEPT_EDITS ||
                permissionMode == AgentPermissionMode.BYPASS ||
                autonomyLevel.allows(AgentAutonomyLevel.DEVELOPER)
        )

    private fun findMatchingTarget(content: String, target: String): String? {
        if (content.split(target).size - 1 == 1) return target
        val crlfTarget = target.replace("\r\n", "\n").replace("\n", "\r\n")
        if (content.split(crlfTarget).size - 1 == 1) return crlfTarget
        val lfTarget = target.replace("\r\n", "\n")
        if (content.split(lfTarget).size - 1 == 1) return lfTarget
        return null
    }

    /** Why [target] cannot be replaced in [content], or null when it occurs exactly once. */
    private fun uniqueMatchProblem(content: String, target: String, resolved: String): String? {
        if (findMatchingTarget(content, target) != null) return null
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
        val path = filePathArgument(call.argumentsJson)
        val target = targetContentArgument(call.argumentsJson)
        val replacement = replacementContentArgument(call.argumentsJson)
        if (path == null || target == null || replacement == null) {
            val missing = when {
                path == null -> "path"
                target == null -> "target_content"
                else -> "replacement_content"
            }
            return ToolOutcome("Missing required argument \"$missing\".", isError = true)
        }
        val resolved = resolvePath(workingDirectory, path)

        parseGitHubPath(resolved)?.let { (repoFullName, relativePath) ->
            val existingContent = when (val result = gitHubRepositoryClient.getFileContent(repoFullName, relativePath)) {
                is VmResult.Success -> result.value
                is VmResult.Failure -> return ToolOutcome(
                    "Failed to read GitHub file before edit: ${result.error.summaryWithReason()}",
                    isError = true,
                )
            }
            uniqueMatchProblem(existingContent, target, resolved)?.let { problem ->
                return ToolOutcome(problem, isError = true)
            }
            val matched = findMatchingTarget(existingContent, target) ?: target
            val effectiveReplacement = if (matched.contains("\r\n") && !replacement.contains("\r\n")) {
                replacement.replace("\n", "\r\n")
            } else if (!matched.contains("\r\n") && replacement.contains("\r\n")) {
                replacement.replace("\r\n", "\n")
            } else {
                replacement
            }
            val newContent = existingContent.replace(matched, effectiveReplacement)
            val autoApply = canAutoApply(ServerEnvironment.DEVELOPMENT, permissionMode, autonomyLevel)
            if (!autoApply) {
                val approved = fileEditApprovalGate.request(
                    FileEditApprovalRequest(
                        path = resolved,
                        isNewFile = false,
                        oldContent = existingContent,
                        newContent = newContent,
                        serverId = serverId.ifBlank { "github" },
                        serverName = "GitHub ($repoFullName)",
                        environment = ServerEnvironment.DEVELOPMENT,
                    ),
                )
                if (!approved) {
                    return ToolOutcome("The edit to \"$resolved\" was not approved.", isError = true)
                }
            }
            return when (
                val result = gitHubRepositoryClient.putFileContent(
                    repoFullName = repoFullName,
                    path = relativePath,
                    content = newContent,
                    commitMessage = "Edit $relativePath via X-Codes AI",
                )
            ) {
                is VmResult.Success -> ToolOutcome("Successfully edited $resolved on GitHub.", isError = false)
                is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
            }
        }

        val server = when {
            RemoteFileSystem.isLocalServer(serverId) || resolved.startsWith("/vmhub/") -> null
            else -> when (val result = serverRepository.get(serverId)) {
                is VmResult.Success -> result.value
                is VmResult.Failure -> null
            }
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

        val matched = findMatchingTarget(existingContent, target) ?: target
        val effectiveReplacement = if (matched.contains("\r\n") && !replacement.contains("\r\n")) {
            replacement.replace("\n", "\r\n")
        } else if (!matched.contains("\r\n") && replacement.contains("\r\n")) {
            replacement.replace("\r\n", "\n")
        } else {
            replacement
        }
        val newContent = existingContent.replace(matched, effectiveReplacement)

        val autoApply = canAutoApply(
            environment = server?.environment ?: ServerEnvironment.LOCAL,
            permissionMode = permissionMode,
            autonomyLevel = autonomyLevel,
        )

        if (!autoApply) {
            val approved = fileEditApprovalGate.request(
                FileEditApprovalRequest(
                    path = resolved,
                    isNewFile = false,
                    oldContent = existingContent,
                    newContent = newContent,
                    serverId = server?.id ?: serverId,
                    serverName = server?.name ?: "Phone folder",
                    environment = server?.environment ?: ServerEnvironment.LOCAL,
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
        val path = filePathArgument(call.argumentsJson)
            ?: return ToolOutcome("Missing required argument \"path\".", isError = true)
        val content = contentArgument(call.argumentsJson)
            ?: return ToolOutcome("Missing required argument \"content\".", isError = true)
        val resolved = resolvePath(workingDirectory, path)

        parseGitHubPath(resolved)?.let { (repoFullName, relativePath) ->
            val existingContent = (gitHubRepositoryClient.getFileContent(repoFullName, relativePath) as? VmResult.Success)?.value
            val autoApply = canAutoApply(ServerEnvironment.DEVELOPMENT, permissionMode, autonomyLevel)
            if (!autoApply) {
                val approved = fileEditApprovalGate.request(
                    FileEditApprovalRequest(
                        path = resolved,
                        isNewFile = existingContent == null,
                        oldContent = existingContent,
                        newContent = content,
                        serverId = serverId.ifBlank { "github" },
                        serverName = "GitHub ($repoFullName)",
                        environment = ServerEnvironment.DEVELOPMENT,
                    ),
                )
                if (!approved) {
                    return ToolOutcome("The write to \"$resolved\" was not approved.", isError = true)
                }
            }
            return when (
                val result = gitHubRepositoryClient.putFileContent(
                    repoFullName = repoFullName,
                    path = relativePath,
                    content = content,
                    commitMessage = "Write $relativePath via X-Codes AI",
                )
            ) {
                is VmResult.Success ->
                    ToolOutcome("Wrote ${content.length} characters to $resolved on GitHub.", isError = false)
                is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
            }
        }

        val server = when {
            RemoteFileSystem.isLocalServer(serverId) || resolved.startsWith("/vmhub/") -> null
            else -> when (val result = serverRepository.get(serverId)) {
                is VmResult.Success -> result.value
                is VmResult.Failure -> null
            }
        }

        // Production always requires a human decision, exactly as UserPreferences
        // documents for AgentAutonomyLevel: "the level raises the floor, never the
        // ceiling." MANUAL asks for everything with an effect, by the user's own
        // per-run choice, regardless of the standing autonomy level. Everything else
        // (ACCEPT_EDITS, BYPASS, or DEVELOPER+/FULL_AGENT autonomy) may auto-apply.
        val autoApply = canAutoApply(
            environment = server?.environment ?: ServerEnvironment.LOCAL,
            permissionMode = permissionMode,
            autonomyLevel = autonomyLevel,
        )

        if (!autoApply) {
            // Existence is checked directly rather than inferred from whether the
            // content could be read: a file that exists but is too large to preview
            // is still an overwrite, not a creation, and must not be shown as one.
            val fileExists = when (val result = remoteFileSystem.exists(serverId, resolved)) {
                is VmResult.Success -> result.value
                is VmResult.Failure -> return ToolOutcome(result.error.summaryWithReason(), isError = true)
            }
            val existingContent = if (fileExists) {
                when (val result = remoteFileSystem.readText(serverId, resolved)) {
                    is VmResult.Success -> result.value
                    is VmResult.Failure -> return ToolOutcome(
                        "Failed to read file before write approval: ${result.error.summaryWithReason()}",
                        isError = true,
                    )
                }
            } else {
                null
            }

            val approved = fileEditApprovalGate.request(
                FileEditApprovalRequest(
                    path = resolved,
                    isNewFile = !fileExists,
                    oldContent = existingContent,
                    newContent = content,
                    serverId = server?.id ?: serverId,
                    serverName = server?.name ?: "Phone folder",
                    environment = server?.environment ?: ServerEnvironment.LOCAL,
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

    private suspend fun runCommand(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val requested = argument(call.argumentsJson, "command")
            ?: return ToolOutcome("Missing required argument \"command\".", isError = true)
        if (RemoteFileSystem.isLocalServer(serverId) || workingDirectory.startsWith("/github/") || workingDirectory.startsWith("/vmhub/")) {
            return ToolOutcome(
                "Terminal commands are not supported in local or virtual workspaces. Running commands requires an active SSH VPS server.",
                isError = true,
            )
        }
        // The model is told it works in the project, so its commands run there, not
        // in the SSH login directory; builds and tests get time to finish.
        val directory = workingDirectory.trim().replace("'", "'\\''")
        val command = if (directory.isBlank()) requested else "cd '$directory' && $requested"

        val result = commandGuard.run(serverId, command, AGENT_COMMAND_LIMITS, requestedByAgent = true)
        return when (result) {
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
        OmniRouteTool.RUN_BUILD -> "Build ${path ?: "project"}"
        OmniRouteTool.LIST_APKS -> "List APKs in ${path ?: "."}"
        OmniRouteTool.WEB_FETCH -> "Fetch ${path ?: "web page"}"
        OmniRouteTool.GOOGLE_SHEET_READ -> "Read spreadsheet ${path ?: "?"}"
        OmniRouteTool.GOOGLE_SHEET_APPEND -> "Append spreadsheet rows"
        null -> toolName
    }
    private suspend fun runBuild(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val tasks = argument(call.argumentsJson, "tasks")?.ifBlank { null } ?: "assembleRelease"
        if (!BUILD_TASKS_PATTERN.matches(tasks)) {
            return ToolOutcome(
                "Invalid tasks \"$tasks\". Use Gradle task names only " +
                    "(letters, digits, ':', '-', '_', '.', spaces).",
                isError = true,
            )
        }
        val directory = argument(call.argumentsJson, "directory")?.ifBlank { null } ?: workingDirectory
        PathGuard.problem(workingDirectory, directory)?.let { problem ->
            return ToolOutcome(problem, isError = true)
        }
        if (RemoteFileSystem.isLocalServer(serverId) || workingDirectory.startsWith("/github/") || workingDirectory.startsWith("/vmhub/")) {
            return ToolOutcome(
                "Gradle builds cannot run on phone storage or virtual workspaces. Attach an active SSH build server to compile.",
                isError = true,
            )
        }
        val resolved = resolvePath(workingDirectory, directory)
        val quotedDirectory = resolved.replace("'", "'\\''")
        val command = "cd '$quotedDirectory' && ./gradlew $tasks --no-daemon"
        val result = commandGuard.run(serverId, command, AGENT_COMMAND_LIMITS, requestedByAgent = true)
        return when (result) {
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

    private suspend fun listApks(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val apkDirectory = argument(call.argumentsJson, "directory")?.ifBlank { null } ?: workingDirectory
        PathGuard.problem(workingDirectory, apkDirectory)?.let { problem ->
            return ToolOutcome(problem, isError = true)
        }
        if (RemoteFileSystem.isLocalServer(serverId) || workingDirectory.startsWith("/github/") || workingDirectory.startsWith("/vmhub/")) {
            return ToolOutcome(
                "APK artifact search requires an active SSH Linux build server.",
                isError = true,
            )
        }
        val resolvedApks = resolvePath(workingDirectory, apkDirectory)
        val quotedApks = resolvedApks.replace("'", "'\\''")
        val result = commandGuard.run(
            serverId,
            "cd '$quotedApks' && find . -maxdepth 5 -type f -name '*.apk' -printf '%P (%s bytes)\n'",
            AGENT_COMMAND_LIMITS,
            requestedByAgent = true,
        )
        return when (result) {
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
            is VmResult.Success -> {
                val output = result.value.combinedOutput()
                ToolOutcome(output = output.ifBlank { "(no apk files found)" }, isError = false)
            }
        }
    }


    /** Confines paths to [workingDirectory] to prevent escaping the project root. */
    private fun resolvePath(workingDirectory: String, path: String): String {
        PathGuard.resolve(workingDirectory, path)?.let { return it }
        val root = workingDirectory.trimEnd('/')
        if (root.startsWith("~") && (path.startsWith(root) || !path.startsWith("/"))) {
            val combined = if (path.startsWith(root)) path else "$root/$path"
            return PathGuard.normalize(combined)
        }
        return digital.vmstudio.code.core.sftp.model.RemotePath.confine(root, path) ?: root
    }

    private fun sanitizeJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            val lines = trimmed.lines()
            val withoutFirst = if (lines.isNotEmpty() && lines.first().startsWith("```")) lines.drop(1) else lines
            val withoutLast = if (withoutFirst.isNotEmpty() && withoutFirst.last().trim() == "```") withoutFirst.dropLast(1) else withoutFirst
            return withoutLast.joinToString("\n").trim()
        }
        return trimmed
    }

    private fun parseArgumentsObject(argumentsJson: String): JsonObject? {
        val sanitized = sanitizeJson(argumentsJson)
        val element = runCatching { json.parseToJsonElement(sanitized) }.getOrNull()
        return when (element) {
            is JsonObject -> element
            is JsonPrimitive -> if (element.isString) {
                runCatching { json.parseToJsonElement(sanitizeJson(element.content)) }.getOrNull() as? JsonObject
            } else null
            else -> null
        }
    }

    /** JSON booleans arrive as `true`, not `"true"`; [argument] reads strings only. */
    private fun booleanArgument(argumentsJson: String, key: String): Boolean? {
        val root = parseArgumentsObject(argumentsJson) ?: return null
        return (root[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
    }

    private fun argument(argumentsJson: String, key: String): String? {
        val root = parseArgumentsObject(argumentsJson) ?: return null
        val el = root[key] ?: return null
        return when (el) {
            is JsonPrimitive -> el.content
            is JsonArray -> el.joinToString("\n") { (it as? JsonPrimitive)?.content ?: it.toString() }
            is JsonObject -> el.toString()
        }
    }

    private fun filePathArgument(argumentsJson: String): String? =
        argument(argumentsJson, "path")
            ?: argument(argumentsJson, "file_path")
            ?: argument(argumentsJson, "filePath")
            ?: argument(argumentsJson, "file")

    private fun contentArgument(argumentsJson: String): String? =
        argument(argumentsJson, "content")
            ?: argument(argumentsJson, "text")
            ?: argument(argumentsJson, "file_text")
            ?: argument(argumentsJson, "code")

    private fun targetContentArgument(argumentsJson: String): String? =
        argument(argumentsJson, "target_content")
            ?: argument(argumentsJson, "target")
            ?: argument(argumentsJson, "old_content")
            ?: argument(argumentsJson, "old_str")
            ?: argument(argumentsJson, "old_string")

    private fun replacementContentArgument(argumentsJson: String): String? =
        argument(argumentsJson, "replacement_content")
            ?: argument(argumentsJson, "replacement")
            ?: argument(argumentsJson, "new_content")
            ?: argument(argumentsJson, "new_str")
            ?: argument(argumentsJson, "new_string")

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
        if (OmniRouteTool.GOOGLE_SHEET_READ in allowedTools) {
            append("Use google_sheet_read and google_sheet_append to inspect and write to connected Google Spreadsheets. ")
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
                    "and self-correct any errors. Use run_build for Gradle builds and " +
                    "list_apks to find built APKs. ",
            )
        }
        if (OmniRouteTool.WRITE_FILE !in allowedTools) {
            append(
                "This run only proposes a plan: you cannot write files or run " +
                    "commands, so describe what you would do instead of attempting it. ",
            )
        }
        append(
            "Prefer relative paths under $workingDirectory. For large or multi-step requests, formulate a plan by listing steps first, then execute them sequentially. Validate your results between steps. When done, reply " +
                "with a clear summary of findings or modifications.",
        )
    }

    private companion object {
        const val TAG = "OmniRouteAgentLoop"
        const val TOOL_ID_LENGTH = 12
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
        /**
         * Room for a whole source file in one write; 4096 cut most writes off. Kept
         * at 8192, which every common gateway model accepts as a maximum.
         */
        const val DEFAULT_MAX_TOKENS = 8192
        const val SIZE_COLUMN_WIDTH = 10
        const val COMMAND_TIMEOUT_MILLIS = 10 * 60_000L
        val BUILD_TASKS_PATTERN = Regex("[A-Za-z0-9:_\\-. ]+")

        /** Long enough for a build or test run; output stays at the usual cap. */
        val AGENT_COMMAND_LIMITS = CommandLimits(timeoutMillis = COMMAND_TIMEOUT_MILLIS)
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
 * The history as it can be resumed: images dropped, and a run cut off mid-tool
 * closed properly. Every tool call needs a result and the model must speak last,
 * or the gateway rejects the next request.
 */
internal fun List<OmniRouteMessage>.closedForResume(): List<OmniRouteMessage> {
    val closed = map { it.withoutImages() }.toMutableList()
    val answered = closed.filterIsInstance<OmniRouteMessage.ToolResult>()
        .mapTo(HashSet()) { it.toolCallId }
    val lastAssistant = closed.filterIsInstance<OmniRouteMessage.Assistant>().lastOrNull()
    lastAssistant?.toolCalls.orEmpty().filterNot { it.id in answered }.forEach { call ->
        closed += OmniRouteMessage.ToolResult(
            toolCallId = call.id,
            toolName = call.name,
            content = "Not run: the run stopped before this tool finished.",
            isError = true,
        )
    }
    if (lastAssistant != null && closed.last() !is OmniRouteMessage.Assistant) {
        closed += OmniRouteMessage.Assistant(text = "(The run stopped here.)", toolCalls = emptyList())
    }
    return closed
}

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

/** HTTP failures a non-streaming retry of the same turn cannot fix. */
private fun VmError.isFinalForTurn(): Boolean =
    this is VmError.Authentication || (this as? VmError.Network)?.statusCode in FINAL_STATUSES

private val FINAL_STATUSES = setOf(401, 403, 404, 413, 429)

internal fun isFailoverCandidate(error: VmError): Boolean {
    val status = (error as? VmError.Network)?.statusCode
    val text = listOfNotNull(error.summary, error.reason).joinToString(" ").lowercase()
    return status == 413 || status == 429 || (status != null && status in 500..504) ||
        text.contains("capability_mismatch") ||
        text.contains("supports tool calling") ||
        text.contains("capability mismatch") ||
        text.contains("tool calling") ||
        text.contains("quota") ||
        text.contains("rate limit") ||
        text.contains("tokens per minute") ||
        text.contains("tpm") ||
        text.contains("reduce your message") ||
        text.contains("overloaded") ||
        text.contains("unavailable") ||
        text.contains("not available in the active live catalog")
}

/** What a turn that cannot continue reports to the chat. */
private fun OmniRouteTurn.failure(): VmError = when (this) {
    is OmniRouteTurn.GatewayError -> VmError.Ai(
        summary = "The gateway reported an error",
        reason = message,
        retryable = retryable,
        provider = "omniroute",
    )
    is OmniRouteTurn.ParseFailed -> VmError.Ai(
        summary = "Could not read the model's response",
        reason = reason,
        provider = "omniroute",
    )
    else -> VmError.Ai(summary = "Unexpected model response", provider = "omniroute")
}

/**
 * Runs one round of tool calls, or, when the model's output was cut off at its
 * length limit mid-arguments, runs none: acting on half a file would be worse.
 */
private suspend fun toolRound(
    truncated: Boolean,
    text: String,
    calls: List<OmniRouteToolCall>,
    run: suspend (OmniRouteToolCall) -> OmniRouteMessage.ToolResult,
): List<OmniRouteMessage> {
    if (truncated) return truncatedRound(text, calls)
    val round = mutableListOf<OmniRouteMessage>(
        OmniRouteMessage.Assistant(text = text.ifBlank { null }, toolCalls = calls),
    )
    for (call in calls) {
        currentCoroutineContext().ensureActive()
        round += run(call)
    }
    return round
}

private const val TRUNCATED_NOTE = "\n\n_(Reply cut off at the model's length limit.)_"

/**
 * A tool round the model's output limit cut off: the calls keep their ids (with
 * empty arguments, since the cut JSON may not parse on the gateway) and each is
 * answered with an error that tells the model how to retry.
 */
internal fun truncatedRound(text: String, calls: List<OmniRouteToolCall>): List<OmniRouteMessage> =
    listOf<OmniRouteMessage>(
        OmniRouteMessage.Assistant(
            text = text.ifBlank { null },
            toolCalls = calls.map { it.copy(argumentsJson = "{}") },
        ),
    ) + calls.map { call ->
        OmniRouteMessage.ToolResult(
            toolCallId = call.id,
            toolName = call.name,
            content = "Not run: your output was cut off at the length limit before this call was " +
                "complete. Write large files in smaller parts (several edit_file calls), then continue.",
            isError = true,
        )
    }

/**
 * The search command: secrets the path guard blocks for reads are excluded here
 * too (a project's `.env` or keys were readable through a search), binary files
 * are skipped, `-e` keeps a query like `-> Unit` from being read as an option, and
 * each exclude is its own flag because brace expansion needs bash.
 */
internal fun grepCommand(query: String, path: String, caseSensitive: Boolean): String {
    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    val flags = if (caseSensitive) "-rnI" else "-rniI"
    val excludeFlags = GREP_EXCLUDED_DIRS.map { "--exclude-dir=$it" } +
        GREP_EXCLUDED_FILES.map { "--exclude=$it" }
    val excludes = excludeFlags.joinToString(" ") { quote(it) }
    return "grep $flags $excludes -e ${quote(query)} ${quote(path)} | head -n $GREP_MAX_LINES"
}

private val GREP_EXCLUDED_DIRS = listOf(
    ".git", "node_modules", "build", ".gradle", ".ssh", ".gnupg", ".aws", ".kube", ".docker",
)
private val GREP_EXCLUDED_FILES = listOf(
    ".env", ".env.*", "*.pem", "*.key", "*.p12", "*.pfx", "*.kdbx", "id_rsa*", "id_ed25519*", "id_ecdsa*",
)
private const val GREP_MAX_LINES = 50

/** Old tool output capped, and whole-file write/edit arguments replaced by a note. */
private fun OmniRouteMessage.shortened(): OmniRouteMessage = when (this) {
    is OmniRouteMessage.ToolResult -> copy(content = capToolOutput(content, TRIMMED_TOOL_OUTPUT_CHARS))
    is OmniRouteMessage.Assistant -> copy(
        toolCalls = toolCalls.map { call ->
            if (call.argumentsJson.length <= TRIMMED_TOOL_OUTPUT_CHARS) {
                call
            } else {
                val note = "${call.argumentsJson.length} characters omitted from history"
                call.copy(argumentsJson = "{\"note\":\"$note\"}")
            }
        },
    )
    is OmniRouteMessage.User -> this
}

/**
 * Removes the first assistant turn after the opening prompt that called tools,
 * with the results that answer it, or null when only the recent window is left.
 */
private fun dropOldestToolRound(history: List<OmniRouteMessage>, keepRecent: Int): List<OmniRouteMessage>? {
    val start = history.indexOfFirst { it is OmniRouteMessage.Assistant && it.toolCalls.isNotEmpty() }
    if (start <= 0 || start >= history.size - keepRecent) return null
    var end = start + 1
    while (end < history.size && history[end] is OmniRouteMessage.ToolResult) end++
    if (end >= history.size - keepRecent) return null
    return history.subList(0, start) + history.subList(end, history.size)
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
        if (index < recentFrom) message.shortened() else message
    }

    while (trimmed.sumOf { it.size() } > budget) {
        val nextUser = trimmed.withIndex()
            .drop(1)
            .firstOrNull { it.value is OmniRouteMessage.User }
            ?.index
        trimmed = when {
            nextUser != null -> trimmed.drop(nextUser)
            // One long task has no later prompt to cut at: drop its oldest tool
            // round (the call and its results together) and keep the request.
            else -> dropOldestToolRound(trimmed, keepRecent) ?: break
        }
    }
    // Still too big with only the recent window left: shorten that too rather than
    // send a request the model rejects, which ended the run mid-task.
    if (trimmed.sumOf { it.size() } > budget) trimmed = trimmed.map { it.shortened() }
    return trimmed
}

