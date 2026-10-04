package digital.vmstudio.code.core.connectors.drive

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Google Drive REST API v3.
 * Supports listing files, uploading project archives/backups, and downloading.
 */
@Singleton
class GoogleDriveClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
) {

    suspend fun listFiles(
        accessToken: String,
        query: String? = null,
        pageSize: Int = 30,
    ): VmResult<List<GoogleDriveFile>> {
        val qParam = query?.let { "&q=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val fields = "files(id,name,mimeType,size,modifiedTime)"
        val url = "$DRIVE_API_BASE/files?pageSize=$pageSize&fields=$fields$qParam"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching {
                    val response = json.decodeFromString<GoogleDriveFileListResponse>(body)
                    response.files
                }
            }
    }

    suspend fun uploadFile(
        accessToken: String,
        filename: String,
        mimeType: String,
        content: ByteArray,
        parentFolderId: String? = null,
    ): VmResult<GoogleDriveFile> {
        val metadataJson = buildJsonObject {
            put("name", filename)
            put("mimeType", mimeType)
            if (parentFolderId != null) {
                // If specifying parents in Google Drive v3, it accepts a JSON array of folder IDs
            }
        }.toString()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                "metadata",
                metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()),
            )
            .addFormDataPart(
                "file",
                filename,
                content.toRequestBody(mimeType.toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE/files?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        return httpClient.execute(request, RetryPolicy(maxAttempts = 2))
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString<GoogleDriveFile>(body)
                }
            }
    }

    suspend fun downloadFile(
        accessToken: String,
        fileId: String,
    ): VmResult<String> {
        val url = "$DRIVE_API_BASE/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy())
    }

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    }
}
