package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme
import java.util.concurrent.TimeUnit

/**
 * Past agent conversations.
 *
 * Distinct from Projects on purpose: a project is *where* the agent works, a
 * conversation is *what* was asked and what came back. Tapping one reopens its
 * transcript and continues it on the server rather than starting cold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentConversationsScreen(
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTasks: () -> Unit = {},
    viewModel: AgentConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    IconButton(onClick = onOpenTasks) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Agent task record",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewConversation) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { error ->
                VmErrorPanel(
                    error = error,
                    modifier = Modifier.padding(spacing.md),
                )
            }

            when {
                state.isLoading -> Unit

                state.conversations.isEmpty() -> VmEmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = "No conversations yet",
                    description = "Pick a project and give the agent a task. It runs " +
                        "Claude Code on that server, where the code is.",
                    actionLabel = "Start one",
                    onAction = onNewConversation,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onOpen = { onOpenConversation(conversation.id) },
                            onDelete = { viewModel.delete(conversation.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VmTheme.spacing

    VmCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                conversation.workingDirectory?.let { directory ->
                    Text(
                        text = directory,
                        style = VmTheme.code.mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    VmChip(text = relativeTime(conversation.updatedAtMillis))
                    if (conversation.totalTokens > 0) {
                        VmChip(text = "${conversation.totalTokens} tokens", monospace = true)
                    }
                    // Whether a follow-up continues on the server or starts cold is
                    // the difference the user actually feels, so it is shown.
                    if (conversation.isResumable) VmChip(text = "Resumable")
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete conversation",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7

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
