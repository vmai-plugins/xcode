package digital.vmstudio.code.feature.ai

import android.net.Uri
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.background.AgentWakeLockManager
import digital.vmstudio.code.core.ai.background.BackgroundAgentRunner
import digital.vmstudio.code.core.ai.background.OmniRouteBackgroundRunner
import digital.vmstudio.code.core.ai.background.BackgroundRun
import digital.vmstudio.code.core.ai.execution.AgentExecutionCoordinator
import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.model.ConversationTurn
import digital.vmstudio.code.core.ai.model.toPermissionMode
import digital.vmstudio.code.core.ai.omniroute.OmniRouteModelCatalog
import digital.vmstudio.code.core.ai.omniroute.OmniRouteModels
import digital.vmstudio.code.core.ai.provider.AiProviderHealth
import digital.vmstudio.code.core.ai.provider.AiProviderRegistry
import digital.vmstudio.code.core.ai.repository.AgentConversationRepository
import digital.vmstudio.code.core.ai.repository.AgentTaskRepository
import digital.vmstudio.code.core.ai.repository.StoredEntry
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.git.GitHubRepository
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import digital.vmstudio.code.core.git.GitHubTreeItem
import digital.vmstudio.code.core.git.GitError
import digital.vmstudio.code.core.git.GitResult
import digital.vmstudio.code.core.git.GitService
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/** SavedStateHandle argument keys */
const val ARG_SERVER_ID = "serverId"
const val ARG_CONVERSATION_ID = "conversationId"
const val ARG_PATH = "path"
const val ARG_INITIAL_PROMPT = "initialPrompt"

/** Streaming/background constants */
private const val STREAMING_ID = "__streaming__"

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
        val isReverted: Boolean = false,
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

    data class SubagentRun(
        override val id: String,
        val subagentId: String,
        val role: String,
        val prompt: String,
        val isRunning: Boolean,
        val summary: String? = null,
        val isError: Boolean = false,
    ) : TranscriptItem

    data class Failure(override val id: String, val error: VmError) : TranscriptItem
}

data class QueuedMessage(
    val id: String,
    val prompt: String,
    val attachments: List<Uri> = emptyList(),
)

data class AgentChatUiState(
    val serverId: String? = null,
    val workingDirectory: String = "",
    val transcript: List<TranscriptItem> = emptyList(),
    val isRunning: Boolean = false,
    val permissionMode: AgentPermissionMode = AgentPermissionMode.ACCEPT_EDITS,
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
    val initialPrompt: String? = null,
    val error: VmError? = null,
    val availableProjects: List<Project> = emptyList(),
    val selectedModel: String = "",
    /** The OmniRoute gateway's available models. */
    val omniModels: OmniRouteModels = OmniRouteModels(),
    val isSyncingModels: Boolean = false,
    val discoveredGitRepos: List<digital.vmstudio.code.core.git.DiscoveredGitRepo> = emptyList(),
    val isDiscoveringGitRepos: Boolean = false,
    val gitDiscoveryError: VmError? = null,
    val githubRepositories: List<GitHubRepository> = emptyList(),
    val isLoadingGithubRepositories: Boolean = false,
    val githubError: VmError? = null,
    val githubImportingRepo: String? = null,
    val availableServers: List<ChatServerOption> = emptyList(),
    val queuedMessages: List<QueuedMessage> = emptyList(),
    val githubFileTree: List<GitHubTreeItem> = emptyList(),
    val isLoadingGithubFileTree: Boolean = false,
    val squadPreset: digital.vmstudio.code.core.ai.model.AgentSquadPreset = digital.vmstudio.code.core.ai.model.AgentSquadPreset.FULL_STACK,
    val chatBubbleColorHex: String? = null,
) {
    /**
     * Claude Code runs on a server, in a folder. A gateway model needs neither: with
     * no project it is a general chat, like the Claude app's.
     */
    val canRun: Boolean
        get() = selectedModel.isNotBlank() && (!isRunning || queuedMessages.isNotEmpty()) && (health == null || health?.isAvailable == true)

    /** Whether the user can send/queue a message right now. */
    val canAcceptInput: Boolean
        get() = selectedModel.isNotBlank() && (health == null || health?.isAvailable == true)

    /** A server and folder are chosen; tools and Claude Code have somewhere to work. */
    val hasProject: Boolean
        get() = workingDirectory.isNotBlank() &&
            (serverId != null || workingDirectory.startsWith("/github/") || workingDirectory.startsWith("/vmhub/"))

    val isResuming: Boolean get() = providerSessionId != null

    val totalTokens: Long
        get() = transcript.filterIsInstance<TranscriptItem.RunSummary>()
            .sumOf { it.inputTokens + it.outputTokens }

    val totalCostUsd: Double
        get() = transcript.filterIsInstance<TranscriptItem.RunSummary>()
            .sumOf { it.costUsd }
}

data class ChatServerOption(
    val id: String,
    val name: String,
    val target: String,
)

@Suppress("LongParameterList")
@HiltViewModel
class AgentChatViewModel @Inject constructor(
    private val providers: AiProviderRegistry,
    private val conversations: AgentConversationRepository,
    private val tasks: AgentTaskRepository,
    private val remoteFileSystem: RemoteFileSystem,
    private val backgroundRunner: BackgroundAgentRunner,
    private val serverSideRunner: OmniRouteBackgroundRunner,
    private val preferences: UserPreferencesSource,
    private val projectRepository: ProjectRepository,
    private val gitService: GitService,
    private val modelCatalog: OmniRouteModelCatalog,
    private val attachmentStager: AttachmentStager,
    private val preferenceStore: UserPreferencesRepository,
    private val serverRepository: ServerRepository,
    private val githubRepositoryClient: GitHubRepositoryClient,
    private val wakeLockManager: AgentWakeLockManager,
    private val credentialStore: SecureCredentialStore,
    @ApplicationContext private val context: Context,
    private val projectHubClient: digital.vmstudio.code.core.project.VmProjectHubClient,
    private val executionCoordinator: AgentExecutionCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String? = savedStateHandle[ARG_SERVER_ID]

    /** Set when reopening a stored conversation rather than starting a new one. */
    private val existingConversationId: String? = savedStateHandle[ARG_CONVERSATION_ID]

    /**
     * Supplied when the agent was opened from a project. Navigation already decodes
     * it; decoding again turned "+" into spaces and crashed on a "%".
     */
    private val projectPath: String? = savedStateHandle.get<String>(ARG_PATH)
    private val initialPrompt: String? = savedStateHandle[ARG_INITIAL_PROMPT]

    private val _uiState = MutableStateFlow(AgentChatUiState(serverId = serverId, initialPrompt = initialPrompt))
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private val detachedJobs = java.util.Collections.synchronizedSet(mutableSetOf<Job>())
    private var healthJob: Job? = null
    private var gitDiscoveryJob: Job? = null

    private val deltas = DeltaBatcher(viewModelScope) { text ->
        _uiState.update { it.copy(transcript = it.transcript.appendDelta(text, STREAMING_ID, ::nextId)) }
    }

    override fun onCleared() {
        // Do not let a delayed coroutine publish into a ViewModel which is leaving
        // the composition. Flushing retains text already received; clear cancels
        // the scheduled publication without leaking a delayed job.
        deltas.flush()
        super.onCleared()
    }

    /** Until then the run list is re-read even though nothing is listed as running yet. */
    private var hasLoadedGithubRepositories = false
    private val itemCounter = AtomicLong(0L)
    /** Atomic to prevent race between background polling and user-initiated refresh */
    private val listRunsUntil = AtomicLong(0L)

    /** Polling for background runs */
    private var pollingJob: Job? = null

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(BACKGROUND_POLL_MILLIS)
                val visible = _uiState.subscriptionCount.value > 0
                val starting = System.currentTimeMillis() < listRunsUntil.get()
                val anyRunning = _uiState.value.backgroundRuns.any { it.isRunning }
                if (visible && (starting || anyRunning)) refreshBackgroundRuns()
            }
        }
    }

    init {
        applyStoredDefaults()
        viewModelScope.launch {
            loadGithubRepositories()
        }
        viewModelScope.launch {
            projectRepository.observeAll().collect { projects ->
                _uiState.update { it.copy(availableProjects = projects) }
            }
        }
        viewModelScope.launch {
            serverRepository.servers.collect { servers ->
                _uiState.update {
                    it.copy(
                        availableServers = listOf(
                            ChatServerOption(
                                id = RemoteFileSystem.LOCAL_SERVER_ID,
                                name = "This Phone",
                                target = "Local storage",
                            ),
                        ) + servers.map { server ->
                            ChatServerOption(server.id, server.name, server.displayTarget)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            modelCatalog.models.collect { models -> _uiState.update { it.copy(omniModels = models) } }
        }
        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                _uiState.update { it.copy(chatBubbleColorHex = prefs.chatBubbleColorHex) }
            }
        }
        // Live model sync: refresh the gateway's list whenever the chat opens on a
        // stale one. A failure here is silent; an explicit sync reports it.
        viewModelScope.launch { modelCatalog.syncIfStale() }
        viewModelScope.launch {
            try {
                projectHubClient.syncAllChatsFromServer()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                VmLog.w(LogCategory.AI, TAG, "Background sync all chats failed", e)
            }
        }
        when {
            // Reopening a stored conversation: its own directory and session id are
            // authoritative, so nothing is probed or prefilled.
            existingConversationId != null -> restoreConversation(existingConversationId)
            // A project supplies its own directory; probing the home directory is the
            // fallback for an agent opened straight from a server.
            !projectPath.isNullOrBlank() ->
                _uiState.update { it.copy(workingDirectory = projectPath) }
            // Defaults the agent's scope to the login home directory rather than `/`.
            else -> serverId?.let { server ->
                viewModelScope.launch {
                    // The folder last used on this server, else the login home. A
                    // failure is not fatal; the user can pick a folder.
                    val remembered = preferences.preferences.first().lastFolders[server]
                    val folder = remembered
                        ?: (remoteFileSystem.homeDirectory(server) as? VmResult.Success)?.value
                    if (folder != null) {
                        _uiState.update {
                            if (it.workingDirectory.isBlank()) it.copy(workingDirectory = folder) else it
                        }
                    }
                }
            }
        }
        refreshBackgroundRuns()
        scanServerGitRepos()
        
        startPolling()
    }

    fun scanServerGitRepos() {
        val srv = _uiState.value.serverId ?: return
        gitDiscoveryJob?.cancel()
        gitDiscoveryJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    discoveredGitRepos = emptyList(),
                    isDiscoveringGitRepos = true,
                    gitDiscoveryError = null,
                )
            }
            try {
                when (val result = gitService.discoverRepositories(srv)) {
                    is GitResult.Success -> _uiState.update { state ->
                        if (state.serverId == srv) {
                            state.copy(
                                discoveredGitRepos = result.value,
                                isDiscoveringGitRepos = false,
                                gitDiscoveryError = null,
                            )
                        } else {
                            state
                        }
                    }
                    is GitResult.Failure -> _uiState.update { state ->
                        if (state.serverId == srv) {
                            state.copy(
                                isDiscoveringGitRepos = false,
                                gitDiscoveryError = mapGitError(result.error),
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _uiState.update { state ->
                    if (state.serverId == srv) {
                        state.copy(
                            isDiscoveringGitRepos = false,
                            gitDiscoveryError = VmError.Git(
                                summary = "Could not scan Git repositories",
                                reason = exception.message,
                                suggestedAction = "Check the VPS connection and try again.",
                                retryable = true,
                            ),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun dismissGitDiscoveryError() {
        _uiState.update { it.copy(gitDiscoveryError = null) }
    }

    fun setSquadPreset(preset: digital.vmstudio.code.core.ai.model.AgentSquadPreset) {
        _uiState.update { it.copy(squadPreset = preset) }
    }

    private fun mapGitError(error: GitError): VmError = when (error) {
        is GitError.NotARepository -> VmError.Git(
            summary = "Not a Git repository",
            reason = "The directory '${error.path}' is not inside a Git repository.",
            suggestedAction = "Navigate to a Git repository and scan again.",
            repository = error.path,
        )
        is GitError.CommandFailed -> VmError.Git(
            summary = "Git repository scan failed",
            reason = error.stderr.ifBlank { "Git exited with code ${error.exitCode}." },
            suggestedAction = "Check that Git is installed on the server and try again.",
            retryable = true,
        )
        is GitError.ParseError -> VmError.Git(
            summary = "Could not parse Git scan results",
            reason = error.message,
            suggestedAction = "Check the installed Git version and try again.",
            retryable = true,
        )
        is GitError.NetworkError -> VmError.Git(
            summary = "Git repository scan could not reach the remote",
            reason = error.message,
            suggestedAction = "Check the VPS connection and try again.",
            retryable = true,
        )
        is GitError.AuthenticationError -> VmError.Git(
            summary = "Git authentication failed during repository scan",
            reason = error.message,
            suggestedAction = "Check the Git credentials configured on the VPS.",
        )
        is GitError.Unknown -> VmError.Git(
            summary = "Unexpected Git repository scan error",
            reason = error.message,
            suggestedAction = "Try again or check the server logs.",
            retryable = true,
        )
    }

    fun selectDiscoveredGitRepo(repo: digital.vmstudio.code.core.git.DiscoveredGitRepo) {
        val srv = _uiState.value.serverId ?: return
        viewModelScope.launch {
            val existing = projectRepository.observeAll().first().firstOrNull {
                it.serverId == srv && it.remotePath == repo.path
            }
            if (existing != null) {
                selectProject(existing)
            } else {
                when (val result = projectRepository.create(
                    name = repo.name,
                    serverId = srv,
                    remotePath = repo.path,
                    branch = repo.branch,
                )) {
                    is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                        val created = projectRepository.get(result.value)
                        selectProject(created)
                    }
                    is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                        setWorkingDirectory(repo.path)
                    }
                }
            }
        }
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
            // Chats saved before tool ids were made unique can repeat one; each row
            // still needs its own key or reopening the chat crashes the list.
            val seen = mutableSetOf<String>()
            val restored = conversations.transcript(conversationId)
                .map { it.toTranscriptItem(::nextId, seen) }
            val modelToRestore = stored.modelId.takeIf { it.isNotBlank() }

            _uiState.update {
                it.copy(
                    conversationId = conversationId,
                    providerSessionId = stored.providerSessionId?.takeIf {
                        it.startsWith(GATEWAY_SESSION_PREFIX)
                    },
                    workingDirectory = stored.workingDirectory ?: it.workingDirectory,
                    transcript = restored,
                    // The route of a reopened chat carries no server; without this
                    // the chat could be read but never continued.
                    serverId = it.serverId ?: stored.serverId,
                    selectedModel = modelToRestore ?: it.selectedModel,
                )
            }
            checkHealth()
            refreshBackgroundRuns()
            if (executionCoordinator.isRunning(conversationId)) {
                _uiState.update { it.copy(isRunning = true) }
                runJob?.cancel()
                runJob = viewModelScope.launch {
                    executionCoordinator.observeEvents(conversationId)?.collect { event ->
                        applyEvent(event)
                        if (event is AgentEvent.Completed || event is AgentEvent.Failed) {
                            _uiState.update { it.copy(isRunning = false) }
                            processNextQueuedMessage()
                        }
                    }
                }
            }
        }
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
    fun sendInBackground(
        rawPrompt: String,
        attachments: List<Uri> = emptyList(),
        onStarted: () -> Unit = {},
    ) {
        val state = _uiState.value
        val promptText = rawPrompt.trim()
        if (promptText.isBlank() || state.isStartingBackgroundRun) return
        val server = state.serverId
        val directory = state.workingDirectory.trim()

        // General chat, Zero-Clone /github/ workspaces, and Local Phone Folders run
        // on-device rather than via SSH on a remote VPS, so detach the active run
        // in-process with the wake lock held.
        if (server.isNullOrBlank() || RemoteFileSystem.isLocalServer(server) || directory.startsWith("/github/")) {
            onStarted()
            send(promptText, attachments)
            detachToBackground()
            return
        }

        _uiState.update { it.copy(isStartingBackgroundRun = true, error = null) }
        viewModelScope.launch {
            var prompt = promptText
            if (attachments.isNotEmpty()) {
                when (val staged = attachmentStager.stage(server, directory, attachments)) {
                    is VmResult.Failure -> {
                        _uiState.update { it.copy(isStartingBackgroundRun = false, error = staged.error) }
                        return@launch
                    }
                    is VmResult.Success -> {
                        if (staged.value.remotePaths.isNotEmpty()) {
                            prompt += "\n\nAttached files on the server:\n" +
                                staged.value.remotePaths.joinToString("\n") { "- $it" }
                        }
                        if (staged.value.inlinedTextFiles.isNotEmpty()) {
                            prompt += "\n\nAttached file contents:\n" +
                                staged.value.inlinedTextFiles.entries.joinToString("\n\n") { (name, content) ->
                                    "--- $name ---\n$content"
                                }
                        }
                    }
                }
            }
            val result = serverSideRunner.start(
                serverId = server,
                workingDirectory = directory,
                prompt = prompt,
                model = state.selectedModel,
                permissionMode = state.permissionMode,
                history = state.transcript.toConversationTurns(),
                contextChars = preferences.contextChars(),
            )
            when (result) {
                is VmResult.Success -> {
                    onStarted()
                    append(
                        TranscriptItem.Diagnostic(
                            id = nextId(),
                            text = "Started in the background as ${result.value}. " +
                                "It keeps running if you leave the app.",
                            isStderr = false,
                        ),
                    )
                    _uiState.update { it.copy(watchServerId = server) }
                    // A run registers on the server a moment after it starts, so the
                    // first read can miss it; keep reading for a minute.
                    listRunsUntil.set(System.currentTimeMillis() + PENDING_START_MILLIS)
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
     * Seeds the permission mode from the Settings autonomy level and the model from
     * the Settings provider, then checks that model's backend.
     *
     * Settings holds the defaults and the chat chips override them for a single
     * chat. The health check waits for the model, because the model decides which
     * backend is checked.
     */
    private fun applyStoredDefaults() {
        _uiState.update { it.copy(isCheckingHealth = true) }
        viewModelScope.launch {
            val prefs = preferences.preferences.first()
            val syncedModels = prefs.aiAvailableModelIds.ifEmpty {
                (modelCatalog.sync() as? VmResult.Success)?.value.orEmpty()
            }
            val omniModel = prefs.aiSelectedModelId?.takeIf { syncedModels.isEmpty() || it in syncedModels }
                ?: syncedModels.firstOrNull()
                ?: prefs.aiSelectedModelId
            val lastModel = prefs.lastChatModel?.takeIf { last ->
                syncedModels.isEmpty() || last in syncedModels
            }
            val defaultFallbackModel = "auto/best-coding"
            val projectKey = projectPath?.trim().orEmpty()
            val projectModel = if (projectKey.isNotBlank()) prefs.projectModels[projectKey] else null
            val effectiveModel = (projectModel ?: lastModel ?: omniModel)?.ifBlank { null } ?: defaultFallbackModel
            _uiState.update { current ->
                val currentModel = current.selectedModel.takeIf { it.isNotBlank() && existingConversationId != null }
                current.copy(
                    permissionMode = prefs.agentAutonomyLevel.toPermissionMode(),
                    selectedModel = currentModel ?: effectiveModel,
                    chatBubbleColorHex = prefs.chatBubbleColorHex,
                )
            }
            checkHealth()
        }
    }

    fun loadGithubRepositories() {
        if (hasLoadedGithubRepositories || _uiState.value.isLoadingGithubRepositories) return
        hasLoadedGithubRepositories = true
        requestGithubRepositories()
    }

    fun triggerCloudBuild(
        workflowFileName: String = "nightly.yml",
        onTriggered: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            try {
                when (val result = githubRepositoryClient.dispatchWorkflow("vmai-plugins/xcode", workflowFileName, "main")) {
                    is VmResult.Success -> {
                        onTriggered(true, "Cloud APK build '$workflowFileName' dispatched to GitHub Actions! Finished APK will deploy to App Hub.")
                    }
                    is VmResult.Failure -> {
                        val reason = (result.error as? VmError.Network)?.reason
                        val action = result.error.suggestedAction
                        val details = listOfNotNull(reason, action).filter { it.isNotBlank() }.joinToString(" ")
                        val fullMsg = if (details.isNotBlank()) {
                            "${result.error.summary}: $details"
                        } else {
                            result.error.summary
                        }
                        onTriggered(false, fullMsg)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                onTriggered(false, exception.message ?: "Failed to trigger cloud build")
            }
        }
    }

    fun refreshGithubRepositories() {
        if (_uiState.value.isLoadingGithubRepositories) return
        hasLoadedGithubRepositories = true
        requestGithubRepositories(forceRefresh = true)
    }

    private fun requestGithubRepositories(forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoadingGithubRepositories = true, githubError = null) }
        viewModelScope.launch {
            try {
                when (val result = githubRepositoryClient.repositories(forceRefresh)) {
                    is VmResult.Success -> _uiState.update {
                        it.copy(githubRepositories = result.value, isLoadingGithubRepositories = false)
                    }
                    is VmResult.Failure -> _uiState.update {
                        it.copy(isLoadingGithubRepositories = false, githubError = result.error)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingGithubRepositories = false,
                        githubError = VmError.Network(
                            summary = "Could not load GitHub repositories",
                            reason = exception.message,
                        ),
                    )
                }
            }
        }
    }

    fun selectGithubRepo(repo: GitHubRepository) {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            val virtualPath = "/github/${repo.fullName}"
            val existing = projectRepository.observeAll().first().firstOrNull {
                it.remotePath == virtualPath
            }
            if (existing != null) {
                selectProject(existing)
                return@launch
            }
            when (val created = projectRepository.create(
                name = repo.fullName,
                serverId = null,
                remotePath = virtualPath,
                branch = repo.defaultBranch,
            )) {
                is VmResult.Success -> projectRepository.get(created.value)?.let(::selectProject)
                is VmResult.Failure -> {
                    // Fallback to setting workingDirectory directly
                    setWorkingDirectory(virtualPath)
                }
            }
        }
    }

    fun loadGithubFileTree(repoFullName: String) {
        _uiState.update { it.copy(isLoadingGithubFileTree = true) }
        viewModelScope.launch {
            try {
                when (val result = githubRepositoryClient.getRepositoryTree(repoFullName)) {
                    is VmResult.Success -> _uiState.update {
                        it.copy(githubFileTree = result.value, isLoadingGithubFileTree = false)
                    }
                    is VmResult.Failure -> _uiState.update {
                        it.copy(isLoadingGithubFileTree = false)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingGithubFileTree = false) }
            }
        }
    }

    fun selectLocalFolder(folderUri: String, folderName: String) {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            val name = folderName.ifBlank { "Local workspace" }
            val existing = projectRepository.observeAll().first().firstOrNull {
                it.serverId == RemoteFileSystem.LOCAL_SERVER_ID && it.remotePath == folderUri
            }
            if (existing != null) {
                selectProject(existing)
                return@launch
            }
            when (val created = projectRepository.create(
                name = name,
                serverId = RemoteFileSystem.LOCAL_SERVER_ID,
                remotePath = folderUri,
                branch = null,
            )) {
                is VmResult.Success -> projectRepository.get(created.value)?.let(::selectProject)
                is VmResult.Failure -> _uiState.update { it.copy(error = created.error) }
            }
        }
    }

    fun reportLocalFolderError(reason: String?) {
        _uiState.update {
            it.copy(
                error = VmError.Validation(
                    summary = "Could not keep access to the selected phone folder",
                    reason = reason,
                    suggestedAction = "Choose a folder from the system picker and allow access.",
                ),
            )
        }
    }

    fun selectServerFolder(serverId: String, path: String) {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            val name = path.trimEnd('/').substringAfterLast('/').ifBlank { "VPS workspace" }
            val existing = projectRepository.observeAll().first().firstOrNull {
                it.serverId == serverId && it.remotePath == path
            }
            if (existing != null) {
                selectProject(existing)
                return@launch
            }
            when (val created = projectRepository.create(
                name = name,
                serverId = serverId,
                remotePath = path,
                branch = null,
            )) {
                is VmResult.Success -> {
                    val project = projectRepository.get(created.value)
                    if (project != null) {
                        selectProject(project)
                    } else {
                        setServerWorkingDirectory(serverId, path)
                    }
                }
                is VmResult.Failure -> _uiState.update { it.copy(error = created.error) }
            }
        }
    }

    private fun setServerWorkingDirectory(serverId: String, path: String) {
        _uiState.update {
            it.copy(
                serverId = serverId,
                workingDirectory = path,
                conversationId = null,
                providerSessionId = null,
                transcript = emptyList(),
                githubFileTree = emptyList(),
            )
        }
        viewModelScope.launch { preferenceStore.setLastFolder(serverId, path) }
        refreshBackgroundRuns()
        checkHealth()
    }

    fun checkHealth() {
        _uiState.update { it.copy(isCheckingHealth = true) }
        // Only the newest check may report: a slow Claude Code probe over SSH that
        // lands after a quick gateway check showed the wrong backend's health and
        // disabled the composer for a model that works.
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            val provider = providers.active()
            when (val result = providers.checkHealth(provider, _uiState.value.serverId)) {
                is VmResult.Success -> {
                    val healthValue = result.value
                    if (healthValue.availableModels.isNotEmpty()) {
                        preferenceStore.setAiModels(healthValue.availableModels, healthValue.freeModels)
                    }
                    _uiState.update {
                        it.copy(health = healthValue, isCheckingHealth = false)
                    }
                }
                is VmResult.Failure -> _uiState.update {
                    it.copy(isCheckingHealth = false, error = result.error)
                }
            }
        }
    }

    fun setWorkingDirectory(path: String) {
        val current = _uiState.value
        if (path == current.workingDirectory) return
        // A Claude session belongs to the folder it started in, so a different folder
        // is a different conversation; leaving the ids would resume a session that is
        // not there.
        val switching = !current.isRunning && current.transcript.isNotEmpty()
        _uiState.update {
            if (switching) {
                // A clean new chat: keeping the old transcript on screen while the
                // ids were dropped forked it, so Recents and reopening never matched
                // what was shown.
                it.copy(
                    workingDirectory = path,
                    conversationId = null,
                    providerSessionId = null,
                    transcript = emptyList(),
                    githubFileTree = emptyList(),
                )
            } else {
                it.copy(workingDirectory = path, githubFileTree = emptyList())
            }
        }
        current.serverId?.let { server ->
            viewModelScope.launch { preferenceStore.setLastFolder(server, path) }
        }
        refreshBackgroundRuns()
    }

    /**
     * Starts a new chat in [project], or with null a general chat with no server or
     * folder (gateway models only). New chats open the way the last one was set.
     */
    fun quickCreateProject(name: String, isLocal: Boolean, serverId: String? = null) {
        if (_uiState.value.isRunning) return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val effectiveServerId = if (isLocal) {
                RemoteFileSystem.LOCAL_SERVER_ID
            } else {
                serverId ?: _uiState.value.availableServers.firstOrNull { !RemoteFileSystem.isLocalServer(it.id) }?.id
                    ?: RemoteFileSystem.LOCAL_SERVER_ID
            }
            val baseDir = if (RemoteFileSystem.isLocalServer(effectiveServerId)) {
                val base = context.getExternalFilesDir(null) ?: context.filesDir
                java.io.File(base, "projects").apply { mkdirs() }
            } else null

            val effectivePath = if (baseDir != null) {
                java.io.File(baseDir, trimmed).apply { mkdirs() }.absolutePath
            } else {
                "~/projects/$trimmed"
            }
            when (val created = projectRepository.create(
                name = trimmed,
                serverId = effectiveServerId,
                remotePath = effectivePath,
                branch = null,
            )) {
                is VmResult.Success -> {
                    projectRepository.get(created.value)?.let(::selectProject)
                }
                is VmResult.Failure -> {
                    _uiState.update { it.copy(error = created.error) }
                }
            }
        }
    }

    fun selectProject(project: Project?) {
        if (_uiState.value.isRunning) {
            detachToBackground()
        }
        gitDiscoveryJob?.cancel()
        gitDiscoveryJob = null
        _uiState.update {
            it.copy(
                serverId = if (project == null) {
                    null
                } else {
                    project.serverId ?: if (project.remotePath.startsWith("/github/")) null else (it.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID)
                },
                workingDirectory = project?.remotePath.orEmpty(),
                conversationId = null,
                providerSessionId = null,
                transcript = emptyList(),
                backgroundRuns = if (project == null) emptyList() else it.backgroundRuns,
                discoveredGitRepos = emptyList(),
                isDiscoveringGitRepos = false,
                gitDiscoveryError = null,
                githubFileTree = emptyList(),
            )
        }
        viewModelScope.launch {
            preferenceStore.setLastChatGeneral(project == null)
            val server = _uiState.value.serverId
            if (project != null && server != null) preferenceStore.setLastFolder(server, project.remotePath)
        }
        refreshBackgroundRuns()
        if (project?.serverId != null && !RemoteFileSystem.isLocalServer(project.serverId)) {
            scanServerGitRepos()
        }
        checkHealth()
    }

    /**
     * Picks the OmniRoute model for the next message.
     */
    fun selectModel(model: String) {
        if (_uiState.value.isRunning) return
        _uiState.update {
            it.copy(
                selectedModel = model,
            )
        }
        viewModelScope.launch {
            preferenceStore.setLastChatModel(model)
            val convId = _uiState.value.conversationId
            if (!convId.isNullOrBlank()) {
                conversations.updateModel(convId, model)
            }
            val workingDir = _uiState.value.workingDirectory.trim()
            if (workingDir.isNotBlank()) {
                preferenceStore.setProjectModel(workingDir, model)
            }
        }
    }

    /** Refreshes the gateway's model list now, reporting a failure. */
    fun syncModels() {
        if (_uiState.value.isSyncingModels) return
        _uiState.update { it.copy(isSyncingModels = true) }
        viewModelScope.launch {
            val result = modelCatalog.sync()
            _uiState.update {
                it.copy(
                    isSyncingModels = false,
                    error = (result as? VmResult.Failure)?.error ?: it.error,
                )
            }
        }
    }

    fun setPermissionMode(mode: AgentPermissionMode) {
        _uiState.update { it.copy(permissionMode = mode) }
    }

    fun queueMessage(prompt: String, attachments: List<Uri> = emptyList()) {
        val effectivePrompt = prompt.ifBlank {
            if (attachments.isNotEmpty()) "Please inspect the attached file(s)." else return
        }
        val item = QueuedMessage(id = nextId(), prompt = effectivePrompt, attachments = attachments)
        _uiState.update { it.copy(queuedMessages = it.queuedMessages + item) }
    }

    fun removeQueuedMessage(id: String) {
        _uiState.update { it.copy(queuedMessages = it.queuedMessages.filterNot { q -> q.id == id }) }
    }

    /** Antigravity-style 'Steer Now': aborts the current tool iteration gracefully and starts executing this queued prompt immediately. */
    fun steerNow(queuedId: String) {
        val target = _uiState.value.queuedMessages.firstOrNull { it.id == queuedId } ?: return
        removeQueuedMessage(queuedId)
        stop()
        send(target.prompt, target.attachments)
    }

    private fun processNextQueuedMessage() {
        val next = _uiState.value.queuedMessages.firstOrNull() ?: return
        _uiState.update { it.copy(queuedMessages = it.queuedMessages.drop(1)) }
        send(next.prompt, next.attachments)
    }

    fun send(prompt: String, attachments: List<Uri> = emptyList()) {
        val state = _uiState.value
        val effectivePrompt = prompt.ifBlank {
            if (attachments.isNotEmpty()) "Please inspect the attached file(s)." else return
        }
        if (!state.canAcceptInput) return

        if (effectivePrompt.trim().equals("/clear", ignoreCase = true) || effectivePrompt.trim().equals("/reset", ignoreCase = true)) {
            startNewConversation()
            return
        }

        if (state.isRunning) {
            queueMessage(effectivePrompt, attachments)
            return
        }

        // Blank in a general chat: the gateway then answers without tools.
        val server = state.serverId.orEmpty()
        append(TranscriptItem.UserPrompt(nextId(), effectivePrompt))
        _uiState.update { it.copy(isRunning = true, error = null) }

        runJob = viewModelScope.launch {
            wakeLockManager.acquire()
            val selfJob = coroutineContext[Job]
            var activeConvId: String? = null
            try {
                val conversationId = state.conversationId ?: createConversation(server, state, prompt)
                activeConvId = conversationId
                if (conversationId == null) {
                    _uiState.update { it.copy(isRunning = false) }
                    return@launch
                }
                val staged = if (attachments.isEmpty()) {
                    StagedAttachments(emptyList(), emptyList())
                } else {
                    val result = attachmentStager.stage(server, state.workingDirectory.trim(), attachments)
                    when (result) {
                        is VmResult.Failure -> {
                            _uiState.update { it.copy(isRunning = false, error = result.error) }
                            return@launch
                        }
                        is VmResult.Success -> result.value.also { files ->
                            val names = files.remotePaths
                                .map { it.substringAfterLast('/') }
                                .ifEmpty { files.displayNames }
                            if (names.isNotEmpty()) {
                                append(
                                    TranscriptItem.Diagnostic(
                                        id = nextId(),
                                        text = "Attached: " + names.joinToString(),
                                        isStderr = false,
                                    ),
                                )
                            }
                        }
                    }
                }
                // The agent finds attachments by path, so the prompt names them.
                var resolvedPrompt = prompt
                val workDir = state.workingDirectory.trim()
                if (resolvedPrompt.contains("@git-diff") && server.isNotBlank() && workDir.isNotBlank()) {
                    when (val diffRes = gitService.diffAll(server, workDir)) {
                        is digital.vmstudio.code.core.git.GitResult.Success -> {
                            val diffSnippet = diffRes.value.files.joinToString("\n") { file ->
                                "--- ${file.displayPath} (+${file.additions} -${file.deletions})\n" +
                                    file.lines.joinToString("\n") { it.text }
                            }.take(10000)
                            if (diffSnippet.isNotBlank()) {
                                resolvedPrompt = resolvedPrompt.replace("@git-diff", "\n```diff\n$diffSnippet\n```\n")
                            }
                        }
                        is digital.vmstudio.code.core.git.GitResult.Failure -> Unit
                    }
                }

                // Expand @file: <path> mentions inserted by the file tree sheet
                val fileMentions = Regex("""@file:\s*([^\s]+)""").findAll(resolvedPrompt).map { it.groupValues[1] }.distinct().toList()
                if (fileMentions.isNotEmpty() && workDir.isNotBlank()) {
                    val fileSnippets = mutableListOf<String>()
                    val githubInfo = if (workDir.startsWith("/github/")) {
                        val clean = workDir.removePrefix("/github/").trim('/')
                        val parts = clean.split('/').filter { it.isNotEmpty() }
                        if (parts.size >= 2) {
                            val repo = "${parts[0]}/${parts[1]}"
                            val subDir = if (parts.size > 2) parts.drop(2).joinToString("/") else ""
                            repo to subDir
                        } else null
                    } else null

                    for (relPath in fileMentions.take(5)) {
                        val content = when {
                            githubInfo != null -> {
                                val (repo, subDir) = githubInfo
                                val cleanRel = relPath.trimStart('/')
                                val resolvedPath = if (subDir.isNotBlank() && !cleanRel.startsWith(subDir)) "$subDir/$cleanRel" else cleanRel
                                (githubRepositoryClient.getFileContent(repo, resolvedPath) as? VmResult.Success)?.value
                                    ?: (githubRepositoryClient.getFileContent(repo, cleanRel) as? VmResult.Success)?.value
                            }
                            server.isNotBlank() -> {
                                val fullPath = if (relPath.startsWith("/")) relPath else "${workDir.trimEnd('/')}/$relPath"
                                (remoteFileSystem.readText(server, fullPath) as? VmResult.Success)?.value
                            }
                            else -> null
                        }
                        if (!content.isNullOrBlank()) {
                            fileSnippets += "--- $relPath ---\n${content.take(24_000)}"
                        }
                    }
                    if (fileSnippets.isNotEmpty()) {
                        resolvedPrompt += "\n\nReferenced files:\n" + fileSnippets.joinToString("\n\n")
                    }
                }

                // Expand @project and @github mentions
                val mentionMatches = Regex("""@"([^"]+)"|@([a-zA-Z0-9_\-\./]+)""").findAll(resolvedPrompt)
                val mentionTokens = mentionMatches.map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.distinct().toList()
                val candidateTokens = mentionTokens.filterNot { token ->
                    token.startsWith("file:") ||
                        token.equals("git-diff", ignoreCase = true) ||
                        token.equals("terminal", ignoreCase = true)
                }

                val matchedProjects = candidateTokens.mapNotNull { token ->
                    state.availableProjects.firstOrNull { project ->
                        project.name.equals(token, ignoreCase = true) ||
                            project.name.replace(" ", "-").equals(token, ignoreCase = true) ||
                            project.id.equals(token, ignoreCase = true)
                    }
                }.distinctBy { it.id }

                val matchedRepos = candidateTokens.mapNotNull { token ->
                    state.githubRepositories.firstOrNull { repo ->
                        repo.fullName.equals(token, ignoreCase = true) ||
                            repo.fullName.substringAfterLast('/').equals(token, ignoreCase = true)
                    }
                }.distinctBy { it.id }

                val isSwarmRequested = resolvedPrompt.contains("@swarm", ignoreCase = true)
                val requestedRoles = buildList {
                    if (resolvedPrompt.contains("@architect", ignoreCase = true)) add("Architect (System Design)")
                    if (resolvedPrompt.contains("@frontend", ignoreCase = true)) add("Frontend (UI/Web/Compose)")
                    if (resolvedPrompt.contains("@backend", ignoreCase = true)) add("Backend (APIs/Databases)")
                    if (resolvedPrompt.contains("@reviewer", ignoreCase = true)) add("Reviewer (Diff Audit/QA)")
                }

                if (matchedProjects.isNotEmpty() || matchedRepos.isNotEmpty() || isSwarmRequested || requestedRoles.isNotEmpty()) {
                    val elements = (matchedProjects.map { "@${it.name}" } +
                        matchedRepos.map { "@${it.fullName}" } +
                        (if (isSwarmRequested) listOf("@swarm") else emptyList()) +
                        requestedRoles.map { "@${it.substringBefore(' ').lowercase()}" }
                    ).joinToString(", ")
                    append(
                        TranscriptItem.Diagnostic(
                            id = nextId(),
                            text = "Mentioned Context: $elements",
                            isStderr = false,
                        ),
                    )
                }

                val fullPrompt = buildString {
                    append(resolvedPrompt)
                    if (isSwarmRequested) {
                        append("\n\n[Swarm Coordinator Mode]\n")
                        append("The user requested multi-agent swarm execution across projects. ")
                        append("Act as the Lead Coordinator: decompose this task into parallel subtasks per project workspace, ")
                        append("specify execution steps and file modifications for each project separately, and highlight dependencies.\n")
                    }
                    if (requestedRoles.isNotEmpty()) {
                        append("\n\n[Active Specialist Roles]\n")
                        append("Adopt the following specialist perspectives: ${requestedRoles.joinToString(", ")}. ")
                        append("Structure your findings, code generation, and verification under these respective roles.\n")
                    }
                    if (matchedProjects.isNotEmpty() || matchedRepos.isNotEmpty()) {
                        append("\n\n[Referenced Projects & Repositories Context]\n")
                        append("The user explicitly referenced external workspace(s) and repository(ies) in this chat:\n")
                        matchedProjects.forEachIndexed { idx, p ->
                            val loc = if (p.isRemote) "Remote Server (${p.serverId})" else "Local Phone Storage"
                            append("${idx + 1}. Project \"${p.name}\"\n")
                            append("   - Location: $loc\n")
                            append("   - Path: ${p.remotePath}\n")
                            if (!p.branch.isNullOrBlank()) append("   - Branch: ${p.branch}\n")
                            if (p.projectTypeId.isNotBlank()) append("   - Project Type: ${p.projectTypeId}\n")
                            if (!p.runtime.isNullOrBlank()) append("   - Runtime: ${p.runtime}\n")
                            if (!p.packageManager.isNullOrBlank()) append("   - Package Manager: ${p.packageManager}\n")
                            if (p.isLocal) {
                                try {
                                    val dir = java.io.File(p.remotePath)
                                    if (dir.exists() && dir.isDirectory) {
                                        val files = dir.listFiles()?.take(20)?.map { if (it.isDirectory) "${it.name}/" else it.name }
                                        if (!files.isNullOrEmpty()) {
                                            append("   - Files: " + files.joinToString(", ") + "\n")
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        matchedRepos.forEachIndexed { idx, r ->
                            append("${idx + 1}. GitHub Repository \"${r.fullName}\"\n")
                            append("   - Clone URL: ${r.cloneUrl}\n")
                            append("   - Default Branch: ${r.defaultBranch}\n")
                            if (!r.description.isNullOrBlank()) append("   - Description: ${r.description}\n")
                        }
                        append("Cross-reference and consider all referenced projects above when answering or fulfilling instructions.\n")
                    }
                    if (staged.remotePaths.isNotEmpty()) {
                        append("\n\nAttached files on the server:\n")
                        append(staged.remotePaths.joinToString("\n") { "- $it" })
                    }
                    if (staged.inlinedTextFiles.isNotEmpty()) {
                        append("\n\nAttached file contents:\n")
                        append(
                            staged.inlinedTextFiles.entries.joinToString("\n\n") { (name, content) ->
                                "--- $name ---\n$content"
                            },
                        )
                    }
                }
                conversations.appendUserPrompt(conversationId, fullPrompt)

                val taskId = tasks.start(
                    conversationId = conversationId,
                    title = prompt,
                    projectId = tasks.findProjectId(server, state.workingDirectory.trim()),
                )

                val gdriveToken = preferenceStore.preferences.first().googleDriveTokenCredentialId?.let { id ->
                    when (val secRes = credentialStore.read(id)) {
                        is VmResult.Success -> {
                            val token = secRes.value.useAsString { it }
                            secRes.value.wipe()
                            token
                        }
                        is VmResult.Failure -> null
                    }
                }

                val gsheetsToken = preferenceStore.preferences.first().googleSheetsTokenCredentialId?.let { id ->
                    when (val secRes = credentialStore.read(id)) {
                        is VmResult.Success -> {
                            val token = secRes.value.useAsString { it }
                            secRes.value.wipe()
                            token
                        }
                        is VmResult.Failure -> null
                    }
                }

                val config = AgentRunConfig(
                    serverId = server,
                    workingDirectory = state.workingDirectory.trim(),
                    prompt = fullPrompt,
                    images = staged.images,
                    resumeSessionId = state.providerSessionId?.takeIf { it.startsWith(GATEWAY_SESSION_PREFIX) },
                    permissionMode = state.permissionMode,
                    model = state.selectedModel,
                    history = state.transcript.toConversationTurns(),
                    autoCompactTokens = preferences.preferences.first().agentMaxContextTokens,
                    contextChars = preferences.contextChars(),
                    webAccessEnabled = preferences.preferences.first().aiWebAccessEnabled,
                    maxIterations = preferences.preferences.first().agentMaxIterations,
                    maxCommandsPerRun = preferences.preferences.first().agentMaxCommandsPerRun,
                    googleDriveToken = gdriveToken,
                    googleSheetsToken = gsheetsToken ?: gdriveToken,
                )

                try {
                    val eventFlow = executionCoordinator.submitRun(
                        conversationId = conversationId,
                        taskId = taskId,
                        prompt = prompt,
                    ) { emitEvent ->
                        providers.active().run(config).collect { event ->
                            emitEvent(event)
                        }
                    }

                    eventFlow.collect { event ->
                        applyEvent(event)
                        if (event is AgentEvent.Completed || event is AgentEvent.Failed) {
                            _uiState.update { it.copy(isRunning = false) }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    deltas.clear()
                    _uiState.update { it.copy(transcript = it.transcript.settleStreamingDraft(::nextId)) }
                    append(
                        TranscriptItem.Failure(
                            id = nextId(),
                            error = VmError.Ai(
                                summary = "The run failed",
                                reason = failure.message,
                                cause = failure,
                            ),
                        ),
                    )
                }
                if (runJob === selfJob) {
                    _uiState.update { it.copy(isRunning = false) }
                    processNextQueuedMessage()
                }
            } finally {
                selfJob?.let { detachedJobs.remove(it) }
                wakeLockManager.release()
                if (runJob === selfJob) {
                    _uiState.update { it.copy(isRunning = false) }
                }
                activeConvId?.let { convId ->
                    viewModelScope.launch {
                        try {
                            projectHubClient.syncChatToServer(convId)
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (e: Exception) {
                            VmLog.w(LogCategory.AI, TAG, "Background chat sync to server failed", e)
                        }
                    }
                }
            }
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

    /** Detaches the current running task to the background, immediately freeing the chat. */
    fun detachToBackground() {
        val current = runJob ?: return
        if (!_uiState.value.isRunning) return
        deltas.flush()
        detachedJobs.add(current)
        runJob = null
        _uiState.update { state ->
            state.copy(
                isRunning = false,
                conversationId = null,
                providerSessionId = null,
                transcript = state.transcript.settleStreamingDraft(::nextId),
            )
        }
        append(
            TranscriptItem.Diagnostic(
                id = nextId(),
                text = "Task detached to background. Chat is now available for new tasks.",
                isStderr = false,
            ),
        )
        processNextQueuedMessage()
    }

    /** Stops the run. Cancelling collection terminates the remote process too. */
    fun stop() {
        deltas.clear()
        _uiState.value.conversationId?.let { executionCoordinator.stopRun(it) }
        runJob?.cancel()
        runJob = null
        detachedJobs.forEach { it.cancel() }
        detachedJobs.clear()
        _uiState.update { state ->
            state.copy(
                isRunning = false,
                transcript = state.transcript.settleStreamingDraft(::nextId),
            )
        }
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
        if (_uiState.value.isRunning) {
            detachToBackground()
        }
        deltas.clear()
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

    private suspend fun createConversation(server: String, state: AgentChatUiState, prompt: String): String? =
        when (
            val created = conversations.create(
                // Named after what was asked, so Recents is not a list of folder names.
                title = chatTitle(prompt),
                providerId = providers.all().first().kind.name,
                modelId = state.selectedModel.ifBlank { state.health?.version.orEmpty() },
                serverId = server.ifBlank { null },
                workingDirectory = state.workingDirectory.trim().ifBlank { null },
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
        // Streamed text waiting to be shown goes first, so events stay in order.
        if (event !is AgentEvent.AssistantDelta) deltas.flush()
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

            is AgentEvent.AssistantDelta -> deltas.add(event.text)

            is AgentEvent.Reasoning -> _uiState.update { state ->
                val last = state.transcript.lastOrNull()
                if (last is TranscriptItem.Reasoning) {
                    val updated = state.transcript.toMutableList()
                    updated[updated.lastIndex] = last.copy(text = last.text + "\n" + event.text)
                    state.copy(transcript = updated)
                } else {
                    state.copy(transcript = state.transcript + TranscriptItem.Reasoning(nextId(), event.text))
                }
            }

            is AgentEvent.ToolStarted -> append(
                TranscriptItem.ToolCall(
                    // A provider that repeats a tool id must not give two rows one key.
                    id = toolItemId(event.toolUseId).takeIf { id ->
                        _uiState.value.transcript.none { it.id == id }
                    } ?: nextId(),
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

            is AgentEvent.SubagentSpawned -> append(
                TranscriptItem.SubagentRun(
                    id = nextId(),
                    subagentId = event.subagentId,
                    role = event.role,
                    prompt = event.prompt,
                    isRunning = true,
                ),
            )

            is AgentEvent.SubagentCompleted -> _uiState.update { state ->
                val updated = state.transcript.map { item ->
                    if (item is TranscriptItem.SubagentRun && item.subagentId == event.subagentId) {
                        item.copy(
                            isRunning = false,
                            summary = event.summary,
                            isError = event.isError,
                        )
                    } else item
                }
                state.copy(transcript = updated)
            }

            is AgentEvent.Completed -> {
                _uiState.update { it.copy(transcript = it.transcript.settleStreamingDraft(::nextId)) }
                append(
                    TranscriptItem.RunSummary(
                        id = nextId(),
                        durationMillis = event.durationMillis,
                        costUsd = event.costUsd,
                        inputTokens = event.inputTokens,
                        outputTokens = event.outputTokens,
                        isError = event.isError,
                    ),
                )
            }

            is AgentEvent.Failed -> {
                _uiState.update { it.copy(transcript = it.transcript.settleStreamingDraft(::nextId)) }
                append(TranscriptItem.Failure(nextId(), event.error))
            }

            is AgentEvent.Diagnostic -> {
                val line = event.line.trim()
                if (!line.contains("no stdin data received", ignoreCase = true) &&
                    !line.contains("proceeding without it", ignoreCase = true) &&
                    !line.contains("raw mode", ignoreCase = true)
                ) {
                    append(TranscriptItem.Diagnostic(nextId(), event.line, event.isStderr))
                }
            }
        }
    }

    fun rollbackFile(toolCallId: String, filePath: String) {
        if (filePath.isBlank() || filePath.contains("..")) return
        val sId = _uiState.value.serverId
        if (sId.isNullOrBlank()) {
            append(
                TranscriptItem.Diagnostic(
                    id = nextId(),
                    text = "Reverting via Git is only supported on a connected server workspace with an active Git repository.",
                    isStderr = true,
                ),
            )
            return
        }
        viewModelScope.launch {
            val repoPath = _uiState.value.workingDirectory.ifBlank {
                val idx = filePath.lastIndexOf('/')
                if (idx > 0) filePath.substring(0, idx) else "."
            }
            when (val result = gitService.restoreFile(sId, repoPath, filePath)) {
                is GitResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            transcript = state.transcript.map { item ->
                                // Only the row that was undone; other edits to the same
                                // file may be older or newer than it.
                                if (item is TranscriptItem.ToolCall && item.id == toolCallId) {
                                    item.copy(isReverted = true)
                                } else {
                                    item
                                }
                            } + TranscriptItem.Diagnostic(
                                id = nextId(),
                                text = "Restored $filePath to its last committed version",
                                isStderr = false,
                            ),
                        )
                    }
                }
                is GitResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            transcript = state.transcript + TranscriptItem.Diagnostic(
                                id = nextId(),
                                text = "Could not undo changes to $filePath: ${result.error}",
                                isStderr = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun rewindToPrompt(promptId: String) {
        _uiState.update { current ->
            val index = current.transcript.indexOfFirst { it.id == promptId }
            if (index >= 0) {
                current.copy(transcript = current.transcript.take(index))
            } else current
        }
    }

    private fun append(item: TranscriptItem) {
        _uiState.update { it.copy(transcript = it.transcript + item) }
    }

    private fun toolItemId(toolUseId: String): String =
        if (toolUseId.isBlank()) nextId() else "tool-$toolUseId"

    private fun nextId(): String = "item-${itemCounter.getAndIncrement()}"

    companion object {
        const val ARG_SERVER_ID = "serverId"
        const val ARG_PATH = "path"
        const val ARG_CONVERSATION_ID = "conversationId"
        const val ARG_INITIAL_PROMPT = "initialPrompt"

        /** Stable id so the in-progress reply keeps its place in the list. */
        private const val STREAMING_ID = "streaming-draft"
    }
}

/** Rebuilds a rendered row from what the conversation store persisted. */
private fun StoredEntry.toTranscriptItem(
    nextId: () -> String,
    seen: MutableSet<String>,
): TranscriptItem = when (this) {
    is StoredEntry.User -> TranscriptItem.UserPrompt(nextId(), text)

    is StoredEntry.Assistant -> TranscriptItem.AssistantText(nextId(), text)

    is StoredEntry.Reasoning -> TranscriptItem.Reasoning(nextId(), text)

    is StoredEntry.Tool -> {
        val (restoredSummary, restoredPath) = restoreToolMeta(name, argumentsJson)
        TranscriptItem.ToolCall(
            id = "tool-$callId".takeIf(seen::add) ?: nextId(),
            name = name,
            summary = restoredSummary,
            affectedPath = restoredPath,
            // Anything still in flight died with the process that ran it.
            isRunning = false,
            isError = isError,
            output = output,
        )
    }

    is StoredEntry.Failed -> TranscriptItem.Failure(
        id = nextId(),
        error = VmError.Ai(summary = summary),
    )
}

private fun restoreToolMeta(name: String, argumentsJson: String): Pair<String, String?> {
    val obj = runCatching { org.json.JSONObject(argumentsJson) }.getOrNull() ?: return name to null
    val path = obj.optString("path").ifBlank { obj.optString("file_path") }.takeIf { it.isNotBlank() }
    val command = obj.optString("command").takeIf { it.isNotBlank() }
    val url = obj.optString("url").takeIf { it.isNotBlank() }
    val summary = when (name) {
        "read_file" -> path?.let { "Read $it" } ?: name
        "write_file" -> path?.let { "Write $it" } ?: name
        "edit_file" -> path?.let { "Edit $it" } ?: name
        "list_files" -> "List ${path ?: "."}"
        "run_command", "Bash" -> command?.take(80) ?: name
        "fetch_url" -> url?.take(80) ?: name
        else -> path ?: command?.take(80) ?: url?.take(80) ?: name
    }
    val affectedPath = if (name in setOf("write_file", "edit_file", "Write", "Edit", "MultiEdit")) path else null
    return summary to affectedPath
}

/** The visible exchange, for a backend that has to be told what was said before. */
private fun List<TranscriptItem>.toConversationTurns(): List<ConversationTurn> = mapNotNull { item ->
    when (item) {
        is TranscriptItem.UserPrompt -> ConversationTurn(fromUser = true, text = item.text)
        is TranscriptItem.AssistantText -> ConversationTurn(fromUser = false, text = item.text)
        is TranscriptItem.StreamingText -> if (item.text.isNotBlank()) ConversationTurn(fromUser = false, text = item.text) else null
        is TranscriptItem.ToolCall -> {
            val desc = if (item.output.isNullOrBlank()) {
                "Ran tool ${item.name} (${item.summary})"
            } else {
                "Ran tool ${item.name} (${item.summary}):\n${item.output.take(2000)}"
            }
            ConversationTurn(fromUser = false, text = desc)
        }
        is TranscriptItem.SubagentRun -> {
            val summaryText = item.summary?.let { "\nReport: $it" }.orEmpty()
            ConversationTurn(fromUser = false, text = "Subagent [${item.role}] executed task: ${item.prompt}$summaryText")
        }
        else -> null
    }
}

/**
 * Whether a stored session id belongs to the backend [model] runs on. A chat made
 * with a gateway model and reopened while a Claude model is selected used to send
 * `claude --resume omniroute-…`, which failed on every message.
 */
private const val GATEWAY_SESSION_PREFIX = "omniroute-"

/** First line of the first message, shortened on a word boundary. */
internal fun chatTitle(prompt: String): String {
    val line = prompt.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    if (line.length <= MAX_TITLE_CHARS) return line.ifEmpty { "New chat" }
    val cut = line.take(MAX_TITLE_CHARS).substringBeforeLast(' ').ifBlank { line.take(MAX_TITLE_CHARS) }
    return "$cut…"
}

private const val MAX_TITLE_CHARS = 48

/** The history budget the user chose in Settings, in characters. */
private suspend fun UserPreferencesSource.contextChars(): Int =
    UserPreferences.contextChars(preferences.first().aiContextSize)

/** How often running background runs are re-read while their chat is open. */
private const val BACKGROUND_POLL_MILLIS = 15_000L

/** How long a just-started background run is looked for before it is listed. */
private const val PENDING_START_MILLIS = 60_000L
