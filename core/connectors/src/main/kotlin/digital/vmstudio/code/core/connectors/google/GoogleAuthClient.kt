package digital.vmstudio.code.core.connectors.google

import android.net.Uri
import android.util.Base64
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.VmHttpClient
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
    }

    private var activeCodeVerifier: String? = null

    /**
     * Generates a cryptographically random PKCE code verifier and challenge,
     * and returns the authorization URI to launch in CustomTabs or external browser.
     */
    fun buildAuthorizationUri(clientId: String = DEFAULT_CLIENT_ID): Uri {
        val verifier = generateCodeVerifier()
        activeCodeVerifier = verifier
        val challenge = generateCodeChallenge(verifier)

        return Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()
    }

    /**
     * Exchanges the authorization code received from the deep link redirect
     * for Google tokens, then fetches the user profile.
     */
    suspend fun exchangeCodeForProfile(
        code: String,
        clientId: String = DEFAULT_CLIENT_ID,
    ): VmResult<GoogleUserProfile> = withContext(ioDispatcher) {
        val verifier = activeCodeVerifier ?: generateCodeVerifier()
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

        httpClient.execute(tokenRequest).flatMap { tokenResponseString ->
            val tokenData = try {
                json.decodeFromString<GoogleTokenResponse>(tokenResponseString)
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
