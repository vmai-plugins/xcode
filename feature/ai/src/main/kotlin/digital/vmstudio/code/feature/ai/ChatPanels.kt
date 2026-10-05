package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ai.background.BackgroundRun
import digital.vmstudio.code.core.ai.provider.AiProviderKind
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmDialog
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
            text = "The AI agent executes commands, runs audits, and edits source code " +
                "inside this remote directory.",
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
            "Explain this project" to
                "Explain the structure of this project, its dependencies and key entry points.",
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
 * Shown only when the chosen backend cannot run, naming that backend and the fix.
 * When everything works it is absent: status belongs in the conversation, not in
 * a permanent bar.
 */
@Composable
internal fun HealthBanner(
    state: AgentChatUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = state.health
    var isDismissed by remember(health) { mutableStateOf(false) }
    if (state.isCheckingHealth || health == null || isDismissed) return
    if (health.isAvailable && health.isAuthenticated) return

    val isGateway = health.kind == AiProviderKind.OMNIROUTE
    val title = when {
        isGateway -> "OmniRoute isn't connected"
        !health.isAvailable -> "Claude Code isn't installed on this server"
        else -> "Claude Code isn't signed in on this server"
    }
    val hint = when {
        isGateway -> "Set your gateway URL and key in Settings, or pick another model."
        !health.isAvailable -> "Install it with: npm install -g @anthropic-ai/claude-code, " +
            "or pick an OmniRoute model."
        else -> "Run \"claude auth login\" on the server, or pick an OmniRoute model."
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VmTheme.spacing.md, vertical = VmTheme.spacing.xs),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(VmTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = VmTheme.colors.warning,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                IconButton(onClick = { isDismissed = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = health.diagnosis ?: hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (health.diagnosis != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
                VmButton(text = "Check again", onClick = onRetry, style = VmButtonStyle.Secondary)
                if (isGateway) {
                    VmButton(text = "Open Settings", onClick = onOpenSettings, style = VmButtonStyle.Tertiary)
                }
                // Claude Code missing but the free gateway is ready: one tap instead of hunting the picker.
                val gatewayModel = state.omniModels.ids.firstOrNull()
                if (!isGateway && gatewayModel != null) {
                    VmButton(
                        text = "Use OmniRoute",
                        onClick = { onPickModel(gatewayModel) },
                        style = VmButtonStyle.Tertiary,
                    )
                }
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
