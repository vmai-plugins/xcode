package digital.vmstudio.code.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ai.repository.TaskRunStatus
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.theme.VmTheme
import java.util.concurrent.TimeUnit

/**
 * The Tasks record (§28): every agent run the app has recorded, newest first.
 *
 * Each row answers "what ran, did it finish, what did it touch?" and links onward
 * to the transcript and — when the run changed files — to diff review.
 */
private enum class TasksScreenTab { HUB_TASKS, AGENT_RUNS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenConversation: (String) -> Unit,
    onReviewChanges: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onSolveWithAi: ((String, String) -> Unit)? = null,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var activeTab by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(TasksScreenTab.HUB_TASKS)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks & Agent Runs")
                        Text(
                            text = if (state.isSyncingHub) {
                                "Syncing VM Project Hub tasks…"
                            } else {
                                "${state.hubTasks.size} Hub tasks · ${state.tasks.size} AI runs"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(
                        onClick = viewModel::syncHubTasks,
                        enabled = !state.isSyncingHub,
                    ) {
                        Text(if (state.isSyncingHub) "Syncing…" else "Sync Hub")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                androidx.compose.material3.FilterChip(
                    selected = activeTab == TasksScreenTab.HUB_TASKS,
                    onClick = { activeTab = TasksScreenTab.HUB_TASKS },
                    label = { Text("VM Project Hub (${state.hubTasks.size})") },
                )
                androidx.compose.material3.FilterChip(
                    selected = activeTab == TasksScreenTab.AGENT_RUNS,
                    onClick = { activeTab = TasksScreenTab.AGENT_RUNS },
                    label = { Text("AI Agent Runs (${state.tasks.size})") },
                )
            }

            when (activeTab) {
                TasksScreenTab.HUB_TASKS -> {
                    if (state.hubTasks.isEmpty() && !state.isSyncingHub) {
                        VmEmptyState(
                            icon = Icons.Default.AutoAwesome,
                            title = if (state.hubError != null) "Sync failed" else "No Project Hub tasks",
                            description = state.hubError ?: "Tap 'Sync Hub' to pull Kanban tasks from vmstudio.digital.",
                            actionLabel = if (state.hubError != null) "Retry Sync" else "Sync Hub",
                            onAction = viewModel::syncHubTasks,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            if (state.hubError != null) {
                                item(key = "hub-error") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = state.hubError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(
                                                onClick = viewModel::dismissHubError,
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Dismiss",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            items(state.hubTasks, key = { "hub-${it.id}" }) { hubTask ->
                                HubTaskRow(
                                    item = hubTask,
                                    onCycleStatus = { viewModel.cycleHubTaskStatus(hubTask) },
                                    onSolveWithAi = onSolveWithAi?.let { solve ->
                                        { solve(hubTask.title, hubTask.detail) }
                                    },
                                )
                            }
                        }
                    }
                }

                TasksScreenTab.AGENT_RUNS -> {
                    when {
                        state.isLoading -> Unit
                        state.tasks.isEmpty() -> VmEmptyState(
                            icon = Icons.Default.AutoAwesome,
                            title = "No agent runs yet",
                            description = "When the agent works in a project, every run is " +
                                "recorded here with what it changed and how it finished.",
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            items(state.tasks, key = { it.task.id }) { row ->
                                TaskRow(
                                    row = row,
                                    onOpenConversation = onOpenConversation,
                                    onReviewChanges = onReviewChanges,
                                    onDelete = { viewModel.delete(row.task.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubTaskRow(
    item: digital.vmstudio.code.core.project.HubTaskItem,
    onCycleStatus: () -> Unit,
    onSolveWithAi: (() -> Unit)? = null,
) {
    val spacing = VmTheme.spacing
    val (statusLabel, statusColor) = when (item.status.lowercase()) {
        "done", "completed" -> "Done" to VmTheme.colors.success
        "in_progress", "doing" -> "In Progress" to MaterialTheme.colorScheme.primary
        else -> "To Do" to MaterialTheme.colorScheme.secondary
    }
    val priorityColor = when (item.priority.lowercase()) {
        "urgent", "critical" -> MaterialTheme.colorScheme.error
        "high" -> VmTheme.colors.warning
        else -> MaterialTheme.colorScheme.tertiary
    }

    VmCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCycleStatus),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VmChip(
                        text = item.projectName,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = CHIP_ALPHA),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    VmChip(
                        text = statusLabel,
                        containerColor = statusColor.copy(alpha = CHIP_ALPHA),
                        contentColor = statusColor,
                    )
                }
                if (onSolveWithAi != null) {
                    IconButton(
                        onClick = onSolveWithAi,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Solve with AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.detail.isNotBlank()) {
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                VmChip(
                    text = item.priority.uppercase(),
                    containerColor = priorityColor.copy(alpha = CHIP_ALPHA),
                    contentColor = priorityColor,
                )
                item.dueDate?.let { due ->
                    VmChip(text = "Due $due", monospace = true)
                }
                VmChip(text = "Tap to advance status")
            }
        }
    }
}

@Composable
private fun TaskRow(
    row: TaskRowUi,
    onOpenConversation: (String) -> Unit,
    onReviewChanges: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val task = row.task
    val spacing = VmTheme.spacing

    VmCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier
                        .padding(top = spacing.xs)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    StatusChip(status = task.status)
                    VmChip(text = relativeTime(task.updatedAtMillis))
                    if (task.toolCallCount > 0) {
                        VmChip(text = "${task.toolCallCount} tools", monospace = true)
                    }
                    if (task.totalTokens > 0) {
                        VmChip(text = formatTokens(task.totalTokens), monospace = true)
                    }
                }

                if (task.changedFiles.isNotEmpty() && row.serverId != null) {
                    val serverId = row.serverId
                    Row(
                        modifier = Modifier
                            .padding(top = spacing.sm)
                            .clickable {
                                onReviewChanges(serverId, task.changedFiles.first())
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${task.changedFiles.size} files changed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                }

                task.errorText?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }

            task.conversationId?.let { conversationId ->
                IconButton(onClick = { onOpenConversation(conversationId) }) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Open transcript",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete task record",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: TaskRunStatus) {
    val (label, color) = when (status) {
        TaskRunStatus.RUNNING -> "Running" to MaterialTheme.colorScheme.primary
        TaskRunStatus.COMPLETED -> "Completed" to MaterialTheme.colorScheme.primary
        TaskRunStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        TaskRunStatus.CANCELLED -> "Stopped" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    VmChip(
        text = label,
        containerColor = color.copy(alpha = CHIP_ALPHA),
        contentColor = color,
    )
}

private const val CHIP_ALPHA = 0.12f
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7
private const val TOKENS_PER_K = 1_000L

private fun relativeTime(millis: Long): String {
    if (millis <= 0L) return "just now"
    val elapsed = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    return when {
        minutes < 1 -> "just now"
        minutes < MINUTES_PER_HOUR -> "${minutes}m ago"
        hours < HOURS_PER_DAY -> "${hours}h ago"
        days < DAYS_PER_WEEK -> "${days}d ago"
        else -> "${days / DAYS_PER_WEEK}w ago"
    }
}

private fun formatTokens(total: Long): String = when {
    total >= TOKENS_PER_K -> "${total / TOKENS_PER_K}k tokens"
    else -> "$total tokens"
}
