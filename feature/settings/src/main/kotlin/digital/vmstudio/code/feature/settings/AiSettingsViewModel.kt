package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.provider.AiProviderHealth
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
import kotlinx.coroutines.CancellationException
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
    val freeModels: Set<String> = emptySet(),
)

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
            freeModels = currentHealth?.freeModels?.takeIf { it.isNotEmpty() }
                ?: preferences.aiFreeModelIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiSettingsUiState(),
    )

    init {
        runConnectionTest()
    }

    /**
     * Stores the gateway key in the Keystore-backed store and keeps only its
     * reference in preferences. The plaintext is wiped as soon as it is encrypted.
     */
    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch { commitApiKey(key) }
    }

    private suspend fun commitApiKey(key: String): Boolean {
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
                    return true
                }
                is VmResult.Failure -> {
                    error.value = stored.error
                    return false
                }
            }
        } finally {
            secret.wipe()
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            try {
                val credentialId = uiState.value.preferences.aiApiKeyCredentialId
                if (credentialId != null) {
                    when (val result = credentialStore.delete(credentialId)) {
                        is VmResult.Success -> Unit
                        is VmResult.Failure -> {
                            error.value = result.error
                            return@launch
                        }
                    }
                }
                preferencesRepository.setAiApiKeyCredentialId(null)
                health.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error.value = VmError.Unexpected(
                    summary = "Could not remove the AI API key",
                    reason = exception.message,
                    cause = exception,
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            preferencesRepository.setAiModel(modelId, uiState.value.preferences.aiModelPreset)
        }
    }

    fun setContextSize(size: String) {
        viewModelScope.launch { preferencesRepository.setAiContextSize(size) }
    }

    fun setToolsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAiToolsEnabled(enabled) }
    }

    fun setWebAccessEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAiWebAccessEnabled(enabled) }
    }

    fun setMaxIterations(iterations: Int) {
        viewModelScope.launch { preferencesRepository.setAgentMaxIterations(iterations) }
    }

    fun setMaxCommandsPerRun(commands: Int) {
        viewModelScope.launch { preferencesRepository.setAgentMaxCommandsPerRun(commands) }
    }

    /**
     * Runs the connection test.
     *
     * Tests the fixed OmniRoute gateway using the saved API key.
     */
    fun runConnectionTest(
        pendingKey: String? = null,
        onKeySaved: () -> Unit = {},
    ) {
        testing.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val keyWasSaved = pendingKey?.takeIf { it.isNotBlank() }?.let {
                    commitApiKey(it)
                } ?: true
                if (!keyWasSaved) return@launch
                if (!pendingKey.isNullOrBlank()) onKeySaved()

                val provider = providers.active()
                // The user tapped "Run test": always probe live, never the cache the
                // agent screen's automatic check may have populated seconds ago.
                when (val result = providers.checkHealth(provider, null, forceRefresh = true)) {
                    is VmResult.Success -> {
                        health.value = result.value
                        persistSyncedModels(result.value.availableModels, result.value.freeModels)
                    }
                    is VmResult.Failure -> error.value = result.error
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error.value = VmError.Unexpected(
                    summary = "AI connection test failed",
                    reason = exception.message,
                    cause = exception,
                )
            } finally {
                testing.value = false
            }
        }
    }

    /**
     * Persists the synced model list so the picker survives leaving Settings.
     * An empty answer keeps the previous list: a gateway that returned no models
     * has not been proven to have none.
     */
    private suspend fun persistSyncedModels(models: List<String>, freeModels: Set<String>) {
        if (models.isNotEmpty()) {
            val prefs = uiState.value.preferences
            val effectiveFree = freeModels.ifEmpty { prefs.aiFreeModelIds }
            preferencesRepository.setAiModels(models, effectiveFree)
            if (prefs.aiSelectedModelId.isNullOrBlank() || prefs.aiSelectedModelId !in models) {
                val preferred = if (UserPreferences.DEFAULT_AI_MODEL_ID in models) {
                    UserPreferences.DEFAULT_AI_MODEL_ID
                } else {
                    models.first()
                }
                preferencesRepository.setAiModel(preferred, prefs.aiModelPreset)
            }
        }
    }

    fun dismissError() {
        error.value = null
    }
}
