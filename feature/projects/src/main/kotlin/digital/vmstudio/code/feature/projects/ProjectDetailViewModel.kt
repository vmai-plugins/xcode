package digital.vmstudio.code.feature.projects

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.GitRepositoryDao
import digital.vmstudio.code.core.database.entity.ActivityCategory
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.database.entity.TaskPriority
import digital.vmstudio.code.core.database.repository.ActivityRepository
import digital.vmstudio.code.core.git.GitResult
import digital.vmstudio.code.core.git.GitService
import digital.vmstudio.code.core.git.GitStatus
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.project.ProjectRepository
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.fs.TransferEvent
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import digital.vmstudio.code.core.update.ApkInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

data class ApkArtifact(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAtSeconds: Long,
    val isRemote: Boolean = true,
    val downloadUrl: String? = null,
)

data class ProjectDetailUiState(
    val project: Project? = null,
    val server: Server? = null,
    val tasks: List<AgentTaskEntity> = emptyList(),
    val isSyncing: Boolean = false,
    val gitStatus: GitStatus? = null,
    val isGitLoading: Boolean = false,
    val error: VmError? = null,
    val isAndroidProject: Boolean = false,
    val isBuildingApk: Boolean = false,
    val buildStatusMessage: String? = null,
    val apkArtifacts: List<ApkArtifact> = emptyList(),
    val isDownloadingApk: Boolean = false,
    val notificationMessage: String? = null,
)

@HiltViewModel
@Suppress("LongParameterList")
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val agentTaskDao: AgentTaskDao,
    private val gitRepositoryDao: GitRepositoryDao,
    private val serverRepository: ServerRepository,
    private val commandGuard: CommandGuard,
    private val gitService: GitService,
    private val activityRepository: ActivityRepository,
    private val remoteFileSystem: RemoteFileSystem,
    private val apkInstaller: ApkInstaller,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val project = MutableStateFlow<Project?>(null)
    private val server = MutableStateFlow<Server?>(null)
    private val isSyncing = MutableStateFlow(false)
    private val isGitLoading = MutableStateFlow(false)
    private val gitStatus = MutableStateFlow<GitStatus?>(null)
    private val error = MutableStateFlow<VmError?>(null)
    private val isAndroidProject = MutableStateFlow(false)
    private val isBuildingApk = MutableStateFlow(false)
    private val buildStatusMessage = MutableStateFlow<String?>(null)
    private val apkArtifacts = MutableStateFlow<List<ApkArtifact>>(emptyList())
    private val isDownloadingApk = MutableStateFlow(false)
    private val notificationMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        combine(project, server, agentTaskDao.observeForProject(projectId)) { proj, srv, taskList ->
            Triple(proj, srv, taskList)
        },
        combine(isSyncing, gitStatus, isGitLoading, error) { syncing, status, loading, err ->
            GitCombinedState(syncing, status, loading, err)
        },
        combine(isAndroidProject, isBuildingApk, buildStatusMessage) { android, building, msg ->
            Triple(android, building, msg)
        },
        combine(apkArtifacts, isDownloadingApk, notificationMessage) { artifacts, downloading, note ->
            Triple(artifacts, downloading, note)
        },
    ) { (proj, srv, taskList), gitState, androidInfo, artifactInfo ->
        ProjectDetailUiState(
            project = proj,
            server = srv,
            tasks = taskList,
            isSyncing = gitState.syncing,
            gitStatus = gitState.status,
            isGitLoading = gitState.loading,
            error = gitState.error,
            isAndroidProject = androidInfo.first,
            isBuildingApk = androidInfo.second,
            buildStatusMessage = androidInfo.third,
            apkArtifacts = artifactInfo.first,
            isDownloadingApk = artifactInfo.second,
            notificationMessage = artifactInfo.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectDetailUiState(),
    )

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val proj = projectRepository.get(projectId)
            project.value = proj
            val srvId = proj?.serverId
            if (srvId != null && proj.isRemote) {
                when (val result = serverRepository.get(srvId)) {
                    is VmResult.Success -> server.value = result.value
                    is VmResult.Failure -> server.value = null
                }
                loadGitStatus(srvId, proj.remotePath)
            } else if (proj != null && proj.remotePath.startsWith("/github/")) {
                server.value = Server(
                    id = "github",
                    name = "GitHub Zero-Clone Virtual Repo",
                    host = "api.github.com",
                    port = 443,
                    username = "git",
                    authMethod = SshAuthMethod.PRIVATE_KEY,
                )
            } else if (proj != null && proj.remotePath.startsWith("/vmhub/")) {
                server.value = Server(
                    id = "vmhub",
                    name = "VM Project Hub (Universal Workspace)",
                    host = "vmstudio.digital",
                    port = 443,
                    username = "hub",
                    authMethod = SshAuthMethod.PRIVATE_KEY,
                )
            } else if (proj != null && proj.isLocal) {
                server.value = Server(
                    id = RemoteFileSystem.LOCAL_SERVER_ID,
                    name = "This Device (Local Storage)",
                    host = "localhost",
                    port = 0,
                    username = "device",
                    authMethod = SshAuthMethod.PASSWORD,
                )
            }
            if (proj != null) {
                checkIfAndroidAndScan(proj)
            }
        }
    }

    fun showTerminalNotice() {
        notificationMessage.value = "Terminal requires an active VPS server. Connect a VPS in Connectors or use the AI Agent."
    }

    private suspend fun fileExistsOnServer(srvId: String, path: String): Boolean {
        val res = remoteFileSystem.exists(srvId, path)
        return res is VmResult.Success && res.value
    }

    private fun checkIfAndroidAndScan(proj: Project) {
        if (proj.remotePath.startsWith("/github/")) {
            isAndroidProject.value = proj.projectTypeId.contains("android", ignoreCase = true) ||
                proj.projectTypeId.contains("kotlin", ignoreCase = true)
            return
        }
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        viewModelScope.launch {
            val isGradle = fileExistsOnServer(srvId, "${proj.remotePath}/build.gradle")
            val isGradleKts = fileExistsOnServer(srvId, "${proj.remotePath}/build.gradle.kts")
            val isWrapper = fileExistsOnServer(srvId, "${proj.remotePath}/gradlew")

            val isAndroid = proj.projectTypeId.contains("android", ignoreCase = true) ||
                proj.projectTypeId.contains("kotlin", ignoreCase = true) ||
                isGradle || isGradleKts || isWrapper
            isAndroidProject.value = isAndroid
            if (isAndroid) {
                scanApkArtifacts()
            }
        }
    }

    fun scanApkArtifacts() {
        val proj = project.value ?: return
        if (proj.remotePath.startsWith("/github/")) return
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        val basePath = proj.remotePath
        viewModelScope.launch {
            val artifacts = mutableListOf<ApkArtifact>()

            val apkDirs = listOf(
                "$basePath/app/build/outputs/apk/debug",
                "$basePath/app/build/outputs/apk/release",
                "$basePath/build/outputs/apk/debug",
                "$basePath/build/outputs/apk/release",
            )
            for (dir in apkDirs) {
                when (val listRes = remoteFileSystem.list(srvId, dir)) {
                    is VmResult.Success -> {
                        val apkEntries = listRes.value.filter { it.name.endsWith(".apk", ignoreCase = true) }
                        apkEntries.forEach { entry ->
                            artifacts.add(
                                ApkArtifact(
                                    name = entry.name,
                                    path = entry.path,
                                    sizeBytes = entry.sizeBytes,
                                    modifiedAtSeconds = entry.modifiedAtSeconds,
                                    isRemote = proj.isRemote,
                                ),
                            )
                        }
                    }
                    is VmResult.Failure -> Unit
                }
            }

            apkArtifacts.value = artifacts
        }
    }

    fun buildAndroidApk() {
        val proj = project.value ?: return
        if (proj.remotePath.startsWith("/github/")) {
            notificationMessage.value = "Zero-Clone GitHub projects do not have an active VPS build server. Connect a VPS to build APKs."
            return
        }
        val srvId = proj.serverId
        if (srvId == null || proj.isLocal) {
            notificationMessage.value = "Local build: Scanning for project APK artifacts..."
            scanApkArtifacts()
            return
        }
        viewModelScope.launch {
            isBuildingApk.value = true
            buildStatusMessage.value = "Running Gradle assembleDebug on server..."
            val cmd = "cd '${sh(proj.remotePath)}' && chmod +x ./gradlew && ./gradlew assembleDebug"
            try {
                when (val res = commandGuard.run(srvId, cmd, CommandLimits(timeoutMillis = 300_000L))) {
                    is VmResult.Success -> {
                        val result = res.value
                        if (!result.isSuccess) {
                            error.value = VmError.Command(
                                summary = "Android build failed",
                                reason = result.stderr.ifBlank { result.stdout }.ifBlank {
                                    result.terminatingSignal?.let { "Build terminated by signal $it." }
                                        ?: "Gradle exited with status ${result.exitCode}."
                                },
                                command = cmd,
                                exitCode = result.exitCode,
                                stderrExcerpt = result.stderr,
                            )
                            buildStatusMessage.value = "Build failed."
                        } else {
                            buildStatusMessage.value = "Build successful!"
                            scanApkArtifacts()
                            try {
                                activityRepository.record(
                                    category = ActivityCategory.PROJECT,
                                    title = "Built Android APK",
                                    detail = proj.name,
                                    serverId = srvId,
                                    projectId = projectId,
                                )
                            } catch (exception: Exception) {
                                error.value = VmError.Storage(
                                    summary = "Build succeeded, but its activity could not be recorded",
                                    reason = exception.message,
                                    cause = exception,
                                )
                            }
                        }
                    }
                    is VmResult.Failure -> {
                        error.value = res.error
                        buildStatusMessage.value = "Build failed."
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error.value = VmError.Command(
                    summary = "Android build failed",
                    reason = exception.message,
                    command = cmd,
                    exitCode = 1,
                    cause = exception,
                )
                buildStatusMessage.value = "Build failed."
            } finally {
                isBuildingApk.value = false
            }
        }
    }

    fun downloadAndInstallApk(artifact: ApkArtifact) {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        if (isDownloadingApk.value) return
        viewModelScope.launch {
            isDownloadingApk.value = true
            var stagedFile: File? = null
            var retainStagedFile = false
            try {
                val localApkFile = if (proj.isRemote) {
                    notificationMessage.value = "Downloading APK from server..."
                    val downloadsDir = File(context.cacheDir, "downloads")
                    if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                        throw IOException("Could not prepare temporary storage for the APK.")
                    }
                    File.createTempFile("apk-", ".apk", downloadsDir).also { temp ->
                        stagedFile = temp
                        when (val failure = apkTransferFailure(
                            remoteFileSystem.download(srvId, artifact.path, temp, resume = false).lastOrNull(),
                        )) {
                            null -> if (!temp.isFile || temp.length() == 0L) {
                                throw IOException("The downloaded APK is empty.")
                            }
                            else -> throw IOException(failure.reason ?: failure.summary, failure.cause)
                        }
                    }
                } else {
                    File(artifact.path).takeIf { it.isFile && it.length() > 0L }
                        ?: throw IOException("APK file not found or empty: ${artifact.path}")
                }
                notificationMessage.value = "Installing ${artifact.name}..."
                apkInstaller.install(localApkFile)
                notificationMessage.value = "Opened installer for ${artifact.name}"
                error.value = null
                retainStagedFile = proj.isRemote
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error.value = VmError.Storage(
                    summary = "Could not prepare ${artifact.name} for installation",
                    reason = exception.message,
                    suggestedAction = "Check the APK file and available storage, then try again.",
                    retryable = true,
                    cause = exception,
                )
            } finally {
                if (!retainStagedFile) stagedFile?.delete()
                isDownloadingApk.value = false
            }
        }
    }

    fun downloadApkToPhoneDownloads(artifact: ApkArtifact) {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        if (isDownloadingApk.value) return
        viewModelScope.launch {
            isDownloadingApk.value = true
            var stagedFile: File? = null
            try {
                val safeName = safeApkDisplayName(artifact.name)
                val source = if (proj.isRemote) {
                    val stagingDirectory = File(context.cacheDir, "apk-downloads")
                    if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
                        throw IOException("Could not prepare temporary storage for the APK.")
                    }
                    File.createTempFile("apk-", ".apk", stagingDirectory).also { temp ->
                        stagedFile = temp
                        when (val failure = apkTransferFailure(
                            remoteFileSystem.download(srvId, artifact.path, temp, resume = false).lastOrNull(),
                        )) {
                            null -> if (!temp.isFile || temp.length() == 0L) {
                                throw IOException("The downloaded APK is empty.")
                            }
                            else -> throw IOException(failure.reason ?: failure.summary, failure.cause)
                        }
                    }
                } else {
                    File(artifact.path).takeIf { it.isFile }
                        ?: throw IOException("APK file not found on phone: ${artifact.path}")
                }
                saveApkToDownloads(source, safeName)
                notificationMessage.value = "Saved $safeName to Downloads"
                error.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error.value = VmError.Storage(
                    summary = "Could not save ${artifact.name} to Downloads",
                    reason = exception.message,
                    suggestedAction = "Check available storage and permissions, then try again.",
                    retryable = true,
                    cause = exception,
                )
            } finally {
                stagedFile?.delete()
                isDownloadingApk.value = false
            }
        }
    }

    private suspend fun saveApkToDownloads(source: File, displayName: String) =
        withContext(Dispatchers.IO) {
            if (!source.isFile || source.length() == 0L) {
                throw IOException("The APK file is missing or empty.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME_TYPE)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: throw IOException("Android could not create a Downloads entry.")
                try {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Android could not open the Downloads entry.")
                    val published = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    if (context.contentResolver.update(uri, published, null, null) != 1) {
                        throw IOException("Android could not publish the APK in Downloads.")
                    }
                } catch (exception: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw exception
                }
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
                if (!downloads.isDirectory && !downloads.mkdirs()) {
                    throw IOException("The public Downloads folder is not available.")
                }
                if (!downloads.canWrite()) {
                    throw IOException("The app does not have permission to write to public Downloads.")
                }
                val destination = uniqueDownloadFile(downloads, displayName)
                try {
                    source.inputStream().use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (exception: Exception) {
                    destination.delete()
                    throw exception
                }
            }
        }

    fun loadGitStatus(srvIdOverride: String? = null, pathOverride: String? = null) {
        val proj = project.value ?: return
        val srvId = srvIdOverride ?: proj.serverId ?: return
        val path = pathOverride ?: proj.remotePath
        if (path.isBlank() || proj.isLocal || isGitLoading.value) return
        isGitLoading.value = true
        viewModelScope.launch {
            try {
                when (val result = gitService.status(srvId, path)) {
                    is GitResult.Success -> gitStatus.value = result.value
                    is GitResult.Failure -> {
                        gitStatus.value = null
                        error.value = VmError.Command(
                            summary = "Git status failed: ${result.error}",
                            exitCode = 1,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                gitStatus.value = null
                error.value = VmError.Unexpected(
                    summary = "Could not load Git status",
                    reason = exception.message,
                    cause = exception,
                )
            } finally {
                isGitLoading.value = false
            }
        }
    }

    fun syncGit() {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: return
        if (proj.isLocal || isSyncing.value) return
        isSyncing.value = true
        error.value = null
        viewModelScope.launch {
            try {
                when (val pullResult = gitService.pull(srvId, proj.remotePath)) {
                    is GitResult.Success -> {
                        activityRepository.record(
                            category = ActivityCategory.GIT,
                            title = "Git pulled ${proj.name}",
                            detail = proj.remotePath,
                            serverId = srvId,
                            projectId = projectId,
                        )
                        loadProject()
                    }
                    is GitResult.Failure -> {
                        val msg = "Git pull failed: ${pullResult.error}"
                        error.value = VmError.Command(summary = msg, exitCode = 1)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error.value = VmError.Unexpected(
                    summary = "Could not pull Git changes",
                    reason = exception.message,
                    cause = exception,
                )
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun commitAndPush(message: String) {
        if (message.isBlank()) return
        val proj = project.value ?: return
        val srvId = proj.serverId ?: return
        if (proj.isLocal || isSyncing.value) return
        isSyncing.value = true
        error.value = null
        viewModelScope.launch {
            try {
                when (val addRes = gitService.add(srvId, proj.remotePath)) {
                    is GitResult.Failure -> {
                        val msg = "Git add failed: ${addRes.error}"
                        error.value = VmError.Command(summary = msg, exitCode = 1)
                        return@launch
                    }
                    is GitResult.Success -> Unit
                }
                when (val commitRes = gitService.commit(srvId, proj.remotePath, message)) {
                    is GitResult.Failure -> {
                        val msg = "Git commit failed: ${commitRes.error}"
                        error.value = VmError.Command(summary = msg, exitCode = 1)
                        return@launch
                    }
                    is GitResult.Success -> Unit
                }
                when (val pushRes = gitService.push(srvId, proj.remotePath)) {
                    is GitResult.Failure -> {
                        val msg = "Git push failed: ${pushRes.error}"
                        error.value = VmError.Command(summary = msg, exitCode = 1)
                    }
                    is GitResult.Success -> {
                        activityRepository.record(
                            category = ActivityCategory.GIT,
                            title = "Git committed & pushed",
                            detail = "$message (${proj.name})",
                            serverId = srvId,
                            projectId = projectId,
                        )
                        loadProject()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                error.value = VmError.Unexpected(
                    summary = "Could not commit and push Git changes",
                    reason = exception.message,
                    cause = exception,
                )
            } finally {
                isSyncing.value = false
            }

            apkArtifacts.value = artifacts
        }
    }

    fun buildAndroidApk() {
        val proj = project.value ?: return
        val srvId = proj.serverId
        if (srvId == null || proj.isLocal) {
            notificationMessage.value = "Local build: Scanning for project APK artifacts..."
            scanApkArtifacts()
            return
        }
        viewModelScope.launch {
            isBuildingApk.value = true
            buildStatusMessage.value = "Running Gradle assembleDebug on server..."
            val cmd = "cd '${sh(proj.remotePath)}' && chmod +x ./gradlew && ./gradlew assembleDebug"
            when (val res = commandGuard.run(srvId, cmd, CommandLimits(timeoutMillis = 300_000L))) {
                is VmResult.Success -> {
                    buildStatusMessage.value = "Build successful!"
                    activityRepository.record(
                        category = ActivityCategory.PROJECT,
                        title = "Built Android APK",
                        detail = proj.name,
                        serverId = srvId,
                        projectId = projectId,
                    )
                    scanApkArtifacts()
                }
                is VmResult.Failure -> {
                    val summary = "Android build failed: ${res.error.summary}"
                    error.value = VmError.Command(summary = summary, exitCode = 1)
                }
            }
            isBuildingApk.value = false
        }
    }

    fun downloadAndInstallApk(artifact: ApkArtifact) {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        viewModelScope.launch {
            isDownloadingApk.value = true
            notificationMessage.value = "Preparing ${artifact.name}..."
            val downloadsDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            val localApkFile = File(downloadsDir, artifact.name)

            if (proj.isRemote) {
                notificationMessage.value = "Downloading APK from server..."
                var downloadSuccess = false
                remoteFileSystem.download(srvId, artifact.path, localApkFile, resume = false)
                    .collect { event ->
                        when (event) {
                            is TransferEvent.Completed -> downloadSuccess = true
                            is TransferEvent.Failed -> error.value = event.error
                            else -> Unit
                        }
                    }
                if (downloadSuccess && localApkFile.exists()) {
                    notificationMessage.value = "Installing ${artifact.name}..."
                    apkInstaller.install(localApkFile)
                }
            } else {
                val file = File(artifact.path)
                if (file.exists()) {
                    apkInstaller.install(file)
                } else {
                    val msg = "APK file not found on phone"
                    error.value = VmError.Storage(summary = msg, reason = artifact.path)
                }
            }
            isDownloadingApk.value = false
        }
    }

    fun downloadApkToPhoneDownloads(artifact: ApkArtifact) {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        viewModelScope.launch {
            isDownloadingApk.value = true
            val publicDownloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS,
            )
            val destDir = if (publicDownloads != null && publicDownloads.exists() && publicDownloads.canWrite()) {
                publicDownloads
            } else {
                File(context.cacheDir, "downloads").apply { mkdirs() }
            }
            val destFile = File(destDir, artifact.name)

            if (proj.isRemote) {
                remoteFileSystem.download(srvId, artifact.path, destFile, resume = false).collect { }
            } else {
                File(artifact.path).copyTo(destFile, overwrite = true)
            }
            notificationMessage.value = "Saved ${artifact.name} to Downloads"
            isDownloadingApk.value = false
        }
    }

    fun loadGitStatus(srvIdOverride: String? = null, pathOverride: String? = null) {
        val proj = project.value ?: return
        val srvId = srvIdOverride ?: proj.serverId ?: return
        val path = pathOverride ?: proj.remotePath
        if (path.isBlank() || proj.isLocal) return
        viewModelScope.launch {
            isGitLoading.value = true
            when (val result = gitService.status(srvId, path)) {
                is GitResult.Success -> gitStatus.value = result.value
                is GitResult.Failure -> gitStatus.value = null
            }
            isGitLoading.value = false
        }
    }

    fun syncGit() {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: return
        if (proj.isLocal) return
        viewModelScope.launch {
            isSyncing.value = true
            when (val pullResult = gitService.pull(srvId, proj.remotePath)) {
                is GitResult.Success -> {
                    activityRepository.record(
                        category = ActivityCategory.GIT,
                        title = "Git pulled ${proj.name}",
                        detail = proj.remotePath,
                        serverId = srvId,
                        projectId = projectId,
                    )
                    loadProject()
                }
                is GitResult.Failure -> {
                    val msg = "Git pull failed: ${pullResult.error}"
                    error.value = VmError.Command(summary = msg, exitCode = 1)
                }
            }
            isSyncing.value = false
        }
    }

    fun commitAndPush(message: String) {
        if (message.isBlank()) return
        val proj = project.value ?: return
        val srvId = proj.serverId ?: return
        if (proj.isLocal) return
        viewModelScope.launch {
            isSyncing.value = true
            when (val addRes = gitService.add(srvId, proj.remotePath)) {
                is GitResult.Failure -> {
                    val msg = "Git add failed: ${addRes.error}"
                    error.value = VmError.Command(summary = msg, exitCode = 1)
                    isSyncing.value = false
                    return@launch
                }
                is GitResult.Success -> Unit
            }
            when (val commitRes = gitService.commit(srvId, proj.remotePath, message)) {
                is GitResult.Failure -> {
                    val msg = "Git commit failed: ${commitRes.error}"
                    error.value = VmError.Command(summary = msg, exitCode = 1)
                    isSyncing.value = false
                    return@launch
                }
                is GitResult.Success -> Unit
            }
            when (val pushRes = gitService.push(srvId, proj.remotePath)) {
                is GitResult.Failure -> {
                    val msg = "Git push failed: ${pushRes.error}"
                    error.value = VmError.Command(summary = msg, exitCode = 1)
                }
                is GitResult.Success -> {
                    activityRepository.record(
                        category = ActivityCategory.GIT,
                        title = "Git committed & pushed",
                        detail = "$message (${proj.name})",
                        serverId = srvId,
                        projectId = projectId,
                    )
                    loadProject()
                }
            }
            isSyncing.value = false
        }
    }

    fun toggleTask(task: AgentTaskEntity) {
        viewModelScope.launch {
            val newStatus = if (task.status == AgentTaskStatus.COMPLETED) {
                AgentTaskStatus.TODO
            } else {
                AgentTaskStatus.COMPLETED
            }
            agentTaskDao.updateStatus(task.id, newStatus, System.currentTimeMillis())
        }
    }

    fun addTask(title: String, priority: TaskPriority = TaskPriority.NORMAL) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newTask = AgentTaskEntity(
                id = "task_" + UUID.randomUUID().toString().take(TASK_ID_LENGTH),
                projectId = projectId,
                conversationId = null,
                title = title.trim(),
                description = "",
                status = AgentTaskStatus.TODO,
                priority = priority,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            agentTaskDao.upsert(newTask)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            agentTaskDao.deleteById(taskId)
        }
    }

    fun dismissMessage() {
        error.value = null
        notificationMessage.value = null
    }

    private fun sh(s: String) = s.replace("'", "'\\''")

    companion object {
        private const val TASK_ID_LENGTH = 8
    }
}

private data class GitCombinedState(
    val syncing: Boolean,
    val status: GitStatus?,
    val loading: Boolean,
    val error: VmError?,
)

internal fun apkTransferFailure(event: TransferEvent?): VmError? = when (event) {
    is TransferEvent.Completed -> null
    is TransferEvent.Failed -> event.error
    else -> VmError.FileSystem(
        summary = "The APK download ended before completion",
        reason = "No completed transfer event was received.",
        retryable = true,
    )
}

internal fun safeApkDisplayName(name: String): String {
    val cleaned = name.trim()
    require(
        cleaned.isNotBlank() &&
            cleaned.endsWith(".apk", ignoreCase = true) &&
            cleaned != "." &&
            cleaned != ".." &&
            '/' !in cleaned &&
            '\\' !in cleaned &&
            '\u0000' !in cleaned,
    ) { "The APK has an invalid filename." }
    return cleaned
}

private fun uniqueDownloadFile(directory: File, name: String): File {
    val extension = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
    val baseName = name.removeSuffix(extension)
    var candidate = File(directory, name)
    var suffix = 1
    while (candidate.exists()) {
        candidate = File(directory, "$baseName ($suffix)$extension")
        suffix++
    }
    return candidate
}
