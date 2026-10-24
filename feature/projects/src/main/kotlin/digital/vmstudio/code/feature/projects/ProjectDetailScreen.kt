package digital.vmstudio.code.feature.projects

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmErrorAction
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenTerminal: (serverId: String, path: String) -> Unit,
    onOpenFiles: (serverId: String, path: String) -> Unit,
    onOpenAgent: (serverId: String, path: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completedCount = state.tasks.count { it.status == AgentTaskStatus.COMPLETED }
    val spacing = VmTheme.spacing
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var commitMessageInput by remember { mutableStateOf("") }
    val project = state.project

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Project Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            state.error?.let { err ->
                item(key = "error") {
                    VmErrorPanel(
                        error = err,
                        actions = listOf(VmErrorAction("Dismiss", viewModel::dismissMessage)),
                    )
                }
            }

            state.notificationMessage?.let { msg ->
                item(key = "notification") {
                    VmCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = viewModel::dismissMessage) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            if (project != null) {
                item(key = "overview") {
                    ProjectOverviewCard(
                        project = project,
                        serverName = state.server?.name,
                        isSyncing = state.isSyncing,
                        onSyncGit = viewModel::syncGit,
                        onChangeColor = { showColorDialog = true },
                    )
                }

                item(key = "actions") {
                    ProjectActionsRow(
                        project = project,
                        onOpenTerminal = onOpenTerminal,
                        onOpenFiles = onOpenFiles,
                        onOpenAgent = onOpenAgent,
                        onTerminalNotice = viewModel::showTerminalNotice,
                    )
                }

                if (state.isAndroidProject || state.apkArtifacts.isNotEmpty()) {
                    item(key = "android-build") {
                        ProjectAndroidBuildCard(
                            state = state,
                            onBuildApk = viewModel::buildAndroidApk,
                            onScanApks = viewModel::scanApkArtifacts,
                            onInstallApk = viewModel::downloadAndInstallApk,
                            onSaveApk = viewModel::downloadApkToPhoneDownloads,
                            onSendWhatsApp = viewModel::sendApkViaWhatsApp,
                        )
                    }
                }

                item(key = "git-control") {
                    ProjectGitControlCard(
                        state = state,
                        onSyncGit = viewModel::syncGit,
                        onOpenCommitDialog = { showCommitDialog = true },
                        onRefreshGit = { viewModel.loadGitStatus() },
                    )
                }
            }

            item(key = "tasks-header") {
                ProjectTasksHeader(
                    completedCount = completedCount,
                    totalCount = state.tasks.size,
                    onAddTask = { showAddTaskDialog = true },
                )
            }

            if (state.tasks.isEmpty()) {
                item(key = "no-tasks") {
                    VmCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No tasks yet for this project. Tap '+' to add one.",
                            modifier = Modifier.padding(spacing.md),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.tasks, key = { it.id }) { task ->
                    ProjectTaskItem(
                        task = task,
                        onToggle = { viewModel.toggleTask(task) },
                        onDelete = { viewModel.deleteTask(task.id) },
                    )
                }
            }
        }
    }

    if (showAddTaskDialog) {
        var taskTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("New Project Task") },
            text = {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("Task description") },
                    placeholder = { Text("e.g. Audit dependencies and SSL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                VmButton(
                    text = "Add",
                    onClick = {
                        if (taskTitle.isNotBlank()) {
                            viewModel.addTask(taskTitle)
                            showAddTaskDialog = false
                        }
                    },
                    enabled = taskTitle.isNotBlank(),
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit & Push") },
            text = {
                OutlinedTextField(
                    value = commitMessageInput,
                    onValueChange = { commitMessageInput = it },
                    label = { Text("Commit message") },
                    placeholder = { Text("e.g. Update feature implementation") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                VmButton(
                    text = "Commit & Push",
                    onClick = {
                        if (commitMessageInput.isNotBlank()) {
                            viewModel.commitAndPush(commitMessageInput.trim())
                            commitMessageInput = ""
                            showCommitDialog = false
                        }
                    },
                    enabled = commitMessageInput.isNotBlank(),
                )
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ProjectOverviewCard(
    project: Project,
    serverName: String?,
    isSyncing: Boolean,
    onSyncGit: () -> Unit,
) {
    val spacing = VmTheme.spacing
    val projectColor = ProjectColors.parseColor(project.colorHex, project.name)
    VmCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, projectColor.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                VmStatusBadge(
                    status = VmStatus.CONNECTED,
                    label = project.projectTypeId.replaceFirstChar { it.uppercase() },
                )
            }

            if (project.description.isNotBlank()) {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.xs))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                val targetText = when {
                    project.remotePath.startsWith("/vmhub/") -> "VM Project Hub (Universal Workspace)"
                    project.remotePath.startsWith("/github/") -> "GitHub Zero-Clone Virtual Repo"
                    serverName != null -> serverName
                    project.isLocal -> "This Device (Local Storage)"
                    else -> "Remote VPS"
                }
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (project.remotePath.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = project.remotePath,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!project.branch.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Branch: ${project.branch}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    VmButton(
                        text = if (isSyncing) "Pulling..." else "Git Pull",
                        onClick = onSyncGit,
                        style = VmButtonStyle.Tertiary,
                        loading = isSyncing,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectActionsRow(
    project: Project,
    onOpenTerminal: (serverId: String, path: String) -> Unit,
    onOpenFiles: (serverId: String, path: String) -> Unit,
    onOpenAgent: (serverId: String, path: String) -> Unit,
    onTerminalNotice: () -> Unit = {},
) {
    val spacing = VmTheme.spacing
    val effectiveServerId = project.serverId ?: RemoteFileSystem.LOCAL_SERVER_ID
    val hasTerminal = project.isRemote && !project.serverId.isNullOrBlank()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        VmButton(
            text = "Terminal",
            onClick = {
                val srv = project.serverId
                if (hasTerminal && srv != null) {
                    onOpenTerminal(srv, project.remotePath)
                } else {
                    onTerminalNotice()
                }
            },
            enabled = true,
            icon = Icons.Default.Terminal,
            style = VmButtonStyle.Secondary,
            modifier = Modifier.weight(1f),
        )
        val isGithubRepo = project.remotePath.startsWith("/github/")
        VmButton(
            text = if (isGithubRepo) "GitHub Tree" else "Files & Code",
            onClick = {
                if (isGithubRepo) {
                    onOpenAgent(effectiveServerId, project.remotePath)
                } else {
                    onOpenFiles(effectiveServerId, project.remotePath)
                }
            },
            enabled = true,
            icon = if (isGithubRepo) Icons.Default.Code else Icons.Default.Folder,
            style = VmButtonStyle.Secondary,
            modifier = Modifier.weight(1f),
        )
        VmButton(
            text = "AI Agent",
            onClick = { onOpenAgent(effectiveServerId, project.remotePath) },
            enabled = true,
            icon = Icons.Default.AutoAwesome,
            style = VmButtonStyle.Primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProjectAndroidBuildCard(
    state: ProjectDetailUiState,
    onBuildApk: () -> Unit,
    onScanApks: () -> Unit,
    onInstallApk: (ApkArtifact) -> Unit,
    onSaveApk: (ApkArtifact) -> Unit,
    onSendWhatsApp: ((ApkArtifact) -> Unit)? = null,
) {
    val spacing = VmTheme.spacing
    VmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Android App & Build",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (state.isBuildingApk || state.isDownloadingApk) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            if (state.buildStatusMessage != null) {
                Text(
                    text = state.buildStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                VmButton(
                    text = if (state.isBuildingApk) "Building APK..." else "Build Android APK",
                    onClick = onBuildApk,
                    enabled = !state.isBuildingApk && !state.isDownloadingApk,
                    loading = state.isBuildingApk,
                    icon = Icons.Default.PlayArrow,
                    style = VmButtonStyle.Primary,
                    modifier = Modifier.weight(1f),
                )
                VmButton(
                    text = "Scan APKs",
                    onClick = onScanApks,
                    icon = Icons.Default.Refresh,
                    style = VmButtonStyle.Secondary,
                )
            }

            if (state.apkArtifacts.isNotEmpty()) {
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Generated APKs & Artifacts (${state.apkArtifacts.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                state.apkArtifacts.forEach { artifact ->
                    ApkArtifactItem(
                        artifact = artifact,
                        isDownloading = state.isDownloadingApk,
                        onInstall = { onInstallApk(artifact) },
                        onSave = { onSaveApk(artifact) },
                        onSendWhatsApp = onSendWhatsApp?.let { { it(artifact) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkArtifactItem(
    artifact: ApkArtifact,
    isDownloading: Boolean,
    onInstall: () -> Unit,
    onSave: () -> Unit,
    onSendWhatsApp: (() -> Unit)? = null,
) {
    val spacing = VmTheme.spacing
    VmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = artifact.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val mb = artifact.sizeBytes / (1024 * 1024.0)
            Text(
                text = "%.1f MB".format(mb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                VmButton(
                    text = "Install",
                    onClick = onInstall,
                    enabled = !isDownloading,
                    icon = Icons.Default.Download,
                    style = VmButtonStyle.Primary,
                    modifier = Modifier.weight(1f),
                )
                VmButton(
                    text = "Save",
                    onClick = onSave,
                    enabled = !isDownloading,
                    style = VmButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
                if (onSendWhatsApp != null) {
                    VmButton(
                        text = "WhatsApp",
                        onClick = onSendWhatsApp,
                        enabled = !isDownloading,
                        style = VmButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectGitControlCard(
    state: ProjectDetailUiState,
    onSyncGit: () -> Unit,
    onOpenCommitDialog: () -> Unit,
    onRefreshGit: () -> Unit,
) {
    val spacing = VmTheme.spacing
    VmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Git Source Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onRefreshGit,
                    enabled = !state.isGitLoading,
                    modifier = Modifier.size(28.dp),
                ) {
                    if (state.isGitLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Git")
                    }
                }
            }

            val git = state.gitStatus
            if (git != null) {
                Text(
                    text = "Branch: ${git.branch}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )

                if (!git.hasChanges) {
                    Text(
                        text = "Working tree clean · Up to date",
                        style = MaterialTheme.typography.bodySmall,
                        color = VmTheme.colors.success,
                    )
                } else {
                    val summaryText = "${git.unstaged.size} modified, ${git.staged.size} staged, " +
                        "${git.untracked.size} untracked"
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    val files = (git.unstaged + git.untracked).take(3)
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        files.forEach { fileEntry ->
                            Text(
                                text = "• ${fileEntry.path}",
                                style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Git repository detected. Tap refresh or pull to inspect status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                VmButton(
                    text = if (state.isSyncing) "Pulling..." else "Pull",
                    onClick = onSyncGit,
                    loading = state.isSyncing,
                    style = VmButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
                val canCommit = !state.isSyncing && (state.gitStatus?.hasChanges == true || state.gitStatus == null)
                VmButton(
                    text = "Commit & Push",
                    onClick = onOpenCommitDialog,
                    enabled = canCommit,
                    style = VmButtonStyle.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProjectTasksHeader(
    completedCount: Int,
    totalCount: Int,
    onAddTask: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VmSectionHeader(
            title = "Tasks ($completedCount/$totalCount)",
        )
        IconButton(onClick = onAddTask) {
            Icon(Icons.Default.Add, contentDescription = "Add Task")
        }
    }
}

@Composable
private fun ProjectTaskItem(
    task: AgentTaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VmTheme.spacing
    val isDone = task.status == AgentTaskStatus.COMPLETED
    VmCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (isDone) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
