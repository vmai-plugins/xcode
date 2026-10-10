package digital.vmstudio.code.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.network.http.RetryPolicy
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject

data class GitHubRepo(
    val id: Long,
    val fullName: String,
    val description: String?,
    val cloneUrl: String
)

data class GitHubSettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val repositories: List<GitHubRepo> = emptyList()
)

@HiltViewModel
class GitHubSettingsViewModel @Inject constructor(
    private val httpClient: VmHttpClient,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHubSettingsUiState())
    val uiState: StateFlow<GitHubSettingsUiState> = _uiState.asStateFlow()

    init {
        loadRepositories()
    }

    fun setToken(token: String) {
        viewModelScope.launch {
            preferencesRepository.setGithubConnection(token, "GitHub User")
            loadRepositories()
        }
    }

    private fun loadRepositories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val prefs = preferencesRepository.preferences.first()
            val token = prefs.githubTokenCredentialId
            if (token == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/user/repos?sort=updated&per_page=100")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val response = httpClient.execute(request, RetryPolicy.NONE)
                if (response is digital.vmstudio.code.core.common.result.VmResult.Success) {
                    val body = response.value
                    val repos = mutableListOf<GitHubRepo>()
                    if (body != null) {
                        try {
                           val jsonArray = JSONArray(body)
                           for (i in 0 until jsonArray.length()) {
                               val obj = jsonArray.getJSONObject(i)
                               repos.add(
                                   GitHubRepo(
                                       id = obj.getLong("id"),
                                       fullName = obj.getString("full_name"),
                                       description = obj.optString("description", ""),
                                       cloneUrl = obj.getString("clone_url")
                                   )
                               )
                           }
                           _uiState.update { it.copy(repositories = repos, isLoading = false) }
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                           _uiState.update { it.copy(error = "JSON parse error", isLoading = false) }
                        }
                    } else {
                       _uiState.update { it.copy(error = "Empty response", isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(error = "Network request failed", isLoading = false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}

