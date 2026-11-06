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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.Brush
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

/** Empty state: a greeting, cluster asset toggles, and starting points. */
@Composable
internal fun EmptyChatHero(
    workingDirectory: String,
    currentServerId: String? = null,
    onSuggestionClick: (String) -> Unit,
    onSelectProjectClick: () -> Unit = {},
    onSelectTargetServer: ((String) -> Unit)? = null,
    onOpenServerPicker: (() -> Unit)? = null,
    onOpenChatHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val isGeneral = workingDirectory.isBlank()
    val vpsId = digital.vmstudio.code.core.common.preferences.UserPreferences.SOVEREIGN_VPS_ID
    val isMsiActive = currentServerId == "laptop-msi"
    val isGpuActive = currentServerId == "laptop-gpu"
    val isVpsActive = currentServerId == vpsId

    val dirName = remember(workingDirectory) {
        workingDirectory.trimEnd('/').substringAfterLast('/', "")
            .ifBlank { "General workspace" }
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
        // --- Obsidian Developer IDE Engine Mark ---
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF38BDF8))),
            ),
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "XC",
                    style = VmTheme.code.mono.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
            }
        }

        Text(
            text = "$greeting! What would you like to build?",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        // Cluster Telemetry Live Status Strip (Interactive 1-Tap Target Selectors)
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // MSI Laptop Target
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMsiActive) Color(0xFF10B981).copy(alpha = 0.28f) else Color(0xFF10B981).copy(alpha = 0.12f),
                border = BorderStroke(
                    width = if (isMsiActive) 2.dp else 1.dp,
                    color = if (isMsiActive) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.35f),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = onSelectTargetServer != null) {
                        onSelectTargetServer?.invoke("laptop-msi")
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Text(
                        text = if (isMsiActive) "✓ MSI: Active" else "MSI: 0ms",
                        style = VmTheme.code.mono.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isMsiActive) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = Color(0xFF10B981),
                    )
                }
            }

            // GPU Compute Node
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isGpuActive) Color(0xFF38BDF8).copy(alpha = 0.28f) else Color(0xFF38BDF8).copy(alpha = 0.12f),
                border = BorderStroke(
                    width = if (isGpuActive) 2.dp else 1.dp,
                    color = if (isGpuActive) Color(0xFF38BDF8) else Color(0xFF38BDF8).copy(alpha = 0.35f),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = onSelectTargetServer != null) {
                        onSelectTargetServer?.invoke("laptop-gpu")
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                    Text(
                        text = if (isGpuActive) "✓ GPU: Active" else "GPU Slv: 5ms",
                        style = VmTheme.code.mono.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isGpuActive) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = Color(0xFF38BDF8),
                    )
                }
            }

            // VPS Hub Target
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isVpsActive) Color(0xFFA855F7).copy(alpha = 0.28f) else Color(0xFFA855F7).copy(alpha = 0.12f),
                border = BorderStroke(
                    width = if (isVpsActive) 2.dp else 1.dp,
                    color = if (isVpsActive) Color(0xFFA855F7) else Color(0xFFA855F7).copy(alpha = 0.35f),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = onSelectTargetServer != null) {
                        onSelectTargetServer?.invoke(vpsId)
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFA855F7)))
                    Text(
                        text = if (isVpsActive) "✓ VPS Hub: Active" else "VPS Hub: Live",
                        style = VmTheme.code.mono.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isVpsActive) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = Color(0xFFA855F7),
                    )
                }
            }
        }

        val activeTargetLabel = when {
            isMsiActive -> "MSI Laptop Orchestrator"
            isGpuActive -> "Laptop 2 (GPU Compute)"
            isVpsActive -> "Sovereign Cloud VPS"
            else -> "This Phone (Local Storage)"
        }

        Text(
            text = if (isGeneral) "Target: $activeTargetLabel · Tap pills above to talk to Laptop or VPS" else "Target: $activeTargetLabel · $dirName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Server / Asset Mixer Trigger
            if (onOpenServerPicker != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenServerPicker)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = when {
                                isMsiActive -> "Laptop 1"
                                isVpsActive -> "Cloud VPS"
                                else -> "Assets"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

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
                        text = if (isGeneral) "Select Project" else "Switch Project",
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

            if (onOpenChatHistory != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenChatHistory)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
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


