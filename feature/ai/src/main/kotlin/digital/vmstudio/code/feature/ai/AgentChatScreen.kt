package digital.vmstudio.code.feature.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
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
    onOpenBrowser: ((String) -> Unit)? = null,
    viewModel: AgentChatViewModel = hiltViewModel(),
    conversationsViewModel: AgentConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val listState = rememberLazyListState()
    val composerFocusRequester = remember { FocusRequester() }
    var composerText by remember { mutableStateOf(state.initialPrompt.orEmpty()) }
    val lastUserPrompt = remember(state.transcript) {
        state.transcript.filterIsInstance<TranscriptItem.UserPrompt>().lastOrNull()?.text
    }
    androidx.compose.runtime.LaunchedEffect(state.initialPrompt) {
        if (!state.initialPrompt.isNullOrBlank() && composerText.isBlank()) {
            composerText = state.initialPrompt.orEmpty()
        }
    }
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var requestOpenProjects by remember { mutableStateOf(false) }
    var directoryServerId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true,
        )
    }
    var dismissNotificationBanner by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotificationPermission = granted
    }
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
        currentColorHex = pendingChatAction?.first?.id?.let { chats.chatColors[it] },
        onDismiss = { pendingChatAction = null },
        onRename = conversationsViewModel::rename,
        onDelete = { id ->
            conversationsViewModel.delete(id)
            viewModel.startNewConversation()
        },
        onSetColor = conversationsViewModel::updateColor,
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
    val previewTarget = rememberHtmlPreview(state.serverId, onOpenBrowser)
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

    var showAntigravitySheet by remember { mutableStateOf(false) }

    if (showAntigravitySheet) {
        AntigravitySessionsSheet(
            isOpen = showAntigravitySheet,
            onDismiss = { showAntigravitySheet = false },
            sessions = state.antigravitySessions,
            isLoading = state.isLoadingAntigravitySessions,
            serverName = state.availableServers.firstOrNull { it.id == state.serverId }?.name ?: "Connected Laptop",
            onRefresh = { viewModel.loadAntigravitySessions() },
            onSelectSession = { session ->
                viewModel.attachAntigravitySession(session)
                Toast.makeText(context, "Attached to Antigravity session ${session.id.take(8)}", Toast.LENGTH_SHORT).show()
            },
            onStartNewSession = {
                viewModel.clearAntigravitySession()
                Toast.makeText(context, "Ready to start new Antigravity session on laptop", Toast.LENGTH_SHORT).show()
            },
        )
    }

    var showCloudBuildDialog by remember { mutableStateOf(false) }
    var isCloudBuilding by remember { mutableStateOf(false) }
    var cloudBuildError by remember { mutableStateOf<String?>(null) }

    if (showCloudBuildDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCloudBuilding) showCloudBuildDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = Color(0xFF26A69A),
                    )
                    Text("GitHub Cloud APK Builder")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dispatch an automated Cloud APK Build on GitHub Actions?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "• Cloud runner compiles latest main\n• Publishes directly to App Hub (apps.vmstudio.digital)\n• Ready to install on your phone in minutes\n• 100% cloud execution — zero phone battery drain",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isCloudBuilding) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Dispatching to GitHub Actions...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    cloudBuildError?.let { errText ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(
                                text = errText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCloudBuilding,
                    onClick = {
                        isCloudBuilding = true
                        cloudBuildError = null
                        viewModel.triggerCloudBuild { success, msg ->
                            isCloudBuilding = false
                            if (success) {
                                showCloudBuildDialog = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                cloudBuildError = msg
                            }
                        }
                    },
                ) {
                    Text("Build & Deploy Now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isCloudBuilding,
                    onClick = {
                        cloudBuildError = null
                        showCloudBuildDialog = false
                    },
                ) {
                    Text("Cancel")
                }
            },
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
                    onTriggerCloudBuild = { showCloudBuildDialog = true },
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
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Full Screen Focus • Tap to exit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
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
                val hasActiveContext = (!state.serverId.isNullOrBlank() && !RemoteFileSystem.isLocalServer(state.serverId)) ||
                    state.workingDirectory.startsWith("/github/") ||
                    state.workingDirectory.startsWith("/vmhub/") ||
                    (state.workingDirectory.isNotBlank() && state.workingDirectory != "/" && state.workingDirectory != "/workspace")

                Column {
                    if (hasActiveContext) {
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
                            clusterNodes = state.clusterNodes,
                            onRefreshCluster = viewModel::refreshClusterNodes,
                        )
                    }

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
                        onOpenChatHistory = onOpenChatHistory,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val customUriHandler = remember(onOpenBrowser, context) {
                        object : androidx.compose.ui.platform.UriHandler {
                            override fun openUri(uri: String) {
                                if (onOpenBrowser != null && (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true))) {
                                    onOpenBrowser(uri)
                                } else {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalUriHandler provides customUriHandler,
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
                                        onEditPrompt = { text, promptId ->
                                            composerText = text
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(groupedTranscript.size)
                                                composerFocusRequester.requestFocus()
                                            }
                                        },
                                        onDeletePrompt = { promptId, text ->
                                            viewModel.rewindToPrompt(promptId)
                                            composerText = text
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(groupedTranscript.size)
                                                composerFocusRequester.requestFocus()
                                            }
                                            Toast.makeText(context, "Turn deleted & prompt loaded for editing", Toast.LENGTH_SHORT).show()
                                        },
                                        onFixError = viewModel::fixErrorWithAi,
                                        onInstallApk = viewModel::installApk,
                                        chatBubbleColorHex = currentChat?.id?.let { chats.chatColors[it] }
                                            ?: state.chatBubbleColorHex,
                                    )
                                    is TranscriptRowItem.ToolBatch -> ToolBatchRow(
                                        batch = rowItem,
                                        onOpenDiff = openDiff,
                                        onRollback = viewModel::rollbackFile,
                                        onPreview = previewTarget,
                                        onFixError = viewModel::fixErrorWithAi,
                                        onInstallApk = viewModel::installApk,
                                    )
                                }
                            }
                            runStatus(state.isRunning, state.transcript)
                        }
                    }

                    val showScrollToBottom by remember {
                        derivedStateOf {
                            val total = groupedTranscript.size
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            total > 2 && lastVisible < total - 1
                        }
                    }
                    if (showScrollToBottom) {
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(groupedTranscript.size)
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp)
                                .size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to bottom",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp),
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

            // Plan-Only Mode Notice for Beginners
            if (state.permissionMode == digital.vmstudio.code.core.ai.model.AgentPermissionMode.PLAN) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Plan Mode active · Proposes changes without modifying files. Switch to 'Accept Edits' in (+) to apply changes.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Notification Permission Prompt Banner for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission && !dismissNotificationBanner) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Task & Build Alerts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Get notified when background agents or cloud builds complete.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Allow", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { dismissNotificationBanner = true },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            ModernComposer(
                text = composerText,
                onTextChange = { composerText = it },
                enabled = state.canAcceptInput,
                isRunning = state.isRunning,
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
                onTriggerCloudBuild = { showCloudBuildDialog = true },
                onOpenConnectors = onOpenConnectors,
                onOpenAntigravity = {
                    viewModel.loadAntigravitySessions()
                    showAntigravitySheet = true
                },
                activeAntigravitySessionId = state.activeAntigravitySessionId,
                onClearAntigravitySession = viewModel::clearAntigravitySession,
                onDetach = viewModel::detachToBackground,
                focusRequester = composerFocusRequester,
                lastUserPrompt = lastUserPrompt,
                onRecallLastPrompt = lastUserPrompt?.let { prompt ->
                    {
                        composerText = prompt
                        coroutineScope.launch {
                            composerFocusRequester.requestFocus()
                        }
                    }
                },
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
