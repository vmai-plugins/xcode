package digital.vmstudio.code.feature.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
fun ServersScreen(
    onAddServer: () -> Unit,
    onOpenServer: (String) -> Unit,
    onEditServer: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ServersContent(
        state = state,
        onAddServer = onAddServer,
        onOpenServer = onOpenServer,
        onEditServer = onEditServer,
        onDeleteServer = viewModel::deleteServer,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

@Composable
internal fun ServersContent(
    state: ServersUiState,
    onAddServer: () -> Unit,
    onOpenServer: (String) -> Unit,
    onEditServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    var pendingDeletion by remember { mutableStateOf<Server?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onAddServer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add server") },
                )
            }
        },
    ) { padding ->
        when {
            state.isEmpty -> VmEmptyState(
                icon = Icons.Default.Dns,
                title = "No servers yet",
                description =
                "Add an SSH server to browse its files, open a terminal, and let the " +
                    "AI agent work inside your projects.",
                actionLabel = "Add server",
                onAction = onAddServer,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    top = spacing.sm,
                    // Clears the FAB so the last card is never unreachable.
                    bottom = spacing.xxxl + spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                state.error?.let { error ->
                    item(key = "error") {
                        VmErrorPanel(
                            error = error,
                            modifier = Modifier.padding(top = padding.calculateTopPadding()),
                            actions = listOf(
                                digital.vmstudio.code.core.ui.component.VmErrorAction(
                                    label = "Dismiss",
                                    onClick = onDismissError,
                                ),
                            ),
                        )
                    }
                }

                state.grouped.forEach { (group, servers) ->
                    if (state.groups.isNotEmpty()) {
                        item(key = "group-${group?.id ?: "ungrouped"}") {
                            VmSectionHeader(title = group?.name ?: "Ungrouped")
                        }
                    }
                    items(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            onClick = { onOpenServer(server.id) },
                            onEdit = { onEditServer(server.id) },
                            onDelete = { pendingDeletion = server },
                        )
                    }
                }
            }
        }
    }

    pendingDeletion?.let { server ->
        VmDialog(
            title = "Delete ${server.name}?",
            onDismiss = { pendingDeletion = null },
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteServer(server.id)
                pendingDeletion = null
            },
            destructive = true,
        ) {
            Text(
                text = "This removes the server and its stored credentials from this device. " +
                    "Nothing on the server itself is changed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: Server,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VmTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }

    VmCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (server.isProduction) "${server.displayTarget} · Production" else server.displayTarget,
                    style = VmTheme.code.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Actions for ${server.name}",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
