package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.DiagnosticsLogSink
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.common.preferences.ThemePreference
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.version.AppVersionProvider
import digital.vmstudio.code.core.security.crypto.KeystoreCrypto
import digital.vmstudio.code.core.security.crypto.VaultCrypto
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.model.ServerDraft
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import digital.vmstudio.code.core.update.UpdateManager
import digital.vmstudio.code.core.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val storedCredentialCount: Int = 0,
    val isHardwareBackedKeystore: Boolean? = null,
    val isErasing: Boolean = false,
    val credentialEraseError: String? = null,
    val installedVersionName: String = "",
    val updateState: UpdateState = UpdateState.Idle,
    val vaultStatus: String? = null,
    val isVaultBusy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val credentialStore: SecureCredentialStore,
    private val keystoreCrypto: KeystoreCrypto,
    private val diagnosticsLogSink: DiagnosticsLogSink,
    private val appVersionProvider: AppVersionProvider,
    private val updateManager: UpdateManager,
    private val serverRepository: ServerRepository,
    private val vaultCrypto: VaultCrypto,
) : ViewModel() {

    private val isErasing = MutableStateFlow(false)
    private val credentialEraseError = MutableStateFlow<String?>(null)
    private val vaultStatus = MutableStateFlow<String?>(null)
    private val isVaultBusy = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        credentialStore.credentials,
        isErasing,
        updateManager.state,
        credentialEraseError,
        vaultStatus,
        isVaultBusy,
    ) { preferences, credentials, erasing, updateState, eraseError, vStatus, vBusy ->
        SettingsUiState(
            preferences = preferences,
            storedCredentialCount = credentials.size,
            isHardwareBackedKeystore = if (credentials.isEmpty()) {
                null
            } else {
                keystoreCrypto.isHardwareBacked()
            },
            isErasing = erasing,
            credentialEraseError = eraseError,
            installedVersionName = appVersionProvider.versionName,
            updateState = updateState,
            vaultStatus = vStatus,
            isVaultBusy = vBusy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun checkForUpdates() = updateManager.checkForUpdate()

    fun downloadUpdate() = updateManager.downloadAndInstall()

    fun retryInstall() = updateManager.retryInstall()

    fun setGithubSettings(token: String, username: String) {
        viewModelScope.launch {
            val credId = if (token.isNotBlank()) {
                val secret = Secret.of(token.trim())
                try {
                    val ref = credentialStore.put(
                        SecretType.GIT_HTTP_TOKEN,
                        "GitHub Personal Access Token",
                        secret,
                    )
                    when (ref) {
                        is VmResult.Success -> ref.value.id
                        else -> null
                    }
                } finally {
                    secret.wipe()
                }
            } else null
            preferencesRepository.setGithubConnection(credId, username.trim().takeIf { it.isNotBlank() })
        }
    }

    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch { preferencesRepository.setThemePreference(preference) }
    }

    fun setEditorFontSize(sp: Float) {
        viewModelScope.launch { preferencesRepository.setEditorFontSize(sp) }
    }

    fun setTerminalFontSize(sp: Float) {
        viewModelScope.launch { preferencesRepository.setTerminalFontSize(sp) }
    }

    fun setAgentAutonomy(level: AgentAutonomyLevel) {
        viewModelScope.launch { preferencesRepository.setAgentAutonomyLevel(level) }
    }

    fun setConfirmDestructiveCommands(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setConfirmDestructiveCommands(enabled) }
    }

    /** Diagnostics are redacted at capture time, so this text is safe to share. */
    fun exportDiagnostics(): String = diagnosticsLogSink.exportAsText()

    fun clearDiagnostics() = diagnosticsLogSink.clear()

    fun eraseAllCredentials() {
        credentialEraseError.value = null
        isErasing.value = true
        viewModelScope.launch {
            try {
                when (val result = credentialStore.eraseAll()) {
                    is VmResult.Success -> {
                        preferencesRepository.setAiApiKeyCredentialId(null)
                        preferencesRepository.setGithubConnection(null, null)
                    }
                    is VmResult.Failure -> credentialEraseError.value = result.error.summary
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                credentialEraseError.value =
                    exception.message ?: "Could not erase all stored credentials."
            } finally {
                isErasing.value = false
            }
        }
    }

    /** Exports all servers and config into a Zero-Trust encrypted base64 payload. */
    fun exportVault(passphrase: String, onComplete: (VmResult<String>) -> Unit) {
        if (passphrase.isBlank()) {
            onComplete(VmResult.Failure(VmError.Validation("Passphrase cannot be blank.")))
            return
        }
        isVaultBusy.value = true
        vaultStatus.value = "Encrypting vault..."
        viewModelScope.launch {
            try {
                val servers = serverRepository.servers.first()
                val prefs = preferencesRepository.preferences.first()

                val rootJson = JSONObject().apply {
                    put("version", 1)
                    put("exportedAtMillis", System.currentTimeMillis())
                    val serversArray = JSONArray()
                    servers.forEach { server ->
                        serversArray.put(JSONObject().apply {
                            put("name", server.name)
                            put("host", server.host)
                            put("port", server.port)
                            put("username", server.username)
                            put("authMethod", server.authMethod.name)
                            put("environment", server.environment.name)
                            put("preferredShell", server.preferredShell ?: "")
                            put("defaultDirectory", server.defaultDirectory ?: "")
                            put("notes", server.notes)
                            put("colorHex", server.colorHex ?: "")
                        })
                    }
                    put("servers", serversArray)
                    put("preferences", JSONObject().apply {
                        put("theme", prefs.themePreference.name)
                        put("editorFontSizeSp", prefs.editorFontSizeSp.toDouble())
                        put("terminalFontSizeSp", prefs.terminalFontSizeSp.toDouble())
                        put("agentAutonomy", prefs.agentAutonomyLevel.name)
                    })
                }

                val exportResult = vaultCrypto.exportEncryptedVault(
                    rootJson.toString(),
                    passphrase.toCharArray(),
                )
                when (exportResult) {
                    is VmResult.Success -> {
                        vaultStatus.value = "Vault exported (${servers.size} servers)"
                        onComplete(exportResult)
                    }
                    is VmResult.Failure -> {
                        vaultStatus.value = "Export failed: ${exportResult.error.summary}"
                        onComplete(exportResult)
                    }
                }
            } catch (e: Exception) {
                val err = VmError.Unexpected("Failed to export vault", cause = e)
                vaultStatus.value = "Export failed: ${e.message}"
                onComplete(VmResult.Failure(err))
            } finally {
                isVaultBusy.value = false
            }
        }
    }

    /** Decrypts and restores servers and configuration from an encrypted vault payload. */
    fun importVault(vaultBase64: String, passphrase: String, onComplete: (VmResult<Int>) -> Unit) {
        if (vaultBase64.isBlank() || passphrase.isBlank()) {
            onComplete(VmResult.Failure(VmError.Validation("Payload and passphrase required.")))
            return
        }
        isVaultBusy.value = true
        vaultStatus.value = "Decrypting vault..."
        viewModelScope.launch {
            try {
                when (val decryptResult = vaultCrypto.importEncryptedVault(vaultBase64, passphrase.toCharArray())) {
                    is VmResult.Success -> {
                        val rootJson = JSONObject(decryptResult.value)
                        val serversArray = rootJson.optJSONArray("servers") ?: JSONArray()
                        var importedCount = 0
                        for (i in 0 until serversArray.length()) {
                            val item = serversArray.getJSONObject(i)
                            val draft = ServerDraft(
                                name = item.optString("name", "Imported Server"),
                                host = item.optString("host", ""),
                                port = item.optInt("port", 22).toString(),
                                username = item.optString("username", "root"),
                                preferredShell = item.optString("preferredShell", ""),
                                defaultDirectory = item.optString("defaultDirectory", ""),
                                notes = item.optString("notes", ""),
                            )
                            if (draft.host.isNotBlank()) {
                                serverRepository.save(draft)
                                importedCount++
                            }
                        }
                        vaultStatus.value = "Successfully imported $importedCount server(s)!"
                        onComplete(VmResult.Success(importedCount))
                    }
                    is VmResult.Failure -> {
                        vaultStatus.value = "Decryption failed: ${decryptResult.error.summary}"
                        onComplete(VmResult.Failure(decryptResult.error))
                    }
                }
            } catch (e: Exception) {
                val err = VmError.Unexpected("Failed to import vault", cause = e)
                vaultStatus.value = "Import failed: ${e.message}"
                onComplete(VmResult.Failure(err))
            } finally {
                isVaultBusy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            preferencesRepository.signOutUser()
        }
    }
}
