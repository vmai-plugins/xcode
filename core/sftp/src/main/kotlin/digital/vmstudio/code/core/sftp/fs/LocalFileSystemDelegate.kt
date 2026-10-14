package digital.vmstudio.code.core.sftp.fs

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemoteListingOptions
import digital.vmstudio.code.core.sftp.model.RemotePermissions
import digital.vmstudio.code.core.sftp.model.sortedFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.io.ByteArrayOutputStream

private const val DIRECTORY_PERMISSIONS = 0b111101101
private const val FILE_PERMISSIONS = 0b110100100
private const val MILLIS_PER_SECOND = 1000L
private const val TRANSFER_BUFFER_BYTES = 32 * 1024
private const val TRANSFER_PROGRESS_INTERVAL_MILLIS = 250L

internal class LocalFileSystemDelegate(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private fun resolveLocalFile(path: String): File {
        if (path.startsWith("/vmhub/") || path.startsWith("/github/")) {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return File(base, "projects$path")
        }
        return File(path)
    }

    suspend fun list(path: String, options: RemoteListingOptions): VmResult<List<RemoteFileEntry>> =
        withContext(ioDispatcher) {
            if (isTreeUriPath(path)) {
                val resolved = resolveDocument(path)
                    ?: return@withContext storageFailure("Directory does not exist", path)
                if (!resolved.document.isDirectory) {
                    return@withContext storageFailure("Path is not a directory", path)
                }
                val entries = resolved.document.listFiles()
                    .map { child ->
                        child.toLocalEntry(
                            documentPath(resolved.treeUri, resolved.relativePath + child.name.orEmpty()),
                        )
                    }
                    .sortedFor(options)
                return@withContext VmResult.Success(entries)
            }
            val target = resolveLocalFile(path)
            if (!target.isDirectory) {
                if (path.startsWith("/vmhub/") || path.startsWith("/github/")) {
                    target.mkdirs()
                } else {
                    return@withContext VmResult.Failure(
                        VmError.Storage(summary = "Directory does not exist or is not accessible", reason = path),
                    )
                }
            }
            val children = target.listFiles()
                ?: return@withContext VmResult.Failure(
                    VmError.Storage(
                        summary = "Could not read directory contents",
                        reason = path,
                        suggestedAction = "Check folder permissions and try again.",
                    ),
                )
            val entries: List<RemoteFileEntry> = children
                .map { it.toLocalEntry(virtualPrefix = if (path.startsWith("/vmhub/") || path.startsWith("/github/")) path else null) }
                .sortedFor(options)
            VmResult.Success(entries)
        }

    suspend fun stat(path: String): VmResult<RemoteFileEntry> = withContext(ioDispatcher) {
        if (isTreeUriPath(path)) {
            val resolved = resolveDocument(path)
                ?: return@withContext storageFailure("File does not exist", path)
            return@withContext VmResult.Success(
                resolved.document.toLocalEntry(documentPath(resolved.treeUri, resolved.relativePath)),
            )
        }
        val file = resolveLocalFile(path)
        if (!file.exists()) {
            return@withContext VmResult.Failure(
                VmError.Storage(summary = "File does not exist", reason = path),
            )
        }
        val isVirtual = path.startsWith("/vmhub/") || path.startsWith("/github/")
        val entry = file.toLocalEntry(virtualPrefix = if (isVirtual) path.substringBeforeLast('/') else null)
        val finalEntry = if (isVirtual) entry.copy(path = path) else entry
        VmResult.Success(finalEntry)
    }

    suspend fun homeDirectory(): VmResult<String> = withContext(ioDispatcher) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val projectsDir = File(base, "projects").apply { mkdirs() }
        VmResult.Success(projectsDir.absolutePath)
    }

    suspend fun createDirectory(path: String): VmResult<Unit> = withContext(ioDispatcher) {
        if (isTreeUriPath(path)) {
            val (parentPath, name) = parentAndName(path)
                ?: return@withContext storageFailure("Invalid folder path", path)
            val parent = resolveDocument(parentPath)?.document
                ?: return@withContext storageFailure("Parent folder does not exist", parentPath)
            val existing = parent.findFile(name)
            if (existing != null) {
                return@withContext if (existing.isDirectory) {
                    VmResult.Success(Unit)
                } else {
                    storageFailure("A file already exists at this path", path)
                }
            }
            if (parent.createDirectory(name) != null) {
                return@withContext VmResult.Success(Unit)
            }
            return@withContext storageFailure("Failed to create directory", path)
        }
        val f = resolveLocalFile(path)
        if (f.mkdirs() || f.isDirectory) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to create directory", reason = path))
        }
    }

    suspend fun createFile(path: String): VmResult<Unit> = withContext(ioDispatcher) {
        if (isTreeUriPath(path)) {
            val (parentPath, name) = parentAndName(path)
                ?: return@withContext storageFailure("Invalid file path", path)
            val parent = resolveDocument(parentPath)?.document
                ?: return@withContext storageFailure("Parent folder does not exist", parentPath)
            if (parent.findFile(name) != null) {
                return@withContext storageFailure("A file already exists at this path", path)
            }
            return@withContext if (parent.createFile("application/octet-stream", name) != null) {
                VmResult.Success(Unit)
            } else {
                storageFailure("Failed to create file", path)
            }
        }
        val f = resolveLocalFile(path)
        f.parentFile?.mkdirs()
        if (f.createNewFile() || f.isFile) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to create file", reason = path))
        }
    }

    suspend fun rename(from: String, to: String): VmResult<Unit> = withContext(ioDispatcher) {
        if (isTreeUriPath(from) || isTreeUriPath(to)) {
            val source = resolveDocument(from)
                ?: return@withContext storageFailure("File does not exist", from)
            val destination = parentAndName(to)
                ?: return@withContext storageFailure("Invalid destination path", to)
            val destinationParent = resolveDocument(destination.first)?.document
                ?: return@withContext storageFailure("Destination folder does not exist", destination.first)
            val sourceParentPath = parentPath(from)
            val sourceParent = resolveDocument(sourceParentPath)?.document
                ?: return@withContext storageFailure("Source folder does not exist", sourceParentPath)
            if (sourceParent.uri != destinationParent.uri) {
                return@withContext storageFailure("Moving phone documents between folders is not supported", from)
            }
            return@withContext if (source.document.renameTo(destination.second)) {
                VmResult.Success(Unit)
            } else {
                storageFailure("Failed to rename file", from)
            }
        }
        val src = resolveLocalFile(from)
        val dst = resolveLocalFile(to)
        dst.parentFile?.mkdirs()
        if (src.renameTo(dst)) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to rename file", reason = from))
        }
    }

    suspend fun delete(path: String, recursive: Boolean): VmResult<Unit> = withContext(ioDispatcher) {
        val trimmed = path.trim()
        if (trimmed == "/" || trimmed.isEmpty()) {
            return@withContext storageFailure("Refusing to delete root directory", path)
        }
        if (isTreeUriPath(path)) {
            val (parentPath, _) = parentAndName(path)
                ?: return@withContext storageFailure("Refusing to delete root storage document", path)
            val resolved = resolveDocument(path)
                ?: return@withContext VmResult.Success(Unit)
            if (recursive && resolved.document.isDirectory) {
                if (!deleteDocumentRecursively(resolved.document)) {
                    return@withContext storageFailure("Failed to delete document", path)
                }
                return@withContext VmResult.Success(Unit)
            }
            return@withContext if (resolved.document.delete()) {
                VmResult.Success(Unit)
            } else {
                storageFailure("Failed to delete document", path)
            }
        }
        val f = resolveLocalFile(path)
        if (f.parentFile == null || f.canonicalPath == "/" || f.canonicalPath == File("/").canonicalPath) {
            return@withContext storageFailure("Refusing to delete root directory", path)
        }
        val ok = if (recursive) f.deleteRecursively() else f.delete()
        if (ok || !f.exists()) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to delete file", reason = path))
        }
    }

    suspend fun readText(path: String, maxBytes: Long): VmResult<String> = withContext(ioDispatcher) {
        if (isTreeUriPath(path)) {
            val resolved = resolveDocument(path)
                ?: return@withContext storageFailure("File does not exist", path)
            if (resolved.document.isDirectory) {
                return@withContext storageFailure("Path is a directory", path)
            }
            return@withContext try {
                val input = context.contentResolver.openInputStream(resolved.document.uri)
                    ?: return@withContext storageFailure("Could not open document", path)
                input.use {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) {
                            return@withContext storageFailure("File exceeds editor limit of $maxBytes bytes.", path)
                        }
                        output.write(buffer, 0, count)
                    }
                    VmResult.Success(decodeTextStrictly(output.toByteArray()))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailure("Failed to read document: ${e.message}", path)
            }
        }
        val file = resolveLocalFile(path)
        if (!file.exists()) {
            return@withContext VmResult.Failure(
                VmError.Storage(summary = "File does not exist", reason = path),
            )
        }
        val length = file.length()
        if (length > maxBytes) {
            return@withContext VmResult.Failure(
                VmError.Storage(
                    summary = "File exceeds editor limit of $maxBytes bytes.",
                    reason = path,
                ),
            )
        }
        try {
            val bytes = file.readBytes()
            VmResult.Success(decodeTextStrictly(bytes))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            VmResult.Failure(
                VmError.Storage(summary = "Failed to decode text: ${e.message}", reason = path),
            )
        }
    }

    suspend fun writeText(path: String, content: String): VmResult<Unit> = withContext(ioDispatcher) {
        if (isTreeUriPath(path)) {
            val resolved = resolveDocument(path)
            val document = if (resolved != null && !resolved.document.isDirectory) {
                resolved.document
            } else {
                val (parentPath, name) = parentAndName(path)
                    ?: return@withContext storageFailure("Invalid file path", path)
                val parent = resolveDocument(parentPath)?.document
                    ?: return@withContext storageFailure("Parent folder does not exist", parentPath)
                parent.findFile(name) ?: parent.createFile("application/octet-stream", name)
                    ?: return@withContext storageFailure("Could not create document", path)
            }
            return@withContext try {
                val output = context.contentResolver.openOutputStream(document.uri, "wt")
                    ?: return@withContext storageFailure("Could not write document", path)
                output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                VmResult.Success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailure("Failed to write document: ${e.message}", path)
            }
        }
        val file = resolveLocalFile(path)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        VmResult.Success(Unit)
    }

    fun download(remotePath: String, destination: File): Flow<TransferEvent> {
        val document = if (isTreeUriPath(remotePath)) resolveDocument(remotePath)?.document else null
        val source = if (document == null) resolveLocalFile(remotePath) else null
        val total = document?.length() ?: source?.length() ?: 0L
        return transfer(remotePath, total, openInput = {
            when {
                isTreeUriPath(remotePath) && document == null -> error("Source document does not exist")
                document != null && !document.isDirectory ->
                    context.contentResolver.openInputStream(document.uri)
                        ?: error("Could not open source document")
                document?.isDirectory == true -> error("Source path is a directory")
                source?.isFile == true -> source.inputStream()
                else -> error("Source file does not exist")
            }
        }, openOutput = {
            destination.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("Could not create destination folder")
            }
            destination.outputStream()
        })
    }

    fun upload(source: File, remotePath: String): Flow<TransferEvent> {
        val destination = if (isTreeUriPath(remotePath)) resolveDocument(remotePath) else null
        val fileDestination = if (destination == null) resolveLocalFile(remotePath) else null
        return transfer(remotePath, source.length(), openInput = {
            if (!source.isFile) error("Source file does not exist")
            source.inputStream()
        }, openOutput = {
            if (isTreeUriPath(remotePath) && destination == null) {
                error("Destination document does not exist")
            } else if (destination != null) {
                val target = if (destination.document.isDirectory) {
                    destination.document.findFile(source.name)
                        ?: destination.document.createFile("application/octet-stream", source.name)
                        ?: error("Could not create destination document")
                } else {
                    destination.document
                }
                context.contentResolver.openOutputStream(target.uri, "wt")
                    ?: error("Could not open destination document")
            } else {
                val target = requireNotNull(fileDestination)
                target.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) error("Could not create destination folder")
                }
                target.outputStream()
            }
        })
    }

    private fun transfer(
        path: String,
        totalBytes: Long,
        openInput: () -> InputStream,
        openOutput: () -> OutputStream,
    ): Flow<TransferEvent> = flow {
        val startedAt = System.currentTimeMillis()
        var transferred = 0L
        try {
            emit(TransferEvent.Started(totalBytes = totalBytes, resumedFromBytes = 0))
            openInput().use { input ->
                openOutput().use { output ->
                    val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                    var lastProgressAt = startedAt
                    var lastProgressBytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        transferred += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= TRANSFER_PROGRESS_INTERVAL_MILLIS) {
                            val elapsed = (now - lastProgressAt).coerceAtLeast(1)
                            emit(
                                TransferEvent.Progress(
                                    TransferProgress(
                                        transferredBytes = transferred,
                                        totalBytes = totalBytes,
                                        bytesPerSecond = (transferred - lastProgressBytes) * 1_000 / elapsed,
                                    ),
                                ),
                            )
                            lastProgressAt = now
                            lastProgressBytes = transferred
                        }
                    }
                }
            }
            emit(
                TransferEvent.Completed(
                    totalBytes = transferred,
                    durationMillis = System.currentTimeMillis() - startedAt,
                ),
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            emit(
                TransferEvent.Failed(
                    VmError.Storage(
                        summary = "Local file transfer failed",
                        reason = exception.message,
                        suggestedAction = "Check folder permissions and available storage, then retry.",
                        retryable = true,
                    ),
                    transferred,
                ),
            )
        }
    }.flowOn(ioDispatcher)

    private fun File.toLocalEntry(virtualPrefix: String? = null): RemoteFileEntry {
        val isDir = isDirectory
        val entryPath = if (virtualPrefix != null) "${virtualPrefix.trimEnd('/')}/$name" else absolutePath
        return RemoteFileEntry(
            name = name,
            path = entryPath,
            type = if (isDir) RemoteFileType.DIRECTORY else RemoteFileType.FILE,
            sizeBytes = if (isDir) 0L else length(),
            modifiedAtSeconds = lastModified() / MILLIS_PER_SECOND,
            permissions = RemotePermissions(if (isDir) DIRECTORY_PERMISSIONS else FILE_PERMISSIONS),
            uid = 0,
            gid = 0,
        )
    }

    suspend fun exists(path: String): Boolean = withContext(ioDispatcher) {
        if (isTreeUriPath(path)) resolveDocument(path) != null else resolveLocalFile(path).exists()
    }

    suspend fun canonicalize(path: String): VmResult<String> = withContext(ioDispatcher) {
        if (!isTreeUriPath(path)) {
            return@withContext try {
                if (path.startsWith("/vmhub/") || path.startsWith("/github/")) {
                    VmResult.Success(path)
                } else {
                    VmResult.Success(resolveLocalFile(path).canonicalPath)
                }
            } catch (e: Exception) {
                storageFailure("Could not resolve local path: ${e.message}", path)
            }
        }
        val resolved = resolveDocument(path)
            ?: return@withContext storageFailure("Document does not exist", path)
        VmResult.Success(documentPath(resolved.treeUri, resolved.relativePath))
    }

    private data class ResolvedDocument(
        val treeUri: Uri,
        val document: DocumentFile,
        val relativePath: List<String>,
    )

    private fun isTreeUriPath(path: String): Boolean =
        path.startsWith("content://") && path.contains("!/")

    private fun resolveDocument(path: String): ResolvedDocument? {
        if (!isTreeUriPath(path)) return null
        val separator = path.indexOf("!/")
        val treeUri = Uri.parse(path.substring(0, separator))
        var document = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val relative = path.substring(separator + 2)
            .split('/')
            .filter(String::isNotEmpty)
            .map { Uri.decode(it) }
        for (segment in relative) {
            document = document.findFile(segment) ?: return null
        }
        return ResolvedDocument(treeUri, document, relative)
    }

    private fun deleteDocumentRecursively(document: DocumentFile): Boolean {
        if (document.isDirectory && document.listFiles().any { !deleteDocumentRecursively(it) }) {
            return false
        }
        return document.delete()
    }

    private fun documentPath(treeUri: Uri, relativePath: List<String>): String =
        buildString {
            append(treeUri)
            append("!/")
            append(relativePath.joinToString("/") { Uri.encode(it) })
        }

    private fun parentPath(path: String): String {
        val separator = path.indexOf("!/")
        val relative = path.substring(separator + 2).trimEnd('/')
        val parent = relative.substringBeforeLast('/', "")
        return documentPath(
            Uri.parse(path.substring(0, separator)),
            parent.split('/').filter(String::isNotEmpty).map { Uri.decode(it) },
        )
    }

    private fun parentAndName(path: String): Pair<String, String>? {
        val separator = path.indexOf("!/")
        if (separator < 0) return null
        val relative = path.substring(separator + 2).trimEnd('/')
        val name = Uri.decode(relative.substringAfterLast('/'))
        if (name.isBlank()) return null
        return parentPath(path) to name
    }

    private fun DocumentFile.toLocalEntry(path: String): RemoteFileEntry =
        RemoteFileEntry(
            name = name.orEmpty(),
            path = path,
            type = if (isDirectory) RemoteFileType.DIRECTORY else RemoteFileType.FILE,
            sizeBytes = if (isDirectory) 0L else length(),
            modifiedAtSeconds = lastModified() / MILLIS_PER_SECOND,
            permissions = if (isDirectory) RemotePermissions.DEFAULT_DIRECTORY else RemotePermissions.DEFAULT_FILE,
            uid = 0,
            gid = 0,
        )

    private fun storageFailure(summary: String, path: String): VmResult.Failure =
        VmResult.Failure(VmError.Storage(summary = summary, reason = path))
}
