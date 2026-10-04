package digital.vmstudio.code.feature.connectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.connectors.api.ProjectsHubClient
import digital.vmstudio.code.core.connectors.drive.GoogleDriveFile
import digital.vmstudio.code.core.connectors.drive.GoogleDriveManager
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectorsUiState(
    val syncState: HubSyncState = HubSyncState(),
    val servers: List<Server> = emptyList(),
    val mcpTools: List<HubMcpTool> = emptyList(),
    val isLoadingMcp: Boolean = false,
    val mcpError: String? = null,
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
)

private data class ExtraState(
    val mcpTools: List<HubMcpTool> = emptyList(),
    val isLoadingMcp: Boolean = false,
    val mcpError: String? = null,
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
)

@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    private val hubClient: ProjectsHubClient,
    private val syncManager: ProjectsHubSyncManager,
    private val googleDriveManager: GoogleDriveManager,
    serverRepository: ServerRepository,
) : ViewModel() {

    private val extraState = MutableStateFlow(ExtraState())

    val uiState: StateFlow<ConnectorsUiState> = combine(
        syncManager.syncState,
        serverRepository.servers,
        extraState,
    ) { sync, srvs, extra ->
        ConnectorsUiState(
            syncState = sync,
            servers = srvs,
            mcpTools = extra.mcpTools,
            isLoadingMcp = extra.isLoadingMcp,
            mcpError = extra.mcpError,
            driveBackups = extra.driveBackups,
            isBackingUpDrive = extra.isBackingUpDrive,
            driveStatusMessage = extra.driveStatusMessage,
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
            extraState.update { it.copy(isLoadingMcp = true, mcpError = null) }
            when (val result = hubClient.getMcpTools()) {
                is VmResult.Success -> {
                    extraState.update { it.copy(mcpTools = result.value, isLoadingMcp = false) }
                }
                is VmResult.Failure -> {
                    extraState.update { it.copy(mcpError = result.error.summary, isLoadingMcp = false) }
                }
            }
        }
    }

    fun refreshDriveBackups(token: String) {
        if (token.isBlank()) return
        viewModelScope.launch {
            when (val result = googleDriveManager.listBackups(token)) {
                is VmResult.Success -> {
                    extraState.update {
                        it.copy(
                            driveBackups = result.value,
                            driveStatusMessage = "Synced ${result.value.size} backups from Google Drive",
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            driveStatusMessage = "Drive sync failed: ${result.error.summary}",
                        )
                    }
                }
            }
        }
    }

    fun backupProjectToDrive(
        serverId: String,
        remotePath: String,
        projectName: String,
        token: String,
    ) {
        if (token.isBlank() || extraState.value.isBackingUpDrive) return
        viewModelScope.launch {
            extraState.update {
                it.copy(
                    isBackingUpDrive = true,
                    driveStatusMessage = "Archiving $projectName on VPS and uploading to Google Drive...",
                )
            }
            when (val result = googleDriveManager.backupProject(serverId, remotePath, projectName, token)) {
                is VmResult.Success -> {
                    extraState.update {
                        it.copy(
                            isBackingUpDrive = false,
                            driveStatusMessage = "Backup created: ${result.value.name} (${result.value.sizeBytes / 1024} KB)",
                        )
                    }
                    refreshDriveBackups(token)
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            isBackingUpDrive = false,
                            driveStatusMessage = "Backup failed: ${result.error.summary}",
                        )
                    }
                }
            }
        }
    }
}
