package digital.vmstudio.code.feature.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.fs.TransferEvent
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Moves bytes between the server and the phone for the Files screen: previews,
 * downloads to share, and uploads picked from the phone.
 *
 * Everything lands under `cacheDir/downloads` (shared through the app's
 * FileProvider) or `cacheDir/uploads` (deleted after sending), so nothing the
 * browser fetches outlives the cache or needs a storage permission.
 */
class FileTransfers @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteFileSystem: RemoteFileSystem,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Reads a text preview, split into lines off the main thread. */
    suspend fun loadText(serverId: String, entry: RemoteFileEntry): PreviewContent =
        when (val result = remoteFileSystem.readText(serverId, entry.path, TEXT_PREVIEW_MAX_BYTES)) {
            is VmResult.Failure -> PreviewContent.Failed(result.error)
            is VmResult.Success -> withContext(ioDispatcher) {
                PreviewContent.Text(previewLines(result.value))
            }
        }

    /** Downloads then decodes an image, subsampled so its longest edge fits the screen. */
    suspend fun loadImage(
        serverId: String,
        entry: RemoteFileEntry,
        onProgress: (Float?) -> Unit,
    ): PreviewContent {
        val file = when (val downloaded = download(serverId, entry, onProgress)) {
            is VmResult.Failure -> return PreviewContent.Failed(downloaded.error)
            is VmResult.Success -> downloaded.value
        }
        val bitmap = withContext(ioDispatcher) { decodeBitmap(file) }
            ?: return PreviewContent.Failed(
                VmError.FileSystem(
                    summary = "Could not show ${entry.name}",
                    reason = "The image is damaged or in a format this phone cannot decode.",
                    path = entry.path,
                ),
            )
        return PreviewContent.Image(bitmap, file)
    }

    /**
     * Downloads [entry] into its own folder under `cacheDir/downloads`, keeping the
     * server's file name so the share sheet and the receiving app show it.
     */
    suspend fun download(
        serverId: String,
        entry: RemoteFileEntry,
        onProgress: (Float?) -> Unit,
    ): VmResult<File> {
        val destination = withContext(ioDispatcher) {
            val root = File(context.cacheDir, DOWNLOAD_DIR)
            pruneOlderThan(root, System.currentTimeMillis() - DOWNLOAD_TTL_MILLIS)
            File(File(root, System.nanoTime().toString()), entry.name)
        }
        val failure = runTransfer(
            remoteFileSystem.download(serverId, entry.path, destination, resume = false),
            onProgress,
            failed = { reason -> transferFailed("Could not download ${entry.name}", reason, entry.path) },
        )
        return if (failure == null) VmResult.Success(destination) else VmResult.Failure(failure)
    }

    /** Uploads [source] to [remotePath]; null on success. */
    suspend fun upload(
        serverId: String,
        source: File,
        remotePath: String,
        onProgress: (Float?) -> Unit,
    ): VmError? = runTransfer(
        remoteFileSystem.upload(serverId, source, remotePath, resume = false),
        onProgress,
        failed = { reason -> transferFailed("Could not upload ${source.name}", reason, remotePath) },
    )

    /**
     * Copies a picked document into `cacheDir/uploads` under its display name, since
     * the SFTP layer streams from a [File] and a content Uri is not one.
     */
    suspend fun copyToCache(uri: Uri): VmResult<File> = withContext(ioDispatcher) {
        val name = displayName(uri).replace(UNSAFE_NAME_CHARS, "_").ifBlank { "upload" }
        val target = File(File(File(context.cacheDir, UPLOAD_DIR), System.nanoTime().toString()), name)
        try {
            target.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext VmResult.Failure(unreadable(name, "The file could not be opened."))
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            VmResult.Success(target)
        } catch (io: IOException) {
            target.parentFile?.deleteRecursively()
            VmResult.Failure(unreadable(name, io.message))
        }
    }

    /** Removes a temp copy made by [copyToCache], folder included. */
    suspend fun discard(file: File) {
        withContext(ioDispatcher) { file.parentFile?.deleteRecursively() }
    }

    /**
     * Drains a transfer flow, reporting progress. A flow that throws or ends without
     * a terminal event is turned into an error rather than escaping as an exception.
     */
    private suspend fun runTransfer(
        transfer: Flow<TransferEvent>,
        onProgress: (Float?) -> Unit,
        failed: (String?) -> VmError,
    ): VmError? {
        val last = transfer
            .onEach { event ->
                if (event is TransferEvent.Progress) onProgress(event.progress.fraction)
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                emit(TransferEvent.Failed(failed(cause.message), 0))
            }
            .lastOrNull()
        return when (last) {
            null -> failed("The transfer ended without reporting a result.")
            is TransferEvent.Failed -> last.error
            else -> null
        }
    }

    private fun decodeBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /** Old downloads are dropped lazily; an hour is ample for a share to finish reading. */
    private fun pruneOlderThan(root: File, cutoffMillis: Long) {
        root.listFiles()?.filter { it.lastModified() < cutoffMillis }?.forEach { it.deleteRecursively() }
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

    private fun unreadable(name: String, reason: String?) =
        VmError.FileSystem(summary = "Could not read $name from the phone", reason = reason, path = name)

    private fun transferFailed(summary: String, reason: String?, path: String) =
        VmError.FileSystem(summary = summary, reason = reason, retryable = true, path = path)

    private companion object {
        /** Must match the cache-path entry in the app's res/xml/file_paths.xml. */
        const val DOWNLOAD_DIR = "downloads"
        const val UPLOAD_DIR = "uploads"
        const val DOWNLOAD_TTL_MILLIS = 60L * 60 * 1000
        val UNSAFE_NAME_CHARS = Regex("[/\\\\:*?\"<>|]")
    }
}
