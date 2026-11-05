package digital.vmstudio.code.core.connectors.gmail

import android.util.Base64
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"

@Singleton
class GmailClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
) {

    /**
     * Lists or searches messages in the user's Gmail mailbox.
     */
    suspend fun listMessages(
        accessToken: String,
        query: String? = null,
        maxResults: Int = 15,
    ): VmResult<GmailMessageListResponse> {
        val params = buildList {
            add("maxResults=${maxResults.coerceIn(1, 50)}")
            query?.let { add("q=${URLEncoder.encode(it, "UTF-8")}") }
        }.joinToString("&")

        val url = "$GMAIL_API_BASE/messages?$params"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString<GmailMessageListResponse>(body)
                }
            }
    }

    /**
     * Reads a full email message by ID.
     */
    suspend fun getMessage(
        accessToken: String,
        messageId: String,
    ): VmResult<GmailMessage> {
        val encodedId = URLEncoder.encode(messageId, "UTF-8")
        val url = "$GMAIL_API_BASE/messages/$encodedId?format=full"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString<GmailMessage>(body)
                }
            }
    }

    /**
     * Sends an email via Gmail API using raw RFC 2822 formatting.
     */
    suspend fun sendEmail(
        accessToken: String,
        to: String,
        subject: String,
        bodyHtml: String,
    ): VmResult<GmailSendResponse> {
        val rawMime = buildRfc2822(to, subject, bodyHtml)
        val encoded = Base64.encodeToString(rawMime.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

        val jsonPayload = buildJsonObject {
            put("raw", encoded)
        }.toString()

        val request = Request.Builder()
            .url("$GMAIL_API_BASE/messages/send")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString<GmailSendResponse>(body)
                }
            }
    }

    private fun buildRfc2822(to: String, subject: String, bodyHtml: String): String {
        return buildString {
            append("MIME-Version: 1.0\r\n")
            append("To: $to\r\n")
            append("Subject: =?UTF-8?B?${Base64.encodeToString(subject.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}?=\r\n")
            append("Content-Type: text/html; charset=UTF-8\r\n\r\n")
            append(bodyHtml)
        }
    }
}
