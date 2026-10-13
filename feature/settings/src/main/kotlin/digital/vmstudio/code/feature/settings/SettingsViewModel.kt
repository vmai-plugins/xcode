package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.log.DiagnosticsLogSink
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.common.preferences.ThemePreference
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.version.AppVersionProvider
import digital.vmstudio.code.core.security.crypto.KeystoreCrypto
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.update.UpdateManager
import digital.vmstudio.code.core.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val storedCredentialCount: Int = 0,
    val isHardwareBackedKeystore: Boolean? = null,
    val isErasing: Boolean = false,
    val credentialEraseError: String? = null,
    val installedVersionName: String = "",
    val updateState: UpdateState = UpdateState.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val credentialStore: SecureCredentialStore,
    private val keystoreCrypto: KeystoreCrypto,
    private val diagnosticsLogSink: DiagnosticsLogSink,
    private val appVersionProvider: AppVersionProvider,
    private val updateManager: UpdateManager,
) : ViewModel() {

    private val isErasing = MutableStateFlow(false)
    private val credentialEraseError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        credentialStore.credentials,
        isErasing,
        updateManager.state,
        credentialEraseError,
    ) { preferences, credentials, erasing, updateState, eraseError ->
        SettingsUiState(
            preferences = preferences,
            storedCredentialCount = credentials.size,
            // Queried lazily here rather than at startup: the first call generates
            // the master key if it does not exist yet, and that should happen when
            // a secret is first stored, not on every cold launch.
            isHardwareBackedKeystore = if (credentials.isEmpty()) {
                null
            } else {
                keystoreCrypto.isHardwareBacked()
            },
            isErasing = erasing,
            credentialEraseError = eraseError,
            installedVersionName = appVersionProvider.versionName,
            updateState = updateState,
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
                val secret = digital.vmstudio.code.core.security.model.Secret.of(token.trim())
                try {
                    val ref = credentialStore.put(
                        digital.vmstudio.code.core.security.model.SecretType.GIT_HTTP_TOKEN,
                        "GitHub Personal Access Token",
                        secret,
                    )
                    when (ref) {
                        is digital.vmstudio.code.core.common.result.VmResult.Success -> ref.value.id
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

    fun signOut() {
        viewModelScope.launch {
            preferencesRepository.signOutUser()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            preferencesRepository.signOutUser()
        }
    }
}
