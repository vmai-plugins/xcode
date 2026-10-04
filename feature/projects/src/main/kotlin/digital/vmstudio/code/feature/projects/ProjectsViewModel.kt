package digital.vmstudio.code.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val servers: List<ServerOption> = emptyList(),
    val isLoading: Boolean = true,
    val error: VmError? = null,
) {
    /**
     * Server lookup keyed once per state update rather than scanned per row.
     *
     * `serverFor` used to do `servers.firstOrNull { it.id == ... }` inside the list
     * composable — an O(servers) scan on every row on every recomposition. Building
     * the map here means the list only pays for the index once per state change.
     */
    private val serversById: Map<String, ServerOption> by lazy { servers.associateBy { it.id } }

    fun serverFor(project: Project): ServerOption? = project.serverId?.let(serversById::get)

    val canCreate: Boolean get() = servers.isNotEmpty()
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projects: ProjectRepository,
    servers: ServerRepository,
) : ViewModel() {

    private val error = MutableStateFlow<VmError?>(null)

    val uiState: StateFlow<ProjectsUiState> = combine(
        projects.observeAll(),
        servers.servers,
        error,
    ) { projectList, serverList, currentError ->
        ProjectsUiState(
            projects = projectList,
            servers = serverList.toServerOptions(),
            isLoading = false,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectsUiState(),
    )

    /** Records the open so the list orders by recency, then hands back the project. */
    fun open(project: Project, onOpened: (Project) -> Unit) {
        viewModelScope.launch {
            projects.markOpened(project.id)
            onOpened(project)
        }
    }

    fun toggleFavorite(project: Project) {
        viewModelScope.launch { projects.setFavorite(project.id, !project.isFavorite) }
    }

    fun delete(project: Project) {
        viewModelScope.launch {
            when (val result = projects.delete(project.id)) {
                is VmResult.Failure -> error.value = result.error
                is VmResult.Success -> Unit
            }
        }
    }

    fun dismissError() {
        error.value = null
    }
}
