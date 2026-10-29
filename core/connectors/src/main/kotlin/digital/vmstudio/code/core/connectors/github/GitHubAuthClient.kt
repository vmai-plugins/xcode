package digital.vmstudio.code.core.connectors.github

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the secure exchange of a GitHub OAuth authorization code for an access token.
 *
 * NOTE: For production, this exchange MUST happen on your secure backend server
 * to avoid exposing the Client Secret in the Android APK.
 */
@Singleton
class GitHubAuthClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
) {

    @Serializable
    private data class TokenRequest(
        val client_id: String,
        val client_secret: String,
        val code: String,
    )

    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val token_type: String = "",
        val scope: String = "",
    )

    suspend fun exchangeCodeForToken(
        code: String,
        clientId: String = "",
        clientSecret: String = "",
    ): VmResult<String> {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return VmResult.Failure(
                digital.vmstudio.code.core.common.error.VmError.Validation(
                    summary = "GitHub OAuth credentials are not configured yet.",
                )
            )
        }
        val requestBody = json.encodeToString(
            TokenRequest.serializer(),
            TokenRequest(client_id = clientId, client_secret = clientSecret, code = code),
        )

        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json")
            .build()

        return httpClient.execute(request).flatMap { responseBody ->
            vmCatching {
                val tokenResponse = json.decodeFromString<TokenResponse>(responseBody)
                tokenResponse.access_token
            }
        }
    }
}
