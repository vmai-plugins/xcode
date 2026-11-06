package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import digital.vmstudio.code.core.project.VmProjectHubClient
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemoteListingOptions
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class FolderPickerState(
    val path: String = "",
    val folders: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: VmError? = null,
)

/** Browses a server's folders over SFTP, one level at a time with strict timeout and manual fallback. */
@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
    private val projectHubClient: VmProjectHubClient,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderPickerState())
    val state: StateFlow<FolderPickerState> = _state.asStateFlow()
    private var loading: Job? = null

    fun setPathDirectly(newPath: String) {
        val clean = if (newPath.startsWith("/")) newPath else "/$newPath"
        _state.update { it.copy(path = clean) }
    }

    fun open(serverId: String, path: String) {
        loading?.cancel()
        val initialTarget = path.ifBlank { "/" }
        _state.update { it.copy(path = initialTarget, isLoading = true, error = null) }
        val isLinux = initialTarget.startsWith("/home/") || initialTarget.startsWith("/var/") || initialTarget.startsWith("/opt/") || initialTarget.startsWith("/etc/")
        loading = viewModelScope.launch {
            val activeServers = serverRepository.servers.first()
            val currentSrv = activeServers.firstOrNull { it.id == serverId }
            val isSrvLan = currentSrv != null && (
                currentSrv.host.startsWith("192.168.") ||
                currentSrv.name.contains("Laptop", ignoreCase = true) ||
                currentSrv.host.startsWith("10.") ||
                currentSrv.host.startsWith("172.")
            )
            val effectiveServerId = if (isLinux && (isSrvLan || RemoteFileSystem.isLocalServer(serverId))) {
                activeServers.firstOrNull { it.host == "200.234.41.231" || it.host.contains("vmstudio", ignoreCase = true) }?.id
                    ?: serverId
            } else {
                serverId
            }

            val target = if (path.isBlank()) {
                val home = withTimeoutOrNull(4_000L) {
                    (remoteFileSystem.homeDirectory(effectiveServerId) as? VmResult.Success)?.value
                }
                home ?: "/"
            } else path

            _state.update { it.copy(path = target) }

            // 1. Try SFTP directly
            val listOutcome = withTimeoutOrNull(5_000L) {
                remoteFileSystem.list(
                    effectiveServerId,
                    target,
                    RemoteListingOptions(showHidden = true),
                )
            }

            if (listOutcome is VmResult.Success) {
                _state.value = FolderPickerState(
                    path = target,
                    folders = listOutcome.value
                        .filter { it.type == RemoteFileType.DIRECTORY || it.symlinkTarget != null }
                        .map { it.name }
                        .sortedBy { it.lowercase() },
                    isLoading = false,
                )
                return@launch
            }

            // 2. Seamless fallback to X-Brain REST /fs/list for Linux VPS paths
            if (isLinux || target == "/" || target.startsWith("/root") || target.startsWith("/home") || target.startsWith("/var")) {
                when (val restOutcome = projectHubClient.listWorkspace(target)) {
                    is VmResult.Success -> {
                        if (restOutcome.value.isNotEmpty()) {
                            _state.value = FolderPickerState(
                                path = target,
                                folders = restOutcome.value,
                                isLoading = false,
                            )
                            return@launch
                        }
                    }
                    is VmResult.Failure -> {
                        // proceed to timeout/error state below
                    }
                }
            }

            if (listOutcome == null) {
                // Timeout protection: don't leave user hanging
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = VmError.Unexpected("Folder listing timed out. You can still use '$target' or enter a path manually below."),
                    )
                }
            } else {
                _state.value = when (listOutcome) {
                    is VmResult.Success -> FolderPickerState(
                        path = target,
                        folders = listOutcome.value
                            .filter { it.type == RemoteFileType.DIRECTORY || it.symlinkTarget != null }
                            .map { it.name }
                            .sortedBy { it.lowercase() },
                        isLoading = false,
                    )
                    is VmResult.Failure -> FolderPickerState(
                        path = target,
                        folders = emptyList(),
                        isLoading = false,
                        error = listOutcome.error,
                    )
                }
            }
        }
    }
}

/**
 * Chooses the agent's working folder by browsing the server: tap a folder to go
 * in, the back arrow to go up, "Use this folder" to pick where you are.
 */
@Composable
internal fun FolderPickerDialog(
    serverId: String,
    startPath: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    viewModel: FolderPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isEditingManually by remember { mutableStateOf(false) }
    var manualInput by remember(state.path) { mutableStateOf(state.path) }

    LaunchedEffect(serverId) { viewModel.open(serverId, startPath) }

    VmDialog(
        title = "Choose folder",
        onDismiss = onDismiss,
        confirmLabel = "Use this folder",
        onConfirm = { onPick(state.path.ifBlank { "/" }) },
        confirmEnabled = state.path.isNotBlank(),
        icon = Icons.Default.Folder,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isEditingManually) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text("Folder Path") },
                        placeholder = { Text("/root or /var/www") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            viewModel.setPathDirectly(manualInput)
                            viewModel.open(serverId, manualInput)
                            isEditingManually = false
                        },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply path", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    IconButton(
                        onClick = { viewModel.open(serverId, parentOf(state.path)) },
                        enabled = state.path.length > 1,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up one folder")
                    }
                    Text(
                        text = state.path.ifBlank { startPath.ifBlank { "/" } },
                        style = VmTheme.code.mono,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                        modifier = Modifier.weight(1f).clickable {
                            manualInput = state.path
                            isEditingManually = true
                        },
                    )
                    IconButton(onClick = {
                        manualInput = state.path
                        isEditingManually = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Type path manually", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.open(serverId, "") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home folder")
                    }
                }
            }

            // Quick Shortcut Chips for instant 1-tap navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("/root", "/home", "/var/www", "/tmp", "/etc").forEach { quickDir ->
                    AssistChip(
                        onClick = {
                            viewModel.open(serverId, quickDir)
                        },
                        label = { Text(quickDir, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp),
            ) {
                when {
                    state.isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Text(
                                "Scanning ${state.path.ifBlank { "server" }}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = state.error?.summary.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.open(serverId, state.path) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Retry")
                                }
                                TextButton(onClick = {
                                    manualInput = state.path
                                    isEditingManually = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Type Path")
                                }
                            }
                        }
                    }
                    state.folders.isEmpty() -> {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "No subfolders in ${state.path}.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Tap 'Use this folder' below to use this directory as your workspace.",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    else -> LazyColumn {
                        items(state.folders, key = { it }) { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.open(serverId, childOf(state.path, name)) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun parentOf(path: String): String =
    path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

internal fun childOf(path: String, name: String): String =
    if (path.endsWith("/")) path + name else "$path/$name"
