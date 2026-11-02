package digital.vmstudio.code.ui.auth

import android.content.Context
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.connectors.google.GoogleDriveOAuthState
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.security.store.HubCredentialManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val googleAuthClient: digital.vmstudio.code.core.connectors.google.GoogleAuthClient,
    private val projectHubClient: digital.vmstudio.code.core.project.VmProjectHubClient,
    private val credentialStore: SecureCredentialStore,
    private val hubCredentialManager: HubCredentialManager,
    private val driveOAuthState: GoogleDriveOAuthState,
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
            _uiState.value = _uiState.value.copy(isLoading = false)
        } catch (e: Exception) {
            Log.w(TAG, "CustomTabs failed, falling back to standard ACTION_VIEW", e)
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (err: Exception) {
                Log.e(TAG, "Failed to launch web browser for Google sign-in", err)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not open a web browser to complete Google Sign-In.",
                )
            }
        }
    }

    /**
     * Resets loading state if the user dismisses an external browser OAuth flow.
     */
    fun resetLoading() {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun handleGoogleAuthCode(code: String, state: String? = null) {
        if (googleAuthClient.isDriveAuthorizationState(state)) {
            handleGoogleDriveAuthCode(code, state)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val verifier = googleAuthClient.consumeAuthorizationCodeVerifier(state)) {
                is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                    when (val result = projectHubClient.exchangeGoogleAuthorizationCode(code, verifier.value)) {
                        is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                            val sessionSecret = Secret.of(result.value.accessToken)
                            try {
                                when (
                                    val stored = credentialStore.put(
                                        type = SecretType.OAUTH_ACCESS_TOKEN,
                                        label = "VM Project Hub session",
                                        secret = sessionSecret,
                                    )
                                ) {
                                    is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                                        val previousId = preferencesRepository.preferences.first()
                                            .hubUserTokenCredentialId
                                        preferencesRepository.setHubUserTokenCredential(stored.value.id)
                                        preferencesRepository.setUserProfile(
                                            email = result.value.email,
                                            name = result.value.name.ifBlank {
                                                result.value.email.substringBefore('@')
                                            },
                                            avatarUrl = result.value.avatarUrl,
                                        )
                                        preferencesRepository.setHasSignedIn(true)
                                        if (previousId != null && previousId != stored.value.id) {
                                            when (val deleted = credentialStore.delete(previousId)) {
                                                is digital.vmstudio.code.core.common.result.VmResult.Success -> Unit
                                                is digital.vmstudio.code.core.common.result.VmResult.Failure ->
                                                    Log.w(TAG, "Old Hub session cleanup failed: ${deleted.error.summary}")
                                            }
                                        }
                                        _uiState.value = _uiState.value.copy(
                                            isLoading = false,
                                            error = null,
                                            isSignedIn = true,
                                        )
                                    }
                                    is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                                        Log.e(TAG, "Could not securely store the VM Project Hub session: ${stored.error.summary}")
                                        _uiState.value = _uiState.value.copy(
                                            isLoading = false,
                                            error = "Google signed in, but the Hub session could not be stored securely.",
                                        )
                                    }
                                }
                            } finally {
                                sessionSecret.wipe()
                            }
                        }
                        is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                            Log.e(TAG, "Google account sign-in failed: ${result.error.summary}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Google sign-in failed: ${result.error.summary}",
                            )
                        }
                    }
                }
                is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                    Log.e(TAG, "Google OAuth state validation failed: ${verifier.error.summary}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Google sign-in failed: ${verifier.error.summary}",
                    )
                }
            }
        }
    }

    fun handleGoogleAuthError(error: String, state: String?) {
        val message = when (error) {
            "access_denied" -> "Google authorization was cancelled."
            else -> "Google authorization failed: ${error.take(120)}"
        }
        if (googleAuthClient.isDriveAuthorizationState(state)) {
            driveOAuthState.updateStatus(message)
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false, error = message)
        }
    }

    private fun handleGoogleDriveAuthCode(code: String, state: String?) {
        viewModelScope.launch {
            driveOAuthState.updateStatus("Completing Google Drive authorization…")
            try {
                when (val result = googleAuthClient.exchangeCodeForDriveRefreshToken(code, state)) {
                    is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                        val refreshToken = Secret.of(result.value)
                        try {
                            when (
                                val stored = credentialStore.put(
                                    type = SecretType.OAUTH_REFRESH_TOKEN,
                                    label = "Google Drive refresh token",
                                    secret = refreshToken,
                                )
                            ) {
                                is digital.vmstudio.code.core.common.result.VmResult.Success -> {
                                    val previous = preferencesRepository.preferences.first()
                                    preferencesRepository.setGoogleDriveRefreshTokenCredential(stored.value.id)
                                    preferencesRepository.setGoogleDriveTokenCredential(null)
                                    listOfNotNull(
                                        previous.googleDriveRefreshTokenCredentialId,
                                        previous.googleDriveTokenCredentialId,
                                    ).distinct().forEach { previousId ->
                                        when (val deleted = credentialStore.delete(previousId)) {
                                            is digital.vmstudio.code.core.common.result.VmResult.Success -> Unit
                                            is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                                                Log.w(TAG, "Old Google Drive credential cleanup failed: ${deleted.error.summary}")
                                            }
                                        }
                                    }
                                    driveOAuthState.updateStatus("Google Drive connected. Full Drive access is ready.")
                                }
                                is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                                    driveOAuthState.updateStatus(
                                        "Google Drive authorization succeeded, but the refresh token could not be stored: ${stored.error.summary}",
                                    )
                                }
                            }
                        } finally {
                            refreshToken.wipe()
                        }
                    }
                    is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                        driveOAuthState.updateStatus("Google Drive connection failed: ${result.error.summary}")
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Log.e(TAG, "Google Drive authorization failed", exception)
                driveOAuthState.updateStatus(
                    "Google Drive connection failed: ${exception.localizedMessage ?: "Unexpected error"}",
                )
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
            when (val stored = hubCredentialManager.store(trimmed)) {
                is digital.vmstudio.code.core.common.result.VmResult.Success -> {
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
                is digital.vmstudio.code.core.common.result.VmResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not securely save the Hub token: ${stored.error.summary}",
                    )
                }
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

    private companion object {
        const val TAG = "AuthViewModel"
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false,
)
