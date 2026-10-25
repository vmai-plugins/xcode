package digital.vmstudio.code.feature.connectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.provider.AiProviderRegistry
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.connectors.drive.GoogleDriveClient
import digital.vmstudio.code.core.connectors.drive.GoogleDriveFile
import digital.vmstudio.code.core.connectors.drive.GoogleDriveManager
import digital.vmstudio.code.core.connectors.sheets.GoogleSheetsClient
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import digital.vmstudio.code.core.connectors.whatsapp.EvolutionApiClient
import digital.vmstudio.code.core.connectors.whatsapp.EvolutionConfig
import digital.vmstudio.code.core.security.model.CredentialRef
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.project.HubCredentials
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.model.ServerDraft
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
    WHATSAPP,
}

data class ConnectorsUiState(
    val selectedFilter: ConnectorFilter = ConnectorFilter.ALL,
    // VPS
    val servers: List<Server> = emptyList(),
    // Google Drive
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
    val hasDriveToken: Boolean = false,
    val savedDriveToken: String? = null,
    val isBrowsingDrive: Boolean = false,
    val driveBrowserFolderId: String = "root",
    val driveBrowserFolderName: String = "My Drive",
    val driveFolderBreadcrumbs: List<Pair<String, String>> = emptyList(),
    val driveFiles: List<GoogleDriveFile> = emptyList(),
    val isLoadingDriveFiles: Boolean = false,
    val drivePreviewFile: GoogleDriveFile? = null,
    val drivePreviewContent: String? = null,
    val isLoadingDrivePreview: Boolean = false,
    // Google Sheets
    val isTestingSheet: Boolean = false,
    val sheetStatusMessage: String? = null,
    val sheetPreviewRows: List<List<String>> = emptyList(),
    val hasSheetsToken: Boolean = false,
    val savedSheetsToken: String? = null,
    val savedSpreadsheetId: String? = null,
    // GitHub
    val githubRepoCount: Int? = null,
    val githubUsername: String? = null,
    val hasGithubToken: Boolean = false,
    val isTestingGithub: Boolean = false,
    val githubStatusMessage: String? = null,
    // VM Project Hub
    val hubProjectCount: Int? = null,
    val hubTaskCount: Int? = null,
    val hasHubKey: Boolean = false,
    val hubBaseUrl: String = "https://saketdhamashram.org",
    val isSyncingHub: Boolean = false,
    val hubStatusMessage: String? = null,
    val vpsConnectionState: digital.vmstudio.code.core.ssh.connection.SshConnectionState = digital.vmstudio.code.core.ssh.connection.SshConnectionState.Disconnected,
    val isConnectingVps: Boolean = false,
    val vpsHost: String? = null,
    // AI Gateway
    val aiModelCount: Int = 0,
    val aiFreeModelCount: Int = 0,
    val hasCustomAiKey: Boolean = false,
    val isTestingAi: Boolean = false,
    val aiLatencyMillis: Long? = null,
    val aiStatusMessage: String? = null,
    // WhatsApp (Evolution API)
    val evolutionApiBaseUrl: String = "https://wa.vmstudio.digital",
    val evolutionApiInstance: String = "",
    val evolutionApiRecipientNumber: String = "",
    val hasEvolutionApiKey: Boolean = false,
    val savedEvolutionApiKey: String? = null,
    val evolutionApiNotificationsEnabled: Boolean = false,
    val isTestingEvolutionApi: Boolean = false,
    val evolutionApiConnected: Boolean? = null,
    val evolutionApiStatusMessage: String? = null,
)

private data class MutableExtraState(
    val selectedFilter: ConnectorFilter = ConnectorFilter.ALL,
    val driveBackups: List<GoogleDriveFile> = emptyList(),
    val isBackingUpDrive: Boolean = false,
    val driveStatusMessage: String? = null,
    val savedDriveToken: String? = null,
    val isBrowsingDrive: Boolean = false,
    val driveBrowserFolderId: String = "root",
    val driveBrowserFolderName: String = "My Drive",
    val driveFolderBreadcrumbs: List<Pair<String, String>> = emptyList(),
    val driveFiles: List<GoogleDriveFile> = emptyList(),
    val isLoadingDriveFiles: Boolean = false,
    val drivePreviewFile: GoogleDriveFile? = null,
    val drivePreviewContent: String? = null,
    val isLoadingDrivePreview: Boolean = false,
    val isTestingSheet: Boolean = false,
    val sheetStatusMessage: String? = null,
    val sheetPreviewRows: List<List<String>> = emptyList(),
    val savedSheetsToken: String? = null,
    val savedSpreadsheetId: String? = null,
    val githubRepoCount: Int? = null,
    val githubUsername: String? = null,
    val isTestingGithub: Boolean = false,
    val githubStatusMessage: String? = null,
    val hubProjectCount: Int? = null,
    val hubTaskCount: Int? = null,
    val isSyncingHub: Boolean = false,
    val hubStatusMessage: String? = null,
    val isConnectingVps: Boolean = false,
    val isTestingAi: Boolean = false,
    val aiLatencyMillis: Long? = null,
    val aiStatusMessage: String? = null,
    val evolutionApiInstance: String? = null,
    val evolutionApiRecipientNumber: String? = null,
    val savedEvolutionApiKey: String? = null,
    val evolutionApiNotificationsEnabled: Boolean = false,
    val isTestingEvolutionApi: Boolean = false,
    val evolutionApiConnected: Boolean? = null,
    val evolutionApiStatusMessage: String? = null,
)

@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    private val googleDriveManager: GoogleDriveManager,
    private val googleDriveClient: GoogleDriveClient,
    private val googleSheetsClient: GoogleSheetsClient,
    private val githubClient: GitHubRepositoryClient,
    private val projectHubClient: digital.vmstudio.code.core.project.VmProjectHubClient,
    private val preferencesRepository: UserPreferencesRepository,
    private val credentialStore: SecureCredentialStore,
    private val aiProviders: AiProviderRegistry,
    private val serverRepository: ServerRepository,
    private val connectionManager: digital.vmstudio.code.core.ssh.connection.SshConnectionManager,
    private val evolutionApiClient: EvolutionApiClient,
) : ViewModel() {

    private val extraState = MutableStateFlow(MutableExtraState())

    val uiState: StateFlow<ConnectorsUiState> = combine(
        serverRepository.servers,
        preferencesRepository.preferences,
        extraState,
        connectionManager.states,
    ) { srvs, prefs, extra, connStates ->
        val targetVps = prefs.autoConnectServerId?.let { id -> srvs.firstOrNull { it.id == id } }
            ?: srvs.firstOrNull { it.host.contains("vmstudio", ignoreCase = true) || it.host == "200.234.41.231" }
            ?: srvs.firstOrNull()
        val vpsState = targetVps?.id?.let { connStates[it] } ?: digital.vmstudio.code.core.ssh.connection.SshConnectionState.Disconnected

        ConnectorsUiState(
            selectedFilter = extra.selectedFilter,
            servers = srvs,
            driveBackups = extra.driveBackups,
            isBackingUpDrive = extra.isBackingUpDrive,
            driveStatusMessage = extra.driveStatusMessage,
            hasDriveToken = prefs.googleDriveTokenCredentialId != null,
            savedDriveToken = extra.savedDriveToken,
            isBrowsingDrive = extra.isBrowsingDrive,
            driveBrowserFolderId = extra.driveBrowserFolderId,
            driveBrowserFolderName = extra.driveBrowserFolderName,
            driveFolderBreadcrumbs = extra.driveFolderBreadcrumbs,
            driveFiles = extra.driveFiles,
            isLoadingDriveFiles = extra.isLoadingDriveFiles,
            drivePreviewFile = extra.drivePreviewFile,
            drivePreviewContent = extra.drivePreviewContent,
            isLoadingDrivePreview = extra.isLoadingDrivePreview,
            isTestingSheet = extra.isTestingSheet,
            sheetStatusMessage = extra.sheetStatusMessage,
            sheetPreviewRows = extra.sheetPreviewRows,
            hasSheetsToken = prefs.googleSheetsTokenCredentialId != null,
            savedSheetsToken = extra.savedSheetsToken,
            savedSpreadsheetId = extra.savedSpreadsheetId ?: prefs.googleSheetsSpreadsheetId,
            githubRepoCount = extra.githubRepoCount,
            githubUsername = extra.githubUsername ?: prefs.githubUsername,
            hasGithubToken = prefs.githubTokenCredentialId != null,
            isTestingGithub = extra.isTestingGithub,
            githubStatusMessage = extra.githubStatusMessage,
            hubProjectCount = extra.hubProjectCount,
            hubTaskCount = extra.hubTaskCount,
            hasHubKey = !prefs.hubApiKey.isNullOrBlank(),
            hubBaseUrl = prefs.hubBaseUrl,
            isSyncingHub = extra.isSyncingHub,
            hubStatusMessage = extra.hubStatusMessage,
            vpsConnectionState = vpsState,
            isConnectingVps = extra.isConnectingVps,
            vpsHost = targetVps?.host,
            aiModelCount = prefs.aiAvailableModelIds.size,
            aiFreeModelCount = prefs.aiFreeModelIds.size,
            hasCustomAiKey = prefs.aiApiKeyCredentialId != null,
            isTestingAi = extra.isTestingAi,
            aiLatencyMillis = extra.aiLatencyMillis,
            aiStatusMessage = extra.aiStatusMessage,
            evolutionApiBaseUrl = prefs.evolutionApiBaseUrl,
            evolutionApiInstance = extra.evolutionApiInstance ?: prefs.evolutionApiInstance.orEmpty(),
            evolutionApiRecipientNumber = extra.evolutionApiRecipientNumber ?: prefs.evolutionApiRecipientNumber.orEmpty(),
            hasEvolutionApiKey = prefs.evolutionApiKeyCredentialId != null,
            savedEvolutionApiKey = extra.savedEvolutionApiKey,
            evolutionApiNotificationsEnabled = extra.evolutionApiNotificationsEnabled || prefs.evolutionApiNotificationsEnabled,
            isTestingEvolutionApi = extra.isTestingEvolutionApi,
            evolutionApiConnected = extra.evolutionApiConnected,
            evolutionApiStatusMessage = extra.evolutionApiStatusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectorsUiState(),
    )

    init {
        checkGitHubStatus()
        testAiGateway()
        loadSavedGoogleTokens()
        loadSavedEvolutionApiKey()
        viewModelScope.launch {
            val key = preferencesRepository.preferences.first().hubApiKey
            if (!key.isNullOrBlank()) {
                refreshProjectHubStatus()
                syncCredentialsIfMissing()
            } else {
                extraState.update {
                    it.copy(
                        hubStatusMessage = "Device not paired. Enter your Hub Key below to sync projects, tasks, and credentials securely."
                    )
                }
            }
        }
    }

    private fun syncCredentialsIfMissing() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            if (prefs.githubTokenCredentialId == null || prefs.aiApiKeyCredentialId == null) {
                when (val credsRes = projectHubClient.fetchCredentials()) {
                    is VmResult.Success -> {
                        val creds = credsRes.value
                        val gh = creds.githubToken
                        if (prefs.githubTokenCredentialId == null && !gh.isNullOrBlank()) {
                            saveGitHubToken(gh)
                        }
                        val ai = creds.omnirouteKey
                        if (prefs.aiApiKeyCredentialId == null && !ai.isNullOrBlank()) {
                            saveCustomAiKey(ai)
                        }
                        syncVpsServer(creds)
                    }
                    is VmResult.Failure -> {}
                }
            }
        }
    }

    private suspend fun syncVpsServer(creds: HubCredentials): String? {
        val host = creds.vpsHost?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val existing = serverRepository.servers.first()
        val match = existing.firstOrNull { it.host.equals(host, ignoreCase = true) }
        if (match != null) return null // Already configured
        val authMethod = if (!creds.vpsPrivateKey.isNullOrBlank()) {
            SshAuthMethod.PRIVATE_KEY
        } else {
            SshAuthMethod.PASSWORD
        }
        val serverName = creds.vpsName?.takeIf { it.isNotBlank() } ?: "VM VPS ($host)"
        val draft = ServerDraft(
            name = serverName,
            host = host,
            port = (creds.vpsPort ?: 22).toString(),
            username = creds.vpsUser?.takeIf { it.isNotBlank() } ?: "root",
            authMethod = authMethod,
            password = creds.vpsPassword.orEmpty(),
            privateKeyPem = creds.vpsPrivateKey.orEmpty(),
            environment = ServerEnvironment.PRODUCTION,
        )
        val result = serverRepository.save(draft)
        return if (result is VmResult.Success) serverName else null
    }

    private fun loadSavedEvolutionApiKey() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            val credId = prefs.evolutionApiKeyCredentialId
            var keyStr: String? = null
            if (credId != null) {
                when (val secRes = credentialStore.read(credId)) {
                    is VmResult.Success -> {
                        keyStr = secRes.value.useAsString { it }
                        secRes.value.wipe()
                    }
                    is VmResult.Failure -> {}
                }
            }
            extraState.update {
                it.copy(
                    savedEvolutionApiKey = keyStr,
                    evolutionApiInstance = prefs.evolutionApiInstance,
                    evolutionApiRecipientNumber = prefs.evolutionApiRecipientNumber,
                    evolutionApiNotificationsEnabled = prefs.evolutionApiNotificationsEnabled,
                )
            }
        }
    }

    fun saveEvolutionApiSettings(
        baseUrl: String,
        instance: String,
        apiKey: String,
        recipientNumber: String,
        notificationsEnabled: Boolean,
    ) {
        viewModelScope.launch {
            val trimmedKey = apiKey.trim()
            val previousId = preferencesRepository.preferences.first().evolutionApiKeyCredentialId
            var newKeyId: String? = null
            if (trimmedKey.isNotBlank()) {
                val secret = Secret.of(trimmedKey)
                try {
                    when (val putRes = credentialStore.put(SecretType.GENERIC, "Evolution API Key", secret)) {
                        is VmResult.Success -> {
                            newKeyId = putRes.value.id
                            previousId?.let { credentialStore.delete(it) }
                        }
                        is VmResult.Failure -> {}
                    }
                } finally {
                    secret.wipe()
                }
            }

            preferencesRepository.setEvolutionApiSettings(
                baseUrl = baseUrl,
                instance = instance,
                recipientNumber = recipientNumber,
                notificationsEnabled = notificationsEnabled,
            )
            if (newKeyId != null) {
                preferencesRepository.setEvolutionApiKeyCredential(newKeyId)
            }
            extraState.update {
                it.copy(
                    evolutionApiInstance = instance,
                    evolutionApiRecipientNumber = recipientNumber,
                    savedEvolutionApiKey = trimmedKey.takeIf { k -> k.isNotBlank() },
                    evolutionApiNotificationsEnabled = notificationsEnabled,
                    evolutionApiStatusMessage = "WhatsApp (Evolution API) settings saved",
                )
            }
        }
    }

    fun testEvolutionApiConnection(
        baseUrl: String,
        instance: String,
        apiKey: String,
        recipientNumber: String,
    ) {
        viewModelScope.launch {
            if (apiKey.isBlank() || instance.isBlank()) {
                extraState.update { it.copy(evolutionApiStatusMessage = "Instance name and API Key are required.") }
                return@launch
            }
            extraState.update { it.copy(isTestingEvolutionApi = true, evolutionApiStatusMessage = "Checking Evolution API connection at $baseUrl...") }
            val config = EvolutionConfig(
                baseUrl = baseUrl,
                apiKey = apiKey.trim(),
                instance = instance.trim(),
                recipientNumber = recipientNumber.trim(),
            )
            when (val res = evolutionApiClient.checkConnection(config)) {
                is VmResult.Success -> {
                    val isConnected = res.value
                    extraState.update {
                        it.copy(
                            isTestingEvolutionApi = false,
                            evolutionApiConnected = isConnected,
                            evolutionApiStatusMessage = if (isConnected) "Instance '$instance' is online & connected to WhatsApp!" else "Instance '$instance' reached, but WhatsApp Web is not linked.",
                        )
                    }
                    if (isConnected && recipientNumber.isNotBlank()) {
                        evolutionApiClient.sendText(
                            config,
                            recipientNumber,
                            "🚀 *X-Codes IDE Connected!*\n\nYour WhatsApp coding & build pipeline via Evolution API (wa.vmstudio.digital) is ready.",
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            isTestingEvolutionApi = false,
                            evolutionApiConnected = false,
                            evolutionApiStatusMessage = "Connection failed: ${res.error.summary}",
                        )
                    }
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

    fun pairHubKey(key: String, customBaseUrl: String? = null) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            extraState.update { it.copy(hubStatusMessage = "Hub Key cannot be empty") }
            return
        }
        val targetUrl = customBaseUrl?.trim()?.takeIf { it.isNotBlank() } ?: "https://saketdhamashram.org"
        viewModelScope.launch {
            extraState.update { it.copy(isSyncingHub = true, hubStatusMessage = "Verifying Hub Key with $targetUrl…") }
            preferencesRepository.setHubBaseUrl(targetUrl)
            when (val testRes = projectHubClient.testHubKey(trimmed, targetUrl)) {
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            isSyncingHub = false,
                            hubStatusMessage = "Pairing failed: ${testRes.error.summary}. Check key in WordPress Admin > VM Project Hub.",
                        )
                    }
                }
                is VmResult.Success -> {
                    preferencesRepository.setHubApiKey(trimmed)
                    extraState.update {
                        it.copy(
                            hubProjectCount = testRes.value,
                            hubStatusMessage = "Paired successfully with $targetUrl! Discovered ${testRes.value} projects. Syncing now…",
                        )
                    }
                    syncUniversalHubProjects()
                }
            }
        }
    }

    fun unpairHub() {
        viewModelScope.launch {
            preferencesRepository.setHubApiKey(null)
            extraState.update {
                it.copy(
                    hubProjectCount = null,
                    hubTaskCount = null,
                    hubStatusMessage = "Device unpaired from VM Project Hub.",
                )
            }
        }
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
        extraState.update { it.copy(isSyncingHub = true, hubStatusMessage = "Syncing universal workspaces & VPS credentials from VM Project Hub…") }
        viewModelScope.launch {
            try {
                // Auto-sync credentials & provision VPS from Hub
                when (val credsRes = projectHubClient.syncHubCredentialsAndVps()) {
                    is VmResult.Success -> {
                        val creds = credsRes.value
                        val gh = creds.githubToken
                        if (!gh.isNullOrBlank() && !githubClient.hasToken()) {
                            saveGitHubToken(gh)
                        }
                        val ai = creds.omnirouteKey
                        if (!ai.isNullOrBlank() && preferencesRepository.preferences.first().aiApiKeyCredentialId == null) {
                            saveCustomAiKey(ai)
                        }
                        // Auto-connect to VPS immediately in background!
                        val prefs = preferencesRepository.preferences.first()
                        prefs.autoConnectServerId?.let { sId ->
                            connectionManager.session(sId)
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
                when (val credsRes = projectHubClient.syncHubCredentialsAndVps()) {
                    is VmResult.Failure -> {
                        extraState.update { it.copy(hubStatusMessage = "Credential sync failed: ${credsRes.error.summary}") }
                    }
                    is VmResult.Success -> {
                        val creds = credsRes.value
                        val syncedParts = mutableListOf<String>()
                        val gh = creds.githubToken
                        if (!gh.isNullOrBlank()) {
                            saveGitHubToken(gh)
                            syncedParts += "GitHub PAT"
                        }
                        val ai = creds.omnirouteKey
                        if (!ai.isNullOrBlank()) {
                            saveCustomAiKey(ai)
                            syncedParts += "OmniRoute AI key"
                        }
                        if (!creds.vpsHost.isNullOrBlank()) {
                            syncedParts += "VPS (${creds.vpsName ?: creds.vpsHost})"
                            // Auto-connect to VPS
                            preferencesRepository.preferences.first().autoConnectServerId?.let { sId ->
                                connectionManager.session(sId)
                            }
                        }
                        extraState.update {
                            it.copy(
                                hubStatusMessage = if (syncedParts.isEmpty()) {
                                    "Credentials already up to date with VM Project Hub"
                                } else {
                                    "Synced ${syncedParts.joinToString(" & ")} from VM Project Hub"
                                }
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

    fun connectHubVps() {
        if (extraState.value.isConnectingVps) return
        extraState.update { it.copy(isConnectingVps = true, hubStatusMessage = "Connecting to VPS…") }
        viewModelScope.launch {
            try {
                // Sync VPS details if needed
                projectHubClient.syncHubCredentialsAndVps()
                val prefs = preferencesRepository.preferences.first()
                val servers = serverRepository.servers.first()
                val targetServer = prefs.autoConnectServerId?.let { id -> servers.firstOrNull { it.id == id } }
                    ?: servers.firstOrNull { it.host.contains("vmstudio", ignoreCase = true) || it.host == "200.234.41.231" }
                    ?: servers.firstOrNull()

                if (targetServer == null) {
                    extraState.update {
                        it.copy(
                            isConnectingVps = false,
                            hubStatusMessage = "No VPS configured. Pair Hub Key or configure external VPS in Servers.",
                        )
                    }
                    return@launch
                }

                when (val connRes = connectionManager.session(targetServer.id)) {
                    is VmResult.Success -> {
                        preferencesRepository.setAutoConnectServerId(targetServer.id)
                        extraState.update {
                            it.copy(
                                isConnectingVps = false,
                                hubStatusMessage = "VPS Connected (${targetServer.displayTarget})! Terminal and Workspaces ready.",
                            )
                        }
                    }
                    is VmResult.Failure -> {
                        extraState.update {
                            it.copy(
                                isConnectingVps = false,
                                hubStatusMessage = "VPS Connection failed: ${connRes.error.summary}",
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                extraState.update {
                    it.copy(
                        isConnectingVps = false,
                        hubStatusMessage = "VPS Error: ${t.message ?: "Unknown error"}",
                    )
                }
            }
        }
    }

    fun disconnectHubVps() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            val servers = serverRepository.servers.first()
            val targetServer = prefs.autoConnectServerId?.let { id -> servers.firstOrNull { it.id == id } }
                ?: servers.firstOrNull { it.host.contains("vmstudio", ignoreCase = true) || it.host == "200.234.41.231" }
                ?: servers.firstOrNull()

            if (targetServer != null) {
                connectionManager.disconnect(targetServer.id)
                extraState.update { it.copy(hubStatusMessage = "Disconnected from VPS (${targetServer.displayTarget}).") }
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

    private fun loadSavedGoogleTokens() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            prefs.googleDriveTokenCredentialId?.let { id ->
                when (val secRes = credentialStore.read(id)) {
                    is VmResult.Success -> {
                        val tokenStr = secRes.value.useAsString { it }
                        secRes.value.wipe()
                        extraState.update { it.copy(savedDriveToken = tokenStr) }
                        refreshDriveBackups(tokenStr)
                    }
                    is VmResult.Failure -> {}
                }
            }
            prefs.googleSheetsTokenCredentialId?.let { id ->
                when (val secRes = credentialStore.read(id)) {
                    is VmResult.Success -> {
                        val tokenStr = secRes.value.useAsString { it }
                        secRes.value.wipe()
                        extraState.update {
                            it.copy(
                                savedSheetsToken = tokenStr,
                                savedSpreadsheetId = prefs.googleSheetsSpreadsheetId
                            )
                        }
                    }
                    is VmResult.Failure -> {}
                }
            }
        }
    }

    fun saveDriveToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val secret = Secret.of(trimmed)
            try {
                when (val putRes = credentialStore.put(SecretType.OAUTH_ACCESS_TOKEN, "Google Drive Token", secret)) {
                    is VmResult.Success -> {
                        val previousId = preferencesRepository.preferences.first().googleDriveTokenCredentialId
                        preferencesRepository.setGoogleDriveTokenCredential(putRes.value.id)
                        extraState.update { it.copy(savedDriveToken = trimmed, driveStatusMessage = "Google Drive token saved securely.") }
                        previousId?.let { oldId -> credentialStore.delete(oldId) }
                        refreshDriveBackups(trimmed)
                    }
                    is VmResult.Failure -> {
                        extraState.update { it.copy(driveStatusMessage = "Failed to save Drive token: ${putRes.error.summary}") }
                    }
                }
            } finally {
                secret.wipe()
            }
        }
    }

    fun clearDriveToken() {
        viewModelScope.launch {
            val currentId = preferencesRepository.preferences.first().googleDriveTokenCredentialId
            preferencesRepository.setGoogleDriveTokenCredential(null)
            currentId?.let { credentialStore.delete(it) }
            extraState.update { it.copy(savedDriveToken = null, driveBackups = emptyList(), driveFiles = emptyList(), isBrowsingDrive = false, driveStatusMessage = "Drive token removed.") }
        }
    }

    fun toggleDriveBrowser() {
        val next = !extraState.value.isBrowsingDrive
        extraState.update { it.copy(isBrowsingDrive = next) }
        if (next && extraState.value.driveFiles.isEmpty()) {
            browseDriveFolder("root", "My Drive")
        }
    }

    fun browseDriveFolder(folderId: String = "root", folderName: String = "My Drive") {
        viewModelScope.launch {
            val token = getActiveDriveToken()
            if (token.isNullOrBlank()) {
                extraState.update { it.copy(driveStatusMessage = "Google Drive token required to browse.") }
                return@launch
            }
            extraState.update { current ->
                val updatedCrumbs = if (folderId == "root") {
                    listOf("root" to "My Drive")
                } else {
                    val existingIndex = current.driveFolderBreadcrumbs.indexOfFirst { it.first == folderId }
                    if (existingIndex >= 0) {
                        current.driveFolderBreadcrumbs.take(existingIndex + 1)
                    } else {
                        current.driveFolderBreadcrumbs + (folderId to folderName)
                    }
                }
                current.copy(
                    isBrowsingDrive = true,
                    isLoadingDriveFiles = true,
                    driveBrowserFolderId = folderId,
                    driveBrowserFolderName = folderName,
                    driveFolderBreadcrumbs = updatedCrumbs,
                )
            }
            when (val res = googleDriveClient.listFolder(token, folderId)) {
                is VmResult.Success -> {
                    val sorted = res.value.sortedWith(
                        compareByDescending<GoogleDriveFile> { it.isFolder }
                            .thenBy { it.name.lowercase() }
                    )
                    extraState.update {
                        it.copy(
                            isLoadingDriveFiles = false,
                            driveFiles = sorted,
                            driveStatusMessage = "Loaded ${sorted.size} items in $folderName",
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            isLoadingDriveFiles = false,
                            driveStatusMessage = "Failed to list folder: ${res.error.summary}",
                        )
                    }
                }
            }
        }
    }

    fun navigateDriveUp() {
        val crumbs = extraState.value.driveFolderBreadcrumbs
        if (crumbs.size > 1) {
            val parent = crumbs[crumbs.size - 2]
            browseDriveFolder(parent.first, parent.second)
        } else if (crumbs.size == 1 && crumbs[0].first != "root") {
            browseDriveFolder("root", "My Drive")
        }
    }

    fun previewDriveFile(file: GoogleDriveFile) {
        if (file.isFolder) {
            browseDriveFolder(file.id, file.name)
            return
        }
        viewModelScope.launch {
            val token = getActiveDriveToken()
            if (token.isNullOrBlank()) {
                extraState.update { it.copy(driveStatusMessage = "Drive token required to read file.") }
                return@launch
            }
            extraState.update {
                it.copy(
                    drivePreviewFile = file,
                    isLoadingDrivePreview = true,
                    drivePreviewContent = null,
                )
            }
            val res = if (file.mimeType.contains("google-apps")) {
                googleDriveClient.exportGoogleDocOrSheet(token, file.id, file.mimeType)
            } else {
                googleDriveClient.downloadFile(token, file.id)
            }
            when (res) {
                is VmResult.Success -> {
                    extraState.update {
                        it.copy(
                            isLoadingDrivePreview = false,
                            drivePreviewContent = res.value,
                        )
                    }
                }
                is VmResult.Failure -> {
                    extraState.update {
                        it.copy(
                            isLoadingDrivePreview = false,
                            drivePreviewContent = "Error reading file: ${res.error.summary}",
                        )
                    }
                }
            }
        }
    }

    fun dismissDrivePreview() {
        extraState.update { it.copy(drivePreviewFile = null, drivePreviewContent = null) }
    }

    private suspend fun getActiveDriveToken(): String? {
        val stateToken = extraState.value.savedDriveToken
        if (!stateToken.isNullOrBlank()) return stateToken
        val id = preferencesRepository.preferences.first().googleDriveTokenCredentialId ?: return null
        return when (val sec = credentialStore.read(id)) {
            is VmResult.Success -> {
                val str = sec.value.useAsString { it }
                sec.value.wipe()
                str
            }
            is VmResult.Failure -> null
        }
    }

    fun saveSheetsSettings(token: String, spreadsheetId: String) {
        val trimmedToken = token.trim()
        val trimmedSheetId = spreadsheetId.trim()
        if (trimmedToken.isBlank() || trimmedSheetId.isBlank()) return
        viewModelScope.launch {
            val secret = Secret.of(trimmedToken)
            try {
                when (val putRes = credentialStore.put(SecretType.OAUTH_ACCESS_TOKEN, "Google Sheets Token", secret)) {
                    is VmResult.Success -> {
                        val previousId = preferencesRepository.preferences.first().googleSheetsTokenCredentialId
                        preferencesRepository.setGoogleSheetsSettings(putRes.value.id, trimmedSheetId)
                        extraState.update {
                            it.copy(
                                savedSheetsToken = trimmedToken,
                                savedSpreadsheetId = trimmedSheetId,
                                sheetStatusMessage = "Sheets settings saved securely."
                            )
                        }
                        previousId?.let { oldId -> credentialStore.delete(oldId) }
                    }
                    is VmResult.Failure -> {
                        extraState.update { it.copy(sheetStatusMessage = "Failed to save Sheets token: ${putRes.error.summary}") }
                    }
                }
            } finally {
                secret.wipe()
            }
        }
    }

    fun clearSheetsSettings() {
        viewModelScope.launch {
            val currentId = preferencesRepository.preferences.first().googleSheetsTokenCredentialId
            preferencesRepository.setGoogleSheetsSettings(null, null)
            currentId?.let { credentialStore.delete(it) }
            extraState.update { it.copy(savedSheetsToken = null, savedSpreadsheetId = null, sheetPreviewRows = emptyList(), sheetStatusMessage = "Sheets settings cleared.") }
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
