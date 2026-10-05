package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderPickerState(
    val path: String = "",
    val folders: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: VmError? = null,
)

/** Browses a server's folders over SFTP, one level at a time. */
@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderPickerState())
    val state: StateFlow<FolderPickerState> = _state.asStateFlow()
    private var loading: Job? = null

    fun open(serverId: String, path: String) {
        loading?.cancel()
        _state.update { it.copy(path = path, isLoading = true, error = null) }
        loading = viewModelScope.launch {
            val target = path.ifBlank {
                (remoteFileSystem.homeDirectory(serverId) as? VmResult.Success)?.value ?: "/"
            }
            _state.value = when (val listed = remoteFileSystem.list(serverId, target)) {
                is VmResult.Success -> FolderPickerState(
                    path = target,
                    folders = listed.value
                        .filter { it.type == RemoteFileType.DIRECTORY || it.symlinkTarget != null }
                        .map { it.name }
                        .sortedBy { it.lowercase() },
                    isLoading = false,
                )
                is VmResult.Failure ->
                    FolderPickerState(path = target, isLoading = false, error = listed.error)
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
    LaunchedEffect(serverId) { viewModel.open(serverId, startPath) }

    VmDialog(
        title = "Choose folder",
        onDismiss = onDismiss,
        confirmLabel = "Use this folder",
        onConfirm = { onPick(state.path) },
        confirmEnabled = state.path.isNotBlank() && !state.isLoading,
        icon = Icons.Default.Folder,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { viewModel.open(serverId, parentOf(state.path)) },
                enabled = state.path.length > 1,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up one folder")
            }
            Text(
                text = state.path.ifBlank { "…" },
                style = VmTheme.code.mono,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.open(serverId, "") }) {
                Icon(Icons.Default.Home, contentDescription = "Home folder")
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 360.dp),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error?.summary.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
                state.folders.isEmpty() -> Text(
                    text = "No folders here. Use this one, or go up.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
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

internal fun parentOf(path: String): String =
    path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

internal fun childOf(path: String, name: String): String =
    if (path.endsWith("/")) path + name else "$path/$name"
