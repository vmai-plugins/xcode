package digital.vmstudio.code.core.connectors.google

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.security.model.Secret
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GoogleUserProfile(
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    val idToken: String? = null,
    val accessToken: String? = null,
)

@Serializable
private data class GoogleTokenResponse(
    val access_token: String? = null,
    val id_token: String? = null,
    val token_type: String? = null,
    val expires_in: Int? = null,
    val scope: String? = null,
    val error: String? = null,
    val error_description: String? = null,
    val refresh_token: String? = null,
)

@Serializable
private data class GoogleUserInfoResponse(
    val sub: String = "",
    val name: String? = null,
    val given_name: String? = null,
    val picture: String? = null,
    val email: String = "",
    val email_verified: Boolean = false,
)

@Singleton
class GoogleAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: VmHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    companion object {
        const val DEFAULT_CLIENT_ID = "1067538652652-3rtonh80siv4an0scham29psk0pilsaq.apps.googleusercontent.com"
        const val REDIRECT_URI = "https://vmstudio.digital/auth/google/callback/"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val PREFS_NAME = "google_oauth_pkce"
        private const val KEY_VERIFIER = "pkce_code_verifier"
        private const val KEY_STATE = "oauth_state"
        private const val KEY_FLOW = "oauth_flow"
        private const val SIGN_IN_FLOW = "sign_in"
        private const val DRIVE_FLOW = "drive"
        private const val PROFILE_SCOPE = "openid email profile"
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var activeCodeVerifier: String? = null

    /**
     * Generates a cryptographically random PKCE code verifier and challenge,
     * persists it so it survives process recreation, and returns the authorization URI.
     */
    fun buildAuthorizationUri(clientId: String = DEFAULT_CLIENT_ID): Uri =
        buildAuthorizationUri(clientId, PROFILE_SCOPE, SIGN_IN_FLOW, requestRefreshToken = false)

    fun buildDriveAuthorizationUri(clientId: String = DEFAULT_CLIENT_ID): Uri =
        buildAuthorizationUri(
            clientId = clientId,
            scope = "$PROFILE_SCOPE $DRIVE_SCOPE",
            flow = DRIVE_FLOW,
            requestRefreshToken = true,
        )

    fun isDriveAuthorizationState(state: String?): Boolean = state?.startsWith("$DRIVE_FLOW.") == true

    fun consumeAuthorizationCodeVerifier(state: String?, isDriveFlow: Boolean = false): VmResult<String> =
        consumePendingFlow(state, if (isDriveFlow) DRIVE_FLOW else SIGN_IN_FLOW)

    private fun buildAuthorizationUri(
        clientId: String,
        scope: String,
        flow: String,
        requestRefreshToken: Boolean,
    ): Uri {
        val verifier = generateCodeVerifier()
        val state = "$flow.${generateCodeVerifier()}"
        activeCodeVerifier = verifier
        prefs.edit()
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_STATE, state)
            .putString(KEY_FLOW, flow)
            .apply()
        val challenge = generateCodeChallenge(verifier)

        val builder = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("prompt", if (requestRefreshToken) "consent select_account" else "select_account")
        if (requestRefreshToken) {
            builder.appendQueryParameter("access_type", "offline")
                .appendQueryParameter("include_granted_scopes", "true")
        }
        return builder.build()
    }

    /**
     * Exchanges the authorization code received from the deep link redirect
     * for Google tokens, then fetches the user profile.
     */
    suspend fun exchangeCodeForProfile(
        code: String,
        clientId: String = DEFAULT_CLIENT_ID,
        state: String? = null,
    ): VmResult<GoogleUserProfile> = withContext(ioDispatcher) {
        exchangeCodeForTokens(code, clientId, state, SIGN_IN_FLOW).flatMap { tokenData ->
            val accessToken = tokenData.access_token
                ?: return@flatMap VmResult.Failure(
                    VmError.Authentication(summary = "No access token in Google response")
                )

            VmResult.Success(tokenData to accessToken)
        }.flatMap { (tokenData, accessToken) ->
            val userInfoRequest = Request.Builder()
                .url(USERINFO_ENDPOINT)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.execute(userInfoRequest).flatMap { userInfoResponseString ->
                try {
                    val userInfo = json.decodeFromString<GoogleUserInfoResponse>(userInfoResponseString)
                    VmResult.Success(
                        GoogleUserProfile(
                            email = userInfo.email.ifBlank { "google-user@vmstudio.digital" },
                            name = userInfo.name ?: userInfo.given_name,
                            picture = userInfo.picture,
                            idToken = tokenData.id_token,
                            accessToken = accessToken,
                        )
                    )
                } catch (e: Exception) {
                    VmResult.Failure(
                        VmError.Network(
                            summary = "Failed to parse Google user info response",
                            reason = e.message,
                            cause = e,
                        )
                    )
                }
            }
        }
    }

    suspend fun exchangeCodeForDriveRefreshToken(
        code: String,
        state: String?,
        clientId: String = DEFAULT_CLIENT_ID,
    ): VmResult<String> = withContext(ioDispatcher) {
        exchangeCodeForTokens(code, clientId, state, DRIVE_FLOW).flatMap { tokenData ->
            val refreshToken = tokenData.refresh_token
                ?: return@flatMap VmResult.Failure(
                    VmError.Authentication(
                        summary = "Google did not issue a Drive refresh token",
                        reason = "Remove X-Codes from your Google Account's third-party connections, then reconnect Drive.",
                    )
                )
            VmResult.Success(refreshToken)
        }
    }

    suspend fun refreshDriveAccessToken(refreshToken: Secret): VmResult<String> = withContext(ioDispatcher) {
        val requestBody = refreshToken.useAsString { token ->
            FormBody.Builder()
                .add("client_id", DEFAULT_CLIENT_ID)
                .add("refresh_token", token)
                .add("grant_type", "refresh_token")
                .build()
        }
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(requestBody)
            .build()

        httpClient.execute(request).flatMap { response ->
            val tokenData = try {
                json.decodeFromString<GoogleTokenResponse>(response)
            } catch (e: Exception) {
                return@flatMap VmResult.Failure(
                    VmError.Network(
                        summary = "Failed to parse Google Drive refresh response",
                        reason = e.message,
                        cause = e,
                    )
                )
            }
            if (!tokenData.error.isNullOrBlank()) {
                return@flatMap VmResult.Failure(
                    VmError.Authentication(
                        summary = "Google Drive authorization expired: ${tokenData.error}",
                        reason = tokenData.error_description,
                    )
                )
            }
            tokenData.access_token?.let { VmResult.Success(it) }
                ?: VmResult.Failure(VmError.Authentication(summary = "No access token in Google refresh response"))
        }
    }

    private suspend fun exchangeCodeForTokens(
        code: String,
        clientId: String,
        state: String?,
        flow: String,
    ): VmResult<GoogleTokenResponse> = withContext(ioDispatcher) {
        val verifier = when (val pending = consumePendingFlow(state, flow)) {
            is VmResult.Success -> pending.value
            is VmResult.Failure -> return@withContext pending
        }
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .build()
        val tokenRequest = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        httpClient.execute(tokenRequest).flatMap { response ->
            val tokenData = try {
                json.decodeFromString<GoogleTokenResponse>(response)
            } catch (e: Exception) {
                return@flatMap VmResult.Failure(
                    VmError.Network(
                        summary = "Failed to parse Google OAuth token response",
                        reason = e.message,
                        cause = e,
                    )
                )
            }
            if (!tokenData.error.isNullOrBlank()) {
                return@flatMap VmResult.Failure(
                    VmError.Authentication(
                        summary = "Google authorization failed: ${tokenData.error}",
                        reason = tokenData.error_description ?: tokenData.error,
                    )
                )
            }
            VmResult.Success(tokenData)
        }
    }

    private fun consumePendingFlow(state: String?, flow: String): VmResult<String> {
        val verifier = activeCodeVerifier ?: prefs.getString(KEY_VERIFIER, null)
        val expectedState = prefs.getString(KEY_STATE, null)
        val expectedFlow = prefs.getString(KEY_FLOW, null)
        val matchesState = !state.isNullOrBlank() && !expectedState.isNullOrBlank() &&
            MessageDigest.isEqual(state.toByteArray(Charsets.UTF_8), expectedState.toByteArray(Charsets.UTF_8))
        if (verifier.isNullOrBlank() || expectedFlow != flow || !matchesState) {
            return VmResult.Failure(
                VmError.Authentication(
                    summary = "Google authorization expired or invalid",
                    reason = "The OAuth state or PKCE verifier is missing. Restart Google sign-in.",
                )
            )
        }
        activeCodeVerifier = null
        prefs.edit()
            .remove(KEY_VERIFIER)
            .remove(KEY_STATE)
            .remove(KEY_FLOW)
            .apply()
        return VmResult.Success(verifier)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
