package digital.vmstudio.code.feature.ai

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.ai.background.BackgroundRun
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Minimal, clean dialog to inspect and edit the working directory.
 */
@Composable
internal fun EditDirectoryDialog(
    currentDirectory: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentDirectory) }

    VmDialog(
        title = "Server Project Path",
        onDismiss = onDismiss,
        confirmLabel = "Save Directory",
        onConfirm = { onConfirm(text.trim()) },
        confirmEnabled = text.isNotBlank(),
        icon = Icons.Default.Folder,
    ) {
        Text(
            text = "The AI agent executes commands, runs audits, and edits source code inside this remote directory.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Absolute Remote Directory") },
            singleLine = true,
            textStyle = VmTheme.code.mono,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** Empty state: a greeting and a few quiet starting points. */
@Composable
internal fun EmptyChatHero(
    workingDirectory: String,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val dirName = remember(workingDirectory) {
        workingDirectory.trimEnd('/').substringAfterLast('/', "").ifBlank { "your server" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = "What should we work on?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.md),
        )
        Text(
            text = dirName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = spacing.lg),
        )

        val suggestions = listOf(
            "Explain this project" to "Explain the structure of this project, its dependencies and key entry points.",
            "Find bugs" to "Review recent changes for bugs and runtime issues.",
            "Write tests" to "Write tests for the core flows of this project.",
            "Git status" to "Summarize the uncommitted changes in this repository.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { (label, prompt) ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, VmTheme.colors.divider),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSuggestionClick(prompt) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * Health banner for AI runtime.
 * Dismissible and collapsible so it never blocks the developer's chat flow.
 */
@Composable
internal fun HealthBanner(
    state: AgentChatUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = state.health
    val spacing = VmTheme.spacing
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }

    if (state.isCheckingHealth) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = "Checking Claude Code CLI on the server...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    if (health == null || isDismissed) return

    val ready = health.isAvailable && health.isAuthenticated
    if (ready) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = 2.dp),
            shape = RoundedCornerShape(10.dp),
            color = VmTheme.colors.successContainer.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, VmTheme.colors.success.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VmStatusBadge(status = VmStatus.CONNECTED, label = "Claude Code Active")
                    health.version?.let { VmChip(text = it, monospace = true) }
                }
                health.latencyMillis?.let { VmChip(text = "${it}ms", monospace = true) }
            }
        }
        return
    }

    // Modern Amber Warning Banner with fast copy & setup actions
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1710),
        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD97706).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column {
                        Text(
                            text = if (!health.isAvailable) "Claude Code CLI not found" else "Claude not authenticated",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFFDE68A),
                        )
                        Text(
                            text = "Required for terminal tools & autonomous file edits",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFFD1D5DB),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = { isDismissed = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    health.diagnosis?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE5E7EB),
                        )
                    }
                    Text(
                        text = "Quick Install on Server (via SSH):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFDE68A),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.sm, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "npm install -g @anthropic-ai/claude-code",
                                style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("npm install -g @anthropic-ai/claude-code"))
                                    Toast.makeText(context, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy command",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VmButton(
                    text = "Check again",
                    onClick = onRetry,
                    style = VmButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
                VmButton(
                    text = if (isExpanded) "Less info" else "Setup instructions",
                    onClick = { isExpanded = !isExpanded },
                    style = VmButtonStyle.Tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Detached runs for this project.
 */
@Composable
internal fun BackgroundRunsSection(
    runs: List<BackgroundRun>,
    onOpenLogs: (String) -> Unit,
    onStop: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    if (runs.isEmpty()) return
    val spacing = VmTheme.spacing

    VmCard(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Running in the background", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh background runs",
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        runs.forEach { run ->
            val id = run.id ?: return@forEach
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
            ) {
                Text(
                    text = run.name ?: id,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        VmChip(text = id, monospace = true)
                        if (run.isBlocked) {
                            VmChip(text = "Needs attention")
                        } else {
                            run.status?.let { VmChip(text = it) }
                        }
                    }

                    VmButton(
                        text = "Output",
                        onClick = { onOpenLogs(id) },
                        style = VmButtonStyle.Tertiary,
                    )
                    IconButton(onClick = { onStop(id) }) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop this run",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
