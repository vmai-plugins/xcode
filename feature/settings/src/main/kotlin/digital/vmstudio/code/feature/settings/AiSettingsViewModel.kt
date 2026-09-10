package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.provider.AiProviderHealth
import digital.vmstudio.code.core.ai.provider.AiProviderKind
import digital.vmstudio.code.core.ai.provider.AiProviderRegistry
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val health: AiProviderHealth? = null,
    val isTesting: Boolean = false,
    val error: VmError? = null,
    val hasStoredKey: Boolean = false,
    /** Fresh sync wins; otherwise the persisted list from the last sync. */
    val availableModels: List<String> = emptyList(),
) {
    val selectedKind: AiProviderKind
        get() = if (preferences.aiProviderId == UserPreferences.PROVIDER_OMNIROUTE) {
            AiProviderKind.OMNIROUTE
        } else {
            AiProviderKind.CLAUDE_CODE_CLI
        }
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val providers: AiProviderRegistry,
    private val credentialStore: SecureCredentialStore,
) : ViewModel() {

    private val health = MutableStateFlow<AiProviderHealth?>(null)
    private val testing = MutableStateFlow(false)
    private val error = MutableStateFlow<VmError?>(null)

    val uiState: StateFlow<AiSettingsUiState> = combine(
        preferencesRepository.preferences,
        health,
        testing,
        error,
    ) { preferences, currentHealth, isTesting, currentError ->
        AiSettingsUiState(
            preferences = preferences,
            health = currentHealth,
            isTesting = isTesting,
            error = currentError,
            hasStoredKey = preferences.aiApiKeyCredentialId != null,
            availableModels = currentHealth?.availableModels?.takeIf { it.isNotEmpty() }
                ?: preferences.aiAvailableModelIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiSettingsUiState(),
    )

    fun selectProvider(kind: AiProviderKind) {
        viewModelScope.launch {
            val id = when (kind) {
                AiProviderKind.CLAUDE_CODE_CLI -> UserPreferences.PROVIDER_CLAUDE_CODE
                AiProviderKind.OMNIROUTE -> UserPreferences.PROVIDER_OMNIROUTE
            }
            val current = uiState.value.preferences
            preferencesRepository.setAiProvider(id, current.aiBaseUrl)
            // A health result belongs to one backend; keeping it across a switch
            // would show the previous provider's status under the new one's name.
            health.value = null
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.setAiProvider(uiState.value.preferences.aiProviderId, url.trim())
            health.value = null
        }
    }

    /**
     * Stores the gateway key in the Keystore-backed store and keeps only its
     * reference in preferences. The plaintext is wiped as soon as it is encrypted.
     */
    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            val previousId = uiState.value.preferences.aiApiKeyCredentialId
            val secret = Secret.of(key.trim())
            try {
                when (
                    val stored = credentialStore.put(
                        type = SecretType.AI_API_KEY,
                        label = "AI gateway key",
                        secret = secret,
                    )
                ) {
                    is VmResult.Success -> {
                        preferencesRepository.setAiApiKeyCredentialId(stored.value.id)
                        // Removed only after the new reference is committed, so a
                        // failure cannot leave the app with no usable key.
                        previousId?.let { credentialStore.delete(it) }
                        health.value = null
                    }
                    is VmResult.Failure -> error.value = stored.error
                }
            } finally {
                secret.wipe()
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            uiState.value.preferences.aiApiKeyCredentialId?.let { credentialStore.delete(it) }
            preferencesRepository.setAiApiKeyCredentialId(null)
            health.value = null
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            preferencesRepository.setAiModel(modelId, uiState.value.preferences.aiModelPreset)
        }
    }

    /**
     * Runs the connection test.
     *
     * [serverId] is required by the CLI backend and ignored by the gateway one; the
     * screen passes whichever it has so the test reports on the selected provider
     * rather than refusing.
     */
    fun runConnectionTest(serverId: String?) {
        testing.value = true
        error.value = null
        viewModelScope.launch {
            val provider = providers.forId(uiState.value.preferences.aiProviderId)
            // The user tapped "Run test": always probe live, never the cache the
            // agent screen's automatic check may have populated seconds ago.
            when (val result = providers.checkHealth(provider, serverId, forceRefresh = true)) {
                is VmResult.Success -> {
                    health.value = result.value
                    persistSyncedModels(result.value.availableModels)
                }
                is VmResult.Failure -> error.value = result.error
            }
            testing.value = false
        }
    }

    /**
     * Persists the synced model list so the picker survives leaving Settings.
     * An empty answer keeps the previous list: a gateway that returned no models
     * has not been proven to have none.
     */
    private suspend fun persistSyncedModels(models: List<String>) {
        if (models.isNotEmpty()) {
            preferencesRepository.setAiModels(models)
        }
    }

    fun dismissError() {
        error.value = null
    }
}
