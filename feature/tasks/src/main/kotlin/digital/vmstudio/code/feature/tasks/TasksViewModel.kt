package digital.vmstudio.code.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.repository.AgentTask
import digital.vmstudio.code.core.ai.repository.AgentTaskRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.project.HubTaskItem
import digital.vmstudio.code.core.project.VmProjectHubClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Tasks screen: combines live Kanban tasks from VM Project Hub
 * (`vmstudio.digital/wp-json/xcodes/v1/tasks`) and recorded AI agent runs.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: AgentTaskRepository,
    private val projectHubClient: VmProjectHubClient,
) : ViewModel() {

    private val hubTasks = MutableStateFlow<List<HubTaskItem>>(emptyList())
    private val isSyncingHub = MutableStateFlow(false)

    val uiState: StateFlow<TasksUiState> = combine(
        tasks.observeAll(),
        hubTasks,
        isSyncingHub,
    ) { list, hubList, syncing ->
        val projectIds = list.map { it.projectId }.distinct()
        val serverMap = coroutineScope {
            projectIds.associateWith { pid ->
                tasks.serverIdFor(pid)
            }
        }
        val rows = list.map { task ->
            TaskRowUi(task = task, serverId = serverMap[task.projectId])
        }
        TasksUiState(
            isLoading = false,
            isSyncingHub = syncing,
            tasks = rows,
            hubTasks = hubList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TasksUiState(isLoading = true),
    )

    init {
        syncHubTasks()
    }

    fun syncHubTasks() {
        if (isSyncingHub.value) return
        isSyncingHub.value = true
        viewModelScope.launch {
            try {
                when (val res = projectHubClient.fetchTasks()) {
                    is VmResult.Success -> hubTasks.value = res.value
                    is VmResult.Failure -> Unit
                }
            } finally {
                isSyncingHub.value = false
            }
        }
    }

    fun cycleHubTaskStatus(item: HubTaskItem) {
        val nextStatus = when (item.status.lowercase()) {
            "todo" -> "in_progress"
            "in_progress" -> "done"
            else -> "todo"
        }
        val previousStatus = item.status
        hubTasks.value = hubTasks.value.map {
            if (it.id == item.id) it.copy(status = nextStatus) else it
        }
        viewModelScope.launch {
            val result = projectHubClient.updateTaskStatus(item.id, nextStatus)
            if (result is VmResult.Failure) {
                // Roll back optimistic state on network or server error
                hubTasks.value = hubTasks.value.map {
                    if (it.id == item.id) it.copy(status = previousStatus) else it
                }
            }
        }
    }

    fun delete(taskId: String) {
        viewModelScope.launch { tasks.delete(taskId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** One rendered row: the task plus the server its changes can be diffed on. */
data class TaskRowUi(
    val task: AgentTask,
    val serverId: String?,
)

data class TasksUiState(
    val isLoading: Boolean = false,
    val isSyncingHub: Boolean = false,
    val tasks: List<TaskRowUi> = emptyList(),
    val hubTasks: List<HubTaskItem> = emptyList(),
)

