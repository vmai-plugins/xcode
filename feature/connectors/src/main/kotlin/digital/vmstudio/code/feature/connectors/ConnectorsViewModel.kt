package digital.vmstudio.code.feature.connectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.connectors.api.ProjectsHubClient
import digital.vmstudio.code.core.connectors.model.HubMcpTool
import digital.vmstudio.code.core.connectors.sync.HubSyncState
import digital.vmstudio.code.core.connectors.sync.ProjectsHubSyncManager
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectorsUiState(
    val syncState: HubSyncState = HubSyncState(),
    val servers: List<Server> = emptyList(),
    val mcpTools: List<HubMcpTool> = emptyList(),
    val isLoadingMcp: Boolean = false,
    val mcpError: String? = null,
)

@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    private val hubClient: ProjectsHubClient,
    private val syncManager: ProjectsHubSyncManager,
    serverRepository: ServerRepository,
) : ViewModel() {

    private val mcpTools = MutableStateFlow<List<HubMcpTool>>(emptyList())
    private val isLoadingMcp = MutableStateFlow(false)
    private val mcpError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConnectorsUiState> = combine(
        syncManager.syncState,
        serverRepository.servers,
        mcpTools,
        isLoadingMcp,
        mcpError,
    ) { sync, srvs, tools, loadingMcp, err ->
        ConnectorsUiState(
            syncState = sync,
            servers = srvs,
            mcpTools = tools,
            isLoadingMcp = loadingMcp,
            mcpError = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectorsUiState(),
    )

    init {
        loadMcpTools()
    }

    fun syncNow() {
        viewModelScope.launch {
            syncManager.syncAll()
            loadMcpTools()
        }
    }

    fun loadMcpTools() {
        viewModelScope.launch {
            isLoadingMcp.value = true
            mcpError.value = null
            when (val result = hubClient.getMcpTools()) {
                is VmResult.Success -> {
                    mcpTools.value = result.value
                }
                is VmResult.Failure -> {
                    mcpError.value = result.error.summary
                }
            }
            isLoadingMcp.value = false
        }
    }
}
