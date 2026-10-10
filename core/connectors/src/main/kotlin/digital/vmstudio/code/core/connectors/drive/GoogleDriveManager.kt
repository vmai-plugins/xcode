package digital.vmstudio.code.core.connectors.drive

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.io.File
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val remoteFileSystem: digital.vmstudio.code.core.sftp.fs.RemoteFileSystem,
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

    private suspend fun runOnServer(serverId: String, command: String) =
        commandGuard.run(serverId, command, limits = CommandLimits(), requestedByAgent = false)

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
        val localFile = File(context.cacheDir, archiveName)

        try {
            // Create archive on the VPS server
            val tarCommand = "tar -czf '$tempRemoteArchive' -C '$remotePath' --exclude='.git' --exclude='node_modules' --exclude='build' ."
            val tarResult = runOnServer(serverId, tarCommand)
            if (tarResult is VmResult.Failure) {
                return@withContext tarResult
            }

            // Stream file from VPS to Local Cache
            remoteFileSystem.download(serverId, tempRemoteArchive, localFile, resume = false).collect()

            // Upload directly to Google Drive from Local Cache
            VmLog.i(LogCategory.CONNECTOR, TAG, "Uploading $archiveName (${localFile.length()} bytes) to Google Drive")
            driveClient.uploadFile(
                accessToken = accessToken,
                filename = archiveName,
                mimeType = "application/gzip",
                file = localFile,
            )
        } catch (e: Exception) {
            VmResult.Failure(VmError.IO("Google Drive Backup Failed", e.message, e))
        } finally {
            // Always clean up Local and Remote Temp files to prevent unbounded storage leaks
            if (localFile.exists()) {
                localFile.delete()
            }
            runOnServer(serverId, "rm -f '$tempRemoteArchive'")
        }
    }

    companion object {
        private const val TAG = "GoogleDriveManager"
    }
}
