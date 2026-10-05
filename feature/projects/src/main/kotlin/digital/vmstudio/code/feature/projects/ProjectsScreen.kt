package digital.vmstudio.code.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * The project list.
 *
 * A project is a named working directory on a server. Naming one is what lets the
 * terminal, the file browser and the agent all open at the right place, instead of
 * the user retyping an absolute path into each of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onOpenProject: (Project) -> Unit,
    title: String = "Projects",
    onCreateProject: () -> Unit,
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    pendingDelete?.let { project ->
        VmDialog(
            title = "Delete ${project.name}?",
            onDismiss = { pendingDelete = null },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.delete(project)
                pendingDelete = null
            },
        ) {
            Text(
                text = "Removes it from this app. The folder and its files on the server " +
                    "are not touched.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
            )
        },
        floatingActionButton = {
            if (state.canCreate) {
                FloatingActionButton(onClick = onCreateProject) {
                    Icon(Icons.Default.Add, contentDescription = "New project")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { error ->
                VmErrorPanel(error = error, modifier = Modifier.padding(spacing.md))
            }

            when {
                state.isLoading -> Unit

                // A project needs somewhere to live, so an empty server list is a
                // different problem from an empty project list and says so.
                !state.canCreate -> VmEmptyState(
                    icon = Icons.Default.Workspaces,
                    title = "Add a server first",
                    description = "A project is a folder on one of your servers. " +
                        "Add a server, then point a project at a directory on it.",
                    actionLabel = "Add server",
                    onAction = onAddServer,
                )

                state.projects.isEmpty() -> VmEmptyState(
                    icon = Icons.Default.Workspaces,
                    title = "No projects yet",
                    description = "Point a project at a directory on one of your servers. " +
                        "The terminal, files and the AI agent all open there.",
                    actionLabel = "New project",
                    onAction = onCreateProject,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            serverLabel = state.serverFor(project)?.name,
                            onOpen = { viewModel.open(project, onOpenProject) },
                            onToggleFavorite = { viewModel.toggleFavorite(project) },
                            onDelete = { pendingDelete = project },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    serverLabel: String?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VmTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }

    VmCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = project.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = project.remotePath,
                    style = VmTheme.code.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                serverLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (project.isFavorite) {
                        Icons.Default.Star
                    } else {
                        Icons.Default.StarBorder
                    },
                    contentDescription = if (project.isFavorite) {
                        "Remove from favourites"
                    } else {
                        "Add to favourites"
                    },
                    tint = if (project.isFavorite) {
                        VmTheme.colors.warning
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More for ${project.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
