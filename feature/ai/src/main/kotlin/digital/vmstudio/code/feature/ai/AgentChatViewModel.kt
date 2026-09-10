package digital.vmstudio.code.feature.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.model.toPermissionMode
import digital.vmstudio.code.core.ai.provider.AiProviderRegistry
import digital.vmstudio.code.core.ai.provider.AiProviderHealth
import digital.vmstudio.code.core.ai.repository.AgentConversationRepository
import digital.vmstudio.code.core.ai.repository.AgentTaskRepository
import digital.vmstudio.code.core.ai.background.BackgroundAgentRunner
import digital.vmstudio.code.core.ai.background.BackgroundRun
import digital.vmstudio.code.core.ai.repository.StoredEntry
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One rendered entry in the transcript. */
sealed interface TranscriptItem {
    val id: String

    data class UserPrompt(override val id: String, val text: String) : TranscriptItem

    data class AssistantText(override val id: String, val text: String) : TranscriptItem

    /** The reply as it is still being generated. */
    data class StreamingText(override val id: String, val text: String) : TranscriptItem

    data class Reasoning(override val id: String, val text: String) : TranscriptItem

    data class ToolCall(
        override val id: String,
        val name: String,
        val summary: String,
        val affectedPath: String?,
        val isRunning: Boolean,
        val isError: Boolean = false,
        val output: String? = null,
    ) : TranscriptItem

    data class Diagnostic(
        override val id: String,
        val text: String,
        val isStderr: Boolean,
    ) : TranscriptItem

    data class RunSummary(
        override val id: String,
        val durationMillis: Long,
        val costUsd: Double,
        val inputTokens: Long,
        val outputTokens: Long,
        val isError: Boolean,
    ) : TranscriptItem

    data class Failure(override val id: String, val error: VmError) : TranscriptItem
}

data class AgentChatUiState(
    val serverId: String? = null,
    val workingDirectory: String = "",
    val transcript: List<TranscriptItem> = emptyList(),
    val isRunning: Boolean = false,
    val permissionMode: AgentPermissionMode = AgentPermissionMode.PLAN,
    val health: AiProviderHealth? = null,
    val isCheckingHealth: Boolean = false,
    val conversationId: String? = null,
    /** Present once a run reports one; enables continuing rather than starting cold. */
    val providerSessionId: String? = null,
    /** Detached runs for this project, as the server reports them. */
    val backgroundRuns: List<BackgroundRun> = emptyList(),
    val isStartingBackgroundRun: Boolean = false,
    /**
     * Set when a detached run has just started and the watcher should begin.
     *
     * Carried in state rather than started here directly: the service lives in the
     * app module, and a feature module must not depend on it.
     */
    val watchServerId: String? = null,
    val error: VmError? = null,
) {
    /** The chat-only backend has no server or working directory to satisfy. */
    val canRun: Boolean
        get() = serverId != null &&
            workingDirectory.isNotBlank() &&
            !isRunning &&
            health?.isAvailable == true

    val isResuming: Boolean get() = providerSessionId != null
}

@HiltViewModel
class AgentChatViewModel @Inject constructor(
    private val providers: AiProviderRegistry,
    private val conversations: AgentConversationRepository,
    private val tasks: AgentTaskRepository,
    private val remoteFileSystem: RemoteFileSystem,
    private val backgroundRunner: BackgroundAgentRunner,
    private val preferences: UserPreferencesSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String? = savedStateHandle[ARG_SERVER_ID]

    /** Set when reopening a stored conversation rather than starting a new one. */
    private val existingConversationId: String? = savedStateHandle[ARG_CONVERSATION_ID]

    /** Supplied when the agent was opened from a project; decoded from the route. */
    private val projectPath: String? = savedStateHandle.get<String>(ARG_PATH)
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    private val _uiState = MutableStateFlow(AgentChatUiState(serverId = serverId))
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private var itemCounter = 0L

    init {
        applyStoredAutonomy()
        checkHealth()
        when {
            // Reopening a stored conversation: its own directory and session id are
            // authoritative, so nothing is probed or prefilled.
            existingConversationId != null -> restoreConversation(existingConversationId)
            // A project supplies its own directory; probing the home directory is the
            // fallback for an agent opened straight from a server.
            !projectPath.isNullOrBlank() ->
                _uiState.update { it.copy(workingDirectory = projectPath) }
            else -> serverId?.let(::prefillWorkingDirectory)
        }
        refreshBackgroundRuns()
    }

    /**
     * Rebuilds a stored transcript so a conversation survives leaving the screen.
     *
     * The provider session id is restored with it, which is what makes the next
     * prompt continue on the server rather than start a cold conversation.
     */
    private fun restoreConversation(conversationId: String) {
        viewModelScope.launch {
            val stored = conversations.get(conversationId) ?: return@launch
            val restored = conversations.transcript(conversationId).map { it.toTranscriptItem() }

            _uiState.update {
                it.copy(
                    conversationId = conversationId,
                    providerSessionId = stored.providerSessionId,
                    workingDirectory = stored.workingDirectory ?: it.workingDirectory,
                    transcript = restored,
                )
            }
        }
    }

    private fun StoredEntry.toTranscriptItem(): TranscriptItem = when (this) {
        is StoredEntry.User -> TranscriptItem.UserPrompt(nextId(), text)

        is StoredEntry.Assistant -> TranscriptItem.AssistantText(nextId(), text)

        is StoredEntry.Reasoning -> TranscriptItem.Reasoning(nextId(), text)

        is StoredEntry.Tool -> TranscriptItem.ToolCall(
            id = "tool-$callId",
            name = name,
            // The human-readable summary is not persisted, so the tool name stands in.
            summary = name,
            affectedPath = null,
            // Anything still in flight died with the process that ran it.
            isRunning = false,
            isError = isError,
            output = output,
        )

        is StoredEntry.Failed -> TranscriptItem.Failure(
            id = nextId(),
            error = VmError.Ai(summary = summary, provider = "restored"),
        )
    }

    // --- detached runs ----------------------------------------------------------

    /**
     * Starts the prompt as a detached run and returns immediately.
     *
     * The point of the feature: the run lives on the server, so closing the app — or
     * losing signal on a train — no longer kills it. Nothing streams back here,
     * because the CLI refuses `--bg` together with the JSON output format; progress
     * is polled via [refreshBackgroundRuns] and read with [backgroundLogs].
     */
    fun sendInBackground(prompt: String) {
        val state = _uiState.value
        val server = state.serverId ?: return
        if (prompt.isBlank() || state.isStartingBackgroundRun) return

        _uiState.update { it.copy(isStartingBackgroundRun = true, error = null) }
        viewModelScope.launch {
            val result = backgroundRunner.start(
                serverId = server,
                workingDirectory = state.workingDirectory.trim(),
                prompt = prompt,
                permissionMode = state.permissionMode,
            )
            when (result) {
                is VmResult.Success -> {
                    append(
                        TranscriptItem.Diagnostic(
                            id = nextId(),
                            text = "Started in the background as ${result.value}. " +
                                "It keeps running if you leave the app.",
                            isStderr = false,
                        ),
                    )
                    _uiState.update { it.copy(watchServerId = server) }
                    refreshBackgroundRuns()
                }
                is VmResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
            _uiState.update { it.copy(isStartingBackgroundRun = false) }
        }
    }

    /** Re-reads detached runs for this project from the server. */
    fun refreshBackgroundRuns() {
        val state = _uiState.value
        val server = state.serverId ?: return
        val directory = state.workingDirectory.trim().takeIf { it.isNotBlank() } ?: return

        viewModelScope.launch {
            when (val result = backgroundRunner.list(server, directory)) {
                is VmResult.Success -> _uiState.update { current ->
                    // Only detached runs are actionable; the user's own interactive
                    // sessions on the same box are none of this screen's business.
                    current.copy(backgroundRuns = result.value.filter { it.isBackground })
                }
                // A polling failure is not worth a banner over a working transcript.
                is VmResult.Failure -> Unit
            }
        }
    }

    fun stopBackgroundRun(runId: String) {
        val server = _uiState.value.serverId ?: return
        viewModelScope.launch {
            when (val result = backgroundRunner.stop(server, runId)) {
                is VmResult.Success -> refreshBackgroundRuns()
                is VmResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    /** Pulls a detached run's output into the transcript as rendered text. */
    fun backgroundLogs(runId: String) {
        val server = _uiState.value.serverId ?: return
        viewModelScope.launch {
            when (val result = backgroundRunner.logs(server, runId)) {
                is VmResult.Success -> append(
                    TranscriptItem.Diagnostic(
                        id = nextId(),
                        text = result.value.ifBlank { "No output yet from $runId." },
                        isStderr = false,
                    ),
                )
                is VmResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    /**
     * Seeds the per-run permission mode from the Settings autonomy level.
     *
     * Settings holds the default and the chat chips override it for a single run;
     * before this the chat always started at PLAN and the stored preference was
     * inert.
     */
    private fun applyStoredAutonomy() {
        viewModelScope.launch {
            val level = preferences.preferences.first().agentAutonomyLevel
            _uiState.update { it.copy(permissionMode = level.toPermissionMode()) }
        }
    }

    /** Defaults the agent's scope to the login home directory rather than to `/`. */
    private fun prefillWorkingDirectory(server: String) {
        viewModelScope.launch {
            when (val home = remoteFileSystem.homeDirectory(server)) {
                is VmResult.Success -> _uiState.update {
                    if (it.workingDirectory.isBlank()) it.copy(workingDirectory = home.value) else it
                }
                is VmResult.Failure -> Unit // Not fatal; the user can type a path.
            }
        }
    }

    fun checkHealth() {
        _uiState.update { it.copy(isCheckingHealth = true) }
        viewModelScope.launch {
            when (val result = providers.checkHealth(providers.active(), serverId)) {
                is VmResult.Success -> _uiState.update {
                    it.copy(health = result.value, isCheckingHealth = false)
                }
                is VmResult.Failure -> _uiState.update {
                    it.copy(isCheckingHealth = false, error = result.error)
                }
            }
        }
    }

    fun setWorkingDirectory(path: String) {
        _uiState.update { it.copy(workingDirectory = path) }
    }

    fun setPermissionMode(mode: AgentPermissionMode) {
        _uiState.update { it.copy(permissionMode = mode) }
    }

    fun send(prompt: String) {
        val state = _uiState.value
        val server = state.serverId ?: return
        if (prompt.isBlank() || state.isRunning) return

        append(TranscriptItem.UserPrompt(nextId(), prompt))
        _uiState.update { it.copy(isRunning = true, error = null) }

        runJob = viewModelScope.launch {
            val conversationId = state.conversationId ?: createConversation(server, state)
            if (conversationId == null) {
                _uiState.update { it.copy(isRunning = false) }
                return@launch
            }
            conversations.appendUserPrompt(conversationId, prompt)

            // The Tasks record (§28): one row per run when the agent works inside a
            // project. A run opened straight from a server stays conversation-only.
            val taskId = tasks.start(
                conversationId = conversationId,
                title = prompt,
                projectId = tasks.findProjectId(server, state.workingDirectory.trim()),
            )

            val config = AgentRunConfig(
                serverId = server,
                workingDirectory = state.workingDirectory.trim(),
                prompt = prompt,
                resumeSessionId = state.providerSessionId,
                permissionMode = state.permissionMode,
                autoCompactTokens = preferences.preferences.first().agentMaxContextTokens,
            )

            try {
                providers.active().run(config).collect { event ->
                    conversations.record(conversationId, event)
                    applyTaskEvent(taskId, event)
                    applyEvent(event)
                }
            } catch (cancelled: CancellationException) {
                // stop() cancels this job; the task row must not outlive it as RUNNING.
                taskId?.let { tasks.cancel(it) }
                throw cancelled
            }
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    /**
     * Mirrors run events into the Tasks record. A bookkeeping failure must never
     * surface as a chat error, so results are ignored here by design.
     */
    private suspend fun applyTaskEvent(taskId: String?, event: AgentEvent) {
        if (taskId == null) return
        when (event) {
            is AgentEvent.ToolStarted -> {
                tasks.recordToolCall(taskId)
                event.affectedPath?.let { tasks.recordChangedFile(taskId, it) }
            }

            is AgentEvent.Completed ->
                if (event.isError) {
                    tasks.fail(taskId, event.resultText ?: "The run ended with an error")
                } else {
                    tasks.complete(taskId, event.inputTokens, event.outputTokens)
                }

            is AgentEvent.Failed -> tasks.fail(taskId, event.error.summary)

            else -> Unit
        }
    }

    /** Stops the run. Cancelling collection terminates the remote process too. */
    fun stop() {
        runJob?.cancel()
        runJob = null
        _uiState.update { it.copy(isRunning = false) }
        append(
            TranscriptItem.Diagnostic(
                id = nextId(),
                text = "Stopped by you. Work already applied on the server is not undone.",
                isStderr = false,
            ),
        )
    }

    /** Forgets the provider session so the next prompt starts a fresh conversation. */
    fun startNewConversation() {
        runJob?.cancel()
        _uiState.update {
            it.copy(
                transcript = emptyList(),
                conversationId = null,
                providerSessionId = null,
                isRunning = false,
                error = null,
            )
        }
    }

    /** Clears the watch signal once the app layer has acted on it. */
    fun onWatchStarted() {
        _uiState.update { it.copy(watchServerId = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun createConversation(server: String, state: AgentChatUiState): String? =
        when (
            val created = conversations.create(
                title = state.workingDirectory.substringAfterLast('/').ifBlank { "Agent" },
                providerId = providers.active().kind.name,
                modelId = state.health?.version.orEmpty(),
                serverId = server,
                workingDirectory = state.workingDirectory.trim(),
            )
        ) {
            is VmResult.Success -> created.value.also { id ->
                _uiState.update { it.copy(conversationId = id) }
            }
            is VmResult.Failure -> {
                _uiState.update { it.copy(error = created.error) }
                null
            }
        }

    private fun applyEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.SessionStarted ->
                _uiState.update { it.copy(providerSessionId = event.sessionId) }

            // The finished message replaces whatever the deltas were building, so the
            // authoritative text wins and a dropped delta cannot corrupt the reply.
            is AgentEvent.AssistantMessage -> _uiState.update {
                it.copy(
                    transcript = it.transcript.dropStreamingDraft() +
                        TranscriptItem.AssistantText(nextId(), event.text),
                )
            }

            is AgentEvent.AssistantDelta -> _uiState.update {
                it.copy(transcript = it.transcript.appendDelta(event.text, STREAMING_ID))
            }

            is AgentEvent.Reasoning ->
                append(TranscriptItem.Reasoning(nextId(), event.text))

            is AgentEvent.ToolStarted -> append(
                TranscriptItem.ToolCall(
                    id = toolItemId(event.toolUseId),
                    name = event.name,
                    summary = event.summary,
                    affectedPath = event.affectedPath,
                    isRunning = true,
                ),
            )

            is AgentEvent.ToolFinished -> _uiState.update { state ->
                state.copy(
                    transcript = state.transcript.finishToolCall(
                        toolItemId = event.toolUseId.takeIf { it.isNotBlank() }?.let(::toolItemId),
                        isError = event.isError,
                        output = event.output,
                    ),
                )
            }

            is AgentEvent.Completed -> append(
                TranscriptItem.RunSummary(
                    id = nextId(),
                    durationMillis = event.durationMillis,
                    costUsd = event.costUsd,
                    inputTokens = event.inputTokens,
                    outputTokens = event.outputTokens,
                    isError = event.isError,
                ),
            )

            is AgentEvent.Failed -> append(TranscriptItem.Failure(nextId(), event.error))

            is AgentEvent.Diagnostic ->
                append(TranscriptItem.Diagnostic(nextId(), event.line, event.isStderr))
        }
    }

    private fun append(item: TranscriptItem) {
        _uiState.update { it.copy(transcript = it.transcript + item) }
    }

    private fun toolItemId(toolUseId: String): String =
        if (toolUseId.isBlank()) nextId() else "tool-$toolUseId"

    private fun nextId(): String = "item-${itemCounter++}"

    companion object {
        const val ARG_SERVER_ID = "serverId"
        const val ARG_PATH = "path"
        const val ARG_CONVERSATION_ID = "conversationId"

        /** Stable id so the in-progress reply keeps its place in the list. */
        private const val STREAMING_ID = "streaming-draft"
    }
}
