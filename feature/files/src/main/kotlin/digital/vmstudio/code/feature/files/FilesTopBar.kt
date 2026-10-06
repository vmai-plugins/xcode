package digital.vmstudio.code.feature.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import digital.vmstudio.code.core.sftp.model.RemoteSortOrder
import digital.vmstudio.code.core.ui.theme.VmTheme

/** The top bar's filter field: whether it is open and what is typed in it. */
internal data class SearchState(val active: Boolean, val query: String)

/**
 * The Files top bar in its three modes: a selection bar, a filter field, or the
 * server switcher with the folder actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilesTopBar(
    state: FilesUiState,
    search: SearchState,
    onSearchChange: (active: Boolean, query: String) -> Unit,
    onBack: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onUpload: () -> Unit,
    viewModel: FilesViewModel,
) {
    TopAppBar(
        title = {
            when {
                state.inSelectionMode -> Text("${state.selectedPaths.size} selected")
                search.active -> FilterField(
                    query = search.query,
                    onQueryChange = { onSearchChange(true, it) },
                )
                else -> ServerSwitcher(
                    current = state.currentServer,
                    servers = state.servers,
                    onSelect = viewModel::selectServer,
                )
            }
        },
        navigationIcon = {
            // The drawer route is top level: its leading slot opens the drawer, and
            // Back (gesture or button) still walks up folders before leaving.
            if (onOpenMenu != null && !state.inSelectionMode && !search.active) {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            when {
                state.inSelectionMode -> SelectionActions(state = state, viewModel = viewModel)
                search.active -> IconButton(onClick = { onSearchChange(false, "") }) {
                    Icon(Icons.Default.Close, contentDescription = "Close filter")
                }
                state.path.isNotEmpty() -> FolderActions(
                    state = state,
                    onSearch = { onSearchChange(true, search.query) },
                    onUpload = onUpload,
                    viewModel = viewModel,
                )
            }
        },
    )
}

@Composable
private fun FilterField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Filter this folder") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}

/**
 * The title doubles as the server picker, so the browser can move to any saved
 * server without going back through the server list.
 */
@Composable
private fun ServerSwitcher(
    current: ServerOption?,
    servers: List<ServerOption>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Column(modifier = Modifier.clickable(enabled = servers.isNotEmpty()) { expanded = true }) {
            Text("Files", maxLines = 1)
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch server",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = server.target,
                                style = VmTheme.code.mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = {
                        if (server.id == current?.id) {
                            Icon(Icons.Default.Check, contentDescription = "Current")
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(server.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionActions(state: FilesUiState, viewModel: FilesViewModel) {
    val selected = state.entries.filter { it.path in state.selectedPaths }
    val single = selected.singleOrNull()
    if (single != null) {
        IconButton(onClick = { viewModel.startAction(FileAction.Rename(single)) }) {
            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename")
        }
        if (!single.isDirectory) {
            IconButton(onClick = { viewModel.share(single) }, enabled = state.transfer == null) {
                Icon(Icons.Default.Share, contentDescription = "Share to phone")
            }
        }
    }
    IconButton(onClick = { viewModel.startAction(FileAction.ConfirmDelete(selected)) }) {
        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
    }
}

@Composable
private fun FolderActions(
    state: FilesUiState,
    onSearch: () -> Unit,
    onUpload: () -> Unit,
    viewModel: FilesViewModel,
) {
    var addExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = onSearch) {
        Icon(Icons.Default.Search, contentDescription = "Filter")
    }
    Box {
        IconButton(onClick = { addExpanded = true }) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
        DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
            DropdownMenuItem(
                text = { Text("New file") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                onClick = {
                    addExpanded = false
                    viewModel.startAction(FileAction.CreateFile)
                },
            )
            DropdownMenuItem(
                text = { Text("New folder") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                onClick = {
                    addExpanded = false
                    viewModel.startAction(FileAction.CreateDirectory)
                },
            )
            DropdownMenuItem(
                text = { Text("Upload file") },
                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                enabled = state.transfer == null,
                onClick = {
                    addExpanded = false
                    onUpload()
                },
            )
        }
    }
    Box {
        IconButton(onClick = { moreExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                    moreExpanded = false
                    viewModel.refresh()
                },
            )
            DropdownMenuItem(
                text = { Text(if (state.options.showHidden) "Hide hidden files" else "Show hidden files") },
                onClick = {
                    moreExpanded = false
                    viewModel.toggleHidden()
                },
            )
            RemoteSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text("Sort by ${order.label()}") },
                    trailingIcon = {
                        if (state.options.sortOrder == order) {
                            Icon(Icons.Default.Check, contentDescription = "Current")
                        }
                    },
                    onClick = {
                        moreExpanded = false
                        viewModel.setSortOrder(order)
                    },
                )
            }
        }
    }
}

private fun RemoteSortOrder.label(): String = when (this) {
    RemoteSortOrder.NAME -> "name"
    RemoteSortOrder.SIZE -> "size"
    RemoteSortOrder.MODIFIED -> "date"
    RemoteSortOrder.TYPE -> "type"
}
