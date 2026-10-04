package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.VmErrorAction
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the top bar shows a menu button (opens the app drawer) instead of Back. */
    onOpenMenu: (() -> Unit)? = null,
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

    val currentProjectName = remember(state.workingDirectory, state.availableProjects) {
        state.availableProjects.firstOrNull { it.remotePath == state.workingDirectory }?.name
            ?: state.workingDirectory.trimEnd('/').substringAfterLast('/').ifBlank { "AI Agent" }
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

    val canSwitchProject = state.availableProjects.isNotEmpty() && !state.isRunning

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = canSwitchProject) {
                                    showProjectMenu = true
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentProjectName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp),
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
                            // Status is shown only when something is wrong or in flight.
                            if (state.isCheckingHealth) {
                                Text(
                                    text = "Connecting…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                    fontWeight = if (isSelected) {
                                                        FontWeight.Bold
                                                    } else {
                                                        FontWeight.Normal
                                                    },
                                                ),
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
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
                                            imageVector = if (isSelected) {
                                                Icons.Default.Check
                                            } else {
                                                Icons.Default.Folder
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
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
                },
                navigationIcon = {
                    if (onOpenMenu != null) {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFiles) {
                        Icon(Icons.Default.Folder, contentDescription = "Files")
                    }
                    if (state.transcript.isNotEmpty()) {
                        IconButton(onClick = viewModel::startNewConversation) {
                            Icon(Icons.Default.Add, contentDescription = "New conversation")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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

            state.error?.let { error ->
                VmErrorPanel(
                    error = error,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                    onRetry = viewModel::checkHealth,
                    actions = listOf(VmErrorAction("Dismiss", viewModel::dismissError)),
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
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

            ModernComposer(
                text = composerText,
                onTextChange = { composerText = it },
                enabled = state.canRun,
                isRunning = state.isRunning,
                isResuming = state.isResuming,
                isStartingBackgroundRun = state.isStartingBackgroundRun,
                permissionMode = state.permissionMode,
                onPermissionModeChange = viewModel::setPermissionMode,
                selectedModel = state.selectedModel,
                onModelChange = viewModel::selectModel,
                workingDirectory = state.workingDirectory,
                onEditDirectory = { showDirectoryDialog = true },
                onSend = viewModel::send,
                onSendInBackground = viewModel::sendInBackground,
                onStop = viewModel::stop,
            )
        }
    }
}
