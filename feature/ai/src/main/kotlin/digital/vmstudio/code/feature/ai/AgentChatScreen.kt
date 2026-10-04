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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import digital.vmstudio.code.core.ai.provider.AiProviderKind
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
import digital.vmstudio.code.core.project.Project
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
    onNavigateToSettings: () -> Unit = {},
    onNavigateToServers: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    viewModel: AgentChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var composerText by remember { mutableStateOf("") }
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showEngineMenu by remember { mutableStateOf(false) }
    var showModelSelectionModal by remember { mutableStateOf(false) }
    var showQuickActionsMenu by remember { mutableStateOf(false) }
    var isWebSearchEnabled by remember { mutableStateOf(true) }

    val isHubAgent = state.selectedEngine == AiProviderKind.OMNIROUTE

    val currentProjectName = remember(state.workingDirectory, state.availableProjects) {
        state.availableProjects.firstOrNull { it.remotePath == state.workingDirectory }?.name
            ?: state.workingDirectory.trimEnd('/').substringAfterLast('/').ifBlank { "AI Agent" }
    }

    val currentModelLabel = remember(state.selectedModel, state.availableModels) {
        val found = state.availableModels.firstOrNull { it.id == state.selectedModel }
        if (found != null) {
            if (found.isFree) "${found.name} (Free)" else found.name
        } else {
            when (state.selectedModel) {
                "cl/DeepSeek V4 Flash (Free)" -> "DeepSeek V4 (Free)"
                "claude-3-7-sonnet-latest" -> "Claude 3.7"
                "claude-3-5-sonnet-latest" -> "Claude 3.5"
                "omniroute/gpt-4o" -> "GPT-4o"
                "claude-3-5-haiku-latest" -> "Haiku 3.5"
                else -> state.selectedModel.substringAfterLast('/').take(18)
            }
        }
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

    if (showModelSelectionModal) {
        ModelSelectionDialog(
            selectedModel = state.selectedModel,
            availableModels = state.availableModels,
            isSyncing = state.isSyncingModels,
            onSelectModel = viewModel::selectModel,
            onSyncModels = viewModel::syncModels,
            onDismiss = { showModelSelectionModal = false },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AgentSidebarDrawer(
                projectName = currentProjectName,
                workingDirectory = state.workingDirectory,
                availableProjects = state.availableProjects,
                recentConversations = state.recentConversations,
                onNewChat = {
                    viewModel.startNewConversation()
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectProject = { proj ->
                    viewModel.selectProject(proj)
                    coroutineScope.launch { drawerState.close() }
                },
                onOpenConversation = { id ->
                    viewModel.openConversation(id)
                    coroutineScope.launch { drawerState.close() }
                },
                onNavigateToSettings = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
            )
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "x-codes",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                actions = {
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
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Conversation")
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
            // Sleek collapsible health banner (hidden in OmniRoute mode)
            HealthBanner(
                state = state,
                onRetry = viewModel::checkHealth,
                onSwitchToOmniRoute = { viewModel.selectEngine(AiProviderKind.OMNIROUTE) },
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
                                onSwitchToOmniRoute = { viewModel.selectEngine(AiProviderKind.OMNIROUTE) },
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

            // Chat controls docked directly above composer (Thumb ergonomics)
            ChatControlsDock(
                state = state,
                currentModelLabel = currentModelLabel,
                isWebSearchEnabled = isWebSearchEnabled,
                onToggleWebSearch = { isWebSearchEnabled = !isWebSearchEnabled },
                onOpenModelPicker = { showModelSelectionModal = true },
                onSelectMode = viewModel::setPermissionMode,
                onSyncModels = viewModel::syncModels,
                enabled = !state.isRunning,
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
}

/**
 * Sleek, modern Workspace Context Bar replacing the huge outlined text field.
 * Displays remote directory with quick tap-to-edit.
 */
/**
 * Sleek, modern Workspace Context Bar matching Web Cockpit 2.0.
 * Displays project directory, git branch, AI engine & model HUD pills, and quick action chips.
 */
@Composable
private fun WorkspaceContextBar(
    state: AgentChatUiState,
    onEditDirectory: () -> Unit,
    onOpenFiles: () -> Unit,
    onPullGit: () -> Unit,
    onSelectEngine: (AiProviderKind) -> Unit,
    onOpenModelPicker: () -> Unit,
    onSelectMode: (AgentPermissionMode) -> Unit,
    onQuickAction: (String) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Cockpit 2.0 HUD Pills (Engine, Model, Mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Engine Pill (Hub Agent vs Claude CLI)
                val isHub = state.selectedEngine == AiProviderKind.OMNIROUTE
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isHub) Color(0xFF1E3A8A).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, if (isHub) Color(0xFF3B82F6) else VmTheme.colors.divider),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val next = if (isHub) AiProviderKind.CLAUDE_CODE_CLI else AiProviderKind.OMNIROUTE
                            onSelectEngine(next)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isHub) "🤖 Hub Agent Loop" else "⚡ Claude CLI",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isHub) Color(0xFF93C5FD) else MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch engine",
                            tint = if (isHub) Color(0xFF93C5FD) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }

                // Model Pill with Live Sync indicator
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenModelPicker),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val modelLabel = remember(state.selectedModel) {
                            state.selectedModel.substringAfterLast('/').take(18)
                        }
                        Text(
                            text = "✨ $modelLabel",
                            style = VmTheme.code.mono.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFA78BFA),
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select model",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                // Mode Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, VmTheme.colors.divider),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val nextMode = when (state.permissionMode) {
                                AgentPermissionMode.PLAN -> AgentPermissionMode.ACCEPT_EDITS
                                AgentPermissionMode.ACCEPT_EDITS -> AgentPermissionMode.BYPASS
                                AgentPermissionMode.BYPASS -> AgentPermissionMode.MANUAL
                                AgentPermissionMode.MANUAL -> AgentPermissionMode.PLAN
                            }
                            onSelectMode(nextMode)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (state.permissionMode) {
                                AgentPermissionMode.PLAN -> "🛡️ Plan only"
                                AgentPermissionMode.ACCEPT_EDITS -> "🪄 Auto-edit"
                                AgentPermissionMode.BYPASS -> "🚀 Full auto"
                                AgentPermissionMode.MANUAL -> "❓ Ask first"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Web Search Toggle Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onQuickAction("Enable web search and fetch live data") },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🌐 Web: ON",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF34D399),
                        )
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
 * Modern modal dialog for live AI model selection with category filters and real-time sync.
 */
@Composable
private fun ModelSelectionDialog(
    selectedModel: String,
    availableModels: List<AgentModelItem>,
    isSyncing: Boolean,
    onSelectModel: (String) -> Unit,
    onSyncModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredModels = remember(availableModels, searchQuery, selectedCategory) {
        availableModels.filter { model ->
            val matchesSearch = searchQuery.isBlank() ||
                model.name.contains(searchQuery, ignoreCase = true) ||
                model.id.contains(searchQuery, ignoreCase = true) ||
                model.description.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                "✨ Free" -> model.isFree
                "🧠 Reasoning" -> model.name.contains("DeepSeek", ignoreCase = true) ||
                    model.name.contains("Sonnet", ignoreCase = true) ||
                    model.description.contains("reasoning", ignoreCase = true)
                "⚡ Fast" -> model.name.contains("Flash", ignoreCase = true) ||
                    model.name.contains("Haiku", ignoreCase = true) ||
                    model.name.contains("Fast", ignoreCase = true)
                "Claude" -> model.provider == AiProviderKind.CLAUDE_CODE_CLI
                else -> true
            }

            matchesSearch && matchesCategory
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "AI Models & Gateways",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${availableModels.size} live models available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Live Sync Button
                    IconButton(
                        onClick = onSyncModels,
                        enabled = !isSyncing,
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync models live",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search models (e.g. DeepSeek, Qwen, GPT-4o)...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )

                Spacer(Modifier.height(8.dp))

                // Filter Chips
                val categories = listOf("All", "✨ Free", "⚡ Fast", "🧠 Reasoning", "Claude")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { cat ->
                        val isCatSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCatSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isCatSelected) MaterialTheme.colorScheme.primary else VmTheme.colors.divider,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedCategory = cat },
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isCatSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Model List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredModels, key = { it.id }) { model ->
                        val isSelected = model.id == selectedModel
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else VmTheme.colors.divider,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectModel(model.id)
                                    onDismiss()
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = model.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (model.isFree) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                            ) {
                                                Text(
                                                    text = "FREE",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                    ),
                                                    color = Color(0xFF34D399),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = model.description,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Text(
                                        text = model.id,
                                        style = VmTheme.code.mono.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectModel(model.id)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    VmButton(
                        text = "Close",
                        onClick = onDismiss,
                        style = VmButtonStyle.Tertiary,
                    )
                }
            }
        }
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
        // Glowing Kimi-Style Avatar Orb
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.45f),
                            Color(0xFF8B5CF6).copy(alpha = 0.25f),
                            Color.Transparent,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = Color(0xFF67E8F9),
                border = BorderStroke(2.dp, Color(0xFFE0F2FE)),
                shadowElevation = 8.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFBAE6FD), Color(0xFF38BDF8), Color(0xFF818CF8)),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Cute expressive eye dots like Kimi avatar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp, 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF0F172A)),
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp, 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF0F172A)),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Hi Webxpro Digital,\nwhat would you like to build today?",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Text(
            text = "Connected to $dirName · VPS 1 Web",
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        // Professional prompt suggestion cards
        val suggestions = listOf(
            Triple(
                Icons.Default.Terminal,
                "Explain Architecture",
                "Inspect directory structure, entry points, and dependencies",
            ),
            Triple(
                Icons.Default.AutoFixHigh,
                "Implement Feature",
                "Help design, write, or refactor application code",
            ),
            Triple(
                Icons.Default.Difference,
                "Review Git Changes",
                "Review working tree diff and summarize modifications",
            ),
            Triple(
                Icons.Default.Cloud,
                "OmniRoute AI Chat",
                "Ask coding questions, brainstorm design, or troubleshoot errors",
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
    onSwitchToOmniRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // If the user has selected Hub Agent Loop, Claude CLI status is not relevant
    if (state.selectedEngine == AiProviderKind.OMNIROUTE) return

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

    // Modern Amber Warning Banner with fast copy & 1-tap switch to free Hub Agent
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
                            text = if (!health.isAvailable) "Claude Code CLI offline" else "Claude login expired / not signed in",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFFDE68A),
                        )
                        Text(
                            text = "Switch to Free Hub Agent Loop (no Anthropic account needed)",
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
                // 1-Tap Free Switch Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2563EB),
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSwitchToOmniRoute),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Use Free Hub Loop",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }

                VmButton(
                    text = "Retry",
                    onClick = onRetry,
                    style = VmButtonStyle.Secondary,
                    modifier = Modifier.weight(0.7f),
                )
                VmButton(
                    text = if (isExpanded) "Less" else "Help",
                    onClick = { isExpanded = !isExpanded },
                    style = VmButtonStyle.Tertiary,
                    modifier = Modifier.weight(0.6f),
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
    onSwitchToOmniRoute: () -> Unit,
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
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MarkdownText(markdown = item.text)

                    // If Anthropic profile expired, show 1-tap Free alternative card
                    val isExpiredLogin = item.text.contains("Anthropic profile login expired", ignoreCase = true) ||
                        (item.text.contains("expired", ignoreCase = true) && item.text.contains("claude.ai", ignoreCase = true))

                    if (isExpiredLogin) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E3A8A).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "100% Free Alternative Available",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF93C5FD),
                                    )
                                }
                                Text(
                                    text = "No paid Claude account needed! Hub Agent Loop runs autonomously on VPS with free models.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color(0xFFE2E8F0),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(onClick = onSwitchToOmniRoute),
                                ) {
                                    Text(
                                        text = "⚡ Switch to Free Hub Agent Loop",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
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

/**
 * Thumb-friendly chat controls dock positioned directly above the input box.
 * Hosts single Model Selector, Autonomy Mode, Web Search toggle, and Live Sync.
 */
@Composable
private fun ChatControlsDock(
    state: AgentChatUiState,
    currentModelLabel: String,
    isWebSearchEnabled: Boolean,
    onToggleWebSearch: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onSelectMode: (AgentPermissionMode) -> Unit,
    onSyncModels: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Single Primary Model Selector Pill (Right at thumb reach above chatbox)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VmTheme.colors.agentContainer,
            border = BorderStroke(1.dp, VmTheme.colors.agent.copy(alpha = 0.5f)),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onOpenModelPicker),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = VmTheme.colors.agent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = currentModelLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = VmTheme.colors.onAgentContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = VmTheme.colors.agent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Autonomy Permission Mode Pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) {
                    val nextMode = when (state.permissionMode) {
                        AgentPermissionMode.PLAN -> AgentPermissionMode.ACCEPT_EDITS
                        AgentPermissionMode.ACCEPT_EDITS -> AgentPermissionMode.BYPASS
                        AgentPermissionMode.BYPASS -> AgentPermissionMode.MANUAL
                        AgentPermissionMode.MANUAL -> AgentPermissionMode.PLAN
                    }
                    onSelectMode(nextMode)
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (icon, label) = when (state.permissionMode) {
                    AgentPermissionMode.PLAN -> Icons.Default.Shield to "Plan"
                    AgentPermissionMode.ACCEPT_EDITS -> Icons.Default.AutoFixHigh to "Auto-edit"
                    AgentPermissionMode.MANUAL -> Icons.Default.HelpOutline to "Ask first"
                    AgentPermissionMode.BYPASS -> Icons.Default.RocketLaunch to "Full auto"
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Web Search Enable / Disable Toggle Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isWebSearchEnabled) Color(0xFF0284C7).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, if (isWebSearchEnabled) Color(0xFF38BDF8) else VmTheme.colors.divider),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onToggleWebSearch),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Toggle Web Search",
                    tint = if (isWebSearchEnabled) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = if (isWebSearchEnabled) "Web: ON" else "Web: OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isWebSearchEnabled) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Model Live Sync Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled && !state.isSyncingModels, onClick = onSyncModels),
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                if (state.isSyncingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync models",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Streamlined Left-side Navigation Drawer.
 * Focuses purely on:
 * 1. New Chat
 * 2. Workspace & Project Switcher (including General AI mode)
 * 3. Recent Chat History
 * 4. Settings entry point at the bottom
 */
@Composable
private fun AgentSidebarDrawer(
    projectName: String,
    workingDirectory: String,
    availableProjects: List<Project>,
    recentConversations: List<ConversationSummary>,
    onNewChat: () -> Unit,
    onSelectProject: (Project?) -> Unit,
    onOpenConversation: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "x-codes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Universal AI Assistant",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = "100% Free",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF34D399),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Primary "+ New Chat" Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onNewChat),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "New Chat",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Scrollable Content: Projects & Chat History
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Section 1: Workspaces / Projects
                Text(
                    text = "WORKSPACE / PROJECT",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )

                // General AI (No Folder Attached)
                val isGeneral = workingDirectory.isBlank()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isGeneral) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    border = BorderStroke(1.dp, if (isGeneral) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectProject(null) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("ðŸŒ", fontSize = 14.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "General AI (No Project)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isGeneral) FontWeight.Bold else FontWeight.Normal),
                                color = if (isGeneral) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Chat, brainstorm, or ask any question",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isGeneral) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Available attached projects
                availableProjects.forEach { proj ->
                    val isSelected = proj.remotePath == workingDirectory
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectProject(proj) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proj.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = proj.remotePath,
                                    style = VmTheme.code.mono.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Section 2: Recent Chat History
                Text(
                    text = "RECENT CHATS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )

                if (recentConversations.isEmpty()) {
                    Text(
                        text = "No past conversations yet.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                } else {
                    recentConversations.take(20).forEach { conv ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenConversation(conv.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("ðŸ’¬", fontSize = 12.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conv.title.ifBlank { "Conversation" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    conv.workingDirectory?.takeIf { it.isNotBlank() }?.let { path ->
                                        Text(
                                            text = path.substringAfterLast('/'),
                                            style = VmTheme.code.mono.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Clean Settings Button at the bottom
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, VmTheme.colors.divider),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onNavigateToSettings),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Servers, Cloud Sync, AI Providers",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
