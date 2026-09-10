package digital.vmstudio.code.core.sftp.fs

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteListingOptions
import digital.vmstudio.code.core.sftp.model.RemotePermissions
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Progress of a byte-oriented operation. */
data class TransferProgress(
    val transferredBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
) {
    /** Null when the total is unknown, which the UI renders as indeterminate. */
    val fraction: Float?
        get() = if (totalBytes > 0) (transferredBytes.toDouble() / totalBytes).toFloat() else null

    val remainingMillis: Long?
        get() = if (totalBytes > 0 && bytesPerSecond > 0) {
            (totalBytes - transferredBytes) * 1_000 / bytesPerSecond
        } else {
            null
        }
}

/**
 * A remote filesystem reached over SFTP.
 *
 * Every method takes a `serverId` rather than holding a connection, so callers do
 * not manage channel lifetimes and the connection manager stays the single owner of
 * the transport.
 *
 * Byte-oriented operations stream to and from local files and never materialise
 * whole contents in memory: a phone browsing a server with multi-gigabyte logs must
 * not be one tap away from an OOM.
 */
interface RemoteFileSystem {

    suspend fun list(
        serverId: String,
        path: String,
        options: RemoteListingOptions = RemoteListingOptions(),
    ): VmResult<List<RemoteFileEntry>>

    suspend fun stat(serverId: String, path: String): VmResult<RemoteFileEntry>

    suspend fun exists(serverId: String, path: String): VmResult<Boolean>

    /** Resolves symlinks and `..` on the server, returning an absolute path. */
    suspend fun canonicalize(serverId: String, path: String): VmResult<String>

    /** The user's home directory, used as the initial browse location. */
    suspend fun homeDirectory(serverId: String): VmResult<String>

    suspend fun createDirectory(serverId: String, path: String): VmResult<Unit>

    suspend fun createFile(serverId: String, path: String): VmResult<Unit>

    suspend fun rename(serverId: String, from: String, to: String): VmResult<Unit>

    /**
     * Deletes a file, or a directory and everything under it.
     *
     * Recursion happens client-side rather than via `rm -rf` so it works on hosts
     * with a restricted shell, and so each removal is individually reportable.
     */
    suspend fun delete(serverId: String, path: String, recursive: Boolean = false): VmResult<Unit>

    suspend fun setPermissions(
        serverId: String,
        path: String,
        permissions: RemotePermissions,
    ): VmResult<Unit>

    /**
     * Reads a text file whose size is within [maxBytes].
     *
     * Bounded on purpose: opening a 2 GB log in the editor is never the user's
     * intent, and refusing with a clear message beats an out-of-memory crash.
     */
    suspend fun readText(
        serverId: String,
        path: String,
        maxBytes: Long = DEFAULT_MAX_TEXT_BYTES,
    ): VmResult<String>

    suspend fun writeText(serverId: String, path: String, content: String): VmResult<Unit>

    /**
     * Downloads to [destination], resuming from its current length when
     * [resume] is true and the remote file is at least that long.
     */
    fun download(
        serverId: String,
        remotePath: String,
        destination: File,
        resume: Boolean = true,
    ): Flow<TransferEvent>

    fun upload(
        serverId: String,
        source: File,
        remotePath: String,
        resume: Boolean = true,
    ): Flow<TransferEvent>

    companion object {
        const val DEFAULT_MAX_TEXT_BYTES: Long = 8L * 1024 * 1024
    }
}

/** Emitted while a transfer runs. */
sealed interface TransferEvent {

    data class Started(val totalBytes: Long, val resumedFromBytes: Long) : TransferEvent

    data class Progress(val progress: TransferProgress) : TransferEvent

    data class Completed(val totalBytes: Long, val durationMillis: Long) : TransferEvent

    data class Failed(
        val error: digital.vmstudio.code.core.common.error.VmError,
        /** How far it got, so a retry can resume rather than restart. */
        val transferredBytes: Long,
    ) : TransferEvent
}
