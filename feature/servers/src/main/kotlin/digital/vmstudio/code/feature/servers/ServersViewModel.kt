package digital.vmstudio.code.feature.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.model.ServerGroup
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServersUiState(
    val isLoading: Boolean = true,
    val servers: List<Server> = emptyList(),
    val groups: List<ServerGroup> = emptyList(),
    val error: VmError? = null,
) {
    val isEmpty: Boolean get() = !isLoading && servers.isEmpty()

    /** Servers grouped for display; ungrouped servers appear under a null key. */
    val grouped: Map<ServerGroup?, List<Server>>
        get() = servers.groupBy { server ->
            groups.firstOrNull { it.id == server.groupId }
        }
}

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connectionManager: SshConnectionManager,
) : ViewModel() {

    private val transientError = MutableStateFlow<VmError?>(null)

    val uiState: StateFlow<ServersUiState> = combine(
        serverRepository.servers,
        serverRepository.groups,
        transientError,
    ) { servers, groups, error ->
        ServersUiState(
            isLoading = false,
            servers = servers,
            groups = groups,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the flow alive briefly across configuration changes so rotating
        // the device does not re-query and flash an empty list.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServersUiState(),
    )

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            when (val result = serverRepository.delete(serverId)) {
                is VmResult.Failure -> transientError.value = result.error
                is VmResult.Success -> {
                    transientError.value = null
                    connectionManager.disconnect(serverId)
                }
            }
        }
    }

    fun dismissError() {
        transientError.value = null
    }
}
