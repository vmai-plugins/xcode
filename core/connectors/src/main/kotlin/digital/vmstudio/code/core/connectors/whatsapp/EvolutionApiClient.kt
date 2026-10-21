package digital.vmstudio.code.core.connectors.whatsapp

import android.util.Base64
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvolutionApiClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Checks if the WhatsApp instance is open and connected to WhatsApp Web.
     */
    suspend fun checkConnection(config: EvolutionConfig): VmResult<Boolean> {
        val base = config.baseUrl.trimEnd('/')
        val url = "$base/instance/connectionState/${config.instance}"
        val request = Request.Builder()
            .url(url)
            .header("apikey", config.apiKey)
            .get()
            .build()

        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 1))) {
            is VmResult.Failure -> result
            is VmResult.Success -> {
                vmCatching {
                    val parsed = json.decodeFromString<EvolutionConnectionStateResponse>(result.value)
                    val state = parsed.instance?.state?.lowercase()
                    state == "open" || state == "connected"
                }
            }
        }
    }

    /**
     * Sends a text message to a specific WhatsApp number.
     * [number] should be in international format without '+' (e.g. "919876543210").
     */
    suspend fun sendText(
        config: EvolutionConfig,
        number: String,
        text: String,
    ): VmResult<EvolutionSendResponse> {
        val cleanNumber = number.filter { it.isDigit() }
        if (cleanNumber.isBlank()) {
            return VmResult.Failure(VmError.Validation(summary = "Invalid WhatsApp number", reason = "Number must contain digits"))
        }

        val base = config.baseUrl.trimEnd('/')
        val url = "$base/message/sendText/${config.instance}"
        val payload = EvolutionSendTextRequest(
            number = cleanNumber,
            text = text,
            linkPreview = true,
        )
        val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("apikey", config.apiKey)
            .post(body)
            .build()

        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> {
                vmCatching {
                    json.decodeFromString<EvolutionSendResponse>(result.value)
                }
            }
        }
    }

    /**
     * Sends a document, APK, or media file to a specific WhatsApp number.
     */
    suspend fun sendMedia(
        config: EvolutionConfig,
        number: String,
        mediaDataOrUrl: String,
        fileName: String,
        mimeType: String = "application/octet-stream",
        caption: String? = null,
    ): VmResult<EvolutionSendResponse> {
        val cleanNumber = number.filter { it.isDigit() }
        if (cleanNumber.isBlank()) {
            return VmResult.Failure(VmError.Validation(summary = "Invalid WhatsApp number", reason = "Number must contain digits"))
        }

        val base = config.baseUrl.trimEnd('/')
        val url = "$base/message/sendMedia/${config.instance}"
        val payload = EvolutionSendMediaRequest(
            number = cleanNumber,
            mediatype = "document",
            mimetype = mimeType,
            caption = caption,
            media = mediaDataOrUrl,
            fileName = fileName,
        )
        val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("apikey", config.apiKey)
            .post(body)
            .build()

        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> {
                vmCatching {
                    json.decodeFromString<EvolutionSendResponse>(result.value)
                }
            }
        }
    }

    /**
     * Sends a local APK file to a WhatsApp number.
     * Encodes file as data URI base64 if under 64MB or uses file path.
     */
    suspend fun sendApk(
        config: EvolutionConfig,
        number: String,
        apkFile: File,
        caption: String? = null,
    ): VmResult<EvolutionSendResponse> {
        if (!apkFile.exists()) {
            return VmResult.Failure(VmError.FileSystem(summary = "APK file not found", reason = apkFile.absolutePath))
        }

        // Evolution API supports base64 data URIs: data:application/vnd.android.package-archive;base64,...
        val bytes = try {
            apkFile.readBytes()
        } catch (e: Exception) {
            return VmResult.Failure(VmError.FileSystem(summary = "Could not read APK", reason = e.message))
        }

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUri = "data:application/vnd.android.package-archive;base64,$base64"

        return sendMedia(
            config = config,
            number = number,
            mediaDataOrUrl = dataUri,
            fileName = apkFile.name,
            mimeType = "application/vnd.android.package-archive",
            caption = caption ?: "Android APK: ${apkFile.name}",
        )
    }
}
