package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.git.GitHubDeviceAuth
import digital.vmstudio.code.core.git.GitHubDevicePollResult
import digital.vmstudio.code.core.git.GitHubRepository
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias GitHubRepo = GitHubRepository

data class GitHubSettingsUiState(
    val isLoading: Boolean = false,
    val hasToken: Boolean = false,
    val error: String? = null,
    val repositories: List<GitHubRepo> = emptyList(),
    val deviceAuth: GitHubDeviceAuth? = null,
    val isPollingDeviceAuth: Boolean = false,
)

@HiltViewModel
class GitHubSettingsViewModel @Inject constructor(
    private val github: GitHubRepositoryClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHubSettingsUiState())
    val uiState: StateFlow<GitHubSettingsUiState> = _uiState.asStateFlow()

    init {
        loadRepositories()
    }

    fun triggerBuild(workflowFileName: String = "nightly.yml", onTriggered: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                when (val result = github.dispatchWorkflow("vmai-plugins/xcode", workflowFileName, "main")) {
                    is VmResult.Success -> {
                        _uiState.update { it.copy(isLoading = false) }
                        onTriggered(true, "Cloud build '$workflowFileName' dispatched to GitHub Actions! Finished APK will be published to Apps Hub.")
                    }
                    is VmResult.Failure -> {
                        val message = result.error.summary
                        _uiState.update { it.copy(isLoading = false, error = message) }
                        onTriggered(false, message)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                val message = exception.message ?: "Failed to trigger GitHub workflow"
                _uiState.update { it.copy(isLoading = false, error = message) }
                onTriggered(false, message)
            }
        }
    }

    fun cancelDeviceAuth() {
        _uiState.update { it.copy(deviceAuth = null, isPollingDeviceAuth = false) }
    }

    fun startDeviceAuth(onCodeReady: (userCode: String, verificationUri: String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = github.requestDeviceCode()) {
                is VmResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.summary) }
                }
                is VmResult.Success -> {
                    val auth = result.value
                    _uiState.update { it.copy(isLoading = false, deviceAuth = auth, isPollingDeviceAuth = true) }
                    onCodeReady(auth.userCode, auth.verificationUri)
                    pollForDeviceToken(auth.deviceCode, auth.intervalSeconds)
                }
            }
        }
    }

    private fun pollForDeviceToken(deviceCode: String, intervalSeconds: Int) {
        viewModelScope.launch {
            val delayMs = maxOf(intervalSeconds, 5) * 1000L
            while (_uiState.value.isPollingDeviceAuth) {
                delay(delayMs)
                when (val pollResult = github.pollDeviceToken(deviceCode = deviceCode)) {
                    is VmResult.Failure -> {
                        _uiState.update { it.copy(isPollingDeviceAuth = false, error = pollResult.error.summary) }
                        break
                    }
                    is VmResult.Success -> {
                        when (val outcome = pollResult.value) {
                            is GitHubDevicePollResult.Pending -> {
                                // continue polling
                            }
                            is GitHubDevicePollResult.Success -> {
                                _uiState.update { it.copy(isPollingDeviceAuth = false, deviceAuth = null) }
                                setToken(outcome.accessToken)
                                break
                            }
                            is GitHubDevicePollResult.Error -> {
                                _uiState.update { it.copy(isPollingDeviceAuth = false, error = outcome.message) }
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    fun setToken(token: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                when (val result = github.saveToken(token)) {
                    is VmResult.Success -> {
                        _uiState.update { it.copy(hasToken = true) }
                        onSaved()
                        loadRepositories()
                    }
                    is VmResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, error = result.error.summary)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "Could not save the GitHub token",
                    )
                }
            }
        }
    }

    fun clearToken(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                when (val result = github.clearToken()) {
                    is VmResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hasToken = false,
                                repositories = emptyList(),
                                error = null,
                            )
                        }
                        onCleared()
                    }
                    is VmResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, error = result.error.summary)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "Could not clear the GitHub token",
                    )
                }
            }
        }
    }

    fun loadRepositories() {
        viewModelScope.launch {
            val tokenExists = github.hasToken()
            if (!tokenExists) {
                _uiState.update { it.copy(isLoading = false, hasToken = false, repositories = emptyList(), error = null) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, hasToken = true, error = null) }
            try {
                when (val result = github.repositories()) {
                    is VmResult.Success -> _uiState.update {
                        it.copy(repositories = result.value, hasToken = true, isLoading = false)
                    }
                    is VmResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, hasToken = true, error = result.error.summary)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = exception.message ?: "Could not load GitHub repositories")
                }
            }
        }
    }
}
