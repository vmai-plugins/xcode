package digital.vmstudio.code.core.connectors.github

import digital.vmstudio.code.core.common.result.VmResult
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
    private data class TokenResponse(
        val access_token: String,
        val token_type: String,
        val scope: String
    )

    suspend fun exchangeCodeForToken(
        code: String,
        clientId: String,
        clientSecret: String
    ): VmResult<String> = vmCatching {
        val requestBody = buildMap {
            put("client_id", clientId)
            put("client_secret", clientSecret)
            put("code", code)
        }.let { json.encodeToString(MapSerializer(String.serializer(), String.serializer()), it) }

        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json")
            .build()

        val response = httpClient.execute(request)
        val tokenResponse = json.decodeFromString<TokenResponse>(response.body?.string() ?: "")
        tokenResponse.access_token
    }
}
