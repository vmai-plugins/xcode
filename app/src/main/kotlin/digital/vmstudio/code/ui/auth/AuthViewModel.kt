package digital.vmstudio.code.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
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
    private val gitHubAuthClient: digital.vmstudio.code.core.connectors.github.GitHubAuthClient,
    private val googleAuthClient: digital.vmstudio.code.core.connectors.google.GoogleAuthClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * Initiates Google Sign-In via Chrome Custom Tabs / external browser with PKCE.
     * Avoids native Play Services SHA-1 fingerprint developer errors and works across all Android devices.
     */
    fun signInWithGoogleBrowser(context: Context) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        val uri = googleAuthClient.buildAuthorizationUri()
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "CustomTabs failed, falling back to standard ACTION_VIEW", e)
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (err: Exception) {
                Log.e(TAG, "Failed to launch web browser for Google sign-in", err)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not open a web browser to complete Google Sign-In.",
                )
            }
        }
    }

    fun handleGoogleAuthCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = googleAuthClient.exchangeCodeForProfile(code)) {
                is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                    val profile = result.value
                    Log.d(TAG, "Google profile retrieved for ${profile.email}")
                    preferencesRepository.setUserProfile(
                        email = profile.email,
                        name = profile.name ?: profile.email.substringBefore('@'),
                        avatarUrl = profile.picture,
                        idToken = profile.idToken,
                    )
                    preferencesRepository.setHasSignedIn(true)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null, isSignedIn = true)
                }
                is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                    Log.e(TAG, "Google OAuth exchange failed: ${result.error.summary}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Google Sign-In failed: ${result.error.summary}",
                    )
                }
            }
        }
    }

    /**
     * Secondary sign-in using an API token or Hub key.
     */
    fun signInWithToken(token: String, onSignedIn: () -> Unit) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid API key or token.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            preferencesRepository.setHubApiKey(trimmed)
            preferencesRepository.setUserProfile(
                email = "token-user@vmstudio.digital",
                name = "API Developer",
                avatarUrl = null,
                idToken = null,
            )
            preferencesRepository.setHasSignedIn(true)
            _uiState.value = _uiState.value.copy(isLoading = false, error = null, isSignedIn = true)
            onSignedIn()
        }
    }

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

    fun skipSignIn(onSignedIn: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setUserProfile(
                email = "Guest User",
                name = "Guest Developer",
                avatarUrl = null,
                idToken = null,
            )
            preferencesRepository.setHasSignedIn(true)
            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            onSignedIn()
        }
    }

    fun handleGitHubAuthCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = gitHubAuthClient.exchangeCodeForToken(code)) {
                is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                    Log.d(TAG, "GitHub Token exchanged successfully.")
                    preferencesRepository.setHasSignedIn(true)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }
                is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                    Log.e(TAG, "GitHub Token exchange failed: ${result.error.summary}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to authenticate with GitHub: ${result.error.summary}"
                    )
                }
            }
        }
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
    val isSignedIn: Boolean = false,
)
