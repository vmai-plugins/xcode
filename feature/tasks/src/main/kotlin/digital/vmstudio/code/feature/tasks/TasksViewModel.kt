package digital.vmstudio.code.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.repository.AgentTask
import digital.vmstudio.code.core.ai.repository.AgentTaskRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Tasks record screen. The list is a live Flow from the repository, so a
 * run that finishes in the background updates an open Tasks screen by itself.
 *
 * Each row is paired with its project's server id so the UI can open diff review
 * without knowing anything about how projects map to servers.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: AgentTaskRepository,
) : ViewModel() {

    val uiState: StateFlow<TasksUiState> =
        tasks.observeAll().map { list ->
            // Fetched concurrently rather than one query per task in sequence: a
            // busy project can emit rapidly, and awaiting each lookup in turn would
            // make this pipeline fall increasingly behind the live repository state.
            val rows = coroutineScope {
                list.map { task ->
                    async { TaskRowUi(task = task, serverId = tasks.serverIdFor(task.projectId)) }
                }.awaitAll()
            }
            TasksUiState(isLoading = false, tasks = rows)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TasksUiState(isLoading = true),
        )

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
    val tasks: List<TaskRowUi> = emptyList(),
)

