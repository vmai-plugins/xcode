package digital.vmstudio.code.feature.servers

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ssh.apps.AppFramework
import digital.vmstudio.code.core.ssh.apps.AppProject
import digital.vmstudio.code.core.ssh.apps.AppStatus
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme
import java.util.Locale
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.ui.platform.LocalContext
/**
 * Apps Hub for one server: directories under the apps root with framework, pm2
 * state, git branch and port. Every action is a guarded remote command, so the
 * approval policy applies here exactly as in the terminal and the agent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerAppsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val context = LocalContext.current
    var showClone by remember { mutableStateOf(false) }
    var previewApp by remember { mutableStateOf<AppProject?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Apps · ${state.serverName.ifBlank { "Server" }}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClone = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Clone from GitHub")
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh apps")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "summary") { AppsSummary(state = state) }

            state.error?.let { error ->
                item(key = "error") {
                    VmErrorPanel(error = error, onRetry = viewModel::refresh)
                }
            }

            if (state.isLoading && !state.hasAnyApp) {
                item(key = "loading") { AppsLoadingRow() }
            } else if (!state.hasAnyApp) {
                item(key = "empty") {
                    VmEmptyState(
                        icon = Icons.Default.Apps,
                        title = "No apps found",
                        description = "Nothing under the apps root yet. Clone a repository " +
                            "from GitHub, or create a folder in the terminal.",
                        actionLabel = "Clone from GitHub",
                        onAction = { showClone = true },
                    )
                }
            } else {
                items(
                    state.apps,
                    key = { "app-${it.path}" },
                ) { app ->
                AppCard(
                    app = app,
                    state = state,
                    viewModel = viewModel,
                    onPreview = { previewApp = it },
                )
            }
            }
        }
    }

    if (showClone) {
        CloneDialog(
            onDismiss = { showClone = false },
            onConfirm = { url, name, branch ->
                showClone = false
                viewModel.clone(url, name, branch)
            },
        )
    }

    state.logSheet?.let { sheet ->
        AppLogsSheet(
            sheet = sheet,
            onDismiss = viewModel::closeLogs,
            onRefresh = viewModel::refreshLogs,
        )
    }

    previewApp?.let { app ->
        WebPreviewSheet(
            app = app,
            onDismiss = { previewApp = null },
            onOpenInBrowser = { url ->
                previewApp = null
                openUrlInBrowser(context, url)
            },
        )
    }
}

private fun openUrlInBrowser(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // No browser available; the URL is shown in the sheet for manual copy
    }
}
@Composable
private fun AppsSummary(state: ServerAppsUiState) {
    VmCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = VmTheme.spacing.sm),
            ) {
                Text(
                    text = state.appsRoot,
                    style = VmTheme.code.mono,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.isLoading) {
                        "Scanning…"
                    } else {
                        val noun = if (state.apps.size == 1) "app" else "apps"
                        "${state.apps.size} $noun · ${state.runningCount} running"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isRefreshing) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AppsLoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Discovering apps…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppCard(
    app: AppProject,
    state: ServerAppsUiState,
    viewModel: ServerAppsViewModel,
    onPreview: (AppProject) -> Unit,
) {
    val busy = app.name in state.busyAppNames
    VmCard(contentPadding = PaddingValues(VmTheme.spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.xxs)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.path,
                    style = VmTheme.code.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs)) {
                VmStatusBadge(
                    status = app.toBadgeStatus(),
                    label = app.status.label,
                )
            }
        }

        MetaRow(app = app)

        AppActions(
            app = app,
            busy = busy,
            viewModel = viewModel,
            onPreview = onPreview,
        )
    }
}

@Composable
private fun MetaRow(app: AppProject) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VmTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
    ) {
        VmChip(
            text = app.framework.displayName,
            containerColor = frameworkContainer(app.framework),
            contentColor = frameworkContent(app.framework),
        )
        app.port?.let { VmChip(text = ":$it", monospace = true) }
        app.branch?.let { VmChip(text = it, monospace = true) }
        app.memoryMb?.let { VmChip(text = "$it MB", monospace = true) }
        app.cpuPercent?.let {
            VmChip(text = String.format(Locale.US, "%.1f%% cpu", it), monospace = true)
        }
    }
}

@Composable
private fun AppActions(
    app: AppProject,
    busy: Boolean,
    viewModel: ServerAppsViewModel,
    onPreview: (AppProject) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VmTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        if (app.isRunning) {
            VmButton(
                text = "Stop",
                icon = Icons.Default.Stop,
                style = VmButtonStyle.Secondary,
                onClick = { viewModel.stop(app) },
                enabled = !busy,
            )
            VmButton(
                text = "Restart",
                icon = Icons.Default.Refresh,
                style = VmButtonStyle.Tertiary,
                onClick = { viewModel.restart(app) },
                enabled = !busy,
            )
        } else {
            VmButton(
                text = "Start",
                icon = Icons.Default.PlayArrow,
                onClick = { viewModel.start(app) },
                enabled = !busy,
                loading = busy,
            )
        }
        VmButton(
            text = "Logs",
            icon = Icons.Default.Terminal,
            style = VmButtonStyle.Tertiary,
            onClick = { viewModel.requestLogs(app) },
            enabled = !busy,
        )
        if (app.isRunning && app.port != null) {
            VmButton(
                text = "Preview",
                icon = Icons.Default.OpenInBrowser,
                style = VmButtonStyle.Tertiary,
                onClick = { onPreview(app) },
                enabled = !busy,
            )
        }
    }
}

private fun AppProject.toBadgeStatus(): VmStatus = when (status) {
    AppStatus.RUNNING -> VmStatus.CONNECTED
    AppStatus.STOPPED -> VmStatus.DISCONNECTED
    AppStatus.CRASHED -> VmStatus.ERROR
    AppStatus.UNKNOWN -> VmStatus.IDLE
}

@Composable
private fun frameworkContainer(framework: AppFramework): Color = when (framework) {
    AppFramework.NODEJS -> VmTheme.colors.successContainer
    AppFramework.PYTHON -> MaterialTheme.colorScheme.primaryContainer
    AppFramework.DOCKER -> MaterialTheme.colorScheme.secondaryContainer
    AppFramework.GOLANG -> MaterialTheme.colorScheme.tertiaryContainer
    AppFramework.RUST -> VmTheme.colors.warningContainer
    AppFramework.STATIC_HTML -> MaterialTheme.colorScheme.primaryContainer
    AppFramework.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun frameworkContent(framework: AppFramework): Color = when (framework) {
    AppFramework.NODEJS -> VmTheme.colors.onSuccessContainer
    AppFramework.PYTHON -> MaterialTheme.colorScheme.onPrimaryContainer
    AppFramework.DOCKER -> MaterialTheme.colorScheme.onSecondaryContainer
    AppFramework.GOLANG -> MaterialTheme.colorScheme.onTertiaryContainer
    AppFramework.RUST -> VmTheme.colors.onWarningContainer
    AppFramework.STATIC_HTML -> MaterialTheme.colorScheme.onPrimaryContainer
    AppFramework.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
@Composable
private fun CloneDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String?, branch: String?) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }

    VmDialog(
        title = "Clone from GitHub",
        onDismiss = onDismiss,
        confirmLabel = "Clone",
        onConfirm = {
            onConfirm(url.trim(), appName.trim().takeIf { it.isNotBlank() }, branch.trim().takeIf { it.isNotBlank() })
        },
        confirmEnabled = url.trim().isNotEmpty(),
        icon = Icons.Default.Add,
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Repository URL") },
            singleLine = true,
            textStyle = VmTheme.code.mono,
            supportingText = {
                Text(
                    text = "https://github.com/owner/repo.git or ssh://git@…",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appName,
            onValueChange = { appName = it },
            label = { Text("Folder name (optional)") },
            singleLine = true,
            textStyle = VmTheme.code.mono,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = VmTheme.spacing.sm),
        )
        OutlinedTextField(
            value = branch,
            onValueChange = { branch = it },
            label = { Text("Branch (optional)") },
            singleLine = true,
            textStyle = VmTheme.code.mono,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = VmTheme.spacing.sm),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLogsSheet(
    sheet: AppLogSheetState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VmTheme.spacing.screenHorizontal)
                .padding(bottom = VmTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Logs · ${sheet.appName}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sheet.appPath,
                        style = VmTheme.code.mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh logs")
                }
            }

            sheet.error?.let { error ->
                VmErrorPanel(error = error, onRetry = onRefresh)
            }

            sheet.content?.let { content ->
                Text(
                    text = content,
                    style = VmTheme.code.terminal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(VmTheme.spacing.md),
                )
            }
        }
    }
}