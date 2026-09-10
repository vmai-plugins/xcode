package digital.vmstudio.code.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectCreateUiState(
    val name: String = "",
    val remotePath: String = "",
    val servers: List<ServerOption> = emptyList(),
    val selectedServerId: String? = null,
    val isSaving: Boolean = false,
    val createdProjectId: String? = null,
    val nameError: String? = null,
    val pathError: String? = null,
    val error: VmError? = null,
) {
    val canCreate: Boolean
        get() = name.isNotBlank() &&
            remotePath.isNotBlank() &&
            selectedServerId != null &&
            !isSaving
}

@HiltViewModel
class ProjectCreateViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val servers: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectCreateUiState())
    val uiState: StateFlow<ProjectCreateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = servers.servers.first().toServerOptions()
            _uiState.update { state ->
                state.copy(
                    servers = list,
                    // Preselected when there is only one, since choosing from a list
                    // of one is not a decision.
                    selectedServerId = state.selectedServerId ?: list.singleOrNull()?.id,
                )
            }
        }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value, nameError = null, error = null) }
    }

    fun setRemotePath(value: String) {
        _uiState.update { it.copy(remotePath = value, pathError = null, error = null) }
    }

    fun selectServer(id: String) {
        _uiState.update { it.copy(selectedServerId = id) }
    }

    fun create() {
        val state = _uiState.value
        val serverId = state.selectedServerId ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            when (
                val result = projects.create(
                    name = state.name,
                    serverId = serverId,
                    remotePath = state.remotePath,
                )
            ) {
                is VmResult.Success -> _uiState.update {
                    it.copy(isSaving = false, createdProjectId = result.value)
                }

                is VmResult.Failure -> _uiState.update {
                    val error = result.error
                    // Field-level errors are shown against the field they concern;
                    // anything else goes to the panel.
                    val fieldName = (error as? VmError.Validation)?.fieldName
                    it.copy(
                        isSaving = false,
                        nameError = if (fieldName == "name") error.summary else null,
                        pathError = if (fieldName == "remotePath") error.summary else null,
                        error = if (fieldName == null) error else null,
                    )
                }
            }
        }
    }
}
