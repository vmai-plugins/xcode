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
import digital.vmstudio.code.core.ai.model.PlanStep
import digital.vmstudio.code.core.git.GitError
import digital.vmstudio.code.core.git.GitResult
import digital.vmstudio.code.core.git.GitService
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
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
    private val gitService: GitService,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

        // The same permission-mode selector the Claude Code CLI path honors
        // (--permission-mode plan / --restricted) must mean the same thing here:
        // PLAN offers no tool that changes anything, and restricted drops the tool
        // that runs commands, regardless of the user's standing autonomy level.
        val allowedTools = buildSet {
            add(OmniRouteTool.READ_FILE)
            add(OmniRouteTool.LIST_DIRECTORY)
            add(OmniRouteTool.GREP_SEARCH)
            add(OmniRouteTool.UPDATE_PLAN)
            add(OmniRouteTool.WEB_FETCH)
            add(OmniRouteTool.WEB_SEARCH)
            add(OmniRouteTool.BROWSE_PAGE)
            add(OmniRouteTool.GIT_INSPECT)
            if (config.permissionMode != AgentPermissionMode.PLAN) {
                add(OmniRouteTool.WRITE_FILE)
                add(OmniRouteTool.EDIT_FILE)
                add(OmniRouteTool.DELETE_PATH)
                add(OmniRouteTool.GIT_CHANGE)
                if (!config.restricted) add(OmniRouteTool.RUN_COMMAND)
            }
        }

        try {
            emit(
                AgentEvent.SessionStarted(
                    sessionId = SESSION_PREFIX + UUID.randomUUID(),
                    workingDirectory = workingDirectory,
                    model = model,
                    availableTools = allowedTools.map { it.toolName },
                ),
            )

            val history = mutableListOf<OmniRouteMessage>(OmniRouteMessage.User(config.prompt))
            var inputTokensTotal = 0L
            var outputTokensTotal = 0L

            for (iteration in 1..MAX_ITERATIONS) {
                currentCoroutineContext().ensureActive()

                val requestBody = OmniRouteToolCodec.buildRequestBody(
                    dialect = dialect,
                    model = model,
                    systemPrompt = systemPrompt(workingDirectory, allowedTools),
                    history = history,
                    maxTokens = DEFAULT_MAX_TOKENS,
                    tools = allowedTools.toList(),
                )
                val cleanBase = baseUrl.trimEnd('/').removeSuffix("/v1")
                val request = Request.Builder()
                    .url("$cleanBase/${dialect.chatPath}")
                    .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                    .applyOmniRouteAuth(key, dialect)
                    .build()

                val responseBody = when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
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
                        emit(
                            AgentEvent.Completed(
                                sessionId = null,
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
                            val tool = OmniRouteTool.fromToolName(call.name)
                            val path = argument(call.argumentsJson, "path")
                            // Resolved to an absolute path, not the raw model argument:
                            // a model editing a repo-root file may well call this with
                            // just "README.md", and DiffReviewViewModel derives the
                            // directory to run git in from this path's dirname - a bare
                            // filename with no "/" would make it try to cd into the
                            // filename itself instead of the actual working directory.
                            val resolvedPath = path?.let { resolvePath(workingDirectory, it) }

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
                                serverId = serverId,
                                workingDirectory = workingDirectory,
                                autonomyLevel = autonomyLevel,
                                permissionMode = config.permissionMode,
                                allowedTools = allowedTools,
                                onPlanUpdated = { steps -> emit(AgentEvent.PlanUpdated(steps)) },
                            )

                            emit(
                                AgentEvent.ToolFinished(
                                    toolUseId = call.id,
                                    isError = outcome.isError,
                                    output = outcome.output,
                                ),
                            )
                            history += OmniRouteMessage.ToolResult(
                                toolCallId = call.id,
                                toolName = call.name,
                                content = outcome.output,
                                isError = outcome.isError,
                            )
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

    private suspend fun dispatch(
        tool: OmniRouteTool?,
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
        autonomyLevel: AgentAutonomyLevel,
        permissionMode: AgentPermissionMode,
        allowedTools: Set<OmniRouteTool>,
        onPlanUpdated: suspend (List<PlanStep>) -> Unit,
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
            OmniRouteTool.WRITE_FILE -> writeFile(call, serverId, workingDirectory, autonomyLevel, permissionMode)
            OmniRouteTool.EDIT_FILE -> editFile(call, serverId, workingDirectory, autonomyLevel, permissionMode)
            OmniRouteTool.RUN_COMMAND -> runCommand(call, serverId)
            OmniRouteTool.DELETE_PATH -> deletePath(call, serverId, workingDirectory, autonomyLevel)
            OmniRouteTool.GIT_INSPECT -> gitInspect(call, serverId, workingDirectory)
            OmniRouteTool.GIT_CHANGE -> gitChange(call, serverId, workingDirectory, autonomyLevel)
            OmniRouteTool.UPDATE_PLAN -> updatePlan(call, onPlanUpdated)
            OmniRouteTool.WEB_FETCH -> webFetch(call)
            OmniRouteTool.WEB_SEARCH -> webSearch(call)
            OmniRouteTool.BROWSE_PAGE -> browsePage(call)
        }
    }

    private suspend fun readFile(call: OmniRouteToolCall, serverId: String, workingDirectory: String): ToolOutcome {
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
        val command = "grep $flags --exclude-dir={.git,node_modules,build,.gradle} '$escapedQuery' '$escapedPath' | head -n 50"

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

    private suspend fun editFile(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
        autonomyLevel: AgentAutonomyLevel,
        permissionMode: AgentPermissionMode,
    ): ToolOutcome {
        val path = argument(call.argumentsJson, "path")
            ?: return ToolOutcome("Missing required argument \"path\".", isError = true)
        val target = argument(call.argumentsJson, "target_content")
            ?: return ToolOutcome("Missing required argument \"target_content\".", isError = true)
        val replacement = argument(call.argumentsJson, "replacement_content")
            ?: return ToolOutcome("Missing required argument \"replacement_content\".", isError = true)
        val resolved = resolvePath(workingDirectory, path)

        val server = when (val result = serverRepository.get(serverId)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return ToolOutcome(result.error.summaryWithReason(), isError = true)
        }

        val existingContent = when (val result = remoteFileSystem.readText(serverId, resolved)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return ToolOutcome("Failed to read file before edit: ${result.error.summaryWithReason()}", isError = true)
        }

        val (matchedContent, matchedTarget, replacementToApply) = when {
            existingContent.contains(target) -> Triple(existingContent, target, replacement)
            existingContent.replace("\r\n", "\n").contains(target.replace("\r\n", "\n")) -> {
                Triple(
                    existingContent.replace("\r\n", "\n"),
                    target.replace("\r\n", "\n"),
                    replacement.replace("\r\n", "\n"),
                )
            }
            else -> {
                return ToolOutcome(
                    "Could not find the target_content in \"$resolved\". Ensure target_content exactly matches the existing file contents.",
                    isError = true,
                )
            }
        }

        val occurrences = matchedContent.split(matchedTarget).size - 1
        if (occurrences > 1) {
            return ToolOutcome(
                "target_content matches $occurrences times in \"$resolved\". Please provide more surrounding context so the match is unique.",
                isError = true,
            )
        }

        val newContent = matchedContent.replace(matchedTarget, replacementToApply)

        val autoApply = server.environment != ServerEnvironment.PRODUCTION &&
            permissionMode != AgentPermissionMode.MANUAL &&
            (
                permissionMode == AgentPermissionMode.ACCEPT_EDITS ||
                    permissionMode == AgentPermissionMode.BYPASS ||
                    autonomyLevel.allows(AgentAutonomyLevel.DEVELOPER)
            )

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
        val autoApply = server.environment != ServerEnvironment.PRODUCTION &&
            permissionMode != AgentPermissionMode.MANUAL &&
            (
                permissionMode == AgentPermissionMode.ACCEPT_EDITS ||
                    permissionMode == AgentPermissionMode.BYPASS ||
                    autonomyLevel.allows(AgentAutonomyLevel.DEVELOPER)
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
            is VmResult.Success -> ToolOutcome("Wrote ${content.length} characters to $resolved.", isError = false)
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

    private suspend fun deletePath(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
        autonomyLevel: AgentAutonomyLevel,
    ): ToolOutcome {
        val path = argument(call.argumentsJson, "path")
            ?: return ToolOutcome("Missing required argument \"path\".", isError = true)
        val recursive = argument(call.argumentsJson, "recursive")?.toBooleanStrictOrNull() ?: false
        val resolved = resolvePath(workingDirectory, path)

        if (resolved == "/" || resolved == "~" || resolved == "/etc" || resolved == "/var" || resolved == "/root") {
            return ToolOutcome("Refusing to delete critical system directory \"$resolved\".", isError = true)
        }

        val server = when (val result = serverRepository.get(serverId)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return ToolOutcome(result.error.summaryWithReason(), isError = true)
        }

        if (server.environment == ServerEnvironment.PRODUCTION || !autonomyLevel.allows(AgentAutonomyLevel.DEVELOPER)) {
            val approved = fileEditApprovalGate.request(
                FileEditApprovalRequest(
                    path = resolved,
                    isNewFile = false,
                    oldContent = "[File or directory deletion: $resolved]",
                    newContent = "[DELETED]",
                    serverId = server.id,
                    serverName = server.name,
                    environment = server.environment,
                ),
            )
            if (!approved) {
                return ToolOutcome("The deletion of \"$resolved\" was not approved.", isError = true)
            }
        }

        return when (val result = remoteFileSystem.delete(serverId, resolved, recursive)) {
            is VmResult.Success -> ToolOutcome("Successfully deleted \"$resolved\".", isError = false)
            is VmResult.Failure -> ToolOutcome(result.error.summaryWithReason(), isError = true)
        }
    }

    private suspend fun gitInspect(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
    ): ToolOutcome {
        val action = argument(call.argumentsJson, "action")?.lowercase() ?: "status"

        return when (action) {
            "status" -> {
                when (val result = gitService.status(serverId, workingDirectory)) {
                    is GitResult.Success -> {
                        val s = result.value
                        val text = buildString {
                            appendLine("On branch ${s.branch}")
                            if (s.staged.isNotEmpty()) {
                                appendLine("Changes to be committed:")
                                s.staged.forEach { appendLine("  ${it.status.prefix} ${it.path}") }
                            }
                            if (s.unstaged.isNotEmpty()) {
                                appendLine("Changes not staged for commit:")
                                s.unstaged.forEach { appendLine("  ${it.status.prefix} ${it.path}") }
                            }
                            if (s.untracked.isNotEmpty()) {
                                appendLine("Untracked files:")
                                s.untracked.forEach { appendLine("  ${it.path}") }
                            }
                            if (!s.hasChanges) {
                                appendLine("Working tree clean.")
                            }
                        }.trim()
                        ToolOutcome(text, isError = false)
                    }
                    is GitResult.Failure -> ToolOutcome("Git status failed: ${result.error}", isError = true)
                }
            }
            "diff" -> {
                when (val result = gitService.diffUnstaged(serverId, workingDirectory)) {
                    is GitResult.Success -> {
                        val diffText = result.value.files.joinToString("\n\n") { fileDiff ->
                            "--- ${fileDiff.oldPath ?: "/dev/null"}\n+++ ${fileDiff.newPath ?: "/dev/null"}\n" +
                                fileDiff.lines.joinToString("\n") { it.text }
                        }
                        ToolOutcome(diffText.ifBlank { "(no unstaged changes)" }, isError = false)
                    }
                    is GitResult.Failure -> ToolOutcome("Git diff failed: ${result.error}", isError = true)
                }
            }
            "staged_diff" -> {
                when (val result = gitService.diffStaged(serverId, workingDirectory)) {
                    is GitResult.Success -> {
                        val diffText = result.value.files.joinToString("\n\n") { fileDiff ->
                            "--- ${fileDiff.oldPath ?: "/dev/null"}\n+++ ${fileDiff.newPath ?: "/dev/null"}\n" +
                                fileDiff.lines.joinToString("\n") { it.text }
                        }
                        ToolOutcome(diffText.ifBlank { "(no staged changes)" }, isError = false)
                    }
                    is GitResult.Failure -> ToolOutcome("Git staged diff failed: ${result.error}", isError = true)
                }
            }
            "log" -> {
                when (val result = gitService.log(serverId, workingDirectory, 15)) {
                    is GitResult.Success -> {
                        val entries = result.value.commits.joinToString("\n") { c ->
                            "${c.shortHash} - ${c.author} : ${c.message}"
                        }
                        ToolOutcome(entries.ifBlank { "(no commits)" }, isError = false)
                    }
                    is GitResult.Failure -> ToolOutcome("Git log failed: ${result.error}", isError = true)
                }
            }
            "branches", "branch" -> {
                when (val result = gitService.currentBranch(serverId, workingDirectory)) {
                    is GitResult.Success -> ToolOutcome("Current branch: ${result.value}", isError = false)
                    is GitResult.Failure -> ToolOutcome("Git branch failed: ${result.error}", isError = true)
                }
            }
            else -> ToolOutcome("Unknown git inspect action \"$action\".", isError = true)
        }
    }

    private suspend fun gitChange(
        call: OmniRouteToolCall,
        serverId: String,
        workingDirectory: String,
        autonomyLevel: AgentAutonomyLevel,
    ): ToolOutcome {
        val action = argument(call.argumentsJson, "action")?.lowercase() ?: "commit"
        val message = argument(call.argumentsJson, "message")
        val target = argument(call.argumentsJson, "target")

        val server = when (val result = serverRepository.get(serverId)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return ToolOutcome(result.error.summaryWithReason(), isError = true)
        }

        return when (action) {
            "commit" -> {
                val commitMsg = message?.ifBlank { null } ?: "Update via VMStudio Code agent"
                when (val addRes = gitService.add(serverId, workingDirectory, emptyList())) {
                    is GitResult.Failure -> return ToolOutcome("Git add failed: ${addRes.error}", isError = true)
                    is GitResult.Success -> Unit
                }
                when (val commitRes = gitService.commit(serverId, workingDirectory, commitMsg)) {
                    is GitResult.Success -> ToolOutcome("Committed changes: \"$commitMsg\"", isError = false)
                    is GitResult.Failure -> ToolOutcome("Git commit failed: ${commitRes.error}", isError = true)
                }
            }
            "pull" -> {
                when (val pullRes = gitService.pull(serverId, workingDirectory)) {
                    is GitResult.Success -> ToolOutcome("Successfully pulled latest changes.", isError = false)
                    is GitResult.Failure -> ToolOutcome("Git pull failed: ${pullRes.error}", isError = true)
                }
            }
            "push" -> {
                if (server.environment == ServerEnvironment.PRODUCTION || !autonomyLevel.allows(AgentAutonomyLevel.DEVELOPER)) {
                    val approved = fileEditApprovalGate.request(
                        FileEditApprovalRequest(
                            path = workingDirectory,
                            isNewFile = false,
                            oldContent = "[Local Git Changes]",
                            newContent = "[Git Push to Remote]",
                            serverId = server.id,
                            serverName = server.name,
                            environment = server.environment,
                        ),
                    )
                    if (!approved) {
                        return ToolOutcome("Git push was not approved.", isError = true)
                    }
                }
                when (val pushRes = gitService.push(serverId, workingDirectory)) {
                    is GitResult.Success -> ToolOutcome("Successfully pushed to remote repository.", isError = false)
                    is GitResult.Failure -> ToolOutcome("Git push failed: ${pushRes.error}", isError = true)
                }
            }
            "checkout" -> {
                val branch = target ?: return ToolOutcome("Missing target branch for checkout.", isError = true)
                val safeBranch = branch.replace("'", "")
                val cmd = "git checkout '$safeBranch'"
                when (val res = commandGuard.run(serverId, cmd, requestedByAgent = true)) {
                    is VmResult.Success -> ToolOutcome("Switched to branch $branch.", isError = false)
                    is VmResult.Failure -> ToolOutcome(res.error.summaryWithReason(), isError = true)
                }
            }
            "branch" -> {
                val branch = target ?: return ToolOutcome("Missing target branch name.", isError = true)
                val safeBranch = branch.replace("'", "")
                val cmd = "git checkout -b '$safeBranch'"
                when (val res = commandGuard.run(serverId, cmd, requestedByAgent = true)) {
                    is VmResult.Success -> ToolOutcome("Created and switched to branch $branch.", isError = false)
                    is VmResult.Failure -> ToolOutcome(res.error.summaryWithReason(), isError = true)
                }
            }
            "init" -> {
                val cmd = "git init"
                when (val res = commandGuard.run(serverId, cmd, requestedByAgent = true)) {
                    is VmResult.Success -> ToolOutcome("Initialized empty Git repository.", isError = false)
                    is VmResult.Failure -> ToolOutcome(res.error.summaryWithReason(), isError = true)
                }
            }
            else -> ToolOutcome("Unknown git change action \"$action\".", isError = true)
        }
    }

    private suspend fun updatePlan(
        call: OmniRouteToolCall,
        onPlanUpdated: suspend (List<PlanStep>) -> Unit,
    ): ToolOutcome {
        val root = runCatching { json.parseToJsonElement(call.argumentsJson) }.getOrNull() as? JsonObject
            ?: return ToolOutcome("Invalid arguments JSON.", isError = true)
        val stepsArray = root["steps"] as? JsonArray
            ?: return ToolOutcome("Missing or invalid \"steps\" array.", isError = true)

        val planSteps = stepsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val desc = (obj["description"] as? JsonPrimitive)?.content
                ?: (obj["step"] as? JsonPrimitive)?.content
                ?: (obj["title"] as? JsonPrimitive)?.content
                ?: return@mapNotNull null
            val status = (obj["status"] as? JsonPrimitive)?.content ?: "pending"
            PlanStep(step = desc, status = status)
        }

        if (planSteps.isEmpty()) {
            return ToolOutcome("No valid plan steps provided.", isError = true)
        }

        onPlanUpdated(planSteps)
        val summary = planSteps.joinToString("\n") { "- [${it.status}] ${it.step}" }
        return ToolOutcome("Plan updated:\n$summary", isError = false)
    }

    private suspend fun webFetch(call: OmniRouteToolCall): ToolOutcome {
        val url = argument(call.argumentsJson, "url")
            ?: return ToolOutcome("Missing required argument \"url\".", isError = true)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolOutcome("Invalid URL: must start with http:// or https://", isError = true)
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) VMStudioCode/0.4.0")
            .get()
            .build()

        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> ToolOutcome("Failed to fetch $url: ${result.error.summaryWithReason()}", isError = true)
            is VmResult.Success -> {
                val clean = result.value
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&nbsp;"), " ")
                    .replace(Regex("&amp;"), "&")
                    .replace(Regex("&lt;"), "<")
                    .replace(Regex("&gt;"), ">")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val truncated = if (clean.length > 8000) clean.take(8000) + "\n...[truncated]" else clean
                ToolOutcome(truncated.ifBlank { "(empty page)" }, isError = false)
            }
        }
    }

    private suspend fun webSearch(call: OmniRouteToolCall): ToolOutcome {
        val query = argument(call.argumentsJson, "query")
            ?: return ToolOutcome("Missing required argument \"query\".", isError = true)

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> ToolOutcome("Search failed: ${result.error.summaryWithReason()}", isError = true)
            is VmResult.Success -> {
                val html = result.value
                val snippetRegex = Regex("""<a class="result__snippet[^"]*"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
                val titleRegex = Regex("""<a class="result__url[^"]*"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)

                val snippets = snippetRegex.findAll(html).map {
                    it.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").replace("&amp;", "&").trim()
                }.take(5).toList()

                val titles = titleRegex.findAll(html).map {
                    it.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
                }.take(5).toList()

                if (snippets.isEmpty()) {
                    val clean = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                    val fallback = if (clean.length > 1000) clean.take(1000) + "..." else clean
                    ToolOutcome(fallback.ifBlank { "(no search results found)" }, isError = false)
                } else {
                    val formatted = snippets.mapIndexed { idx, snippet ->
                        val link = titles.getOrNull(idx)?.let { " [$it]" } ?: ""
                        "${idx + 1}.$link $snippet"
                    }.joinToString("\n\n")
                    ToolOutcome(formatted, isError = false)
                }
            }
        }
    }

    private suspend fun browsePage(call: OmniRouteToolCall): ToolOutcome {
        val url = argument(call.argumentsJson, "url")
            ?: return ToolOutcome("Missing required argument \"url\".", isError = true)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolOutcome("Invalid URL: must start with http:// or https://", isError = true)
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) VMStudioCode/0.4.0")
            .get()
            .build()

        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> ToolOutcome("Failed to browse $url: ${result.error.summaryWithReason()}", isError = true)
            is VmResult.Success -> {
                val html = result.value
                val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
                val title = titleRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: "No title"

                val linkRegex = Regex("<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"[^>]*>(.*?)</a>", RegexOption.IGNORE_CASE)
                val links = linkRegex.findAll(html).mapNotNull {
                    val href = it.groupValues[1].trim()
                    val text = it.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                    if (href.isNotBlank() && !href.startsWith("#") && !href.startsWith("javascript:")) {
                        "- $text ($href)"
                    } else null
                }.distinct().take(10).joinToString("\n")

                val clean = html
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&nbsp;"), " ")
                    .replace(Regex("&amp;"), "&")
                    .replace(Regex("&lt;"), "<")
                    .replace(Regex("&gt;"), ">")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val summary = if (clean.length > 3000) clean.take(3000) + "\n...[truncated]" else clean

                val report = buildString {
                    appendLine("🌐 Browse Result: $url")
                    appendLine("Title: $title")
                    if (links.isNotBlank()) {
                        appendLine("\nKey Page Links:")
                        appendLine(links)
                    }
                    appendLine("\nPage Content:")
                    appendLine(summary.ifBlank { "(empty page)" })
                }
                ToolOutcome(report, isError = false)
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
        OmniRouteTool.DELETE_PATH -> "Delete ${path ?: "?"}"
        OmniRouteTool.GIT_INSPECT -> "Git inspect"
        OmniRouteTool.GIT_CHANGE -> "Git change"
        OmniRouteTool.UPDATE_PLAN -> "Update plan"
        OmniRouteTool.WEB_FETCH -> "Fetch web page"
        OmniRouteTool.WEB_SEARCH -> "Web search"
        OmniRouteTool.BROWSE_PAGE -> "Browse ${path ?: "page"}"
        null -> toolName
    }

    /** Absolute and home-relative paths pass through; anything else is relative to [workingDirectory]. */
    private fun resolvePath(workingDirectory: String, path: String): String =
        if (path.startsWith("/") || path.startsWith("~")) path else "${workingDirectory.trimEnd('/')}/$path"

    private fun argument(argumentsJson: String, key: String): String? {
        val root = runCatching { json.parseToJsonElement(argumentsJson) }.getOrNull() as? JsonObject ?: return null
        return (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun VmError.summaryWithReason(): String = reason?.let { "$summary: $it" } ?: summary

    private fun systemPrompt(workingDirectory: String, allowedTools: Set<OmniRouteTool>): String = buildString {
        append(
            "You are VMStudio Code, an autonomous AI coding agent running inside $workingDirectory on a remote " +
                "server, reached through tools. First inspect the project structure with list_directory, read_file, or grep_search. ",
        )
        if (OmniRouteTool.EDIT_FILE in allowedTools) {
            append(
                "When modifying existing files, always prefer edit_file to replace targeted code sections precisely. ",
            )
        }
        if (OmniRouteTool.WRITE_FILE in allowedTools) {
            append(
                "Use write_file to create new files or rewrite small files entirely. ",
            )
        }
        if (OmniRouteTool.RUN_COMMAND in allowedTools) {
            append("Use run_command to run build, test, git, and lint commands, inspect outputs, and self-correct any errors. ")
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
        const val DEFAULT_MAX_TOKENS = 4096
        const val SIZE_COLUMN_WIDTH = 10
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
