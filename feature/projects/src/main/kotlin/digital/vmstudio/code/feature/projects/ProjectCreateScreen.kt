package digital.vmstudio.code.feature.projects

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreateScreen(
    onNavigateBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val folderName = DocumentFile.fromTreeUri(context, uri)?.name.orEmpty()
                viewModel.selectLocalFolder(uri.toString(), folderName)
            } catch (exception: SecurityException) {
                viewModel.reportFolderAccessError(exception.message ?: "Permission was not granted.")
            }
        }
    }

    LaunchedEffect(state.createdProjectId, state.error) {
        if (state.error == null) state.createdProjectId?.let(onCreated)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("New Workspace", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text("Workspace Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            SourceSelectionGrid(
                currentSource = state.source,
                onSelectSource = { source ->
                    if (source == ProjectSource.LINKED) folderPicker.launch(null)
                    else viewModel.setSource(source)
                },
            )

            if (state.source == ProjectSource.GITHUB) {
                OutlinedTextField(
                    value = state.githubUrl,
                    onValueChange = viewModel::setGithubUrl,
                    label = { Text("GitHub Repository URL") },
                    placeholder = { Text("https://github.com/owner/repo") },
                    singleLine = true,
                    isError = state.githubUrlError != null,
                    supportingText = state.githubUrlError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.branch,
                    onValueChange = viewModel::setBranch,
                    label = { Text("Git Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Workspace Name") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.source != ProjectSource.LOCAL && state.source != ProjectSource.LINKED) {
                TargetServerSection(
                    servers = state.servers,
                    selectedServerId = state.selectedServerId,
                    onSelectServer = viewModel::selectServer,
                )
            }

            OutlinedTextField(
                value = state.remotePath,
                onValueChange = viewModel::setRemotePath,
                label = { Text("Directory on the server/device") },
                singleLine = true,
                isError = state.pathError != null,
                textStyle = VmTheme.code.mono,
                modifier = Modifier.fillMaxWidth(),
            )

            EnvironmentAndRuntimeSection(
                environment = state.environment,
                onEnvironmentChange = viewModel::setEnvironment,
                runtime = state.runtime,
                onRuntimeChange = viewModel::setRuntime,
            )

            state.error?.let { error ->
                if (state.createdProjectId == null) {
                    VmErrorPanel(error = error, onRetry = viewModel::create)
                } else {
                    VmCard {
                        Text(error.summary, style = MaterialTheme.typography.titleSmall)
                        error.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        error.suggestedAction?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            VmButton(
                text = if (state.createdProjectId == null) "Create Workspace" else "Open Workspace",
                onClick = {
                    val createdId = state.createdProjectId
                    if (createdId == null) viewModel.create() else onCreated(createdId)
                },
                enabled = state.canCreate,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SourceSelectionGrid(
    currentSource: ProjectSource,
    onSelectSource: (ProjectSource) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceChip(
            label = "WordPress",
            icon = Icons.Default.Dns,
            selected = currentSource == ProjectSource.WORDPRESS,
            accentColor = Color(0xFF2196F3),
            onClick = { onSelectSource(ProjectSource.WORDPRESS) },
        )
        SourceChip(
            label = "Local Phone",
            icon = Icons.Default.PhoneAndroid,
            selected = currentSource == ProjectSource.LOCAL,
            accentColor = Color(0xFF10B981),
            onClick = { onSelectSource(ProjectSource.LOCAL) },
        )
        SourceChip(
            label = "VPS Cloud",
            icon = Icons.Default.Dns,
            selected = currentSource == ProjectSource.VPS,
            accentColor = Color(0xFF06B6D4),
            onClick = { onSelectSource(ProjectSource.VPS) },
        )
        SourceChip(
            label = "Existing Folder",
            icon = Icons.Default.Folder,
            selected = currentSource == ProjectSource.LINKED,
            accentColor = Color(0xFF64748B),
            onClick = { onSelectSource(ProjectSource.LINKED) },
        )
        SourceChip(
            label = "GitHub Clone",
            icon = Icons.Default.Code,
            selected = currentSource == ProjectSource.GITHUB,
            accentColor = Color(0xFF6366F1),
            onClick = { onSelectSource(ProjectSource.GITHUB) },
        )
    }
}

@Composable
private fun SourceChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, accentColor) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TargetServerSection(
    servers: List<ServerOption>,
    selectedServerId: String?,
    onSelectServer: (String) -> Unit,
) {
    VmCard {
        Text("Target Environment Host", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        servers.forEach { server ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectServer(server.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = selectedServerId == server.id,
                    onClick = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = server.target,
                        style = VmTheme.code.mono,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvironmentAndRuntimeSection(
    environment: ServerEnvironment,
    onEnvironmentChange: (ServerEnvironment) -> Unit,
    runtime: String,
    onRuntimeChange: (String) -> Unit,
) {
    val spacing = VmTheme.spacing
    VmCard {
        Text("Stack & Tooling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        Text("Target Environment", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            ServerEnvironment.values().filter { it != ServerEnvironment.UNSPECIFIED }.forEach { env ->
                FilterChip(
                    selected = environment == env,
                    onClick = { onEnvironmentChange(env) },
                    label = { Text(env.name.lowercase().replaceFirstChar { it.titlecase() }) },
                )
            }
        }

        Spacer(Modifier.height(spacing.xs))

        Text("Runtime Preset", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            listOf("Node.js", "Python", "Go", "Rust", "Java", "Flutter", "Static").forEach { item ->
                FilterChip(
                    selected = runtime == item,
                    onClick = { onRuntimeChange(item) },
                    label = { Text(item) },
                )
            }
        }
    }
}
