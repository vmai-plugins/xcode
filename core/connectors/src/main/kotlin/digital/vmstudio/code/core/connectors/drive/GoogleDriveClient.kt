package digital.vmstudio.code.core.connectors.drive

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
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
        val files = mutableListOf<GoogleDriveFile>()
        var pageToken: String? = null

        do {
            val queryParams = buildList {
                add("pageSize=$pageSize")
                add("fields=nextPageToken,files(id,name,mimeType,size,modifiedTime)")
                query?.let { add("q=${URLEncoder.encode(it, "UTF-8")}") }
                pageToken?.let { add("pageToken=${URLEncoder.encode(it, "UTF-8")}") }
            }.joinToString("&")
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/files?$queryParams")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            when (val result = httpClient.execute(request, RetryPolicy())) {
                is VmResult.Failure -> return result
                is VmResult.Success -> {
                    when (val parsed = vmCatching {
                        json.decodeFromString<GoogleDriveFileListResponse>(result.value)
                    }) {
                        is VmResult.Failure -> return parsed
                        is VmResult.Success -> {
                            files += parsed.value.files
                            pageToken = parsed.value.nextPageToken
                        }
                    }
                }
            }
        } while (pageToken != null)

        return VmResult.Success(files)
    }
    suspend fun uploadFile(
        accessToken: String,
        filename: String,
        mimeType: String,
        content: ByteArray,
        parentFolderId: String? = null,
    ): VmResult<GoogleDriveFile> {
        return uploadFile(
            accessToken,
            filename,
            mimeType,
            content.toRequestBody(mimeType.toMediaType()),
            parentFolderId,
        )
    }

    suspend fun uploadFile(
        accessToken: String,
        filename: String,
        mimeType: String,
        file: java.io.File,
        parentFolderId: String? = null,
    ): VmResult<GoogleDriveFile> {
        return uploadFile(accessToken, filename, mimeType, file.asRequestBody(mimeType.toMediaType()), parentFolderId)
    }

    private suspend fun uploadFile(
        accessToken: String,
        filename: String,
        mimeType: String,
        fileBody: okhttp3.RequestBody,
        parentFolderId: String? = null,
    ): VmResult<GoogleDriveFile> {
        val metadataJson = buildJsonObject {
            put("name", filename)
            put("mimeType", mimeType)
            parentFolderId?.let { parent -> put("parents", buildJsonArray { add(parent) }) }
        }.toString()

        val relatedType = "multipart/related".toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(relatedType)
            .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(fileBody)
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
        val encodedFileId = URLEncoder.encode(fileId, "UTF-8")
        val url = "$DRIVE_API_BASE/files/$encodedFileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy())
    }

    suspend fun downloadFileTo(
        accessToken: String,
        fileId: String,
        destination: java.io.File,
    ): VmResult<java.io.File> {
        val encodedFileId = URLEncoder.encode(fileId, "UTF-8")
        val url = "$DRIVE_API_BASE/files/$encodedFileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return httpClient.download(request, destination)
    }

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    }
}



