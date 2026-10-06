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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onSelectProject: (Project) -> Unit,
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
                    onDismiss = { showProjects = false },
                    onSelect = { project ->
                        showProjects = false
                        onSelectProject(project)
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

@Composable
private fun ProjectMenu(
    expanded: Boolean,
    projects: List<Project>,
    workingDirectory: String,
    onDismiss: () -> Unit,
    onSelect: (Project) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        projects.forEach { project ->
            val isSelected = project.remotePath == workingDirectory
            val tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) tint else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = project.remotePath,
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
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { onSelect(project) },
            )
        }
    }
}
