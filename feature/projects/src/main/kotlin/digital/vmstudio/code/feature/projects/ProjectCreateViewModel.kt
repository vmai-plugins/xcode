package digital.vmstudio.code.feature.projects

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.fs.TransferEvent
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemotePath
import digital.vmstudio.code.core.sftp.model.RemotePermissions
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.util.zip.ZipInputStream
import java.util.UUID
import javax.inject.Inject

enum class ProjectSource {
    LOCAL,
    VPS,
    LINKED,
    EMPTY,
}

private const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024
private const val MAX_EXPANDED_BYTES = 500L * 1024 * 1024
private const val MAX_ARCHIVE_ENTRIES = 50_000

data class ProjectCreateUiState(
    val source: ProjectSource = ProjectSource.LOCAL,
    val name: String = "",
    val remotePath: String = "",
    val isRemotePathManuallySet: Boolean = false,
    val servers: List<ServerOption> = emptyList(),
    val selectedServerId: String? = null,
    val environment: ServerEnvironment = ServerEnvironment.DEVELOPMENT,
    val runtime: String = "Node.js",
    val packageManager: String = "npm",
    val colorHex: String = "#6366F1",
    val statusMessage: String? = null,
    val isSaving: Boolean = false,
    val createdProjectId: String? = null,
    val nameError: String? = null,
    val pathError: String? = null,
    val error: VmError? = null,
) {
    val canCreate: Boolean
        get() = name.isNotBlank() &&
            remotePath.isNotBlank() &&
            selectedServerId != null && !isSaving
}

@HiltViewModel
class ProjectCreateViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val servers: ServerRepository,
    private val commandGuard: CommandGuard,
    private val remoteFileSystem: RemoteFileSystem,
    private val githubRepositoryClient: GitHubRepositoryClient,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectCreateUiState())
    val uiState: StateFlow<ProjectCreateUiState> = _uiState.asStateFlow()

    init {
        if (savedStateHandle.get<String>(ARG_MODE) == MODE_LINK) {
            setSource(ProjectSource.LINKED)
        }
        viewModelScope.launch {
            val list = servers.servers.first().toServerOptions()
            val localOption = ServerOption(
                id = RemoteFileSystem.LOCAL_SERVER_ID,
                name = "This Device (Local Storage)",
                target = "Local Phone Storage",
            )
            val allServers = listOf(localOption) + list
            _uiState.update { state ->
                val chosenServerId = state.selectedServerId ?: RemoteFileSystem.LOCAL_SERVER_ID
                val isLocal = chosenServerId == RemoteFileSystem.LOCAL_SERVER_ID
                val defaultPath = defaultProjectPath(isLocal, state.name)
                state.copy(
                    servers = allServers,
                    selectedServerId = chosenServerId,
                    remotePath = if (!state.isRemotePathManuallySet) {
                        defaultPath
                    } else {
                        state.remotePath
                    },
                )
            }
        }
    }

    fun selectLocalFolder(treeUri: String, folderName: String) {
        _uiState.update { state ->
            state.copy(
                source = ProjectSource.LINKED,
                selectedServerId = RemoteFileSystem.LOCAL_SERVER_ID,
                remotePath = "$treeUri!/",
                isRemotePathManuallySet = true,
                name = state.name.ifBlank { folderName },
                error = null,
                pathError = null,
            )
        }
    }

    fun reportFolderAccessError(reason: String) {
        _uiState.update {
            it.copy(
                error = VmError.Storage(
                    summary = "Could not access the selected folder",
                    reason = reason,
                    suggestedAction = "Choose the folder again and grant read/write access.",
                ),
            )
        }
    }

    fun setSource(source: ProjectSource) {
        _uiState.update { state ->
            val targetServerId = when (source) {
                ProjectSource.LOCAL -> RemoteFileSystem.LOCAL_SERVER_ID
                ProjectSource.LINKED -> state.selectedServerId ?: RemoteFileSystem.LOCAL_SERVER_ID
                ProjectSource.VPS -> state.servers.firstOrNull { it.id != RemoteFileSystem.LOCAL_SERVER_ID }?.id
                    ?: state.selectedServerId ?: RemoteFileSystem.LOCAL_SERVER_ID
                ProjectSource.EMPTY -> state.selectedServerId ?: RemoteFileSystem.LOCAL_SERVER_ID
            }
            val newPath = when (source) {
                ProjectSource.LOCAL -> defaultProjectPath(true, state.name)
                ProjectSource.VPS -> "~/projects/${state.name.ifBlank { "vps-app" }}"
                ProjectSource.EMPTY -> defaultProjectPath(RemoteFileSystem.isLocalServer(targetServerId), state.name)
                ProjectSource.LINKED -> state.remotePath
            }
            state.copy(
                source = source,
                selectedServerId = targetServerId,
                remotePath = newPath,
                isRemotePathManuallySet = when (source) {
                    ProjectSource.LOCAL, ProjectSource.VPS, ProjectSource.EMPTY -> false
                    ProjectSource.LINKED -> state.isRemotePathManuallySet
                },
            )
        }
    }

    fun setColor(hex: String) {
        _uiState.update { it.copy(colorHex = hex) }
    }

    fun setName(name: String) {
        _uiState.update {
            val isLocal = RemoteFileSystem.isLocalServer(it.selectedServerId)
            val path = if (!it.isRemotePathManuallySet) {
                defaultProjectPath(isLocal, name)
            } else {
                it.remotePath
            }
            it.copy(name = name, nameError = null, remotePath = path)
        }
    }

    fun setRemotePath(path: String) {
        _uiState.update {
            it.copy(
                remotePath = path,
                isRemotePathManuallySet = true,
                pathError = null,
            )
        }
    }

    fun setServerId(serverId: String) {
        _uiState.update { state ->
            val isLocal = RemoteFileSystem.isLocalServer(serverId)
            val newPath = if (!state.isRemotePathManuallySet) {
                defaultProjectPath(isLocal, state.name)
            } else {
                state.remotePath
            }
            state.copy(selectedServerId = serverId, remotePath = newPath)
        }
    }

    fun selectServer(serverId: String) {
        setServerId(serverId)
    }

    fun setEnvironment(env: ServerEnvironment) {
        _uiState.update { it.copy(environment = env) }
    }

    fun setRuntime(runtime: String) {
        val defaultSetup = defaultSetupCommandFor(runtime)
        _uiState.update {
            it.copy(
                runtime = runtime,
                packageManager = when (runtime) {
                    "Node.js" -> "npm"
                    "Python" -> "pip"
                    "Go" -> "go"
                    "Rust" -> "cargo"
                    "Java" -> "gradle"
                    "Flutter" -> "flutter"
                    else -> it.packageManager
                },
            )
        }
    }

    fun create() {
        val state = _uiState.value
        if (state.isSaving) return
        val serverId = state.selectedServerId ?: return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Name cannot be empty") }
            return
        }
        if (state.remotePath.isBlank()) {
            _uiState.update { it.copy(pathError = "Remote path cannot be empty") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                executeProjectCreation(serverId, state)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = null,
                        error = VmError.FileSystem(
                            summary = "Could not create workspace",
                            reason = exception.message,
                            retryable = true,
                        ),
                    )
                }
            }
        }
    }

    private fun creationStatusMessage(source: ProjectSource, isLocal: Boolean): String = when (source) {
        ProjectSource.LOCAL -> "Initializing local workspace on phone..."
        ProjectSource.VPS -> "Creating remote cloud workspace on VPS..."
        ProjectSource.LINKED -> if (isLocal) "Verifying phone folder..." else "Verifying remote directory..."
        ProjectSource.EMPTY -> if (isLocal) "Creating phone directory..." else "Creating directory..."
    }

    private suspend fun executeProjectCreation(serverId: String, state: ProjectCreateUiState) {
        val isLocal = RemoteFileSystem.isLocalServer(serverId)
        var newWorkspaceCreated = false

        _uiState.update {
            it.copy(
                isSaving = true,
                statusMessage = creationStatusMessage(state.source, isLocal),
                nameError = null,
                pathError = null,
                error = null,
            )
        }

        if (state.source == ProjectSource.EMPTY ||
            state.source == ProjectSource.LOCAL ||
            state.source == ProjectSource.VPS
        ) {
            val targetExisted = when (val exists = remoteFileSystem.exists(serverId, state.remotePath)) {
                is VmResult.Failure -> {
                    _uiState.update {
                        it.copy(isSaving = false, statusMessage = null, error = exists.error)
                    }
                    return
                }
                is VmResult.Success -> exists.value
            }
            val mkdirResult = createDirectory(serverId, state.remotePath)
            if (mkdirResult is VmResult.Failure) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = null,
                        error = mkdirResult.error,
                    )
                }
                return
            }
            newWorkspaceCreated = !targetExisted
        } else if (state.source == ProjectSource.LINKED) {
            when (val statResult = remoteFileSystem.stat(serverId, state.remotePath)) {
                is VmResult.Failure -> {
                    _uiState.update {
                        it.copy(isSaving = false, statusMessage = null, error = statResult.error)
                    }
                    return
                }
                is VmResult.Success -> if (statResult.value.type != RemoteFileType.DIRECTORY) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = null,
                            error = VmError.FileSystem(
                                summary = "The selected workspace is not a folder",
                                path = state.remotePath,
                            ),
                        )
                    }
                    return
                }
            }
        }

        _uiState.update { it.copy(statusMessage = "Saving workspace configuration...") }
        when (val result = projects.create(
            name = state.name.trim(),
            serverId = serverId,
            remotePath = state.remotePath.trim(),
            branch = null,
            environment = state.environment,
            runtime = state.runtime,
            packageManager = state.packageManager,
        )) {
            is VmResult.Success -> {
                val createdId = result.value
                var configurationError: VmError? = null
                if (state.colorHex.isNotBlank()) {
                    when (val colorResult = projects.updateColor(createdId, state.colorHex)) {
                        is VmResult.Failure -> if (configurationError == null) {
                            configurationError = colorResult.error
                        }
                        is VmResult.Success -> Unit
                    }
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = null,
                        createdProjectId = createdId,
                        error = configurationError,
                    )
                }
            }
            is VmResult.Failure -> {
                val error = if (newWorkspaceCreated) {
                    when (val cleanup = remoteFileSystem.delete(serverId, state.remotePath, recursive = true)) {
                        is VmResult.Success -> result.error
                        is VmResult.Failure -> VmError.FileSystem(
                            summary = "Workspace record could not be saved, and its new folder could not be removed",
                            reason = "${result.error.summary}. Cleanup failed: ${cleanup.error.summary}",
                            suggestedAction = "Retry cleanup or remove the unlinked folder manually.",
                            retryable = true,
                            path = state.remotePath,
                            cause = cleanup.error.cause,
                        )
                    }
                } else {
                    result.error
                }
                _uiState.update { it.copy(isSaving = false, statusMessage = null, error = error) }
            }
        }
    }

    private suspend fun createDirectory(serverId: String, remotePath: String): VmResult<Unit> {
        return remoteFileSystem.createDirectory(serverId, remotePath)
    }
    private fun escapePath(path: String): String =
        "'" + path.replace("'", "'\\''") + "'"

    private fun normalizeGitHubUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.matches(Regex("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+$")) ->
                "https://github.com/$trimmed.git"
            trimmed.startsWith("github.com/") ->
                "https://$trimmed"
            else -> trimmed
        }
    }

    private fun defaultProjectPath(isLocal: Boolean, name: String): String {
        val safeName = name.ifBlank { "my-project" }.replace(UNSAFE_PROJECT_NAME, "_")
        return if (isLocal) {
            File(localProjectsRoot(), safeName).absolutePath
        } else {
            "~/projects/$safeName"
        }
    }

    private fun localProjectsRoot(): File =
        context.getExternalFilesDir("projects") ?: File(context.filesDir, "projects")

    private fun deriveRepoName(url: String): String {
        val cleaned = url.trim().removeSuffix("/").removeSuffix(".git")
        return cleaned.substringAfterLast("/").substringAfterLast(":")
    }

    private fun defaultSetupCommandFor(runtime: String): String = when (runtime) {
        "Node.js" -> "npm install"
        "Python" -> "pip install -r requirements.txt"
        "Go" -> "go mod download"
        "Rust" -> "cargo build"
        "Java" -> "./gradlew build"
        "Flutter" -> "flutter pub get"
        else -> ""
    }

    companion object {
        private val REPOSITORY_SEGMENT = Regex("[A-Za-z0-9_.-]+")
        private val UNSAFE_PROJECT_NAME = Regex("[/\\\\:*?\"<>|]")
        const val ARG_MODE = "mode"
        const val MODE_LINK = "link"
    }
}

internal fun safeZipEntryFile(targetDir: File, entryName: String): File? {
    if (entryName.isBlank() || entryName.startsWith('/') || '\\' in entryName || ':' in entryName || '\u0000' in entryName) {
        return null
    }
    val segments = entryName.split('/')
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
    val root = targetDir.canonicalFile
    val output = File(root, entryName).canonicalFile
    return output.takeIf { it.path.startsWith(root.path + File.separator) }
}

internal fun isTrustedGithubArchiveRedirect(uri: URI): Boolean =
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("codeload.github.com", ignoreCase = true) &&
        uri.rawUserInfo == null &&
        (uri.port == -1 || uri.port == 443)

private class CappedInputStream(
    input: InputStream,
    private val maxBytes: Long,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) recordBytes(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytes = super.read(buffer, offset, length)
        if (bytes > 0) recordBytes(bytes.toLong())
        return bytes
    }

    private fun recordBytes(bytes: Long) {
        bytesRead += bytes
        if (bytesRead > maxBytes) throw IOException("The repository archive exceeds the ${maxBytes / (1024 * 1024)} MB import limit.")
    }
}
