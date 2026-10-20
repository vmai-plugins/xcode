package digital.vmstudio.code.feature.connectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.provider.AiProviderRegistry
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.connectors.drive.GoogleDriveFile
import digital.vmstudio.code.core.connectors.drive.GoogleDriveManager
import digital.vmstudio.code.core.connectors.sheets.GoogleSheetsClient
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectorFilter {
    ALL,
    CLOUD_SERVERS,
    GIT_VCS,
    DATA_SHEETS,
    AI_GATEWAY,
}

data class ConnectorsUiState(
    val selectedFilter: ConnectorFilter = ConnectorFilter.ALL,
    // VPS
    val servers: List<Server> = emptyList(),
    // Google Drive
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
    // Google Sheets
    val isTestingSheet: Boolean = false,
    val sheetStatusMessage: String? = null,
    val sheetPreviewRows: List<List<String>> = emptyList(),
    // GitHub
    val githubRepoCount: Int? = null,
    val githubUsername: String? = null,
    val hasGithubToken: Boolean = false,
    val isTestingGithub: Boolean = false,
    val githubStatusMessage: String? = null,
    // VM Project Hub
    val hubProjectCount: Int? = null,
    val hubTaskCount: Int? = null,
    val isSyncingHub: Boolean = false,
    val hubStatusMessage: String? = null,
    // AI Gateway
    val aiModelCount: Int = 0,
    val aiFreeModelCount: Int = 0,
    val hasCustomAiKey: Boolean = false,
    val isTestingAi: Boolean = false,
    val aiLatencyMillis: Long? = null,
    val aiStatusMessage: String? = null,
)

private data class MutableExtraState(
    val selectedFilter: ConnectorFilter = ConnectorFilter.ALL,
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
    val isTestingSheet: Boolean = false,
    val sheetStatusMessage: String? = null,
    val sheetPreviewRows: List<List<String>> = emptyList(),
    val githubRepoCount: Int? = null,
    val githubUsername: String? = null,
    val isTestingGithub: Boolean = false,
    val githubStatusMessage: String? = null,
    val hubProjectCount: Int? = null,
    val hubTaskCount: Int? = null,
    val isSyncingHub: Boolean = false,
    val hubStatusMessage: String? = null,
    val isTestingAi: Boolean = false,
    val aiLatencyMillis: Long? = null,
    val aiStatusMessage: String? = null,
)

@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    private val googleDriveManager: GoogleDriveManager,
    private val googleSheetsClient: GoogleSheetsClient,
    private val githubClient: GitHubRepositoryClient,
    private val projectHubClient: digital.vmstudio.code.core.project.VmProjectHubClient,
    private val preferencesRepository: UserPreferencesRepository,
    private val credentialStore: SecureCredentialStore,
    private val aiProviders: AiProviderRegistry,
    serverRepository: ServerRepository,
) : ViewModel() {

    private val extraState = MutableStateFlow(MutableExtraState())

    val uiState: StateFlow<ConnectorsUiState> = combine(
        serverRepository.servers,
        preferencesRepository.preferences,
        extraState,
    ) { srvs, prefs, extra ->
        ConnectorsUiState(
            selectedFilter = extra.selectedFilter,
            servers = srvs,
            driveBackups = extra.driveBackups,
            isBackingUpDrive = extra.isBackingUpDrive,
            driveStatusMessage = extra.driveStatusMessage,
            isTestingSheet = extra.isTestingSheet,
            sheetStatusMessage = extra.sheetStatusMessage,
            sheetPreviewRows = extra.sheetPreviewRows,
            githubRepoCount = extra.githubRepoCount,
            githubUsername = extra.githubUsername ?: prefs.githubUsername,
            hasGithubToken = prefs.githubTokenCredentialId != null,
            isTestingGithub = extra.isTestingGithub,
            githubStatusMessage = extra.githubStatusMessage,
            hubProjectCount = extra.hubProjectCount,
            hubTaskCount = extra.hubTaskCount,
            isSyncingHub = extra.isSyncingHub,
            hubStatusMessage = extra.hubStatusMessage,
            aiModelCount = prefs.aiAvailableModelIds.size,
            aiFreeModelCount = prefs.aiFreeModelIds.size,
            hasCustomAiKey = prefs.aiApiKeyCredentialId != null,
            isTestingAi = extra.isTestingAi,
            aiLatencyMillis = extra.aiLatencyMillis,
            aiStatusMessage = extra.aiStatusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectorsUiState(),
    )

    init {
        checkGitHubStatus()
        refreshProjectHubStatus()
        testAiGateway()
        syncCredentialsIfMissing()
    }

    private fun syncCredentialsIfMissing() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            if (prefs.githubTokenCredentialId == null || prefs.aiApiKeyCredentialId == null) {
                when (val credsRes = projectHubClient.fetchCredentials()) {
                    is VmResult.Success -> {
                        val creds = credsRes.value
                        if (prefs.githubTokenCredentialId == null && !creds.githubToken.isNullOrBlank()) {
                            saveGitHubToken(creds.githubToken)
                        }
                        if (prefs.aiApiKeyCredentialId == null && !creds.omnirouteKey.isNullOrBlank()) {
                            saveCustomAiKey(creds.omnirouteKey)
                        }
                    }
                    is VmResult.Failure -> {}
                }
            }
        }
    }

    fun selectFilter(filter: ConnectorFilter) {
        extraState.update { it.copy(selectedFilter = filter) }
    }

    // --- GitHub -------------------------------------------------------------

    fun saveGitHubToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            extraState.update { it.copy(githubStatusMessage = "Token cannot be empty") }
            return
        }
        viewModelScope.launch {
            extraState.update { it.copy(isTestingGithub = true, githubStatusMessage = "Saving and validating token…") }
            try {
                when (val saveRes = githubClient.saveToken(trimmed)) {
                    is VmResult.Success -> {
                        when (val repoRes = githubClient.repositories(forceRefresh = true)) {
                            is VmResult.Success -> {
                                val user = preferencesRepository.preferences.first().githubUsername
                                extraState.update {
                                    it.copy(
                                        githubRepoCount = repoRes.value.size,
                                        githubUsername = user,
                                        githubStatusMessage = "Connected: ${repoRes.value.size} accessible repositories",
                                    )
                                }
                            }
                            is VmResult.Failure -> {
                                extraState.update {
                                    it.copy(
                                        githubRepoCount = 0,
                                        githubStatusMessage = "Saved token, but repository check failed: ${repoRes.error.summary}",
                                    )
                                }
                            }
                        }
                    }
                    is VmResult.Failure -> {
                        extraState.update { it.copy(githubStatusMessage = "Failed to save: ${saveRes.error.summary}") }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                extraState.update { it.copy(githubStatusMessage = "Error: ${t.message ?: "Unknown error"}") }
            } finally {
                extraState.update { it.copy(isTestingGithub = false) }
            }
        }
    }

    fun clearGitHubToken() {
        viewModelScope.launch {
            extraState.update { it.copy(isTestingGithub = true) }
            try {
                githubClient.clearToken()
                extraState.update {
                    it.copy(
                        githubRepoCount = null,
                        githubUsername = null,
                        githubStatusMessage = "Token removed. GitHub unlinked.",
                    )
                }
            } finally {
                extraState.update { it.copy(isTestingGithub = false) }
            }
        }
    }

    fun testGitHub() {
        viewModelScope.launch {
            extraState.update { it.copy(isTestingGithub = true, githubStatusMessage = "Checking GitHub connection…") }
            try {
                when (val res = githubClient.repositories(forceRefresh = true)) {
                    is VmResult.Success -> {
                        val user = preferencesRepository.preferences.first().githubUsername
                        extraState.update {
                            it.copy(
                                githubRepoCount = res.value.size,
                                githubUsername = user,
                                githubStatusMessage = "Verified! ${res.value.size} repositories accessible",
                            )
                        }
                    }
                    is VmResult.Failure -> {
                        extraState.update {
                            it.copy(
                                githubStatusMessage = "Check failed: ${res.error.summary}",
                            )
                        }
                    }
                }
            } finally {
                extraState.update { it.copy(isTestingGithub = false) }
            }
        }
    }

    private fun checkGitHubStatus() {
        viewModelScope.launch {
            if (!githubClient.hasToken()) {
                extraState.update {
                    it.copy(
                        githubRepoCount = null,
                        githubStatusMessage = "Not connected · Tap 'Connect' to add your Personal Access Token",
                    )
                }
                return@launch
            }
            when (val res = githubClient.repositories(forceRefresh = false)) {
                is VmResult.Success -> {
                    val user = preferencesRepository.preferences.first().githubUsername
                    extraState.update {
                        it.copy(
                            githubRepoCount = res.value.size,
                            githubUsername = user,
                            githubStatusMessage = "Connected: ${res.value.size} accessible repositories",
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            githubStatusMessage = "Token configured, but unreachable: ${res.error.summary}",
                        )
                    }
                }
            }
        }
    }

    // --- VM Project Hub -----------------------------------------------------

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
        extraState.update { it.copy(isSyncingHub = true, hubStatusMessage = "Syncing universal workspaces & credentials from VM Project Hub…") }
        viewModelScope.launch {
            try {
                // Auto-sync credentials from Hub if present
                when (val credsRes = projectHubClient.fetchCredentials()) {
                    is VmResult.Success -> {
                        val creds = credsRes.value
                        if (!creds.githubToken.isNullOrBlank() && !githubClient.hasToken()) {
                            saveGitHubToken(creds.githubToken)
                        }
                        if (!creds.omnirouteKey.isNullOrBlank() && preferencesRepository.preferences.first().aiApiKeyCredentialId == null) {
                            saveCustomAiKey(creds.omnirouteKey)
                        }
                    }
                    is VmResult.Failure -> {}
                }

                when (val res = projectHubClient.syncUniversalProjects()) {
                    is VmResult.Success -> {
                        val tCount = (projectHubClient.fetchTasks() as? VmResult.Success)?.value?.size ?: 0
                        extraState.update {
                            it.copy(
                                hubProjectCount = res.value,
                                hubTaskCount = tCount,
                                hubStatusMessage = "Synced ${res.value} universal workspaces & $tCount tasks (GitHub / Local / VPS-ready)",
                            )
                        }
                    }
                    is VmResult.Failure -> {
                        extraState.update {
                            it.copy(
                                hubStatusMessage = "Sync error: ${res.error.summary}",
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                extraState.update { it.copy(hubStatusMessage = "Sync failed: ${t.message ?: "Unknown error"}") }
            } finally {
                extraState.update { it.copy(isSyncingHub = false) }
            }
        }
    }

    fun syncCredentialsFromHub() {
        viewModelScope.launch {
            extraState.update { it.copy(hubStatusMessage = "Pulling credentials from VM Project Hub…") }
            try {
                when (val credsRes = projectHubClient.fetchCredentials()) {
                    is VmResult.Failure -> {
                        extraState.update { it.copy(hubStatusMessage = "Credential sync failed: ${credsRes.error.summary}") }
                    }
                    is VmResult.Success -> {
                        val creds = credsRes.value
                        val syncedParts = mutableListOf<String>()
                        if (!creds.githubToken.isNullOrBlank()) {
                            saveGitHubToken(creds.githubToken)
                            syncedParts += "GitHub PAT"
                        }
                        if (!creds.omnirouteKey.isNullOrBlank()) {
                            saveCustomAiKey(creds.omnirouteKey)
                            syncedParts += "OmniRoute AI key"
                        }
                        extraState.update {
                            it.copy(
                                hubStatusMessage = "Synced ${syncedParts.joinToString(" & ")} from VM Project Hub"
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                extraState.update { it.copy(hubStatusMessage = "Credential sync error: ${t.message ?: "Unknown"}") }
            }
        }
    }

    // --- Google Sheets ------------------------------------------------------

    fun testGoogleSheets(token: String, spreadsheetId: String, range: String) {
        if (token.isBlank() || spreadsheetId.isBlank()) {
            extraState.update {
                it.copy(sheetStatusMessage = "Please enter an Access Token and Spreadsheet ID.")
            }
            return
        }
        viewModelScope.launch {
            extraState.update { it.copy(isTestingSheet = true, sheetStatusMessage = "Querying Sheets API v4…", sheetPreviewRows = emptyList()) }
            try {
                val cleanRange = range.ifBlank { "Sheet1!A1:E5" }
                when (val result = googleSheetsClient.getValues(token, spreadsheetId, cleanRange)) {
                    is VmResult.Success -> {
                        val rows = result.value.stringValues
                        extraState.update {
                            it.copy(
                                sheetPreviewRows = rows.take(5),
                                sheetStatusMessage = "Success! Read ${rows.size} rows from ${result.value.range}.",
                            )
                        }
                    }
                    is VmResult.Failure -> {
                        extraState.update {
                            it.copy(
                                sheetStatusMessage = "Failed: ${result.error.summary}",
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                extraState.update { it.copy(sheetStatusMessage = "Failed: ${t.message ?: "Unknown error"}") }
            } finally {
                extraState.update { it.copy(isTestingSheet = false) }
            }
        }
    }

    // --- Google Drive -------------------------------------------------------

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
                    driveStatusMessage = "Archiving $projectName on VPS and uploading to Google Drive…",
                )
            }
            try {
                when (val result = googleDriveManager.backupProject(serverId, remotePath, projectName, token)) {
                    is VmResult.Success -> {
                        extraState.update {
                            it.copy(
                                driveStatusMessage = "Backup created: ${result.value.name} " +
                                    "(${result.value.sizeBytes / 1024} KB)",
                            )
                        }
                        refreshDriveBackups(token)
                    }
                    is VmResult.Failure -> {
                        extraState.update {
                            it.copy(
                                driveStatusMessage = "Backup failed: ${result.error.summary}",
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                extraState.update { it.copy(driveStatusMessage = "Backup error: ${t.message ?: "Unknown error"}") }
            } finally {
                extraState.update { it.copy(isBackingUpDrive = false) }
            }
        }
    }

    // --- AI Gateway & Provider ----------------------------------------------

    fun testAiGateway() {
        viewModelScope.launch {
            extraState.update { it.copy(isTestingAi = true, aiStatusMessage = "Testing AI gateway latency & sync…") }
            try {
                val start = System.currentTimeMillis()
                val provider = aiProviders.active()
                when (val res = aiProviders.checkHealth(provider, serverId = null, forceRefresh = true)) {
                    is VmResult.Success -> {
                        val latency = System.currentTimeMillis() - start
                        val health = res.value
                        preferencesRepository.setAiModels(
                            models = health.availableModels,
                            freeIds = health.freeModels,
                        )
                        extraState.update {
                            it.copy(
                                aiLatencyMillis = latency,
                                aiStatusMessage = "Online · ${health.availableModels.size} models (${health.freeModels.size} free 0-cost) · ${latency}ms latency",
                            )
                        }
                    }
                    is VmResult.Failure -> {
                        extraState.update {
                            it.copy(
                                aiStatusMessage = "Gateway notice: ${res.error.summary}",
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                extraState.update { it.copy(aiStatusMessage = "Check error: ${t.message ?: "Unknown"}") }
            } finally {
                extraState.update { it.copy(isTestingAi = false) }
            }
        }
    }

    fun saveCustomAiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            extraState.update { it.copy(isTestingAi = true, aiStatusMessage = "Encrypting API key in Android Keystore…") }
            try {
                val previousId = preferencesRepository.preferences.first().aiApiKeyCredentialId
                val secret = Secret.of(trimmed)
                when (
                    val stored = credentialStore.put(
                        type = SecretType.AI_API_KEY,
                        label = "AI gateway key",
                        secret = secret,
                    )
                ) {
                    is VmResult.Success -> {
                        preferencesRepository.setAiApiKeyCredentialId(stored.value.id)
                        previousId?.let { credentialStore.delete(it) }
                        testAiGateway()
                    }
                    is VmResult.Failure -> {
                        extraState.update { it.copy(aiStatusMessage = "Failed to store key: ${stored.error.summary}") }
                    }
                }
            } finally {
                extraState.update { it.copy(isTestingAi = false) }
            }
        }
    }

    fun clearCustomAiKey() {
        viewModelScope.launch {
            val previousId = preferencesRepository.preferences.first().aiApiKeyCredentialId
            preferencesRepository.setAiApiKeyCredentialId(null)
            previousId?.let { credentialStore.delete(it) }
            extraState.update {
                it.copy(aiStatusMessage = "Using built-in VM Studio OmniRoute Cloud key")
            }
            testAiGateway()
        }
    }
}
