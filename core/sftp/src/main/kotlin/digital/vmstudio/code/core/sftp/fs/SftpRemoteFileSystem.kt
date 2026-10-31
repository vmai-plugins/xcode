package digital.vmstudio.code.core.sftp.fs

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemoteListingOptions
import digital.vmstudio.code.core.sftp.model.RemotePath
import digital.vmstudio.code.core.sftp.model.RemotePermissions
import digital.vmstudio.code.core.sftp.model.sortedFor
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.connection.SshSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.sftp.Response
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.io.RandomAccessFile
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SftpRemoteFileSystem @Inject constructor(
    private val connectionManager: SshConnectionManager,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RemoteFileSystem {

    private val localFs = LocalFileSystemDelegate(context, ioDispatcher)

    private fun isLocalOrVirtual(serverId: String?, path: String): Boolean =
        RemoteFileSystem.isLocalServer(serverId) ||
            path.startsWith("/vmhub/") ||
            path.startsWith("/github/") ||
            path.startsWith("content://")

    private fun safeMkdirs(sftp: SFTPClient, path: String) {
        val clean = RemotePath.normalise(path)
        val segments = clean.split('/').filter { it.isNotEmpty() }
        var current = if (clean.startsWith("/")) "/" else ""
        for (segment in segments) {
            current = if (current == "/" || current.isEmpty()) current + segment else "$current/$segment"
            if (segment.matches(Regex("^[a-zA-Z]:$"))) continue
            if (sftp.statExistence(current) == null) {
                runCatching { sftp.mkdir(current) }
            }
        }
    }

    private fun expandPath(sftp: SFTPClient, path: String): String {
        val trimmed = path.trim().replace('\\', '/')
        if (trimmed == "~" || trimmed.startsWith("~/")) {
            val home = runCatching { sftp.canonicalize(".") }.getOrNull()?.trimEnd('/') ?: ""
            if (home.isNotBlank()) {
                val remainder = if (trimmed == "~") "" else trimmed.removePrefix("~")
                return RemotePath.normalise(home + remainder)
            }
        }
        return RemotePath.normalise(trimmed)
    }

    override suspend fun list(
        serverId: String,
        path: String,
        options: RemoteListingOptions,
    ): VmResult<List<RemoteFileEntry>> {
        if (isLocalOrVirtual(serverId, path)) return localFs.list(path, options)
        return withSftp(serverId, path) { sftp ->
            val normalised = expandPath(sftp, path)
            sftp.ls(normalised)
                .map { it.toEntry() }
                .sortedFor(options)
        }
    }

    override suspend fun stat(serverId: String, path: String): VmResult<RemoteFileEntry> {
        if (isLocalOrVirtual(serverId, path)) return localFs.stat(path)
        return withSftp(serverId, path) { sftp ->
            val normalised = expandPath(sftp, path)
            val attributes = sftp.stat(normalised)
            RemoteFileEntry(
                name = RemotePath.name(normalised),
                path = normalised,
                type = attributes.type.toRemoteType(),
                sizeBytes = attributes.size,
                modifiedAtSeconds = attributes.mtime,
                permissions = RemotePermissions(attributes.mode.permissionsMask),
                uid = attributes.uid,
                gid = attributes.gid,
            )
        }
    }

    override suspend fun exists(serverId: String, path: String): VmResult<Boolean> {
        if (isLocalOrVirtual(serverId, path)) {
            return VmResult.Success(localFs.exists(path))
        }
        return withSftp(serverId, path) { sftp ->
            sftp.statExistence(expandPath(sftp, path)) != null
        }
    }

    override suspend fun canonicalize(serverId: String, path: String): VmResult<String> {
        if (isLocalOrVirtual(serverId, path)) {
            return localFs.canonicalize(path)
        }
        return withSftp(serverId, path) { sftp ->
            val canon = sftp.canonicalize(expandPath(sftp, path))
            RemotePath.normalise(canon)
        }
    }

    override suspend fun homeDirectory(serverId: String): VmResult<String> {
        if (RemoteFileSystem.isLocalServer(serverId)) return localFs.homeDirectory()
        return withSftp(serverId, ".") { sftp ->
            val canon = sftp.canonicalize(".")
            RemotePath.normalise(canon)
        }
    }

    override suspend fun createDirectory(serverId: String, path: String): VmResult<Unit> {
        if (isLocalOrVirtual(serverId, path)) return localFs.createDirectory(path)
        return withSftp(serverId, path) { sftp ->
            val normalised = expandPath(sftp, path)
            safeMkdirs(sftp, normalised)
            VmLog.i(LogCategory.SFTP, TAG, "Created directory $path")
        }
    }

    override suspend fun createFile(serverId: String, path: String): VmResult<Unit> {
        if (isLocalOrVirtual(serverId, path)) return localFs.createFile(path)
        return withSftp(serverId, path) { sftp ->
            val normalised = expandPath(sftp, path)
            val parent = RemotePath.parent(normalised)
            if (!parent.isNullOrBlank() && parent != "/" && !parent.matches(Regex("^/?[a-zA-Z]:$"))) {
                safeMkdirs(sftp, parent)
            }
            if (sftp.statExistence(normalised) != null) {
                error("A file already exists at $normalised")
            }
            // CREAT with EXCL so a race with another client fails rather than
            // silently truncating a file created a moment earlier.
            sftp.open(normalised, setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)).close()
            VmLog.i(LogCategory.SFTP, TAG, "Created file $path")
        }
    }

    override suspend fun rename(serverId: String, from: String, to: String): VmResult<Unit> {
        if (isLocalOrVirtual(serverId, from) || isLocalOrVirtual(serverId, to)) return localFs.rename(from, to)
        return withSftp(serverId, from) { sftp ->
            sftp.rename(expandPath(sftp, from), expandPath(sftp, to))
            VmLog.i(LogCategory.SFTP, TAG, "Renamed $from to $to")
        }
    }

    override suspend fun delete(
        serverId: String,
        path: String,
        recursive: Boolean,
    ): VmResult<Unit> {
        if (isLocalOrVirtual(serverId, path)) return localFs.delete(path, recursive)
        return withSftp(serverId, path) { sftp ->
            val normalised = expandPath(sftp, path)
            if (normalised == "/" || normalised.isBlank() || normalised.matches(Regex("^/?[a-zA-Z]:$"))) {
                error("Refusing to delete root directory: $path")
            }
            val attributes = sftp.lstat(normalised)
            if (attributes.type == FileMode.Type.DIRECTORY) {
                if (!recursive) {
                    sftp.rmdir(normalised)
                } else {
                    deleteRecursively(sftp, normalised)
                }
            } else {
                sftp.rm(normalised)
            }
            VmLog.i(LogCategory.SFTP, TAG, "Deleted $path (recursive=$recursive)")
        }
    }

    override suspend fun setPermissions(
        serverId: String,
        path: String,
        permissions: RemotePermissions,
    ): VmResult<Unit> {
        if (isLocalOrVirtual(serverId, path)) return VmResult.Success(Unit)
        return withSftp(serverId, path) { sftp ->
            sftp.chmod(expandPath(sftp, path), permissions.mode)
        }
    }

    override suspend fun readText(
        serverId: String,
        path: String,
        maxBytes: Long,
    ): VmResult<String> {
        if (isLocalOrVirtual(serverId, path)) return localFs.readText(path, maxBytes)
        return withSftp(serverId, path) { sftp ->
            val normalised = expandPath(sftp, path)
            sftp.open(normalised, setOf(OpenMode.READ)).use { remote ->
                val length = remote.length()
                if (length > maxBytes) {
                    error(
                        "This file is ${formatBytes(length)}, above the " +
                            "${formatBytes(maxBytes)} editor limit.",
                    )
                }
                if (length > 0L) {
                    val buffer = ByteArray(length.toInt().coerceAtLeast(0))
                    var offset = 0
                    while (offset < buffer.size) {
                        val read = remote.read(offset.toLong(), buffer, offset, buffer.size - offset)
                        if (read <= 0) break
                        offset += read
                    }
                    decodeTextStrictly(buffer, offset)
                } else {
                    // For dynamic Linux files (/proc, /sys) that report length 0, read in chunks.
                    val out = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(CHUNK_BYTES)
                    var offset = 0L
                    while (offset < maxBytes) {
                        val read = remote.read(offset, chunk, 0, minOf(chunk.size.toLong(), maxBytes - offset).toInt())
                        if (read <= 0) break
                        out.write(chunk, 0, read)
                        offset += read
                    }
                    val bytes = out.toByteArray()
                    decodeTextStrictly(bytes, bytes.size)
                }
            }
        }
    }

    override suspend fun writeText(
        serverId: String,
        path: String,
        content: String,
    ): VmResult<Unit> {
        if (isLocalOrVirtual(serverId, path)) return localFs.writeText(path, content)
        return withSftp(serverId, path) { sftp ->
            val normalised = expandPath(sftp, path)
            val parent = RemotePath.parent(normalised)
            if (!parent.isNullOrBlank() && parent != "/" && !parent.matches(Regex("^/?[a-zA-Z]:$"))) {
                safeMkdirs(sftp, parent)
            }
            val bytes = content.toByteArray(Charsets.UTF_8)
            // TRUNC so a shorter replacement does not leave the tail of the old file.
            val modes = setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            sftp.open(normalised, modes).use { remote ->
                var offset = 0
                while (offset < bytes.size) {
                    val chunk = minOf(CHUNK_BYTES, bytes.size - offset)
                    remote.write(offset.toLong(), bytes, offset, chunk)
                    offset += chunk
                }
            }
            VmLog.i(LogCategory.SFTP, TAG, "Wrote ${bytes.size} bytes to $path")
        }
    }

    override fun download(
        serverId: String,
        remotePath: String,
        destination: File,
        resume: Boolean,
    ): Flow<TransferEvent> {
        if (isLocalOrVirtual(serverId, remotePath)) return localFs.download(remotePath, destination)
        return flow {
        val startedAt = System.currentTimeMillis()
        var transferred = 0L

        val session = when (val result = connectionManager.session(serverId)) {
            is VmResult.Failure -> {
                emit(TransferEvent.Failed(result.error, 0))
                return@flow
            }
            is VmResult.Success -> result.value
        }

        val sftp = when (val result = session.openSftp()) {
            is VmResult.Failure -> {
                emit(TransferEvent.Failed(result.error, 0))
                return@flow
            }
            is VmResult.Success -> result.value
        }

        try {
            val normalised = expandPath(sftp, remotePath)
            sftp.open(normalised, setOf(OpenMode.READ)).use { remote ->
                val total = remote.length()
                destination.parentFile?.mkdirs()

                // Resume only when the partial file is genuinely a prefix of a
                // longer remote file; if the remote shrank, the local partial is
                // stale and restarting is the correct behaviour.
                val existing = if (destination.exists()) destination.length() else 0L
                if (resume && existing >= total && total > 0) {
                    emit(TransferEvent.Started(totalBytes = total, resumedFromBytes = total))
                    emit(TransferEvent.Completed(totalBytes = total, transferredBytes = total))
                    return@flow
                }
                val startOffset = if (resume && existing in 1 until total) existing else 0L
                transferred = startOffset

                emit(TransferEvent.Started(totalBytes = total, resumedFromBytes = startOffset))

                RandomAccessFile(destination, "rw").use { file ->
                    file.setLength(startOffset)
                    file.seek(startOffset)

                    val buffer = ByteArray(CHUNK_BYTES)
                    var lastEmit = System.currentTimeMillis()
                    var lastBytes = transferred

                    while (transferred < total) {
                        currentCoroutineContext().ensureActive()
                        val read = remote.read(transferred, buffer, 0, buffer.size)
                        if (read <= 0) break
                        file.write(buffer, 0, read)
                        transferred += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= PROGRESS_INTERVAL_MILLIS) {
                            val elapsed = (now - lastEmit).coerceAtLeast(1)
                            emit(
                                TransferEvent.Progress(
                                    TransferProgress(
                                        transferredBytes = transferred,
                                        totalBytes = total,
                                        bytesPerSecond = (transferred - lastBytes) * 1_000 / elapsed,
                                    ),
                                ),
                            )
                            lastEmit = now
                            lastBytes = transferred
                        }
                    }
                }
                emitCompletionOrTruncated(remotePath, transferred, total, startedAt)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            emit(TransferEvent.Failed(mapSftpError(throwable, remotePath), transferred))
        } finally {
            runCatching { sftp.close() }
        }
    }.flowOn(ioDispatcher)
    }

    override fun upload(
        serverId: String,
        source: File,
        remotePath: String,
        resume: Boolean,
    ): Flow<TransferEvent> {
        if (isLocalOrVirtual(serverId, remotePath)) return localFs.upload(source, remotePath)
        return flow {
        val startedAt = System.currentTimeMillis()
        var transferred = 0L

        val session = when (val result = connectionManager.session(serverId)) {
            is VmResult.Failure -> {
                emit(TransferEvent.Failed(result.error, 0))
                return@flow
            }
            is VmResult.Success -> result.value
        }

        val sftp = when (val result = session.openSftp()) {
            is VmResult.Failure -> {
                emit(TransferEvent.Failed(result.error, 0))
                return@flow
            }
            is VmResult.Success -> result.value
        }

        try {
            val normalised = expandPath(sftp, remotePath)
            val total = source.length()

            val alreadyThere = sftp.statExistence(normalised)?.size ?: 0L
            // Upper bound is inclusive: a remote file already exactly the right size
            // (a previous upload that completed but wasn't recorded locally) should
            // short-circuit to done rather than retransmitting the whole file, which
            // "1 until total" excluded by construction.
            val startOffset = if (resume && alreadyThere in 1..total) alreadyThere else 0L
            transferred = startOffset

            val modes = if (startOffset > 0) {
                setOf(OpenMode.WRITE, OpenMode.CREAT)
            } else {
                setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            }

            emit(TransferEvent.Started(totalBytes = total, resumedFromBytes = startOffset))

            val parent = RemotePath.parent(normalised)
            if (!parent.isNullOrBlank() && parent != "/" && !parent.matches(Regex("^/?[a-zA-Z]:$"))) {
                safeMkdirs(sftp, parent)
            }

            sftp.open(normalised, modes).use { remote ->
                RandomAccessFile(source, "r").use { file ->
                    file.seek(startOffset)
                    val buffer = ByteArray(CHUNK_BYTES)
                    var lastEmit = System.currentTimeMillis()
                    var lastBytes = transferred

                    while (transferred < total) {
                        currentCoroutineContext().ensureActive()
                        val read = file.read(buffer)
                        if (read <= 0) break
                        remote.write(transferred, buffer, 0, read)
                        transferred += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= PROGRESS_INTERVAL_MILLIS) {
                            val elapsed = (now - lastEmit).coerceAtLeast(1)
                            emit(
                                TransferEvent.Progress(
                                    TransferProgress(
                                        transferredBytes = transferred,
                                        totalBytes = total,
                                        bytesPerSecond = (transferred - lastBytes) * 1_000 / elapsed,
                                    ),
                                ),
                            )
                            lastEmit = now
                            lastBytes = transferred
                        }
                    }
                }
            }
            emitCompletionOrTruncated(remotePath, transferred, total, startedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            emit(TransferEvent.Failed(mapSftpError(throwable, remotePath), transferred))
        } finally {
            runCatching { sftp.close() }
        }
    }.flowOn(ioDispatcher)
    }

    /**
     * The read/write loop exits as soon as a single read returns no bytes, which is
     * also what a stalled channel or a server closing the connection early looks
     * like - not just a genuinely finished transfer. Reporting [TransferEvent.Completed]
     * unconditionally there let a truncated transfer look identical to a full one,
     * with no signal to the user and nothing to make a retry resume rather than
     * silently accept the short file as correct.
     */
    private suspend fun FlowCollector<TransferEvent>.emitCompletionOrTruncated(
        path: String,
        transferred: Long,
        total: Long,
        startedAt: Long,
    ) {
        if (transferred < total) {
            emit(
                TransferEvent.Failed(
                    VmError.FileSystem(
                        summary = "Transfer stopped early",
                        reason = "Only $transferred of $total bytes were transferred " +
                            "before the connection stopped sending data.",
                        suggestedAction = "Retry the transfer; it will resume from where it left off.",
                        retryable = true,
                        path = path,
                    ),
                    transferred,
                ),
            )
        } else {
            emit(
                TransferEvent.Completed(
                    totalBytes = transferred,
                    durationMillis = System.currentTimeMillis() - startedAt,
                ),
            )
        }
    }

    // --- internals -------------------------------------------------------------

    private val clientLocks = ConcurrentHashMap<String, Mutex>()
    private val cachedClients = ConcurrentHashMap<String, SFTPClient>()

    private fun clientLockFor(serverId: String): Mutex =
        clientLocks.computeIfAbsent(serverId) { Mutex() }

    /**
     * Executes a metadata/file operation using a pooled SFTP client.
     *
     * Caches the SFTP channel per server to eliminate repeated channel handshakes,
     * which otherwise exhaust sshd MaxSessions on high-frequency agent tool walks.
     * If an operation fails, the channel is invalidated and cleanly recreated.
     */
    private suspend fun <T> withSftp(
        serverId: String,
        path: String,
        block: suspend (SFTPClient) -> T,
    ): VmResult<T> = withContext(ioDispatcher) {
        val mutex = clientLockFor(serverId)
        mutex.withLock {
            connectionManager.session(serverId).flatMap { session: SshSession ->
                val cached = cachedClients[serverId]
                val sftp = if (cached != null) {
                    cached
                } else {
                    when (val openRes = session.openSftp()) {
                        is VmResult.Success -> {
                            cachedClients[serverId] = openRes.value
                            openRes.value
                        }
                        is VmResult.Failure -> return@flatMap openRes
                    }
                }

                try {
                    VmResult.Success(block(sftp))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    cachedClients.remove(serverId)
                    runCatching { sftp.close() }
                    VmResult.Failure(mapSftpError(throwable, path))
                }
            }
        }
    }

    private fun deleteRecursively(sftp: SFTPClient, path: String) {
        if (path == "/" || path.isBlank()) error("Refusing to recursively delete root directory.")
        sftp.ls(path)
            .filterNot { it.name == "." || it.name == ".." }
            .forEach { entry ->
                // `ls` returns lstat-style attributes, so a symlink to a directory
                // reports as SYMLINK, not DIRECTORY. Recurse only into real
                // directories; everything else (files, symlinks) is unlinked, which
                // for a symlink removes the link and never touches its target.
                when (entry.attributes.type) {
                    FileMode.Type.DIRECTORY -> deleteRecursively(sftp, entry.path)
                    else -> sftp.rm(entry.path)
                }
            }
        sftp.rmdir(path)
    }

    private fun mapSftpError(throwable: Throwable, path: String): VmError {
        if (throwable is SFTPException) {
            return when (throwable.statusCode) {
                Response.StatusCode.NO_SUCH_FILE -> VmError.FileSystem(
                    summary = "File not found",
                    reason = "$path does not exist on the server.",
                    suggestedAction = "Refresh the listing; it may have been moved or deleted.",
                    path = path,
                    cause = throwable,
                )

                Response.StatusCode.PERMISSION_DENIED -> VmError.FileSystem(
                    summary = "Permission denied",
                    reason = "The account does not have access to $path.",
                    suggestedAction = "Check the file's owner and mode, or connect as a user " +
                        "with the necessary rights.",
                    path = path,
                    cause = throwable,
                )

                Response.StatusCode.NO_SPACE_ON_FILESYSTEM -> VmError.FileSystem(
                    summary = "No space left on the server",
                    reason = "The remote filesystem is full.",
                    suggestedAction = "Free space on the host and retry.",
                    retryable = true,
                    path = path,
                    cause = throwable,
                )

                Response.StatusCode.QUOTA_EXCEEDED -> VmError.FileSystem(
                    summary = "Disk quota exceeded",
                    reason = "The account's quota on the server is full.",
                    suggestedAction = "Free space or ask for a larger quota.",
                    path = path,
                    cause = throwable,
                )

                Response.StatusCode.FILE_ALREADY_EXISTS -> VmError.FileSystem(
                    summary = "Already exists",
                    reason = "$path is already present on the server.",
                    suggestedAction = "Choose a different name.",
                    path = path,
                    cause = throwable,
                )

                else -> VmError.FileSystem(
                    summary = "File operation failed",
                    reason = throwable.message,
                    suggestedAction = "Retry; if it persists, check the server's SFTP logs.",
                    retryable = true,
                    path = path,
                    cause = throwable,
                )
            }
        }

        return VmError.FileSystem(
            summary = "File operation failed",
            reason = throwable.message,
            suggestedAction = "Check the connection and retry.",
            retryable = true,
            path = path,
            cause = throwable,
        )
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
        bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
        else -> "$bytes bytes"
    }

    private companion object {
        const val TAG = "SftpRemoteFileSystem"

        /**
         * 32 KiB balances throughput against responsiveness: larger chunks stall
         * cancellation, smaller ones waste round trips on a high-latency link.
         */
        const val CHUNK_BYTES = 32 * 1024
        const val PROGRESS_INTERVAL_MILLIS = 250L
    }
}

private fun RemoteResourceInfo.toEntry(): RemoteFileEntry {
    val attributes = attributes
    return RemoteFileEntry(
        name = name,
        path = path,
        type = attributes.type.toRemoteType(),
        sizeBytes = attributes.size,
        modifiedAtSeconds = attributes.mtime,
        permissions = RemotePermissions(attributes.mode.permissionsMask),
        uid = attributes.uid,
        gid = attributes.gid,
    )
}

private fun FileMode.Type.toRemoteType(): RemoteFileType = when (this) {
    FileMode.Type.REGULAR -> RemoteFileType.FILE
    FileMode.Type.DIRECTORY -> RemoteFileType.DIRECTORY
    FileMode.Type.SYMLINK -> RemoteFileType.SYMLINK
    FileMode.Type.BLOCK_SPECIAL,
    FileMode.Type.CHAR_SPECIAL,
    FileMode.Type.FIFO_SPECIAL,
    FileMode.Type.SOCKET_SPECIAL,
    -> RemoteFileType.SPECIAL
    else -> RemoteFileType.UNKNOWN
}

/**
 * Text that is not valid UTF-8 (Latin-1 configs, binaries) is refused instead of
 * decoded with replacement characters, because saving that text back would
 * silently rewrite every such byte on the server.
 */
internal fun decodeTextStrictly(bytes: ByteArray, length: Int = bytes.size): String {
    if ((0 until length).any { bytes[it] == 0.toByte() }) {
        error("This looks like a binary file; it cannot be opened as text.")
    }
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, 0, length))
            .toString()
    } catch (notUtf8: CharacterCodingException) {
        error("This file is not UTF-8 text; editing it here would corrupt it (${notUtf8.message}).")
    }
}
