package digital.vmstudio.code.feature.projects

import android.content.Context
import android.os.Environment
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

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

    private suspend fun fileExistsOnServer(srvId: String, path: String): Boolean {
        val res = remoteFileSystem.exists(srvId, path)
        return res is VmResult.Success && res.value
    }

    private fun checkIfAndroidAndScan(proj: Project) {
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
        val srvId = proj.serverId
        if (srvId == null || proj.isLocal) {
            notificationMessage.value = "Local build: Scanning for project APK artifacts..."
            scanApkArtifacts()
            return
        }
        viewModelScope.launch {
            isBuildingApk.value = true
            try {
                buildStatusMessage.value = "Running Gradle assembleDebug on server..."
                val cmd = "cd ${digital.vmstudio.code.core.ssh.command.shellQuotePath(proj.remotePath)} && chmod +x ./gradlew && ./gradlew assembleDebug"
                when (val res = commandGuard.run(srvId, cmd, CommandLimits(timeoutMillis = 300_000L))) {
                    is VmResult.Success -> {
                        if (res.value.isSuccess) {
                            buildStatusMessage.value = "Build successful!"
                            activityRepository.record(
                                category = ActivityCategory.PROJECT,
                                title = "Built Android APK",
                                detail = proj.name,
                                serverId = srvId,
                                projectId = projectId,
                            )
                            scanApkArtifacts()
                        } else {
                            val failureDetail = res.value.stderr.ifBlank { res.value.stdout }.takeLast(400)
                            val summary = "Android build failed: ${failureDetail.ifBlank { "Exit code ${res.value.exitCode}" }}"
                            buildStatusMessage.value = null
                            error.value = VmError.Command(
                                summary = summary,
                                exitCode = res.value.exitCode,
                                command = "./gradlew assembleDebug",
                                stderrExcerpt = failureDetail,
                            )
                        }
                    }
                    is VmResult.Failure -> {
                        val summary = "Android build failed: ${res.error.summary}"
                        buildStatusMessage.value = null
                        error.value = VmError.Command(summary = summary, exitCode = 1)
                    }
                }
            } finally {
                isBuildingApk.value = false
            }
        }
    }

    fun downloadAndInstallApk(artifact: ApkArtifact) {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        val safeName = try {
            safeApkDisplayName(artifact.name)
        } catch (e: IllegalArgumentException) {
            error.value = VmError.Validation(summary = "Invalid APK filename", reason = e.message)
            return
        }
        viewModelScope.launch {
            isDownloadingApk.value = true
            try {
                notificationMessage.value = "Preparing $safeName..."
                val downloadsDir = File(context.cacheDir, "downloads").apply { mkdirs() }
                val localApkFile = File(downloadsDir, safeName)

                if (proj.isRemote) {
                    notificationMessage.value = "Downloading APK from server..."
                    var lastEvent: TransferEvent? = null
                    remoteFileSystem.download(srvId, artifact.path, localApkFile, resume = false)
                        .collect { event ->
                            lastEvent = event
                            when (event) {
                                is TransferEvent.Completed -> Unit
                                is TransferEvent.Failed -> error.value = event.error
                                else -> Unit
                            }
                        }
                    val failure = apkTransferFailure(lastEvent)
                    if (failure != null) {
                        error.value = failure
                    } else if (localApkFile.exists()) {
                        notificationMessage.value = "Installing $safeName..."
                        val installResult = apkInstaller.install(localApkFile)
                        if (installResult is VmResult.Failure) {
                            error.value = installResult.error
                        }
                    }
                } else {
                    val file = File(artifact.path)
                    if (file.exists()) {
                        try {
                            file.copyTo(localApkFile, overwrite = true)
                            notificationMessage.value = "Installing $safeName..."
                            val installResult = apkInstaller.install(localApkFile)
                            if (installResult is VmResult.Failure) {
                                error.value = installResult.error
                            }
                        } catch (e: Exception) {
                            error.value = VmError.Storage(
                                summary = "Could not stage APK for installation",
                                reason = e.message,
                                cause = e,
                            )
                        }
                    } else {
                        val msg = "APK file not found on phone"
                        error.value = VmError.Storage(summary = msg, reason = artifact.path)
                    }
                }
            } finally {
                isDownloadingApk.value = false
            }
        }
    }

    fun downloadApkToPhoneDownloads(artifact: ApkArtifact) {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
        val safeName = try {
            safeApkDisplayName(artifact.name)
        } catch (e: IllegalArgumentException) {
            error.value = VmError.Validation(summary = "Invalid APK filename", reason = e.message)
            return
        }
        viewModelScope.launch {
            isDownloadingApk.value = true
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
                val destDir = if (publicDownloads != null && publicDownloads.exists() && publicDownloads.canWrite()) {
                    publicDownloads
                } else {
                    File(context.cacheDir, "downloads").apply { mkdirs() }
                }
                val destFile = File(destDir, safeName)

                if (proj.isRemote) {
                    var lastEvent: TransferEvent? = null
                    remoteFileSystem.download(srvId, artifact.path, destFile, resume = false)
                        .collect { event ->
                            lastEvent = event
                            if (event is TransferEvent.Failed) {
                                error.value = event.error
                            }
                        }
                    val failure = apkTransferFailure(lastEvent)
                    if (failure != null) {
                        error.value = failure
                    } else {
                        notificationMessage.value = "Saved $safeName to Downloads"
                    }
                } else {
                    val file = File(artifact.path)
                    if (file.exists()) {
                        file.copyTo(destFile, overwrite = true)
                        notificationMessage.value = "Saved $safeName to Downloads"
                    } else {
                        error.value = VmError.Storage(
                            summary = "APK file not found on phone",
                            reason = artifact.path,
                        )
                    }
                }
            } catch (e: Exception) {
                error.value = VmError.Storage(
                    summary = "Could not save APK to Downloads",
                    reason = e.message,
                    cause = e,
                )
            } finally {
                isDownloadingApk.value = false
            }
        }
    }

    fun loadGitStatus(srvIdOverride: String? = null, pathOverride: String? = null) {
        val proj = project.value ?: return
        val srvId = srvIdOverride ?: proj.serverId ?: return
        val path = pathOverride ?: proj.remotePath
        if (path.isBlank() || proj.isLocal) return
        viewModelScope.launch {
            isGitLoading.value = true
            try {
                when (val result = gitService.status(srvId, path)) {
                    is GitResult.Success -> gitStatus.value = result.value
                    is GitResult.Failure -> gitStatus.value = null
                }
            } finally {
                isGitLoading.value = false
            }
        }
    }

    fun syncGit() {
        val proj = project.value ?: return
        val srvId = proj.serverId ?: return
        if (proj.isLocal) return
        viewModelScope.launch {
            isSyncing.value = true
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
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun commitAndPush(message: String) {
        if (message.isBlank()) return
        val proj = project.value ?: return
        val srvId = proj.serverId ?: return
        if (proj.isLocal) return
        viewModelScope.launch {
            isSyncing.value = true
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
            } finally {
                isSyncing.value = false
            }
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
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newTask = AgentTaskEntity(
                id = "task_" + UUID.randomUUID().toString().take(TASK_ID_LENGTH),
                projectId = projectId,
                conversationId = null,
                title = trimmed,
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

    fun showTerminalNotice(message: String = "Terminal is only available for remote SSH servers. Local device shell requires Termux or ADB.") {
        notificationMessage.value = message
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

internal fun safeApkDisplayName(name: String): String {
    if (!name.lowercase().endsWith(".apk") || name.contains('/') || name.contains('\\') || name.contains("..")) {
        throw IllegalArgumentException("Invalid APK filename: $name")
    }
    return name
}

internal fun apkTransferFailure(event: TransferEvent?): VmError? = when (event) {
    is TransferEvent.Completed -> null
    is TransferEvent.Failed -> event.error
    else -> VmError.FileSystem(
        summary = "Transfer did not complete successfully",
        reason = "The APK download stream was interrupted before completion.",
    )
}


