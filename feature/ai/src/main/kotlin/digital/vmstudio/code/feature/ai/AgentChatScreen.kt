package digital.vmstudio.code.feature.ai

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.documentfile.provider.DocumentFile
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ui.component.VmErrorAction
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the top bar shows a menu button (opens the app drawer) instead of Back. */
    onOpenMenu: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onOpenConnectors: () -> Unit = onOpenSettings,
    /**
     * Invoked when a detached run starts, so the host can begin watching it. The
     * watcher is a service in the app module, which a feature must not reach into.
     */
    onWatchBackgroundRuns: (serverId: String) -> Unit = {},
    onOpenDiff: (serverId: String, filePath: String) -> Unit = { _, _ -> },
    onOpenFiles: (serverId: String, path: String) -> Unit = { _, _ -> },
    onAddNewProject: (() -> Unit)? = null,
    onOpenChatHistory: (() -> Unit)? = null,
    viewModel: AgentChatViewModel = hiltViewModel(),
    conversationsViewModel: AgentConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var requestOpenProjects by remember { mutableStateOf(false) }
    var directoryServerId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val localFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val folderName = DocumentFile.fromTreeUri(context, uri)?.name.orEmpty()
                viewModel.selectLocalFolder("${uri}!/", folderName)
            } catch (exception: SecurityException) {
                viewModel.reportLocalFolderError(exception.message)
            }
        }
    }
    val chats by conversationsViewModel.uiState.collectAsStateWithLifecycle()
    val currentChat = chats.conversations.firstOrNull { it.id == state.conversationId }
    val chatTitle = currentChat?.title?.takeIf { it.isNotBlank() }
    var pendingChatAction by remember {
        mutableStateOf<Pair<ConversationSummary, ConversationAction>?>(null)
    }
    // Slides the header away while reading and back on the first scroll up.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var isFullScreen by remember { mutableStateOf(false) }
    var isHeaderVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val chatScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -12f && isHeaderVisible) {
                    isHeaderVisible = false
                } else if (delta > 12f && !isHeaderVisible) {
                    isHeaderVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 10) {
            isHeaderVisible = true
        }
    }

    ConversationActionDialogs(
        pending = pendingChatAction,
        onDismiss = { pendingChatAction = null },
        onRename = conversationsViewModel::rename,
        onDelete = { id ->
            conversationsViewModel.delete(id)
            viewModel.startNewConversation()
        },
    )

    val currentProjectName = remember(state.workingDirectory, state.availableProjects) {
        state.availableProjects.firstOrNull { it.remotePath == state.workingDirectory }?.name
            ?: state.workingDirectory.trimEnd('/').substringAfterLast('/').ifBlank { "General chat" }
    }

    val groupedTranscript = remember(state.transcript) { groupTranscript(state.transcript) }

    state.watchServerId?.let { server ->
        LaunchedEffect(server) {
            onWatchBackgroundRuns(server)
            viewModel.onWatchStarted()
        }
    }

    FollowTail(listState, state.transcript)

    HostKeyPrompt(serverId = state.serverId)

    if (showDirectoryDialog) {
        val targetServerId = directoryServerId ?: state.serverId
        DirectoryChooser(
            serverId = targetServerId,
            currentDirectory = if (targetServerId == state.serverId) state.workingDirectory else "",
            onDismiss = {
                showDirectoryDialog = false
                directoryServerId = null
            },
            onChoose = { newDir ->
                if (targetServerId != null) {
                    viewModel.selectServerFolder(targetServerId, newDir)
                } else {
                    viewModel.setWorkingDirectory(newDir)
                }
                showDirectoryDialog = false
                directoryServerId = null
            },
        )
    }

    // Always offered: the menu also holds "General chat".
    val canSwitchProject = !state.isRunning
    val previewTarget = rememberHtmlPreview(state.serverId)
    // A reopened chat learns its server only once restored, so it is read at tap time.
    val openDiff: (String) -> Unit = { path -> state.serverId?.let { onOpenDiff(it, path) } }
    var attachments by remember { mutableStateOf(emptyList<Uri>()) }
    var showGithubTreeSheet by remember { mutableStateOf(false) }
    val githubRepoFullName = remember(state.workingDirectory) {
        if (state.workingDirectory.startsWith("/github/")) {
            state.workingDirectory.removePrefix("/github/")
        } else null
    }

    val artifacts = remember(state.transcript) {
        state.transcript.filterIsInstance<TranscriptItem.ToolCall>()
            .mapNotNull { item ->
                item.affectedPath?.let { path ->
                    SessionArtifact(filePath = path, isReverted = item.isReverted)
                }
            }
            .associateBy { it.filePath }
            .values
            .toList()
    }
    var showArtifactsSheet by remember { mutableStateOf(false) }

    if (showArtifactsSheet) {
        ArtifactsSheet(
            artifacts = artifacts,
            onOpenDiff = openDiff,
            onOpenFile = { path ->
                if (githubRepoFullName != null) {
                    showGithubTreeSheet = true
                } else {
                    state.serverId?.let { onOpenFiles(it, path) }
                }
            },
            onDismiss = { showArtifactsSheet = false },
        )
    }

    if (showGithubTreeSheet && githubRepoFullName != null) {
        GitHubFileTreeSheet(
            repoFullName = githubRepoFullName,
            files = state.githubFileTree,
            isLoading = state.isLoadingGithubFileTree,
            onRefresh = { viewModel.loadGithubFileTree(githubRepoFullName) },
            onSelectFile = { path ->
                composerText = if (composerText.isBlank()) "@file: $path " else "$composerText @file: $path "
            },
            onDismiss = { showGithubTreeSheet = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (!isFullScreen) {
                ChatTopBar(
                    title = chatTitle ?: currentProjectName,
                    isConnecting = state.isCheckingHealth,
                    projects = state.availableProjects,
                    workingDirectory = state.workingDirectory,
                    canSwitchProject = canSwitchProject,
                    hasConversation = currentChat != null,
                    hasTranscript = state.transcript.isNotEmpty(),
                    scrollBehavior = scrollBehavior,
                    onOpenMenu = onOpenMenu,
                    onNavigateBack = onNavigateBack,
                    onSelectProject = viewModel::selectProject,
                    onOpenCodespace = viewModel::selectGithubRepo,
                    githubRepositories = state.githubRepositories,
                    isLoadingGithubRepositories = state.isLoadingGithubRepositories,
                    githubError = state.githubError,
                    availableServers = state.availableServers,
                    onOpenGithubPicker = viewModel::loadGithubRepositories,
                    onRefreshGithubRepositories = viewModel::refreshGithubRepositories,
                    onChooseDirectory = {
                        directoryServerId = null
                        showDirectoryDialog = true
                    },
                    onChooseLocalFolder = { localFolderPicker.launch(null) },
                    onBrowseServer = { serverId ->
                        if (RemoteFileSystem.isLocalServer(serverId)) {
                            localFolderPicker.launch(null)
                        } else {
                            directoryServerId = serverId
                            showDirectoryDialog = true
                        }
                    },
                    discoveredGitRepos = state.discoveredGitRepos,
                    onSelectDiscoveredRepo = viewModel::selectDiscoveredGitRepo,
                    onOpenPreview = if (previewTarget != null && state.workingDirectory.isNotBlank()) {
                        { previewTarget(state.workingDirectory) }
                    } else null,
                    onNewChat = viewModel::startNewConversation,
                    onOpenFiles = if (githubRepoFullName != null) {
                        { showGithubTreeSheet = true }
                    } else {
                        { state.serverId?.let { onOpenFiles(it, state.workingDirectory.trim()) } }
                    },
                    onConversationAction = { action -> currentChat?.let { pendingChatAction = it to action } },
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { isFullScreen = !isFullScreen },
                    artifactsCount = artifacts.size,
                    onOpenArtifacts = { showArtifactsSheet = true },
                    onAddNewProject = onAddNewProject,
                    onQuickCreateProject = viewModel::quickCreateProject,
                    onOpenChatHistory = onOpenChatHistory,
                    requestOpenProjects = requestOpenProjects,
                    onRequestOpenProjectsConsumed = { requestOpenProjects = false },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullScreen) PaddingValues(0.dp) else padding)
                .imePadding(),
        ) {
            // Fullscreen focus floating indicator
            AnimatedVisibility(
                visible = isFullScreen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xCC1E1F22),
                        border = BorderStroke(1.dp, Color(0xFF2E3036)),
                        modifier = Modifier.clickable { isFullScreen = false },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Focus Mode",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF8E919A),
                            )
                            Text(
                                text = "Full Screen Focus • Tap to exit",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE2E2E5),
                            )
                        }
                    }
                }
            }

            // Smooth auto-hiding environment context bar and health banner
            AnimatedVisibility(
                visible = !isFullScreen && isHeaderVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    EnvironmentContextBar(
                        workingDirectory = state.workingDirectory,
                        serverName = state.availableServers.firstOrNull { it.id == state.serverId }?.name,
                        githubRepoName = when {
                            state.workingDirectory.startsWith("/github/") -> state.workingDirectory.removePrefix("/github/")
                            else -> state.githubRepositories.firstOrNull { repo ->
                                repo.fullName.equals(state.workingDirectory.trimEnd('/').substringAfterLast('/'), ignoreCase = true)
                            }?.fullName
                        },
                        onOpenProjectPicker = {
                            viewModel.loadGithubRepositories()
                            requestOpenProjects = true
                        },
                        onOpenServerPicker = {
                            val firstRemoteServer = state.availableServers
                                .firstOrNull { !RemoteFileSystem.isLocalServer(it.id) }
                                ?.id
                            if (firstRemoteServer != null) {
                                directoryServerId = firstRemoteServer
                                showDirectoryDialog = true
                            } else {
                                onOpenSettings()
                            }
                        },
                        onOpenConnectors = onOpenConnectors,
                    )

                    HealthBanner(
                        state = state,
                        onRetry = viewModel::checkHealth,
                        onOpenSettings = onOpenSettings,
                        onPickModel = viewModel::selectModel,
                    )
                }
            }

            state.error?.let { error ->
                VmErrorPanel(
                    error = error,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                    onRetry = viewModel::checkHealth,
                    actions = listOf(VmErrorAction("Dismiss", viewModel::dismissError)),
                )
            }
            state.gitDiscoveryError?.let { error ->
                VmErrorPanel(
                    error = error,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                    onRetry = viewModel::scanServerGitRepos,
                    actions = listOf(VmErrorAction("Dismiss", viewModel::dismissGitDiscoveryError)),
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
                        onSelectProjectClick = {
                            viewModel.loadGithubRepositories()
                            requestOpenProjects = true
                        },
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(chatScrollConnection),
                        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(groupedTranscript, key = { it.id }, contentType = { it::class }) { rowItem ->
                            when (rowItem) {
                                is TranscriptRowItem.Single -> TranscriptRow(
                                    item = rowItem.item,
                                    onOpenDiff = openDiff,
                                    onRollback = viewModel::rollbackFile,
                                    onPreview = previewTarget,
                                    chatBubbleColorHex = state.chatBubbleColorHex,
                                )
                                is TranscriptRowItem.ToolBatch -> ToolBatchRow(
                                    batch = rowItem,
                                    onOpenDiff = openDiff,
                                    onRollback = viewModel::rollbackFile,
                                    onPreview = previewTarget,
                                )
                            }
                        }
                        runStatus(state.isRunning, state.transcript)
                    }

                    val showScrollToBottom by remember {
                        derivedStateOf {
                            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 200
                        }
                    }
                    if (showScrollToBottom) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2E3036),
                            border = BorderStroke(1.dp, Color(0xFF3E4046)),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(spacing.md)
                                .size(36.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(groupedTranscript.size)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to bottom",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
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

            // Antigravity-style Queued Tasks Shelf
            QueuedTasksShelf(
                queued = state.queuedMessages,
                onSteerNow = viewModel::steerNow,
                onRemove = viewModel::removeQueuedMessage,
            )

            ModernComposer(
                text = composerText,
                onTextChange = { composerText = it },
                enabled = state.canAcceptInput,
                isRunning = state.isRunning,
                isResuming = state.isResuming,
                isStartingBackgroundRun = state.isStartingBackgroundRun,
                permissionMode = state.permissionMode,
                onPermissionModeChange = viewModel::setPermissionMode,
                squadPreset = state.squadPreset,
                onSquadPresetChange = viewModel::setSquadPreset,
                selectedModel = state.selectedModel,
                onModelChange = viewModel::selectModel,
                omniModels = state.omniModels,
                isSyncingModels = state.isSyncingModels,
                onSyncModels = viewModel::syncModels,
                workingDirectory = state.workingDirectory,
                onEditDirectory = { showDirectoryDialog = true },
                availableProjects = state.availableProjects,
                githubRepositories = state.githubRepositories,
                attachments = attachments,
                onAttach = { uri -> attachments = (attachments + uri).distinct() },
                onRemoveAttachment = { uri -> attachments = attachments - uri },
                onSend = { text ->
                    viewModel.send(text, attachments)
                    attachments = emptyList()
                },
                onSendInBackground = { text, cleared ->
                    viewModel.sendInBackground(text, attachments) {
                        attachments = emptyList()
                        cleared()
                    }
                },
                onStop = viewModel::stop,
                onOpenPreview = if (previewTarget != null && state.workingDirectory.isNotBlank()) {
                    { previewTarget(state.workingDirectory) }
                } else null,
                onOpenFiles = if (githubRepoFullName != null) {
                    { showGithubTreeSheet = true }
                } else state.serverId?.let { sid ->
                    { onOpenFiles(sid, state.workingDirectory.trim()) }
                },
                onDetach = viewModel::detachToBackground,
            )
        }
    }
}

/** Browses the server's folders live when a server is chosen; typing the path is the fallback. */
@Composable
private fun DirectoryChooser(
    serverId: String?,
    currentDirectory: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    if (serverId != null) {
        FolderPickerDialog(
            serverId = serverId,
            startPath = currentDirectory,
            onDismiss = onDismiss,
            onPick = onChoose,
        )
    } else {
        EditDirectoryDialog(
            currentDirectory = currentDirectory,
            onDismiss = onDismiss,
            onConfirm = onChoose,
        )
    }
}
