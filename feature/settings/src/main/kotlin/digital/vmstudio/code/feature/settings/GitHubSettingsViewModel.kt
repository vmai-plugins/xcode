package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.git.GitHubRepository
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import kotlinx.coroutines.CancellationException
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
            _uiState.update { it.copy(isLoading = true, hasToken = tokenExists, error = null) }
            try {
                when (val result = github.repositories()) {
                    is VmResult.Success -> _uiState.update {
                        it.copy(repositories = result.value, hasToken = tokenExists, isLoading = false)
                    }
                    is VmResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, hasToken = tokenExists, error = result.error.summary)
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
