package digital.vmstudio.code.feature.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.ai.model.ImageAttachment
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.fs.TransferEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** What the chat needs from staged attachments: where they landed and inline images. */
data class StagedAttachments(
    val remotePaths: List<String>,
    val images: List<ImageAttachment>,
)

/**
 * Puts files the user attached where the agent can reach them.
 *
 * Every file is uploaded to `.xcodes/attachments` in the working directory, so
 * Claude Code and the gateway's file tools can both open it by path. Images small
 * enough to inline are also returned as base64 for vision-capable gateway models.
 */
class AttachmentStager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteFileSystem: RemoteFileSystem,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun stage(
        serverId: String,
        workingDirectory: String,
        uris: List<Uri>,
    ): VmResult<StagedAttachments> {
        val root = workingDirectory.trimEnd('/')
        val remoteDir = "$root/$ATTACHMENT_DIR"
        ensureDirectory(serverId, remoteDir)?.let { return VmResult.Failure(it) }
        keepOutOfGit(serverId, root)

        val paths = mutableListOf<String>()
        val images = mutableListOf<ImageAttachment>()
        for (uri in uris) {
            val local = when (val copied = copyToCache(uri)) {
                is VmResult.Success -> copied.value
                is VmResult.Failure -> return copied
            }
            try {
                val remotePath = "$remoteDir/${System.currentTimeMillis()}-${local.name}"
                upload(serverId, local, remotePath)?.let { return VmResult.Failure(it) }
                paths += remotePath
                inlineImage(uri, local)?.let(images::add)
            } finally {
                local.delete()
            }
        }
        return VmResult.Success(StagedAttachments(paths, images))
    }

    private suspend fun ensureDirectory(serverId: String, path: String): VmError? {
        val exists = remoteFileSystem.exists(serverId, path)
        if (exists is VmResult.Success && exists.value) return null
        // Parents first: `.xcodes` may not exist yet either.
        remoteFileSystem.createDirectory(serverId, path.substringBeforeLast('/'))
        return (remoteFileSystem.createDirectory(serverId, path) as? VmResult.Failure)?.error
    }

    /**
     * Adds `.xcodes/` to the repository's local exclude file, so attachments never
     * show in `git status` or get committed. Local to this clone, unlike .gitignore,
     * so the project's own files are untouched. Best effort: no repo, no change.
     */
    private suspend fun keepOutOfGit(serverId: String, root: String) {
        val exclude = "$root/.git/info/exclude"
        val gitDir = remoteFileSystem.exists(serverId, "$root/.git")
        if (gitDir !is VmResult.Success || !gitDir.value) return
        val current = (remoteFileSystem.readText(serverId, exclude) as? VmResult.Success)?.value.orEmpty()
        if (current.lineSequence().any { it.trim() == GIT_EXCLUDE_LINE }) return
        remoteFileSystem.createDirectory(serverId, "$root/.git/info")
        val separator = if (current.isEmpty() || current.endsWith("\n")) "" else "\n"
        remoteFileSystem.writeText(serverId, exclude, current + separator + GIT_EXCLUDE_LINE + "\n")
    }

    private suspend fun upload(serverId: String, file: File, remotePath: String): VmError? =
        when (val last = remoteFileSystem.upload(serverId, file, remotePath, resume = false).last()) {
            is TransferEvent.Failed -> last.error
            else -> null
        }

    private suspend fun copyToCache(uri: Uri): VmResult<File> = withContext(ioDispatcher) {
        val name = displayName(uri).replace(UNSAFE_NAME_CHARS, "_").ifBlank { "attachment" }
        val target = File(context.cacheDir, "attach-${System.nanoTime()}-$name")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext unreadable(name, "The file could not be opened.")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            if (target.length() > MAX_ATTACHMENT_BYTES) {
                target.delete()
                return@withContext unreadable(name, "Files over 20 MB cannot be attached.")
            }
            VmResult.Success(File(target.parentFile, name).also { target.renameTo(it) })
        } catch (io: IOException) {
            target.delete()
            unreadable(name, io.message)
        }
    }

    private fun inlineImage(uri: Uri, file: File): ImageAttachment? {
        val mediaType = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
        if (mediaType == null || file.length() > MAX_INLINE_IMAGE_BYTES) return null
        return ImageAttachment(mediaType, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

    private fun unreadable(name: String, reason: String?): VmResult<File> = VmResult.Failure(
        VmError.FileSystem(summary = "Could not attach $name", reason = reason, path = name),
    )

    private companion object {
        const val ATTACHMENT_DIR = ".xcodes/attachments"
        const val GIT_EXCLUDE_LINE = ".xcodes/"
        const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
        const val MAX_INLINE_IMAGE_BYTES = 4L * 1024 * 1024
        val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
