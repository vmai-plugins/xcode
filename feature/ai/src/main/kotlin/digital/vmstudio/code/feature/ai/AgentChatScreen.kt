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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ai.background.BackgroundRun
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.markdown.MarkdownText
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Invoked when a detached run starts, so the host can begin watching it. The
     * watcher is a service in the app module, which a feature must not reach into.
     */
    onWatchBackgroundRuns: (serverId: String) -> Unit = {},
    onOpenDiff: (filePath: String) -> Unit = {},
    onOpenFiles: () -> Unit = {},
    viewModel: AgentChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }

    val currentProjectName = remember(state.workingDirectory, state.availableProjects) {
        state.availableProjects.firstOrNull { it.remotePath == state.workingDirectory }?.name
            ?: state.workingDirectory.trimEnd('/').substringAfterLast('/').ifBlank { "AI Agent" }
    }

    val currentModelLabel = when (state.selectedModel) {
        "claude-3-7-sonnet-latest" -> "Claude 3.7"
        "claude-3-5-sonnet-latest" -> "Claude 3.5"
        "omniroute/gpt-4o" -> "GPT-4o"
        "claude-3-5-haiku-latest" -> "Haiku 3.5"
        else -> "Claude 3.7"
    }

    val groupedTranscript = remember(state.transcript) { groupTranscript(state.transcript) }

    state.watchServerId?.let { server ->
        LaunchedEffect(server) {
            onWatchBackgroundRuns(server)
            viewModel.onWatchStarted()
        }
    }

    // Follow the tail as events arrive
    LaunchedEffect(groupedTranscript.size) {
        if (groupedTranscript.isNotEmpty()) {
            listState.animateScrollToItem(groupedTranscript.lastIndex)
        }
    }

    if (showDirectoryDialog) {
        EditDirectoryDialog(
            currentDirectory = state.workingDirectory,
            onDismiss = { showDirectoryDialog = false },
            onConfirm = { newDir ->
                viewModel.setWorkingDirectory(newDir)
                showDirectoryDialog = false
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // Project Selector Row
                        Box {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = state.availableProjects.isNotEmpty() && !state.isRunning) {
                                        showProjectMenu = true
                                    }
                                    .padding(vertical = 2.dp, horizontal = 2.dp),
                            ) {
                                Text(
                                    text = currentProjectName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp),
                                )
                                if (state.availableProjects.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select project",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showProjectMenu,
                                onDismissRequest = { showProjectMenu = false },
                            ) {
                                state.availableProjects.forEach { proj ->
                                    val isSelected = proj.remotePath == state.workingDirectory
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = proj.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = proj.remotePath,
                                                    style = VmTheme.code.mono.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectProject(proj)
                                            showProjectMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        // Subtitle status row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val status = when {
                                state.isCheckingHealth -> VmStatus.CONNECTING
                                state.health?.isAvailable == true -> VmStatus.CONNECTED
                                else -> VmStatus.DISCONNECTED
                            }
                            val label = when {
                                state.isCheckingHealth -> "Probing runtime..."
                                state.health?.isAvailable == true -> "Server online"
                                else -> "CLI offline"
                            }
                            VmStatusBadge(status = status, label = label)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Model Selector Dropdown Pill
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VmTheme.colors.agentContainer,
                            border = BorderStroke(1.dp, VmTheme.colors.agent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !state.isRunning) { showModelMenu = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = VmTheme.colors.agent,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = currentModelLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = VmTheme.colors.onAgentContainer,
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = VmTheme.colors.agent,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                        ) {
                            listOf(
                                Triple("claude-3-7-sonnet-latest", "Claude 3.7 Sonnet", "Hybrid reasoning · Recommended"),
                                Triple("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet", "High speed & coding accuracy"),
                                Triple("omniroute/gpt-4o", "OmniRoute GPT-4o", "Multi-provider gateway"),
                                Triple("claude-3-5-haiku-latest", "Claude 3.5 Haiku", "Fast & cost-efficient"),
                            ).forEach { (id, name, desc) ->
                                val isSelected = state.selectedModel == id
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectModel(id)
                                        showModelMenu = false
                                    },
                                )
                            }
                        }
                    }

                    val hasTouchedFiles = state.transcript.any { it is TranscriptItem.ToolCall && it.affectedPath != null && !it.isReverted }
                    if (hasTouchedFiles) {
                        IconButton(onClick = viewModel::rollbackRun) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Rollback run modifications",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    if (state.transcript.isNotEmpty()) {
                        IconButton(onClick = viewModel::startNewConversation) {
                            Icon(Icons.Default.Add, contentDescription = "New conversation")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Sleek collapsible health banner
            HealthBanner(state = state, onRetry = viewModel::checkHealth)

            // Ultra-compact project workspace context bar (replaces the bulky 90dp textfield)
            WorkspaceContextBar(
                state = state,
                onEditDirectory = { showDirectoryDialog = true },
                onOpenFiles = onOpenFiles,
                onPullGit = { composerText = "git pull origin main" },
            )

            state.error?.let { error ->
                VmErrorPanel(
                    error = error,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                    onRetry = viewModel::checkHealth,
                )
            }

            // Main chat content or modern empty state hero
            if (state.transcript.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyChatHero(
                        workingDirectory = state.workingDirectory,
                        onSuggestionClick = { prompt ->
                            composerText = prompt
                        },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(groupedTranscript, key = { it.id }) { rowItem ->
                        when (rowItem) {
                            is TranscriptRowItem.Single -> TranscriptRow(
                                item = rowItem.item,
                                onOpenDiff = onOpenDiff,
                                onRollback = viewModel::rollbackFile,
                            )
                            is TranscriptRowItem.ToolBatch -> ToolBatchRow(
                                batch = rowItem,
                                onOpenDiff = onOpenDiff,
                                onRollback = viewModel::rollbackFile,
                            )
                        }
                    }
                }
            }

            // Background runs if any exist
            BackgroundRunsSection(
                runs = state.backgroundRuns,
                onOpenLogs = viewModel::backgroundLogs,
                onStop = viewModel::stopBackgroundRun,
                onRefresh = viewModel::refreshBackgroundRuns,
            )

            // Sleek mode selector docked above composer
            ModeSelectorRow(
                currentMode = state.permissionMode,
                enabled = !state.isRunning,
                onModeSelect = viewModel::setPermissionMode,
            )

            // Redesigned modern composer
            ModernComposer(
                text = composerText,
                onTextChange = { composerText = it },
                enabled = state.canRun,
                isRunning = state.isRunning,
                isResuming = state.isResuming,
                isStartingBackgroundRun = state.isStartingBackgroundRun,
                onSend = viewModel::send,
                onSendInBackground = viewModel::sendInBackground,
                onStop = viewModel::stop,
            )
        }
    }
}

/**
 * Sleek, modern Workspace Context Bar replacing the huge outlined text field.
 * Displays remote directory with quick tap-to-edit.
 */
@Composable
private fun WorkspaceContextBar(
    state: AgentChatUiState,
    onEditDirectory: () -> Unit,
    onOpenFiles: () -> Unit,
    onPullGit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val dirName = remember(state.workingDirectory) {
        val trimmed = state.workingDirectory.trimEnd('/')
        trimmed.substringAfterLast('/', trimmed)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VmTheme.colors.divider),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Top Row: Project Title & Branch Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = dirName.ifBlank { "Remote Workspace" },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Git Branch Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VmTheme.colors.successContainer.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, VmTheme.colors.success.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🌿 main",
                            style = VmTheme.code.mono.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = VmTheme.colors.success,
                        )
                    }
                }
            }

            // Bottom Row: Path & Quick Action Chips (Files, Pull, Edit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.workingDirectory.ifBlank { "Tap to set path" },
                    style = VmTheme.code.mono.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !state.isRunning, onClick = onEditDirectory),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Files Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenFiles),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Files",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = "Files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Git Pull Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onPullGit),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Pull",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = "Pull",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Edit Path Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !state.isRunning, onClick = onEditDirectory),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit directory",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimal, clean dialog to inspect and edit the working directory.
 */
@Composable
private fun EditDirectoryDialog(
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

/**
 * Modern, styled segmented pill selector for agent permission modes.
 */
@Composable
private fun ModeSelectorRow(
    currentMode: AgentPermissionMode,
    enabled: Boolean,
    onModeSelect: (AgentPermissionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentPermissionMode.entries.forEach { mode ->
            val isSelected = currentMode == mode
            val (icon, label) = when (mode) {
                AgentPermissionMode.PLAN -> Icons.Default.Shield to "Plan only"
                AgentPermissionMode.ACCEPT_EDITS -> Icons.Default.AutoFixHigh to "Auto-edit"
                AgentPermissionMode.MANUAL -> Icons.Default.HelpOutline to "Ask first"
                AgentPermissionMode.BYPASS -> Icons.Default.RocketLaunch to "Full auto"
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) {
                    VmTheme.colors.agentContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) VmTheme.colors.agent else VmTheme.colors.divider,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = enabled) { onModeSelect(mode) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) VmTheme.colors.agent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) {
                            VmTheme.colors.onAgentContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Beautiful AI Welcome hero with curated action cards when transcript is empty.
 */
@Composable
private fun EmptyChatHero(
    workingDirectory: String,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val dirName = remember(workingDirectory) {
        val trimmed = workingDirectory.trimEnd('/')
        trimmed.substringAfterLast('/', "Repository")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Glowing Sparkle Avatar
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.35f),
                            Color(0xFF6366F1).copy(alpha = 0.15f),
                            Color.Transparent,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = VmTheme.colors.agentContainer,
                border = BorderStroke(1.5.dp, VmTheme.colors.agent),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = VmTheme.colors.agent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Text(
            text = "AI Coding Partner",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = spacing.sm),
        )

        Text(
            text = "Connected to $dirName",
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp, bottom = spacing.md),
        )

        // Suggestion Action Cards
        val suggestions = listOf(
            Triple(
                Icons.Default.AccountTree,
                "Explain Architecture",
                "Analyze project structure, dependencies, and key entrypoints.",
            ),
            Triple(
                Icons.Default.BugReport,
                "Audit & Find Bugs",
                "Scan recent files for potential syntax bugs and runtime issues.",
            ),
            Triple(
                Icons.Default.Science,
                "Generate Unit Tests",
                "Create comprehensive test cases for core application flows.",
            ),
            Triple(
                Icons.Default.Difference,
                "Review Git Status",
                "Inspect uncommitted changes and summarize local modifications.",
            ),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { (icon, title, prompt) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, VmTheme.colors.divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSuggestionClick(prompt) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
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
private fun HealthBanner(
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

private sealed interface TranscriptRowItem {
    val id: String
    data class Single(val item: TranscriptItem) : TranscriptRowItem {
        override val id: String get() = item.id
    }
    data class ToolBatch(
        override val id: String,
        val tools: List<TranscriptItem.ToolCall>,
    ) : TranscriptRowItem
}

private fun groupTranscript(items: List<TranscriptItem>): List<TranscriptRowItem> {
    val result = mutableListOf<TranscriptRowItem>()
    val currentTools = mutableListOf<TranscriptItem.ToolCall>()

    fun flushTools() {
        if (currentTools.isEmpty()) return
        if (currentTools.size == 1) {
            result.add(TranscriptRowItem.Single(currentTools.first()))
        } else {
            result.add(
                TranscriptRowItem.ToolBatch(
                    id = "batch-${currentTools.first().id}-${currentTools.size}",
                    tools = currentTools.toList(),
                ),
            )
        }
        currentTools.clear()
    }

    for (item in items) {
        if (item is TranscriptItem.ToolCall) {
            currentTools.add(item)
        } else {
            flushTools()
            result.add(TranscriptRowItem.Single(item))
        }
    }
    flushTools()
    return result
}

/**
 * Aggregated batch card for consecutive tool calls (matching Web Cockpit 2.0).
 * Prevents long streams of CLI/bash commands from cluttering the conversation.
 */
@Composable
private fun ToolBatchRow(
    batch: TranscriptRowItem.ToolBatch,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val isRunning = batch.tools.any { it.isRunning }
    val hasError = batch.tools.any { it.isError }
    val successCount = batch.tools.count { !it.isError && !it.isRunning }
    val spacing = VmTheme.spacing

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (hasError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else VmTheme.colors.divider,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else if (hasError) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    Text(
                        text = if (isRunning) "Running ${batch.tools.size} tools..." else "⚡ Ran ${batch.tools.size} tools",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = if (isRunning) "in progress" else "$successCount/${batch.tools.size} succeeded",
                            style = VmTheme.code.mono.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "Hide" else "Details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    batch.tools.forEach { tool ->
                        ToolRow(item = tool, onOpenDiff = onOpenDiff, onRollback = onRollback)
                    }
                }
            }
        }
    }
}

/**
 * Message Row in the conversation transcript.
 */
@Composable
private fun TranscriptRow(
    item: TranscriptItem,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)? = null,
) {
    val spacing = VmTheme.spacing

    when (item) {
        is TranscriptItem.UserPrompt -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        is TranscriptItem.AssistantText -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = VmTheme.colors.agentContainer,
                modifier = Modifier
                    .size(26.dp)
                    .padding(top = 2.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = VmTheme.colors.agent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, VmTheme.colors.divider),
                modifier = Modifier.weight(1f),
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MarkdownText(markdown = item.text)
                }
            }
        }

        is TranscriptItem.StreamingText -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = VmTheme.colors.agentContainer,
                modifier = Modifier
                    .size(26.dp)
                    .padding(top = 2.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = VmTheme.colors.agent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, VmTheme.colors.divider),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.text + "▌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        is TranscriptItem.Reasoning -> CollapsibleReasoning(item)

        is TranscriptItem.ToolCall -> ToolRow(item, onOpenDiff = onOpenDiff, onRollback = onRollback)

        is TranscriptItem.Diagnostic -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = VmTheme.colors.codeSurface,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = item.text,
                style = VmTheme.code.mono.copy(fontSize = 11.sp),
                color = if (item.isStderr) VmTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(spacing.sm),
            )
        }

        is TranscriptItem.RunSummary -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (item.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (item.isError) MaterialTheme.colorScheme.error else VmTheme.colors.success,
                    modifier = Modifier.size(14.dp),
                )
                VmChip(text = "${item.durationMillis / 1000}s", monospace = true)
                VmChip(text = "${item.inputTokens + item.outputTokens} tokens", monospace = true)
                if (item.costUsd > 0) {
                    VmChip(text = "$%.4f".format(item.costUsd), monospace = true)
                }
            }
        }

        is TranscriptItem.Failure -> VmErrorPanel(error = item.error)

        is TranscriptItem.PlanChecklist -> PlanChecklistCard(item = item)
    }
}

@Composable
private fun PlanChecklistCard(item: TranscriptItem.PlanChecklist) {
    val spacing = VmTheme.spacing
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Implementation Plan",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                val completedCount = item.steps.count { it.status.equals("completed", ignoreCase = true) }
                Text(
                    text = "$completedCount / ${item.steps.size}",
                    style = VmTheme.code.mono.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            androidx.compose.material3.HorizontalDivider(color = VmTheme.colors.divider)

            item.steps.forEach { step ->
                val isCompleted = step.status.equals("completed", ignoreCase = true)
                val isInProgress = step.status.equals("in_progress", ignoreCase = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        isCompleted -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VmTheme.colors.success,
                            modifier = Modifier.size(16.dp),
                        )
                        isInProgress -> CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                ),
                        )
                    }
                    Text(
                        text = step.step,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None,
                        ),
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Modern Thought Process accordion for agent reasoning.
 */
@Composable
private fun CollapsibleReasoning(item: TranscriptItem.Reasoning) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = VmTheme.spacing

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VmTheme.colors.agentContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, VmTheme.colors.agent.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = VmTheme.colors.agent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (expanded) "Thinking Process" else "Thinking Process (tap to expand)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = VmTheme.colors.onAgentContainer,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VmTheme.colors.agent,
                    modifier = Modifier.size(14.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = VmTheme.colors.onAgentContainer,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        }
    }
}

/**
 * Terminal & tool execution card.
 */
@Composable
private fun ToolRow(
    item: TranscriptItem.ToolCall,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = VmTheme.spacing
    val hasOutput = !item.output.isNullOrBlank()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VmTheme.colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (hasOutput) Modifier.clickable { expanded = !expanded } else Modifier),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    item.isRunning -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    item.isError -> Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    else -> Icon(
                        imageVector = if (item.name.contains("bash", ignoreCase = true)) {
                            Icons.Default.Terminal
                        } else {
                            Icons.Default.Build
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val affectedPath = item.affectedPath
                if (affectedPath != null) {
                    if (item.isReverted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "Reverted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else if (onRollback != null) {
                        IconButton(
                            onClick = { onRollback(item.id, affectedPath) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Rollback changes",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = { onOpenDiff(affectedPath) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Difference,
                            contentDescription = "Review changes",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded && hasOutput) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0B0F14),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    val rawOutput = item.output.orEmpty()
                    val outputLines = rawOutput.lines()
                    val isDiff = outputLines.any { it.startsWith("+") || it.startsWith("-") }
                    if (isDiff) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            outputLines.take(300).forEach { line ->
                                val (bg, textColor) = when {
                                    line.startsWith("+++") || line.startsWith("---") -> Color(0xFF1E293B) to Color(0xFF94A3B8)
                                    line.startsWith("+") -> Color(0xFF143823) to Color(0xFF4ADE80)
                                    line.startsWith("-") -> Color(0xFF38181D) to Color(0xFFF87171)
                                    line.startsWith("@@") -> Color(0xFF1E1B4B) to Color(0xFF818CF8)
                                    else -> Color.Transparent to Color(0xFFCBD5E1)
                                }
                                Text(
                                    text = line,
                                    style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                    color = textColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            text = rawOutput,
                            style = VmTheme.code.mono.copy(fontSize = 11.sp),
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern floating composer with multi-line text field, background run trigger,
 * and high-contrast circular gradient action button.
 */
@Composable
private fun ModernComposer(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isRunning: Boolean,
    isResuming: Boolean,
    isStartingBackgroundRun: Boolean,
    onSend: (String) -> Unit,
    onSendInBackground: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, VmTheme.colors.divider),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                enabled = !isRunning,
                placeholder = {
                    Text(
                        text = if (isResuming) "Continue discussion..." else "Ask agent or describe changes...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Detached Background Run Button
                IconButton(
                    onClick = {
                        val toSend = text
                        onTextChange("")
                        onSendInBackground(toSend)
                    },
                    enabled = enabled && text.isNotBlank() && !isStartingBackgroundRun,
                    modifier = Modifier.size(34.dp),
                ) {
                    if (isStartingBackgroundRun) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Run in background",
                            tint = if (enabled && text.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                if (isRunning) {
                    // Glowing Red Stop Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onStop),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop agent",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                } else {
                    // Sleek gradient Send button
                    val canSend = enabled && text.isNotBlank()
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(
                                if (canSend) {
                                    Modifier.background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(enabled = canSend) {
                                val toSend = text
                                onTextChange("")
                                onSend(toSend)
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
 * Detached runs for this project.
 */
@Composable
private fun BackgroundRunsSection(
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
