package digital.vmstudio.code.core.connectors.drive

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Google Drive REST API v3.
 * Supports listing files, uploading project archives/backups, exporting documents, and downloading.
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
        pageToken: String? = null,
    ): VmResult<List<GoogleDriveFile>> {
        val qParam = query?.let { "&q=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val pageParam = pageToken?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val url = "$DRIVE_API_BASE/files?pageSize=$pageSize&fields=files(id,name,mimeType,size,modifiedTime),nextPageToken$qParam$pageParam"

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
                putJsonArray("parents") {
                    add(parentFolderId)
                }
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

    suspend fun exportFile(
        accessToken: String,
        fileId: String,
        exportMimeType: String = "text/plain",
    ): VmResult<String> {
        val encodedMime = URLEncoder.encode(exportMimeType, "UTF-8")
        val url = "$DRIVE_API_BASE/files/$fileId/export?mimeType=$encodedMime"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy())
    }

    suspend fun createResumableUploadSession(
        accessToken: String,
        filename: String,
        mimeType: String,
        parentFolderId: String? = null,
    ): VmResult<String> {
        val metadataJson = buildJsonObject {
            put("name", filename)
            put("mimeType", mimeType)
            if (parentFolderId != null) {
                putJsonArray("parents") {
                    add(parentFolderId)
                }
            }
        }.toString()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE/files?uploadType=resumable")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("X-Upload-Content-Type", mimeType)
            .post(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        return httpClient.execute(request, RetryPolicy())
    }

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    }
}
