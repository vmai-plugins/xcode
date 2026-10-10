package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
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
    onSelectGithubRepo: ((String) -> Unit)? = null,
    onChooseDirectory: (() -> Unit)? = null,
    discoveredGitRepos: List<digital.vmstudio.code.core.git.DiscoveredGitRepo> = emptyList(),
    onSelectDiscoveredRepo: ((digital.vmstudio.code.core.git.DiscoveredGitRepo) -> Unit)? = null,
    onNewChat: () -> Unit,
    onOpenFiles: () -> Unit,
    onConversationAction: (ConversationAction) -> Unit,
) {
    var showProjects by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    TopAppBar(
        expandedHeight = ChatBarHeight,
        scrollBehavior = scrollBehavior,
        title = {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canSwitchProject) { showProjects = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (isConnecting) "Connecting…" else title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (canSwitchProject) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch project",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                    onSelectGithubRepo = onSelectGithubRepo,
                    onSelectDiscoveredRepo = { repo ->
                        showProjects = false
                        onSelectDiscoveredRepo?.invoke(repo)
                    },
                    onChooseDirectory = {
                        showProjects = false
                        onChooseDirectory?.invoke()
                    },
                )
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
            if (hasTranscript) {
                IconButton(onClick = onNewChat) { Icon(Icons.Default.Add, contentDescription = "New chat") }
            }
            Box {
                IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
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
    onSelectGithubRepo: ((String) -> Unit)? = null,
    onSelectDiscoveredRepo: ((digital.vmstudio.code.core.git.DiscoveredGitRepo) -> Unit)? = null,
    onChooseDirectory: (() -> Unit)? = null,
) {
    if (!expanded) return

    var searchQuery by remember { mutableStateOf("") }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text("Open folder on remote server…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Text("Navigate filesystem and start agent directly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
