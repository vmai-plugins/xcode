package digital.vmstudio.code.feature.projects

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmErrorAction
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmSectionHeader
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

            if (project != null) {
                // 1. Overview Card
                item(key = "overview") {
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
                                Text(
                                    text = state.server?.name ?: "Remote VPS",
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
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Action Buttons
                item(key = "actions") {
                    val srvId = project.serverId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        VmButton(
                            text = "Terminal",
                            onClick = { if (srvId != null) onOpenTerminal(srvId, project.remotePath) },
                            enabled = srvId != null,
                            icon = Icons.Default.Terminal,
                            style = VmButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        VmButton(
                            text = "Files",
                            onClick = { if (srvId != null) onOpenFiles(srvId, project.remotePath) },
                            enabled = srvId != null,
                            icon = Icons.Default.Folder,
                            style = VmButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        VmButton(
                            text = "AI Agent",
                            onClick = { if (srvId != null) onOpenAgent(srvId, project.remotePath) },
                            enabled = srvId != null,
                            icon = Icons.Default.AutoAwesome,
                            style = VmButtonStyle.Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 3. Project Tasks
            item(key = "tasks-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VmSectionHeader(
                        title = "Tasks ($completedCount/${state.tasks.size})",
                    )
                    IconButton(onClick = { showAddTaskDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task")
                    }
                }
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
                    val isDone = task.status == AgentTaskStatus.COMPLETED
                    VmCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleTask(task) }
                                .padding(spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isDone) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = if (isDone) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(spacing.sm))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (isDone) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                ),
                                color = if (isDone) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.deleteTask(task.id) }) {
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
}
