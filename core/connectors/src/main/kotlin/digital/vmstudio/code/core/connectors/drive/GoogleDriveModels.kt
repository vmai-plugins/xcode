package digital.vmstudio.code.core.connectors.drive

import kotlinx.serialization.Serializable

@Serializable
data class GoogleDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String? = null,
    val modifiedTime: String? = null,
) {
    val sizeBytes: Long get() = size?.toLongOrNull() ?: 0L
    val isFolder: Boolean get() = mimeType == "application/vnd.google-apps.folder"
    val isDoc: Boolean get() = mimeType == "application/vnd.google-apps.document"
    val isSheet: Boolean get() = mimeType == "application/vnd.google-apps.spreadsheet"
    val isPresentation: Boolean get() = mimeType == "application/vnd.google-apps.presentation"
}

@Serializable
data class GoogleDriveFileListResponse(
    val files: List<GoogleDriveFile> = emptyList(),
    val nextPageToken: String? = null,
)
