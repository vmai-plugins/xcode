package digital.vmstudio.code.core.connectors.drive

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates Google Drive cloud backups, exports, and sync for VPS projects.
 */
@Singleton
class GoogleDriveManager @Inject constructor(
    private val driveClient: GoogleDriveClient,
    private val commandGuard: CommandGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun listBackups(accessToken: String): VmResult<List<GoogleDriveFile>> = withContext(ioDispatcher) {
        driveClient.listFiles(
            accessToken = accessToken,
            query = "name contains 'backup' or mimeType = 'application/gzip' or mimeType = 'application/zip'",
        )
    }

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
        val cleanName = projectName.lowercase().replace("\\s+".toRegex(), "-")
        val archiveName = "$cleanName-backup-$timestamp.tar.gz"
        val tempRemoteArchive = "/tmp/$archiveName"

        // Create archive on the VPS server
        val tarCommand = "tar -czf '$tempRemoteArchive' -C '$remotePath' --exclude='.git' --exclude='node_modules' --exclude='build' ."
        val tarResult = commandGuard.run(serverId, tarCommand, limits = CommandLimits(), requestedByAgent = false)
        if (tarResult is VmResult.Failure) {
            return@withContext tarResult
        }

        // Read base64 content of the archive from VPS
        val base64Command = "base64 -w 0 '$tempRemoteArchive'"
        val base64Output = when (val base64Result = commandGuard.run(serverId, base64Command, limits = CommandLimits(), requestedByAgent = false)) {
            is VmResult.Success -> base64Result.value.stdout.trim()
            is VmResult.Failure -> return@withContext base64Result
        }

        val archiveBytes = try {
            android.util.Base64.decode(base64Output, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            return@withContext VmResult.Failure(
                VmError.FileSystem(
                    summary = "Failed to decode archive payload from server",
                    reason = e.message,
                    path = tempRemoteArchive,
                ),
            )
        }

        // Clean up temp archive on server
        commandGuard.run(serverId, "rm -f '$tempRemoteArchive'", limits = CommandLimits(), requestedByAgent = false)

        // Upload to Google Drive
        VmLog.i(LogCategory.CONNECTOR, TAG, "Uploading $archiveName (${archiveBytes.size} bytes) to Google Drive")
        driveClient.uploadFile(
            accessToken = accessToken,
            filename = archiveName,
            mimeType = "application/gzip",
            content = archiveBytes,
        )
    }

    companion object {
        private const val TAG = "GoogleDriveManager"
    }
}
