package digital.vmstudio.code.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.repository.AgentConversation
import digital.vmstudio.code.core.ai.repository.AgentConversationRepository
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import javax.inject.Inject

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val servers: List<ServerOption> = emptyList(),
    val isLoading: Boolean = true,
    val error: VmError? = null,
) {
    /**
     * Server lookup keyed once per state update rather than scanned per row.
     */
    private val serversById: Map<String, ServerOption> by lazy { servers.associateBy { it.id } }

    fun serverFor(project: Project): ServerOption? {
        val id = project.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        return serversById[id]
    }

    val canCreate: Boolean get() = true
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val conversations: AgentConversationRepository,
    servers: ServerRepository,
) : ViewModel() {

    private val error = MutableStateFlow<VmError?>(null)

    val uiState: StateFlow<ProjectsUiState> = combine(
        projects.observeAll(),
        servers.servers,
        error,
    ) { projectList, serverList, currentError ->
        val localOption = ServerOption(
            id = RemoteFileSystem.LOCAL_SERVER_ID,
            name = "This Device (Local)",
            target = "Local Phone Storage",
        )
        val allOptions = listOf(localOption) + serverList.toServerOptions()
        ProjectsUiState(
            projects = projectList,
            servers = allOptions,
            isLoading = false,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectsUiState(),
    )

    /** Records the open so the list orders by recency, then hands back the project. */
    private var opening = false

    /** A quick double tap used to push the project screen twice. */
    fun open(project: Project, onOpened: (Project) -> Unit) {
        if (opening) return
        opening = true
        viewModelScope.launch {
            try {
                projects.markOpened(project.id)
                onOpened(project)
            } finally {
                opening = false
            }
        }
    }

    fun toggleFavorite(project: Project) {
        viewModelScope.launch { projects.setFavorite(project.id, !project.isFavorite) }
    }

    fun updateColor(project: Project, colorHex: String?) {
        viewModelScope.launch { projects.updateColor(project.id, colorHex) }
    }

    fun delete(project: Project) {
        viewModelScope.launch {
            when (val result = projects.delete(project.id)) {
                is VmResult.Failure -> error.value = result.error
                // Its chats go too; otherwise Recents keeps pointing at a project that is gone.
                is VmResult.Success -> conversations.observeConversations().first()
                    .filter { it.belongsTo(project) }
                    .forEach { conversations.delete(it.id) }
            }
        }
    }

    fun dismissError() {
        error.value = null
    }
}

/** A chat belongs to a project when it ran on the project's server, in its folder. */
internal fun AgentConversation.belongsTo(project: Project): Boolean =
    serverId != null &&
        serverId == project.serverId &&
        workingDirectory?.trimEnd('/')?.ifEmpty { "/" } == project.remotePath.trimEnd('/').ifEmpty { "/" }
