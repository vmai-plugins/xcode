package digital.vmstudio.code.feature.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.connectors.agent.ProjectsHubAgentManager
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import digital.vmstudio.code.core.database.entity.TaskPriority
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProjectDetailUiState(
    val project: Project? = null,
    val server: Server? = null,
    val tasks: List<AgentTaskEntity> = emptyList(),
    val isAuditing: Boolean = false,
    val auditMessage: String? = null,
    val error: VmError? = null,
)

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val agentTaskDao: AgentTaskDao,
    private val agentManager: ProjectsHubAgentManager,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val project = MutableStateFlow<Project?>(null)
    private val server = MutableStateFlow<Server?>(null)
    private val isAuditing = MutableStateFlow(false)
    private val auditMessage = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<VmError?>(null)

    private val baseProjectFlow = combine(project, server, isAuditing) { proj, srv, auditing ->
        Triple(proj, srv, auditing)
    }

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        baseProjectFlow,
        agentTaskDao.observeForProject(projectId),
        auditMessage,
        error,
    ) { (proj, srv, auditing), taskList, msg, err ->
        ProjectDetailUiState(
            project = proj,
            server = srv,
            tasks = taskList,
            isAuditing = auditing,
            auditMessage = msg,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectDetailUiState(),
    )

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val proj = projectRepository.get(projectId)
            project.value = proj
            val srvId = proj?.serverId
            if (srvId != null) {
                when (val result = serverRepository.get(srvId)) {
                    is VmResult.Success -> server.value = result.value
                    is VmResult.Failure -> server.value = null
                }
            }
        }
    }

    fun auditProject() {
        viewModelScope.launch {
            isAuditing.value = true
            auditMessage.value = null
            when (val result = agentManager.auditProject(projectId)) {
                is VmResult.Success -> {
                    auditMessage.value = "Audit triggered successfully. Growth run dispatched."
                }
                is VmResult.Failure -> {
                    error.value = result.error
                }
            }
            isAuditing.value = false
        }
    }

    fun toggleTask(task: AgentTaskEntity) {
        viewModelScope.launch {
            val newStatus = if (task.status == AgentTaskStatus.COMPLETED) {
                AgentTaskStatus.TODO
            } else {
                AgentTaskStatus.COMPLETED
            }
            agentTaskDao.updateStatus(task.id, newStatus, System.currentTimeMillis())
        }
    }

    fun addTask(title: String, priority: TaskPriority = TaskPriority.NORMAL) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newTask = AgentTaskEntity(
                id = "task_" + UUID.randomUUID().toString().take(8),
                projectId = projectId,
                conversationId = null,
                title = title.trim(),
                description = "",
                status = AgentTaskStatus.TODO,
                priority = priority,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            agentTaskDao.upsert(newTask)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            agentTaskDao.deleteById(taskId)
        }
    }

    fun dismissMessage() {
        auditMessage.value = null
        error.value = null
    }
}
