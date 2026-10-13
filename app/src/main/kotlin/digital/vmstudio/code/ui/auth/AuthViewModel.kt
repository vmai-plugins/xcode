package digital.vmstudio.code.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import digital.vmstudio.code.core.ssh.model.ServerDraft
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun signInWithGoogle(context: Context, onSignedIn: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val credentialManager = CredentialManager.create(context)

                val webClientId = "1067538652652-3rtonh80siv4an0scham29psk0pilsaq.apps.googleusercontent.com"

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context,
                )

                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                    preferencesRepository.setUserProfile(
                        email = googleIdTokenCredential.id,
                        name = googleIdTokenCredential.displayName
                    )

                    preferencesRepository.setHasSignedIn(true)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    onSignedIn()
                } else {
                    throw RuntimeException("Unexpected credential type")
                }
            } catch (e: NoCredentialException) {
                Log.w("Auth", "No Google credential returned; completing sign-in with default profile", e)
                preferencesRepository.setUserProfile(email = "Google Account", name = "X-codes Developer")
                preferencesRepository.setHasSignedIn(true)
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                onSignedIn()
            } catch (e: GetCredentialException) {
                Log.w("Auth", "CredentialManager fallback; completing sign-in with default profile", e)
                preferencesRepository.setUserProfile(email = "Google Account", name = "X-codes Developer")
                preferencesRepository.setHasSignedIn(true)
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                onSignedIn()
            } catch (e: Exception) {
                Log.e("Auth", "Sign in error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Unknown authentication error."
                )
            }
        }
    }

    fun skipSignIn(onSignedIn: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setHasSignedIn(true)
            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            onSignedIn()
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
