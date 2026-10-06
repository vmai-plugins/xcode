package digital.vmstudio.code.feature.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorAction
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmProgress
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A live browser for a server's files.
 *
 * Reached two ways: from a server or project with that server fixed (and maybe a
 * starting folder), or from the drawer, where it opens on the most recent server
 * and a switcher in the top bar moves between any saved ones. While visible it
 * re-lists the folder every few seconds, so files an agent writes appear without
 * a manual refresh.
 *
 * [onOpenMenu] is set for the drawer route, which shows the menu button in place
 * of the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenEditor: (serverId: String, filePath: String) -> Unit = { _, _ -> },
    onAddServer: () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchActive by rememberSaveable { mutableStateOf(false) }
    // Keyed on the folder: a filter typed for one folder means nothing in the next.
    var query by rememberSaveable(state.serverId, state.path) { mutableStateOf("") }

    // Live only while on screen: paused in the background, in the editor, and so on.
    LifecycleResumeEffect(viewModel) {
        viewModel.setLive(true)
        onPauseOrDispose { viewModel.setLive(false) }
    }
    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { file -> shareFile(context, file) }
    }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(viewModel::upload)
    }

    fun goBack() {
        when {
            searchActive -> {
                searchActive = false
                query = ""
            }
            state.inSelectionMode -> viewModel.clearSelection()
            !viewModel.navigateUp() -> onNavigateBack()
        }
    }

    // Back closes search and selection, then walks up the directory tree before
    // leaving the screen, which is what a file browser is expected to do.
    BackHandler(enabled = true) { goBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FilesTopBar(
                state = state,
                search = SearchState(active = searchActive, query = query),
                onSearchChange = { active, text ->
                    searchActive = active
                    query = text
                },
                onBack = ::goBack,
                onOpenMenu = onOpenMenu,
                onUpload = { uploadPicker.launch("*/*") },
                viewModel = viewModel,
            )
        },
    ) { padding ->
        FilesContent(
            state = state,
            query = query,
            onAddServer = onAddServer,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    state.pendingAction?.let { action ->
        FileOverlays(
            action = action,
            state = state,
            onOpenEditor = onOpenEditor,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesContent(
    state: FilesUiState,
    query: String,
    onAddServer: () -> Unit,
    viewModel: FilesViewModel,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    if (state.noServer) {
        VmEmptyState(
            icon = Icons.Default.Dns,
            title = "No servers yet",
            description = "Add a server to browse, preview and share its files from here.",
            actionLabel = "Add server",
            onAction = onAddServer,
            modifier = modifier,
        )
        return
    }
    // Set by the pull gesture so its spinner shows only for the refresh it started,
    // not for the live ticks or navigation, which have their own indicators.
    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) { if (!state.isLoading) pulling = false }
    val visible = remember(state.entries, query) { filterEntries(state.entries, query) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Breadcrumbs(
                crumbs = state.breadcrumbs,
                onNavigate = viewModel::navigateTo,
                modifier = Modifier.weight(1f),
            )
            if (state.path.isNotEmpty()) LiveIndicator(stale = state.liveStale)
        }

        when {
            state.transfer != null -> VmProgress(
                progress = state.transfer.fraction,
                label = state.transfer.label,
                trailingLabel = state.transfer.fraction?.let { "${(it * 100).toInt()}%" },
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
            )
            state.isLoading && !pulling -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { error ->
            VmErrorPanel(
                error = error,
                modifier = Modifier.padding(spacing.md),
                onRetry = viewModel::refresh,
                actions = listOf(VmErrorAction(label = "Dismiss", onClick = viewModel::dismissError)),
            )
        }

        PullToRefreshBox(
            isRefreshing = pulling && state.isLoading,
            onRefresh = {
                pulling = true
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            FileList(state = state, visible = visible, query = query, viewModel = viewModel)
        }
    }
}

/**
 * The listing. Empty states live inside the list so pull-to-refresh still works
 * on an empty folder; entries are keyed by path, so a live update that adds or
 * removes a file keeps the scroll position on the rows that stayed.
 */
@Composable
private fun FileList(
    state: FilesUiState,
    visible: List<RemoteFileEntry>,
    query: String,
    viewModel: FilesViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = VmTheme.spacing.xxl),
    ) {
        when {
            state.isEmpty -> item(key = "empty") {
                VmEmptyState(
                    icon = Icons.Default.FolderOpen,
                    title = "Empty folder",
                    description = "There is nothing here. Hidden files are " +
                        if (state.options.showHidden) "shown." else "hidden.",
                )
            }
            visible.isEmpty() && query.isNotBlank() && state.entries.isNotEmpty() -> item(key = "no-match") {
                VmEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No matches",
                    description = "Nothing in this folder has \"${query.trim()}\" in its name.",
                )
            }
        }
        items(visible, key = { it.path }) { entry ->
            FileRow(
                entry = entry,
                selected = entry.path in state.selectedPaths,
                selectionMode = state.inSelectionMode,
                onClick = {
                    if (state.inSelectionMode) viewModel.toggleSelection(entry) else viewModel.open(entry)
                },
                onLongClick = { viewModel.toggleSelection(entry) },
                onEdit = if (!entry.isDirectory) {
                    { viewModel.startAction(FileAction.Edit(entry)) }
                } else {
                    null
                },
            )
        }
    }
}

/** Dialogs and the file sheet, driven by the pending action. */
@Composable
private fun FileOverlays(
    action: FileAction,
    state: FilesUiState,
    onOpenEditor: (serverId: String, filePath: String) -> Unit,
    viewModel: FilesViewModel,
) {
    when (action) {
        is FileAction.Edit -> LaunchedEffect(action) {
            viewModel.startAction(null)
            onOpenEditor(state.serverId, action.entry.path)
        }
        is FileAction.Details -> FileSheet(
            entry = action.entry,
            preview = state.preview,
            sharing = state.transfer != null,
            onEdit = { viewModel.startAction(FileAction.Edit(action.entry)) },
            onShare = { viewModel.share(action.entry) },
            onDismiss = { viewModel.startAction(null) },
        )
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

/**
 * A dot that says the listing is live. Turns amber when the last background
 * re-listing failed, so a frozen list is never mistaken for a quiet folder.
 */
@Composable
private fun LiveIndicator(stale: Boolean) {
    VmStatusBadge(
        status = if (stale) VmStatus.DEGRADED else VmStatus.CONNECTED,
        label = if (stale) "Reconnecting" else "Live",
        modifier = Modifier.padding(end = VmTheme.spacing.md),
    )
}

@Composable
private fun Breadcrumbs(
    crumbs: List<Pair<String, String>>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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

        // Both are handled by FileOverlays: the editor screen and the file sheet.
        is FileAction.Edit, is FileAction.Details -> Unit
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

/**
 * Hands a downloaded file to the Android share sheet through the app's
 * FileProvider, whose `downloads/` cache path covers [FileTransfers]' folder. The
 * read grant lets the chosen app open it without any storage permission.
 */
private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${file.name}"))
}

private fun RemoteFileEntry.icon() = when (type) {
    RemoteFileType.DIRECTORY -> Icons.Default.Folder
    RemoteFileType.SYMLINK -> Icons.Default.Link
    RemoteFileType.SPECIAL -> Icons.Default.Visibility
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}
