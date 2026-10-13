package digital.vmstudio.code.feature.connectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.connectors.drive.GoogleDriveFile
import digital.vmstudio.code.core.connectors.drive.GoogleDriveManager
import digital.vmstudio.code.core.connectors.sheets.GoogleSheetsClient
import digital.vmstudio.code.core.git.GitHubRepositoryClient
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
    val servers: List<Server> = emptyList(),
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
    val isTestingSheet: Boolean = false,
    val sheetStatusMessage: String? = null,
    val githubRepoCount: Int? = null,
    val githubStatusMessage: String? = null,
    val hubProjectCount: Int? = null,
    val hubTaskCount: Int? = null,
    val isSyncingHub: Boolean = false,
    val hubStatusMessage: String? = null,
)

private data class ExtraState(
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
    val isTestingSheet: Boolean = false,
    val sheetStatusMessage: String? = null,
    val githubRepoCount: Int? = null,
    val githubStatusMessage: String? = null,
    val hubProjectCount: Int? = null,
    val hubTaskCount: Int? = null,
    val isSyncingHub: Boolean = false,
    val hubStatusMessage: String? = null,
)

@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    private val googleDriveManager: GoogleDriveManager,
    private val googleSheetsClient: GoogleSheetsClient,
    private val githubClient: GitHubRepositoryClient,
    private val projectHubClient: digital.vmstudio.code.core.project.VmProjectHubClient,
    serverRepository: ServerRepository,
) : ViewModel() {

    private val extraState = MutableStateFlow(ExtraState())

    val uiState: StateFlow<ConnectorsUiState> = combine(
        serverRepository.servers,
        extraState,
    ) { srvs, extra ->
        ConnectorsUiState(
            servers = srvs,
            driveBackups = extra.driveBackups,
            isBackingUpDrive = extra.isBackingUpDrive,
            driveStatusMessage = extra.driveStatusMessage,
            isTestingSheet = extra.isTestingSheet,
            sheetStatusMessage = extra.sheetStatusMessage,
            githubRepoCount = extra.githubRepoCount,
            githubStatusMessage = extra.githubStatusMessage,
            hubProjectCount = extra.hubProjectCount,
            hubTaskCount = extra.hubTaskCount,
            isSyncingHub = extra.isSyncingHub,
            hubStatusMessage = extra.hubStatusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectorsUiState(),
    )

    init {
        checkGitHubStatus()
        refreshProjectHubStatus()
    }

    fun refreshProjectHubStatus() {
        viewModelScope.launch {
            val projectsRes = projectHubClient.fetchProjects()
            val tasksRes = projectHubClient.fetchTasks()
            val pCount = (projectsRes as? VmResult.Success)?.value?.size
            val tCount = (tasksRes as? VmResult.Success)?.value?.size
            extraState.update {
                it.copy(
                    hubProjectCount = pCount,
                    hubTaskCount = tCount,
                    hubStatusMessage = if (pCount != null) {
                        "Connected to vmstudio.digital ($pCount universal projects · ${tCount ?: 0} Kanban tasks)"
                    } else {
                        "Ready to sync with vmstudio.digital/wp-json/xcodes/v1"
                    },
                )
            }
        }
    }

    fun syncUniversalHubProjects() {
        if (extraState.value.isSyncingHub) return
        extraState.update { it.copy(isSyncingHub = true, hubStatusMessage = "Syncing universal workspaces from VM Project Hub…") }
        viewModelScope.launch {
            when (val res = projectHubClient.syncUniversalProjects()) {
                is VmResult.Success -> {
                    val tCount = (projectHubClient.fetchTasks() as? VmResult.Success)?.value?.size ?: 0
                    extraState.update {
                        it.copy(
                            isSyncingHub = false,
                            hubProjectCount = res.value,
                            hubTaskCount = tCount,
                            hubStatusMessage = "Synced ${res.value} universal workspaces & $tCount tasks (GitHub / Local / VPS-ready)",
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            isSyncingHub = false,
                            hubStatusMessage = "Sync error: ${res.error.summary}",
                        )
                    }
                }
            }
        }
    }

    private fun checkGitHubStatus() {
        viewModelScope.launch {
            when (val res = githubClient.repositories(forceRefresh = false)) {
                is VmResult.Success -> {
                    extraState.update {
                        it.copy(
                            githubRepoCount = res.value.size,
                            githubStatusMessage = "Connected: ${res.value.size} accessible repositories",
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            githubRepoCount = null,
                            githubStatusMessage = "Not connected (Token required in Settings)",
                        )
                    }
                }
            }
        }
    }

    fun testGoogleSheets(token: String, spreadsheetId: String, range: String) {
        if (token.isBlank() || spreadsheetId.isBlank()) {
            extraState.update {
                it.copy(sheetStatusMessage = "Please enter an Access Token and Spreadsheet ID.")
            }
            return
        }
        viewModelScope.launch {
            extraState.update { it.copy(isTestingSheet = true, sheetStatusMessage = "Testing Sheets API v4...") }
            val cleanRange = range.ifBlank { "A1:E5" }
            when (val result = googleSheetsClient.getValues(token, spreadsheetId, cleanRange)) {
                is VmResult.Success -> {
                    val rowsCount = result.value.values.size
                    extraState.update {
                        it.copy(
                            isTestingSheet = false,
                            sheetStatusMessage = "Success! Read $rowsCount rows from ${result.value.range}.",
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            isTestingSheet = false,
                            sheetStatusMessage = "Failed: ${result.error.summary}",
                        )
                    }
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
                            driveStatusMessage = "Backup created: ${result.value.name} " +
                                "(${result.value.sizeBytes / 1024} KB)",
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
