package digital.vmstudio.code.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(context: Context, onSignedIn: () -> Unit) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // CredentialManager requires an Activity context to anchor the system bottom sheet dialog
                val activity = context.findActivity() ?: context
                val credentialManager = CredentialManager.create(activity)

                val webClientId = "1067538652652-3rtonh80siv4an0scham29psk0pilsaq.apps.googleusercontent.com"

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false) // Prompt user so they can select or switch accounts
                    .setNonce(generateHashedNonce())
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = activity,
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val email = googleIdTokenCredential.id
                    val displayName = googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore('@')
                    val avatarUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    val idToken = googleIdTokenCredential.idToken

                    preferencesRepository.setUserProfile(
                        email = email,
                        name = displayName,
                        avatarUrl = avatarUrl,
                        idToken = idToken,
                    )
                    preferencesRepository.setHasSignedIn(true)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    onSignedIn()
                } else {
                    throw IllegalStateException("Unexpected credential format received: ${credential::class.java.simpleName}")
                }
            } catch (e: GetCredentialCancellationException) {
                // User intentionally cancelled / dismissed the Google Account chooser
                Log.d(TAG, "Google sign-in cancelled by user")
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            } catch (e: NoCredentialException) {
                // No Google account configured on device
                Log.w(TAG, "No Google accounts available on device", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No Google account found on this device. Please add an account in Android Settings or Continue as Guest.",
                )
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error: ${e.type}", e)
                val errorMessage = when {
                    e.message?.contains("16", ignoreCase = true) == true ||
                        e.message?.contains("Cannot find a matching credential", ignoreCase = true) == true ->
                        "Account selection was dismissed. Tap Continue with Google to retry."
                    e.message?.contains("10", ignoreCase = true) == true ||
                        e.message?.contains("Developer Error", ignoreCase = true) == true ->
                        "Play Services authorization error (Developer Error). Please Continue as Guest or verify signing credentials."
                    else ->
                        e.localizedMessage ?: "Google Sign-In failed. Please try again or Continue as Guest."
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMessage,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected authentication exception", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "An unexpected error occurred during sign-in.",
                )
            }
        }
    }

    fun signInWithGitHub(context: Context) {
        if (_uiState.value.isLoading) return

        val clientId = "YOUR_GITHUB_CLIENT_ID" // TODO: Add to local.properties
        val redirectUri = "vmstudio://auth/github"
        val authUrl = "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=${Uri.encode(redirectUri)}&scope=repo,user"

        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(authUrl))
    }

    private fun generateHashedNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)
