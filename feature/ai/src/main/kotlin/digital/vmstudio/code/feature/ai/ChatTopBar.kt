package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import digital.vmstudio.code.core.git.DiscoveredGitRepo
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.git.GitHubRepository
import digital.vmstudio.code.core.ui.theme.VmTheme

/** One slim line, so the conversation gets the screen; it slides away while reading. */
private val ChatBarHeight = 48.dp

/**
 * The chat's header: a single line with the chat (or project) name and a few
 * icons. The project picker, Files, Rename and Delete sit behind the title or ⋮
 * instead of each taking room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    title: String,
    isConnecting: Boolean,
    projects: List<Project>,
    workingDirectory: String,
    canSwitchProject: Boolean,
    hasConversation: Boolean,
    hasTranscript: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenMenu: (() -> Unit)?,
    onNavigateBack: () -> Unit,
    /** Null starts a general chat with no project. */
    onSelectProject: (Project?) -> Unit,
    onOpenCodespace: ((GitHubRepository) -> Unit)? = null,
    githubRepositories: List<GitHubRepository> = emptyList(),
    isLoadingGithubRepositories: Boolean = false,
    githubError: digital.vmstudio.code.core.common.error.VmError? = null,
    availableServers: List<ChatServerOption> = emptyList(),
    onOpenGithubPicker: (() -> Unit)? = null,
    onRefreshGithubRepositories: (() -> Unit)? = null,
    onChooseDirectory: (() -> Unit)? = null,
    onChooseLocalFolder: (() -> Unit)? = null,
    onBrowseServer: ((String) -> Unit)? = null,
    discoveredGitRepos: List<digital.vmstudio.code.core.git.DiscoveredGitRepo> = emptyList(),
    onSelectDiscoveredRepo: ((digital.vmstudio.code.core.git.DiscoveredGitRepo) -> Unit)? = null,
    onOpenPreview: (() -> Unit)? = null,
    onNewChat: () -> Unit,
    onOpenFiles: () -> Unit,
    onConversationAction: (ConversationAction) -> Unit,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    artifactsCount: Int = 0,
    onOpenArtifacts: (() -> Unit)? = null,
    onAddNewProject: (() -> Unit)? = null,
    onQuickCreateProject: ((name: String, isLocal: Boolean) -> Unit)? = null,
    onOpenChatHistory: (() -> Unit)? = null,
    requestOpenProjects: Boolean = false,
    onRequestOpenProjectsConsumed: () -> Unit = {},
) {
    var showProjects by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(requestOpenProjects) {
        if (requestOpenProjects) {
            showProjects = true
            onOpenGithubPicker?.invoke()
            onRequestOpenProjectsConsumed()
        }
    }

    TopAppBar(
        expandedHeight = ChatBarHeight,
        scrollBehavior = scrollBehavior,
        title = {
            val displayTitle = when {
                isConnecting -> "Connectingâ€¦"
                title.isNotBlank() && title != "Agent Chat" -> title
                workingDirectory.isNotBlank() && workingDirectory != "/" -> workingDirectory.trimEnd('/').substringAfterLast('/')
                else -> "Select Project"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1F22))
                    .clickable(enabled = canSwitchProject) {
                        showProjects = true
                        onOpenGithubPicker?.invoke()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (workingDirectory.isNotBlank()) MaterialTheme.colorScheme.primary else Color(0xFF8E919A),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E2E5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (canSwitchProject) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch project",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF8E919A),
                    )
                }
            }
        },
        navigationIcon = {
            if (onOpenMenu != null) {
                IconButton(onClick = onOpenMenu) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            } else {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (onOpenPreview != null) {
                IconButton(onClick = onOpenPreview) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Web Preview",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (artifactsCount > 0 && onOpenArtifacts != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenArtifacts)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = "Deliverables",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = artifactsCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0E7FF),
                        )
                    }
                }
            }
            if (hasTranscript) {
                IconButton(onClick = onNewChat) { Icon(Icons.Default.Add, contentDescription = "New chat") }
            }
            if (onToggleFullScreen != null) {
                IconButton(onClick = onToggleFullScreen) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = if (isFullScreen) "Exit Focus" else "Smart Focus",
                        tint = if (isFullScreen) MaterialTheme.colorScheme.primary else Color(0xFF8E919A),
                    )
                }
            }
            Box {
                IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    if (artifactsCount > 0 && onOpenArtifacts != null) {
                        DropdownMenuItem(
                            text = { Text("Deliverables ($artifactsCount)") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                showMore = false
                                onOpenArtifacts()
                            },
                        )
                    }
                    if (onOpenChatHistory != null) {
                        DropdownMenuItem(
                            text = { Text("Chat History") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showMore = false
                                onOpenChatHistory()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Files") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            showMore = false
                            onOpenFiles()
                        },
                    )
                    if (hasConversation) {
                        DropdownMenuItem(
                            text = { Text("Rename chat") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMore = false
                                onConversationAction(ConversationAction.RENAME)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete chat", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                showMore = false
                                onConversationAction(ConversationAction.DELETE)
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
    ProjectMenu(
        expanded = showProjects,
        projects = projects,
        workingDirectory = workingDirectory,
        discoveredGitRepos = discoveredGitRepos,
        onDismiss = { showProjects = false },
        onSelect = { project ->
            showProjects = false
            onSelectProject(project)
        },
        onOpenCodespace = { repo ->
            showProjects = false
            onOpenCodespace?.invoke(repo)
        },
        githubRepositories = githubRepositories,
        isLoadingGithubRepositories = isLoadingGithubRepositories,
        githubError = githubError,
        availableServers = availableServers,
        onOpenGithubPicker = onOpenGithubPicker,
        onRefreshGithubRepositories = onRefreshGithubRepositories,
        onSelectDiscoveredRepo = { repo ->
            showProjects = false
            onSelectDiscoveredRepo?.invoke(repo)
        },
        onChooseDirectory = {
            showProjects = false
            onChooseDirectory?.invoke()
        },
        onChooseLocalFolder = {
            showProjects = false
            onChooseLocalFolder?.invoke()
        },
        onBrowseServer = { serverId ->
            showProjects = false
            onBrowseServer?.invoke(serverId)
        },
        onAddNewProject = onAddNewProject?.let { callback ->
            {
                showProjects = false
                callback()
            }
        },
        onQuickCreateProject = onQuickCreateProject,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectMenu(
    expanded: Boolean,
    projects: List<Project>,
    workingDirectory: String,
    discoveredGitRepos: List<digital.vmstudio.code.core.git.DiscoveredGitRepo> = emptyList(),
    onDismiss: () -> Unit,
    onSelect: (Project?) -> Unit,
    onOpenCodespace: ((GitHubRepository) -> Unit)? = null,
    githubRepositories: List<GitHubRepository> = emptyList(),
    isLoadingGithubRepositories: Boolean = false,
    githubError: digital.vmstudio.code.core.common.error.VmError? = null,
    availableServers: List<ChatServerOption> = emptyList(),
    onOpenGithubPicker: (() -> Unit)? = null,
    onRefreshGithubRepositories: (() -> Unit)? = null,
    onSelectDiscoveredRepo: ((digital.vmstudio.code.core.git.DiscoveredGitRepo) -> Unit)? = null,
    onChooseDirectory: (() -> Unit)? = null,
    onChooseLocalFolder: (() -> Unit)? = null,
    onBrowseServer: ((String) -> Unit)? = null,
    onAddNewProject: (() -> Unit)? = null,
    onQuickCreateProject: ((name: String, isLocal: Boolean) -> Unit)? = null,
) {
    if (!expanded) return

    var searchQuery by remember { mutableStateOf("") }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQuickCreateDialog by remember { mutableStateOf(false) }
    var quickProjectName by remember { mutableStateOf("") }
    var isLocalWorkspace by remember { mutableStateOf(true) }

    if (showQuickCreateDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQuickCreateDialog = false },
            title = { Text("Create New Project", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Quickly initialize a workspace to work on code directly in this chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = quickProjectName,
                        onValueChange = { quickProjectName = it },
                        label = { Text("Workspace Name") },
                        placeholder = { Text("e.g. my-app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = isLocalWorkspace,
                            onClick = { isLocalWorkspace = true },
                            label = { Text("Phone Storage") },
                        )
                        androidx.compose.material3.FilterChip(
                            selected = !isLocalWorkspace,
                            onClick = { isLocalWorkspace = false },
                            label = { Text("VPS Cloud") },
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        if (quickProjectName.isNotBlank()) {
                            onQuickCreateProject?.invoke(quickProjectName.trim(), isLocalWorkspace)
                            showQuickCreateDialog = false
                            onDismiss()
                        }
                    },
                    enabled = quickProjectName.isNotBlank(),
                ) {
                    Text("Create & Select")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showQuickCreateDialog = false
                        if (onAddNewProject != null) {
                            onDismiss()
                            onAddNewProject()
                        }
                    },
                ) {
                    Text("Advancedâ€¦")
                }
            },
        )
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // --- VS Code Style Search Box ---
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search repositories or switch workspace…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                // 0. Quick Action: Add / Create Project
                if (onAddNewProject != null && (searchQuery.isBlank() || "create new project".contains(searchQuery, ignoreCase = true) || "add project".contains(searchQuery, ignoreCase = true))) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onAddNewProject()
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        "Create New Project…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "Add a new local or remote workspace",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. General Chat (Workspace neutral)
                if (searchQuery.isBlank() || "general chat".contains(searchQuery, ignoreCase = true)) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (workingDirectory.isBlank()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onSelect(null)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (workingDirectory.isBlank()) Icons.Default.Check else Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (workingDirectory.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text("General Chat", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("No folder pinned · gateway AI models", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (onOpenCodespace != null) {
                    val filteredGithub = githubRepositories.filter {
                        searchQuery.isBlank() || it.fullName.contains(searchQuery, ignoreCase = true)
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "GITHUB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            if (onRefreshGithubRepositories != null) {
                                IconButton(
                                    onClick = onRefreshGithubRepositories,
                                    enabled = !isLoadingGithubRepositories,
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh GitHub repositories")
                                }
                            }
                        }
                        Text(
                            "Select a repository to set as active environment in chat directly via GitHub API.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        githubError?.let { error ->
                            Text(
                                text = error.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            error.suggestedAction?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isLoadingGithubRepositories) {
                            CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp))
                        }
                        if (!isLoadingGithubRepositories && githubRepositories.isEmpty() && githubError == null) {
                            Text(
                                "No repositories to show. Connect a GitHub token in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(filteredGithub, key = { "github-${it.id}" }) { repo ->
                        val isSelected = workingDirectory == "/github/${repo.fullName}"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onOpenCodespace(repo)
                                }
                                .padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Code,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(repo.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (repo.isPrivate) "Private · ${repo.defaultBranch}" else "Public · ${repo.defaultBranch}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Active Projects & Workspaces
                val filteredProjects = projects.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.remotePath.contains(searchQuery, ignoreCase = true)
                }
                if (filteredProjects.isNotEmpty()) {
                    item {
                        Text(
                            text = "WORKSPACES & PROJECTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                        )
                    }
                    items(filteredProjects.size) { index ->
                        val project = filteredProjects[index]
                        val isSelected = project.remotePath == workingDirectory
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onSelect(project)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(project.remotePath, style = VmTheme.code.mono.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // 3. Discovered Server Repos
                val filteredGit = discoveredGitRepos.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
                }
                if (filteredGit.isNotEmpty()) {
                    item {
                        Text(
                            text = "SERVER GIT REPOSITORIES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                        )
                    }
                    items(filteredGit.size) { index ->
                        val repo = filteredGit[index]
                        val isSelected = repo.path == workingDirectory
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onSelectDiscoveredRepo?.invoke(repo)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.AccountTree,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(repo.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${repo.branch} · ${repo.path}", style = VmTheme.code.mono.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // 4. Remote Server Folder Quick Action
                if (onChooseDirectory != null) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onChooseDirectory()
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        "Open folder on remote server…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "Navigate filesystem and start agent directly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (onChooseLocalFolder != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onChooseLocalFolder()
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        "Open local phone folder…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Use a folder without copying it into the app",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (onBrowseServer != null && availableServers.isNotEmpty()) {
                    item {
                        Text(
                            "BROWSE VPS FOLDERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(availableServers, key = { "server-${it.id}" }) { server ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBrowseServer(server.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    server.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    server.target,
                                    style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
