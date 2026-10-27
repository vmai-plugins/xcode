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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ai.background.BackgroundRun
import digital.vmstudio.code.core.ai.provider.AiProviderKind
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
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
    onSelectProjectClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val isGeneral = workingDirectory.isBlank()
    val dirName = remember(workingDirectory) {
        workingDirectory.trimEnd('/').substringAfterLast('/', "")
            .ifBlank { "General chat · pick a project above to work on code" }
    }
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // --- Kimi Glowing Orb Avatar ---
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF1E88E5).copy(alpha = 0.15f),
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF29B6F6),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(4.dp, 8.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                            Box(modifier = Modifier.size(4.dp, 8.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                        }
                    }
                }
            }
        }

        Text(
            text = "$greeting! What would you like to build today?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )

        Text(
            text = if (isGeneral) "General workspace · No project pinned" else "Active folder: $dirName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )

        // Prominent interactive project switcher in empty chat
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onSelectProjectClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (isGeneral) "Select or Add Project Workspace" else "Switch Project Workspace",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.size(18.dp))

        // --- Teamily & Antigravity Agent Squad Actions ---
        val actions = listOf(
            "Full-Stack Pod" to "Act as a Full-Stack Engineering squad: audit project structure, inspect entry points, implement robust features, and verify complete builds.",
            "Security Auditor" to "Run a strict security and vulnerability audit on this codebase. Check credentials, permissions, input sanitization, and data leaks.",
            "Test Suite" to "Write comprehensive unit, integration, and edge-case tests for the critical business logic in this repo.",
            "DevOps / Release" to "Audit deployment configs, Gradle build tasks, Dockerfiles, and verify release APK / binary artifact readiness.",
            "Data & Sheets" to "Inspect tabular data, CSVs, or Google Sheets connectors, and generate automated processing scripts.",
            "Git Status" to "Check git status, show uncommitted diffs, and review modified files in detail.",
            "Fix Errors" to "Review recent runtime errors or test failures, trace root causes, and apply complete fixes.",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.forEach { (label, prompt) ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSuggestionClick(prompt) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
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

    var expanded by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4CAF50),
                    )
                    Text(
                        text = "${runs.size} task${if (runs.size > 1) "s" else ""} running",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh tasks",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.size(8.dp))
                runs.forEach { run ->
                    val id = run.id ?: return@forEach
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = run.name ?: id,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        VmButton(
                            text = "Logs",
                            onClick = { onOpenLogs(id) },
                            style = VmButtonStyle.Tertiary,
                        )
                        IconButton(
                            onClick = { onStop(id) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop this run",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Antigravity-style Queued Tasks Shelf:
 * Sits right above the composer when tasks are queued while an agent is running.
 * Allows mid-flight steering ("Steer Now") or discarding.
 */
/**
 * Antigravity-style Queued Tasks Shelf:
 * Clean, dark rounded pill card matching Google Antigravity mobile UI:
 * "Queued Messages (count) | Sends after agent finishes working ⌄"
 * Individual items with prompt preview, arrow forward (steer now), edit, and trash icon.
 */
@Composable
internal fun QueuedTasksShelf(
    queued: List<QueuedMessage>,
    onSteerNow: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queued.isEmpty()) return
    val spacing = VmTheme.spacing
    var expanded by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Queued Messages",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = queued.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Sends after agent finishes working",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.size(8.dp))
                queued.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = item.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onSteerNow(item.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Steer Now",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = { onRemove(item.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove",
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


