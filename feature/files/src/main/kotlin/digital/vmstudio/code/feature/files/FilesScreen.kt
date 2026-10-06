package digital.vmstudio.code.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemoteSortOrder
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    onNavigateBack: () -> Unit,
    onOpenEditor: (serverId: String, filePath: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }

    // Back walks up the directory tree before leaving the screen, which is what a
    // file browser is expected to do.
    BackHandler(enabled = true) {
        when {
            state.inSelectionMode -> viewModel.clearSelection()
            !viewModel.navigateUp() -> onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.inSelectionMode) {
                            "${state.selectedPaths.size} selected"
                        } else {
                            "Files"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                state.inSelectionMode -> viewModel.clearSelection()
                                !viewModel.navigateUp() -> onNavigateBack()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                    }
                },
                actions = {
                    if (state.inSelectionMode) {
                        IconButton(
                            onClick = {
                                val selected = state.entries.filter { it.path in state.selectedPaths }
                                viewModel.startAction(FileAction.ConfirmDelete(selected))
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("New file") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.startAction(FileAction.CreateFile)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New folder") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.startAction(FileAction.CreateDirectory)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.options.showHidden) {
                                            "Hide hidden files"
                                        } else {
                                            "Show hidden files"
                                        },
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.toggleHidden()
                                },
                            )
                            RemoteSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text("Sort by ${order.label()}") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.setSortOrder(order)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Breadcrumbs(
                crumbs = state.breadcrumbs,
                onNavigate = viewModel::navigateTo,
            )

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let { error ->
                VmErrorPanel(
                    error = error,
                    modifier = Modifier.padding(spacing.md),
                    onRetry = viewModel::refresh,
                    actions = listOf(
                        digital.vmstudio.code.core.ui.component.VmErrorAction(
                            label = "Dismiss",
                            onClick = viewModel::dismissError,
                        ),
                    ),
                )
            }

            when {
                state.isEmpty -> VmEmptyState(
                    icon = Icons.Default.FolderOpen,
                    title = "Empty folder",
                    description = "There is nothing here. Hidden files are " +
                        if (state.options.showHidden) "shown." else "hidden.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.xxl),
                ) {
                    items(state.entries, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selected = entry.path in state.selectedPaths,
                            selectionMode = state.inSelectionMode,
                            onClick = {
                                when {
                                    state.inSelectionMode -> viewModel.toggleSelection(entry)
                                    else -> viewModel.open(entry)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(entry) },
                            onEdit = if (!entry.isDirectory) {
                                { viewModel.startAction(FileAction.Edit(entry)) }
                            } else null,
                        )
                    }
                }
            }
        }
    }

    state.pendingAction?.let { action ->
        when (action) {
            is FileAction.Edit -> {
                onOpenEditor(state.serverId, action.entry.path)
                viewModel.startAction(null)
            }
            else -> FileActionDialogs(
                action = action,
                onDismiss = { viewModel.startAction(null) },
                onCreateFile = viewModel::createFile,
                onCreateDirectory = viewModel::createDirectory,
                onRename = viewModel::rename,
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
private fun Breadcrumbs(
    crumbs: List<Pair<String, String>>,
    onNavigate: (String) -> Unit,
) {
    if (crumbs.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = VmTheme.spacing.md, vertical = VmTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, (label, path) ->
            if (index > 0) {
                Text(
                    text = "/",
                    style = VmTheme.code.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = VmTheme.spacing.xxs),
                )
            }
            Text(
                text = label,
                style = VmTheme.code.mono,
                color = if (index == crumbs.lastIndex) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .combinedClickable(onClick = { onNavigate(path) })
                    .padding(vertical = VmTheme.spacing.xs),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: RemoteFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val spacing = VmTheme.spacing
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }

        Icon(
            imageVector = entry.icon(),
            contentDescription = null,
            tint = if (entry.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.looksSensitive) {
                    // Flagged so the user knows this file is withheld from AI
                    // context by default.
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "May contain secrets",
                        tint = VmTheme.colors.warning,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Text(
                text = buildString {
                    append(entry.permissions.toRwxString())
                    if (!entry.isDirectory) {
                        append("  ").append(formatSize(entry.sizeBytes))
                    }
                    if (entry.modifiedAtSeconds > 0) {
                        append("  ").append(formatter.format(Date(entry.modifiedAtSeconds * 1000)))
                    }
                },
                style = VmTheme.code.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onEdit != null && !entry.isDirectory) {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FileActionDialogs(
    action: FileAction,
    onDismiss: () -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateDirectory: (String) -> Unit,
    onRename: (RemoteFileEntry, String) -> Unit,
    onDelete: (List<RemoteFileEntry>) -> Unit,
) {
    when (action) {
        FileAction.CreateFile -> NameDialog(
            title = "New file",
            initial = "",
            confirmLabel = "Create",
            onDismiss = onDismiss,
            onConfirm = onCreateFile,
        )

        FileAction.CreateDirectory -> NameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = onDismiss,
            onConfirm = onCreateDirectory,
        )

        is FileAction.Rename -> NameDialog(
            title = "Rename",
            initial = action.entry.name,
            confirmLabel = "Rename",
            onDismiss = onDismiss,
            onConfirm = { onRename(action.entry, it) },
        )

        is FileAction.ConfirmDelete -> {
            val hasDirectory = action.entries.any { it.isDirectory }
            VmDialog(
                title = if (action.entries.size == 1) {
                    "Delete ${action.entries.first().name}?"
                } else {
                    "Delete ${action.entries.size} items?"
                },
                onDismiss = onDismiss,
                confirmLabel = "Delete",
                destructive = true,
                onConfirm = { onDelete(action.entries) },
            ) {
                Text(
                    text = if (hasDirectory) {
                        "Folders are deleted with everything inside them. This happens on " +
                            "the server and cannot be undone."
                    } else {
                        "This happens on the server and cannot be undone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is FileAction.Edit -> Unit // Editing opens the editor screen, not a dialog here.

        is FileAction.Details -> VmDialog(
            title = action.entry.name,
            onDismiss = onDismiss,
            confirmLabel = "Close",
            onConfirm = onDismiss,
            dismissLabel = null,
        ) {
            DetailRow("Path", action.entry.path)
            DetailRow("Type", action.entry.type.name.lowercase())
            DetailRow("Size", formatSize(action.entry.sizeBytes))
            DetailRow(
                "Permissions",
                "${action.entry.permissions.toRwxString()} (${action.entry.permissions.toOctal()})",
            )
            DetailRow("Owner", "uid ${action.entry.uid} / gid ${action.entry.gid}")
            if (action.entry.permissions.isWorldWritable) {
                Text(
                    text = "This file is world-writable, which is usually a misconfiguration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VmTheme.colors.warning,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = VmTheme.code.mono,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = VmTheme.spacing.md),
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Keyed on `initial`: this composable is reused across CreateFile/CreateDirectory/
    // Rename at one call site (the `when` branch in FileActionDialogs), so switching
    // the rename target directly from one entry to another without an intervening
    // recomposition where the dialog is absent would otherwise keep the previous
    // entry's stale, possibly-edited text instead of resetting to the new one's name.
    var name by remember(initial) { mutableStateOf(initial) }
    // A name with a separator would silently create or move into another directory.
    val invalid = name.isBlank() || name.contains('/') || name == "." || name == ".."

    VmDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(name.trim()) },
        confirmEnabled = !invalid,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name") },
            isError = name.isNotEmpty() && invalid,
            supportingText = {
                if (name.isNotEmpty() && invalid) {
                    Text("Names cannot contain / or be . or ..")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun RemoteFileEntry.icon() = when (type) {
    RemoteFileType.DIRECTORY -> Icons.Default.Folder
    RemoteFileType.SYMLINK -> Icons.Default.Link
    RemoteFileType.SPECIAL -> Icons.Default.Visibility
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun RemoteSortOrder.label(): String = when (this) {
    RemoteSortOrder.NAME -> "name"
    RemoteSortOrder.SIZE -> "size"
    RemoteSortOrder.MODIFIED -> "date"
    RemoteSortOrder.TYPE -> "type"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "unknown"
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}
