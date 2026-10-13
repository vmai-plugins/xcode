package digital.vmstudio.code.feature.ai

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
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
import digital.vmstudio.code.core.git.codespacesUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the top bar shows a menu button (opens the app drawer) instead of Back. */
    onOpenMenu: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    /**
     * Invoked when a detached run starts, so the host can begin watching it. The
     * watcher is a service in the app module, which a feature must not reach into.
     */
    onWatchBackgroundRuns: (serverId: String) -> Unit = {},
    onOpenDiff: (serverId: String, filePath: String) -> Unit = { _, _ -> },
    onOpenFiles: (serverId: String, path: String) -> Unit = { _, _ -> },
    viewModel: AgentChatViewModel = hiltViewModel(),
    conversationsViewModel: AgentConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    var showDirectoryDialog by remember { mutableStateOf(false) }
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Dynamic Environment Context Bar (GitHub, VPS, Google Connectors)
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
                    directoryServerId = null
                    showDirectoryDialog = true
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
                onOpenConnectors = onOpenSettings,
            )

            // Sleek collapsible health banner
            HealthBanner(
                state = state,
                onRetry = viewModel::checkHealth,
                onOpenSettings = onOpenSettings,
                onPickModel = viewModel::selectModel,
            )

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
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                selectedModel = state.selectedModel,
                onModelChange = viewModel::selectModel,
                omniModels = state.omniModels,
                isSyncingModels = state.isSyncingModels,
                onSyncModels = viewModel::syncModels,
                workingDirectory = state.workingDirectory,
                onEditDirectory = { showDirectoryDialog = true },
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
