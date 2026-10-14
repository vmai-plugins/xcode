package digital.vmstudio.code.core.connectors.drive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.fs.TransferEvent
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.command.shellQuotePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates Google Drive cloud backups, exports, and sync for VPS projects.
 */
@Singleton
class GoogleDriveManager @Inject constructor(
    private val driveClient: GoogleDriveClient,
    @ApplicationContext private val context: Context,
    private val remoteFileSystem: RemoteFileSystem,
    private val commandGuard: CommandGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun listBackups(accessToken: String): VmResult<List<GoogleDriveFile>> =
        withContext(ioDispatcher) {
            driveClient.listFiles(
                accessToken = accessToken,
                query = "name contains 'backup' or mimeType = 'application/gzip' or mimeType = 'application/zip'",
            )
        }

    private suspend fun runOnServer(
        serverId: String,
        command: String,
        limits: CommandLimits = CommandLimits(),
    ) = commandGuard.run(serverId, command, limits = limits, requestedByAgent = false)

    /**
     * Creates a compressed tarball archive of [remotePath] on [serverId] and uploads it to Google Drive.
     */
    suspend fun backupProject(
        serverId: String,
        remotePath: String,
        projectName: String,
        accessToken: String,
    ): VmResult<GoogleDriveFile> = withContext(ioDispatcher) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val cleanName = projectName.lowercase()
            .replace(UNSAFE_NAME_CHARS, "-")
            .trim('-', '.', '_')
            .ifBlank { "project" }
        val archiveName = "$cleanName-backup-$timestamp-${UUID.randomUUID()}.tar.gz"
        val tempRemoteArchive = "/tmp/$archiveName"
        val localFile = File(context.cacheDir, archiveName)

        val operationResult = try {
            val tarCommand = "tar -czf ${shellQuotePath(tempRemoteArchive)} -C ${shellQuotePath(remotePath)} " +
                "--exclude='.git' --exclude='node_modules' --exclude='build' ."
            when (val tarResult = runOnServer(serverId, tarCommand, CommandLimits.LONG_RUNNING)) {
                is VmResult.Failure -> tarResult
                is VmResult.Success -> if (!tarResult.value.isSuccess && tarResult.value.exitCode != 1) {
                    VmResult.Failure(
                        VmError.Command(
                            summary = "Could not create the project backup archive",
                            reason = tarResult.value.stderr.ifBlank { "tar exited unsuccessfully." },
                            suggestedAction = "Check that the project folder is accessible and has enough disk space.",
                            command = "tar <project files>",
                            exitCode = tarResult.value.exitCode,
                            stderrExcerpt = tarResult.value.stderr,
                        ),
                    )
                } else {
                    val transfer = remoteFileSystem.download(
                        serverId,
                        tempRemoteArchive,
                        localFile,
                        resume = false,
                    ).lastOrNull()
                    when (transfer) {
                        is TransferEvent.Failed -> VmResult.Failure(transfer.error)
                        is TransferEvent.Completed -> if (localFile.isFile && localFile.length() > 0L) {
                            VmLog.i(
                                LogCategory.CONNECTOR,
                                TAG,
                                "Uploading $archiveName (${localFile.length()} bytes) to Google Drive",
                            )
                            driveClient.uploadFile(
                                accessToken = accessToken,
                                filename = archiveName,
                                mimeType = "application/gzip",
                                file = localFile,
                            )
                        } else {
                            VmResult.Failure(
                                VmError.FileSystem(
                                    summary = "The project backup archive is empty",
                                    path = tempRemoteArchive,
                                ),
                            )
                        }
                        else -> VmResult.Failure(
                            VmError.FileSystem(
                                summary = "The project archive download did not complete",
                                reason = "The transfer ended without a completion event.",
                                retryable = true,
                                path = tempRemoteArchive,
                            ),
                        )
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            val cleanupError = withContext(NonCancellable) {
                cleanupBackup(serverId, tempRemoteArchive, localFile)
            }
            cleanupError?.let {
                VmLog.e(LogCategory.CONNECTOR, TAG, "Backup cancellation cleanup failed: ${it.reason}", it.cause)
            }
            throw cancellation
        } catch (exception: Exception) {
            VmResult.Failure(
                VmError.FileSystem(
                    summary = "Google Drive backup failed",
                    reason = exception.message,
                    cause = exception,
                ),
            )
        }

        val cleanupError = withContext(NonCancellable) {
            cleanupBackup(serverId, tempRemoteArchive, localFile)
        }
        if (cleanupError != null) {
            if (operationResult is VmResult.Success) {
                VmLog.w(
                    LogCategory.CONNECTOR,
                    TAG,
                    "Backup uploaded successfully to Google Drive, but temporary file cleanup reported: ${cleanupError.summary} - ${cleanupError.reason}",
                )
                return@withContext operationResult
            }
            val operationError = (operationResult as? VmResult.Failure)?.error
            return@withContext VmResult.Failure(
                VmError.Ssh(
                    summary = "Google Drive backup and cleanup failed",
                    reason = listOfNotNull(operationError?.summary, operationError?.reason, cleanupError.summary, cleanupError.reason)
                        .joinToString(" "),
                    suggestedAction = "Inspect and remove temporary backup files on the VPS and phone.",
                    cause = cleanupError.cause,
                ),
            )
        }
        operationResult
    }

    private suspend fun cleanupBackup(serverId: String, remotePath: String, localFile: File): VmError? {
        val failures = mutableListOf<String>()
        try {
            if (localFile.exists() && !localFile.delete()) {
                failures += "Could not remove the temporary phone archive."
            }
        } catch (exception: Exception) {
            failures += "Could not remove the temporary phone archive: ${exception.message}"
        }
        try {
            when (val exists = remoteFileSystem.exists(serverId, remotePath)) {
                is VmResult.Failure -> failures += exists.error.summary
                is VmResult.Success -> if (exists.value) {
                    when (val deleted = remoteFileSystem.delete(serverId, remotePath)) {
                        is VmResult.Failure -> failures += deleted.error.summary
                        is VmResult.Success -> Unit
                    }
                }
            }
        } catch (exception: Exception) {
            failures += "Could not remove the temporary VPS archive: ${exception.message}"
        }
        return failures.takeIf { it.isNotEmpty() }?.let { problems ->
            VmError.FileSystem(
                summary = "Could not remove temporary backup files",
                reason = problems.joinToString("; "),
                suggestedAction = "Inspect the temporary archive paths and remove any files that remain.",
                path = remotePath,
            )
        }
    }

    companion object {
        private const val TAG = "GoogleDriveManager"
        private val UNSAFE_NAME_CHARS = Regex("[^a-z0-9._-]")
    }
}