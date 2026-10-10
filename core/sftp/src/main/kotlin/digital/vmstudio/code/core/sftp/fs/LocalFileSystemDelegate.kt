package digital.vmstudio.code.core.sftp.fs

import android.content.Context
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemoteListingOptions
import digital.vmstudio.code.core.sftp.model.RemotePermissions
import digital.vmstudio.code.core.sftp.model.sortedFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

private const val DIRECTORY_PERMISSIONS = 0b111101101
private const val FILE_PERMISSIONS = 0b110100100
private const val MILLIS_PER_SECOND = 1000L
private const val LOCAL_TRANSFER_DURATION_MS = 10L

internal class LocalFileSystemDelegate(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun list(path: String, options: RemoteListingOptions): VmResult<List<RemoteFileEntry>> =
        withContext(ioDispatcher) {
            val target = File(path)
            if (!target.exists()) {
                return@withContext VmResult.Failure(
                    VmError.Storage(summary = "Directory does not exist", reason = path),
                )
            }
            val entries: List<RemoteFileEntry> = target.listFiles()
                ?.map { it.toLocalEntry() }
                ?.sortedFor(options)
                ?: emptyList()
            VmResult.Success(entries)
        }

    suspend fun stat(path: String): VmResult<RemoteFileEntry> = withContext(ioDispatcher) {
        val file = File(path)
        if (!file.exists()) {
            return@withContext VmResult.Failure(
                VmError.Storage(summary = "File does not exist", reason = path),
            )
        }
        VmResult.Success(file.toLocalEntry())
    }

    suspend fun homeDirectory(): VmResult<String> = withContext(ioDispatcher) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val projectsDir = File(base, "projects").apply { mkdirs() }
        VmResult.Success(projectsDir.absolutePath)
    }

    suspend fun createDirectory(path: String): VmResult<Unit> = withContext(ioDispatcher) {
        val f = File(path)
        if (f.mkdirs() || f.isDirectory) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to create directory", reason = path))
        }
    }

    suspend fun createFile(path: String): VmResult<Unit> = withContext(ioDispatcher) {
        val f = File(path)
        f.parentFile?.mkdirs()
        if (f.createNewFile()) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to create file", reason = path))
        }
    }

    suspend fun rename(from: String, to: String): VmResult<Unit> = withContext(ioDispatcher) {
        val src = File(from)
        val dst = File(to)
        dst.parentFile?.mkdirs()
        if (src.renameTo(dst)) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to rename file", reason = from))
        }
    }

    suspend fun delete(path: String, recursive: Boolean): VmResult<Unit> = withContext(ioDispatcher) {
        val f = File(path)
        val ok = if (recursive) f.deleteRecursively() else f.delete()
        if (ok || !f.exists()) {
            VmResult.Success(Unit)
        } else {
            VmResult.Failure(VmError.Storage(summary = "Failed to delete file", reason = path))
        }
    }

    suspend fun readText(path: String, maxBytes: Long): VmResult<String> = withContext(ioDispatcher) {
        val file = File(path)
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
            VmResult.Success(bytes.decodeToString())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            VmResult.Failure(
                VmError.Storage(summary = "Failed to decode text: ${e.message}", reason = path),
            )
        }
    }

    suspend fun writeText(path: String, content: String): VmResult<Unit> = withContext(ioDispatcher) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        VmResult.Success(Unit)
    }

    fun download(remotePath: String, destination: File): Flow<TransferEvent> = flow {
        val src = File(remotePath)
        val total = src.length()
        emit(TransferEvent.Started(totalBytes = total, resumedFromBytes = 0))
        destination.parentFile?.mkdirs()
        src.copyTo(destination, overwrite = true)
        val completed = TransferEvent.Completed(
            totalBytes = total,
            durationMillis = LOCAL_TRANSFER_DURATION_MS,
        )
        emit(completed)
    }.flowOn(ioDispatcher)

    fun upload(source: File, remotePath: String): Flow<TransferEvent> = flow {
        val dst = File(remotePath)
        val total = source.length()
        emit(TransferEvent.Started(totalBytes = total, resumedFromBytes = 0))
        dst.parentFile?.mkdirs()
        source.copyTo(dst, overwrite = true)
        val completed = TransferEvent.Completed(
            totalBytes = total,
            durationMillis = LOCAL_TRANSFER_DURATION_MS,
        )
        emit(completed)
    }.flowOn(ioDispatcher)

    private fun File.toLocalEntry(): RemoteFileEntry {
        val isDir = isDirectory
        return RemoteFileEntry(
            name = name,
            path = absolutePath,
            type = if (isDir) RemoteFileType.DIRECTORY else RemoteFileType.FILE,
            sizeBytes = if (isDir) 0L else length(),
            modifiedAtSeconds = lastModified() / MILLIS_PER_SECOND,
            permissions = RemotePermissions(if (isDir) DIRECTORY_PERMISSIONS else FILE_PERMISSIONS),
            uid = 0,
            gid = 0,
        )
    }
}

