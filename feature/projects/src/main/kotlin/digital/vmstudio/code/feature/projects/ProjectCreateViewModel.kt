package digital.vmstudio.code.feature.projects

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.dao.GitRepositoryDao
import digital.vmstudio.code.core.database.entity.GitRepositoryEntity
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.command.shellQuotePath
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

enum class ProjectSource {
    GITHUB,
    LOCAL,
    GDRIVE,
    VPS,
    LINKED,
    EMPTY,
}

data class ProjectCreateUiState(
    val source: ProjectSource = ProjectSource.GITHUB,
    val name: String = "",
    val remotePath: String = "",
    val servers: List<ServerOption> = emptyList(),
    val selectedServerId: String? = null,
    val githubUrl: String = "",
    val branch: String = "main",
    val environment: ServerEnvironment = ServerEnvironment.DEVELOPMENT,
    val runtime: String = "Node.js",
    val packageManager: String = "npm",
    val colorHex: String = "#6366F1",
    val runSetup: Boolean = true,
    val setupCommand: String = "npm install",
    val statusMessage: String? = null,
    val isSaving: Boolean = false,
    val createdProjectId: String? = null,
    val nameError: String? = null,
    val pathError: String? = null,
    val githubUrlError: String? = null,
    val error: VmError? = null,
) {
    val canCreate: Boolean
        get() = name.isNotBlank() &&
            remotePath.isNotBlank() &&
            selectedServerId != null &&
            (source != ProjectSource.GITHUB || githubUrl.isNotBlank()) &&
            !isSaving
}

@HiltViewModel
class ProjectCreateViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val servers: ServerRepository,
    private val commandGuard: CommandGuard,
    private val gitRepositoryDao: GitRepositoryDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectCreateUiState())
    val uiState: StateFlow<ProjectCreateUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.get<String>(ARG_GITHUB_URL)?.takeIf { it.isNotBlank() }?.let { url ->
            setGithubUrl(url)
            setSource(ProjectSource.GITHUB)
        }
        savedStateHandle.get<String>(ARG_GITHUB_BRANCH)?.takeIf { it.isNotBlank() }?.let { branch ->
            setBranch(branch)
        }
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
                    remotePath = state.remotePath.ifBlank { defaultPath },
                )
            }
        }
    }

    fun setSource(source: ProjectSource) {
        _uiState.update { state ->
            val isLocalTarget = source == ProjectSource.LOCAL || source == ProjectSource.GDRIVE
            val targetServerId = if (isLocalTarget) {
                RemoteFileSystem.LOCAL_SERVER_ID
            } else {
                state.servers.firstOrNull { it.id != RemoteFileSystem.LOCAL_SERVER_ID }?.id
                    ?: state.selectedServerId ?: RemoteFileSystem.LOCAL_SERVER_ID
            }
            val newPath = when (source) {
                ProjectSource.LOCAL -> "/sdcard/VMStudio/Projects/${state.name.ifBlank { "my-app" }}"
                ProjectSource.GDRIVE -> "/sdcard/VMStudio/GoogleDrive/${state.name.ifBlank { "cloud-app" }}"
                ProjectSource.VPS -> "~/projects/${state.name.ifBlank { "vps-app" }}"
                ProjectSource.GITHUB -> defaultProjectPath(RemoteFileSystem.isLocalServer(targetServerId), state.name)
                else -> state.remotePath
            }
            state.copy(
                source = source,
                selectedServerId = targetServerId,
                remotePath = newPath,
                githubUrlError = null,
            )
        }
    }

    fun setColor(hex: String) {
        _uiState.update { it.copy(colorHex = hex) }
    }

    fun setName(name: String) {
        _uiState.update {
            val isLocal = RemoteFileSystem.isLocalServer(it.selectedServerId)
            val path = if (isDefaultOrBlankPath(it.remotePath)) {
                defaultProjectPath(isLocal, name)
            } else {
                it.remotePath
            }
            it.copy(name = name, nameError = null, remotePath = path)
        }
    }

    fun setRemotePath(path: String) {
        _uiState.update { it.copy(remotePath = path, pathError = null) }
    }

    fun setServerId(serverId: String) {
        _uiState.update { state ->
            val isLocal = RemoteFileSystem.isLocalServer(serverId)
            val newPath = if (isDefaultOrBlankPath(state.remotePath)) {
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

    fun setGithubUrl(url: String) {
        val repoName = deriveRepoName(url)
        _uiState.update { state ->
            val isLocal = RemoteFileSystem.isLocalServer(state.selectedServerId)
            val defaultPath = defaultProjectPath(isLocal, repoName)
            val updatedName = if (state.name.isBlank() && repoName.isNotBlank()) repoName else state.name
            val updatedPath = if (isDefaultOrBlankPath(state.remotePath) && repoName.isNotBlank()) {
                defaultPath
            } else {
                state.remotePath
            }
            state.copy(
                githubUrl = url,
                githubUrlError = null,
                name = updatedName,
                remotePath = updatedPath,
            )
        }
    }

    fun setBranch(branch: String) {
        _uiState.update { it.copy(branch = branch) }
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
                setupCommand = defaultSetup.ifBlank { it.setupCommand },
                runSetup = defaultSetup.isNotBlank(),
            )
        }
    }

    fun setPackageManager(packageManager: String) {
        _uiState.update { it.copy(packageManager = packageManager) }
    }

    fun setRunSetup(runSetup: Boolean) {
        _uiState.update { it.copy(runSetup = runSetup) }
    }

    fun setSetupCommand(command: String) {
        _uiState.update { it.copy(setupCommand = command) }
    }

    fun selectLocalFolder(uri: String, folderName: String) {
        _uiState.update { state ->
            val chosenName = if (state.name.isBlank() && folderName.isNotBlank()) folderName else state.name
            state.copy(
                selectedServerId = RemoteFileSystem.LOCAL_SERVER_ID,
                remotePath = uri,
                name = chosenName,
                pathError = null,
                nameError = null,
                error = null,
            )
        }
    }

    fun reportFolderAccessError(message: String) {
        _uiState.update {
            it.copy(
                pathError = message,
                error = VmError.FileSystem(
                    summary = "Failed to access chosen folder",
                    path = it.remotePath,
                    reason = message,
                    suggestedAction = "Please grant folder permission or choose another folder.",
                ),
            )
        }
    }

    fun create() {
        val state = _uiState.value
        val serverId = state.selectedServerId ?: return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Name cannot be empty") }
            return
        }
        if (state.remotePath.isBlank()) {
            _uiState.update { it.copy(pathError = "Remote path cannot be empty") }
            return
        }
        if (state.source == ProjectSource.GITHUB && state.githubUrl.isBlank()) {
            _uiState.update { it.copy(githubUrlError = "Repository URL cannot be empty") }
            return
        }

        viewModelScope.launch {
            executeProjectCreation(serverId)
        }
    }

    private fun creationStatusMessage(source: ProjectSource, isLocal: Boolean): String = when (source) {
        ProjectSource.GITHUB -> if (isLocal) {
            "Downloading repository from GitHub to phone..."
        } else {
            "Cloning repository..."
        }
        ProjectSource.LOCAL -> "Initializing local workspace on phone..."
        ProjectSource.GDRIVE -> "Connecting Google Drive workspace..."
        ProjectSource.VPS -> "Creating remote cloud workspace on VPS..."
        ProjectSource.LINKED -> if (isLocal) "Verifying phone folder..." else "Verifying remote directory..."
        ProjectSource.EMPTY -> if (isLocal) "Creating phone directory..." else "Creating directory..."
    }

    private suspend fun executeProjectCreation(serverId: String) {
        val state = _uiState.value
        val normalizedRepoUrl = normalizeGitHubUrl(state.githubUrl)
        val isLocal = RemoteFileSystem.isLocalServer(serverId)

        _uiState.update {
            it.copy(
                isSaving = true,
                statusMessage = creationStatusMessage(state.source, isLocal),
                nameError = null,
                pathError = null,
                githubUrlError = null,
                error = null,
            )
        }

        try {
            if (state.source == ProjectSource.GITHUB) {
                val cloneResult = cloneProject(serverId, normalizedRepoUrl, state.branch, state.remotePath)
                if (cloneResult is VmResult.Failure) {
                    _uiState.update {
                        it.copy(
                            statusMessage = null,
                            error = cloneResult.error,
                        )
                    }
                    return
                }
            } else if (state.source == ProjectSource.EMPTY || state.source == ProjectSource.LOCAL || state.source == ProjectSource.VPS || state.source == ProjectSource.GDRIVE) {
                val mkdirResult = createDirectory(serverId, state.remotePath)
                if (mkdirResult is VmResult.Failure && !isLocal) {
                    _uiState.update {
                        it.copy(
                            statusMessage = null,
                            error = mkdirResult.error,
                        )
                    }
                    return
                }
            }

            if (state.runSetup && state.setupCommand.isNotBlank() && !isLocal) {
                _uiState.update { it.copy(statusMessage = "Running ${state.setupCommand}...") }
                commandGuard.run(
                    serverId = serverId,
                    command = "cd ${escapePath(state.remotePath)} && ${state.setupCommand}",
                    limits = CommandLimits.LONG_RUNNING,
                )
            }

            _uiState.update { it.copy(statusMessage = "Saving workspace configuration...") }
            when (val result = projects.create(
                name = state.name.trim(),
                serverId = serverId,
                remotePath = state.remotePath.trim(),
                branch = state.branch.takeIf { it.isNotBlank() },
                environment = state.environment,
                runtime = state.runtime,
                packageManager = state.packageManager,
            )) {
                is VmResult.Success -> {
                    val createdId = result.value
                    if (state.colorHex.isNotBlank()) {
                        projects.updateColor(createdId, state.colorHex)
                    }
                    if (state.source == ProjectSource.GITHUB && state.githubUrl.isNotBlank()) {
                        gitRepositoryDao.upsert(
                            GitRepositoryEntity(
                                id = UUID.randomUUID().toString(),
                                projectId = createdId,
                                workingTreePath = state.remotePath.trim(),
                                remoteName = "origin",
                                remoteUrl = normalizedRepoUrl,
                                defaultBranch = state.branch,
                                currentBranch = state.branch,
                            ),
                        )
                    }
                    _uiState.update { it.copy(statusMessage = null, createdProjectId = createdId) }
                }
                is VmResult.Failure -> {
                    _uiState.update { it.copy(statusMessage = null, error = result.error) }
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _uiState.update {
                it.copy(
                    statusMessage = null,
                    error = VmError.Unexpected(summary = "Failed to create project: ${t.message ?: "Unknown error"}"),
                )
            }
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private suspend fun cloneProject(
        serverId: String,
        repoUrl: String,
        branch: String,
        remotePath: String,
    ): VmResult<Unit> {
        val isLocal = RemoteFileSystem.isLocalServer(serverId)
        if (isLocal) {
            return downloadAndExtractZip(repoUrl, branch, File(remotePath))
        }
        val branchArg = if (branch.isNotBlank()) "-b ${escapePath(branch)}" else ""
        val cloneCommand = "git clone $branchArg ${escapePath(repoUrl)} ${escapePath(remotePath)}"
        val result = commandGuard.run(
            serverId = serverId,
            command = cloneCommand,
            limits = CommandLimits.LONG_RUNNING,
        )
        return when (result) {
            is VmResult.Success -> {
                if (result.value.isSuccess) {
                    VmResult.Success(Unit)
                } else {
                    VmResult.Failure(
                        VmError.Command(
                            summary = "Failed to clone repository",
                            command = cloneCommand,
                            exitCode = result.value.exitCode,
                            stderrExcerpt = result.value.stderr,
                            suggestedAction = "Check the repository URL and your SSH permissions.",
                        ),
                    )
                }
            }
            is VmResult.Failure -> result
        }
    }

    private suspend fun downloadAndExtractZip(
        repoUrl: String,
        branch: String,
        targetDir: File,
    ): VmResult<Unit> = withContext(Dispatchers.IO) {
        try {
            targetDir.mkdirs()
            val coordinates = extractGitHubRepoCoordinates(repoUrl)
                ?: return@withContext VmResult.Success(Unit)
            val owner = coordinates.first
            val repo = coordinates.second
            val effectiveBranch = branch.ifBlank { "main" }
            val zipUrlString = "https://github.com/$owner/$repo/archive/refs/heads/$effectiveBranch.zip"

            val url = URL(zipUrlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "x-codes-mobile")

            var finalStream: InputStream? = null
            if (conn.responseCode in 300..399) {
                val redirectUrl = conn.getHeaderField("Location")
                if (redirectUrl != null) {
                    val redirectUri = runCatching { java.net.URI(redirectUrl) }.getOrNull()
                    if (redirectUri != null && isTrustedGithubArchiveRedirect(redirectUri)) {
                        val conn2 = URL(redirectUrl).openConnection() as HttpURLConnection
                        conn2.connectTimeout = 15000
                        conn2.readTimeout = 60000
                        conn2.setRequestProperty("User-Agent", "x-codes-mobile")
                        if (conn2.responseCode == 200) {
                            finalStream = conn2.inputStream
                        }
                    }
                }
            } else if (conn.responseCode == 200) {
                finalStream = conn.inputStream
            }

            if (finalStream != null) {
                extractZip(finalStream, targetDir)
                VmResult.Success(Unit)
            } else {
                VmResult.Failure(
                    VmError.Network(
                        summary = "Failed to download repository archive",
                        reason = "GitHub returned HTTP ${conn.responseCode}. The repository may be private or the branch does not exist.",
                        suggestedAction = "Ensure the repository is public and the branch exists.",
                    ),
                )
            }
        } catch (e: Exception) {
            targetDir.mkdirs()
            VmResult.Failure(
                VmError.Network(
                    summary = "Failed to download repository from GitHub",
                    reason = e.message,
                    cause = e,
                ),
            )
        }
    }

    private fun extractZip(inputStream: InputStream, targetDir: File) {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            var rootDirPrefix: String? = null
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (rootDirPrefix == null) {
                    rootDirPrefix = if (entry.isDirectory) {
                        name
                    } else if ('/' in name) {
                        name.substringBefore('/') + '/'
                    } else {
                        null
                    }
                }
                val relativePath = if (rootDirPrefix != null && name.startsWith(rootDirPrefix)) {
                    name.removePrefix(rootDirPrefix)
                } else {
                    name
                }
                if (relativePath.isNotEmpty()) {
                    val outFile = safeZipEntryFile(targetDir, relativePath)
                    if (outFile != null) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private suspend fun createDirectory(serverId: String, remotePath: String): VmResult<Unit> {
        val isLocal = RemoteFileSystem.isLocalServer(serverId)
        if (isLocal) {
            return try {
                File(remotePath).mkdirs()
                VmResult.Success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                VmResult.Failure(
                    VmError.Command(
                        summary = "Failed to create directory",
                        command = "mkdir -p $remotePath",
                        suggestedAction = "Check storage permissions: ${e.message}",
                    )
                )
            }
        }
        val result = commandGuard.run(
            serverId = serverId,
            command = "mkdir -p ${escapePath(remotePath)}",
            limits = CommandLimits.QUICK,
        )
        return when (result) {
            is VmResult.Success -> {
                if (result.value.isSuccess) {
                    VmResult.Success(Unit)
                } else {
                    VmResult.Failure(
                        VmError.Command(
                            summary = "Failed to create directory",
                            command = "mkdir -p $remotePath",
                            exitCode = result.value.exitCode,
                            stderrExcerpt = result.value.stderr,
                            suggestedAction = "Check write permissions for the directory path.",
                        ),
                    )
                }
            }
            is VmResult.Failure -> result
        }
    }
    private fun escapePath(path: String): String = shellQuotePath(path)

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

    private fun isDefaultOrBlankPath(path: String): Boolean =
        path.isBlank() || path.startsWith("~/projects/") || path.startsWith("/sdcard/VMStudio/")

    private fun defaultProjectPath(isLocal: Boolean, name: String): String {
        val safeName = name.ifBlank { "my-project" }
        return if (isLocal) {
            "/sdcard/VMStudio/Projects/$safeName"
        } else {
            "~/projects/$safeName"
        }
    }

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
        const val ARG_GITHUB_URL = "githubUrl"
        const val ARG_GITHUB_BRANCH = "githubBranch"
        const val ARG_MODE = "mode"
        const val MODE_LINK = "link"
    }
}

internal fun safeZipEntryFile(targetDir: File, entryPath: String): File? {
    if (entryPath.startsWith("/") || entryPath.contains("\\") || entryPath.contains(":")) {
        return null
    }
    val canonicalTarget = targetDir.canonicalFile
    val candidate = File(targetDir, entryPath).canonicalFile
    val targetPath = canonicalTarget.path
    val expectedPrefix = if (targetPath.endsWith(File.separator)) targetPath else targetPath + File.separator
    return if (candidate.path.startsWith(expectedPrefix) || candidate == canonicalTarget) {
        candidate
    } else {
        null
    }
}

internal fun isTrustedGithubArchiveRedirect(uri: java.net.URI): Boolean {
    if (uri.scheme != "https") return false
    val host = uri.host ?: return false
    if (!host.equals("codeload.github.com", ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    val port = uri.port
    return port == -1 || port == 443
}

internal fun extractGitHubRepoCoordinates(repoUrl: String): Pair<String, String>? {
    val cleanUrl = repoUrl.trim().removeSuffix(".git").removeSuffix("/")
    val parts = cleanUrl.split("/").takeLast(2)
    if (parts.size < 2) return null
    val owner = parts[0].substringAfterLast(":")
    val repo = parts[1]
    if (owner.isBlank() || repo.isBlank()) return null
    return Pair(owner, repo)
}






