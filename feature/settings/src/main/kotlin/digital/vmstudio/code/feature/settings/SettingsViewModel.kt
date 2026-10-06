package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.log.DiagnosticsLogSink
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.common.preferences.ThemePreference
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
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
    val isHardwareBackedKeystore: Boolean = false,
    val isErasing: Boolean = false,
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

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        credentialStore.credentials,
        isErasing,
        updateManager.state,
    ) { preferences, credentials, erasing, updateState ->
        SettingsUiState(
            preferences = preferences,
            storedCredentialCount = credentials.size,
            // Queried lazily here rather than at startup: the first call generates
            // the master key if it does not exist yet, and that should happen when
            // a secret is first stored, not on every cold launch.
            isHardwareBackedKeystore = keystoreCrypto.isHardwareBacked(),
            isErasing = erasing,
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

    fun eraseAllCredentials(onComplete: (Boolean) -> Unit) {
        isErasing.value = true
        viewModelScope.launch {
            val result = credentialStore.eraseAll()
            isErasing.value = false
            onComplete(result.isSuccess)
        }
    }
}
