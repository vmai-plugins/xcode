package digital.vmstudio.code.feature.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemoteListingOptions
import digital.vmstudio.code.core.sftp.model.RemotePath
import digital.vmstudio.code.core.sftp.model.RemoteSortOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilesUiState(
    val serverId: String = "",
    val path: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val options: RemoteListingOptions = RemoteListingOptions(),
    val isLoading: Boolean = true,
    val error: VmError? = null,
    val selectedPaths: Set<String> = emptySet(),
    /** Non-null while a create/rename dialog is open. */
    val pendingAction: FileAction? = null,
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty() && error == null

    val inSelectionMode: Boolean get() = selectedPaths.isNotEmpty()

    /** Breadcrumb segments from the root down to the current directory. */
    val breadcrumbs: List<Pair<String, String>>
        get() {
            if (path.isEmpty()) return emptyList()
            val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            return buildList {
                add("/" to "/")
                segments.forEach { segment ->
                    accumulated = "$accumulated/$segment"
                    add(segment to accumulated)
                }
            }
        }
}

sealed interface FileAction {
    data object CreateFile : FileAction
    data object CreateDirectory : FileAction
    data class Rename(val entry: RemoteFileEntry) : FileAction
    data class ConfirmDelete(val entries: List<RemoteFileEntry>) : FileAction
    data class Details(val entry: RemoteFileEntry) : FileAction
    data class Edit(val entry: RemoteFileEntry) : FileAction
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "Files requires a serverId"
    }

    private val _uiState = MutableStateFlow(FilesUiState(serverId = serverId))
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    /** Directories visited, so Back walks up rather than leaving the screen. */
    private val history = ArrayDeque<String>()

    /** A starting folder (a project's or a chat's), when the caller has one. */
    private val startPath: String? = savedStateHandle.get<String>(ARG_PATH)?.takeIf { it.isNotBlank() }

    init {
        viewModelScope.launch {
            if (startPath != null) return@launch navigateTo(startPath, recordHistory = false)
            when (val home = remoteFileSystem.homeDirectory(serverId)) {
                is VmResult.Success -> navigateTo(home.value, recordHistory = false)
                is VmResult.Failure -> {
                    // Falling back to / rather than failing outright: an account with
                    // no readable home can still browse from the root.
                    _uiState.update { it.copy(error = home.error) }
                    navigateTo(RemotePath.ROOT, recordHistory = false)
                }
            }
        }
    }

    fun navigateTo(path: String, recordHistory: Boolean = true) {
        val current = _uiState.value.path
        if (recordHistory && current.isNotEmpty() && current != path) {
            history.addLast(current)
        }
        _uiState.update {
            it.copy(path = path, isLoading = true, error = null, selectedPaths = emptySet())
        }
        refresh()
    }

    /** Returns false when there is nowhere left to go, so the caller can pop the screen. */
    fun navigateUp(): Boolean {
        val previous = history.removeLastOrNull()
        if (previous != null) {
            navigateTo(previous, recordHistory = false)
            return true
        }
        val parent = RemotePath.parent(_uiState.value.path) ?: return false
        navigateTo(parent, recordHistory = false)
        return true
    }

    private var refreshJob: Job? = null

    fun refresh() {
        val state = _uiState.value
        if (state.path.isEmpty()) return
        _uiState.update { it.copy(isLoading = true) }
        // A slow listing that lands after the user moved on must not overwrite the
        // folder now shown, so the old request is dropped and stale results ignored.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val result = remoteFileSystem.list(serverId, state.path, state.options)
            _uiState.update {
                when {
                    it.path != state.path -> it
                    result is VmResult.Success ->
                        it.copy(entries = result.value, isLoading = false, error = null)
                    result is VmResult.Failure ->
                        it.copy(entries = emptyList(), isLoading = false, error = result.error)
                    else -> it
                }
            }
        }
    }

    fun setSortOrder(order: RemoteSortOrder) {
        _uiState.update { it.copy(options = it.options.copy(sortOrder = order)) }
        refresh()
    }

    fun toggleHidden() {
        _uiState.update {
            it.copy(options = it.options.copy(showHidden = !it.options.showHidden))
        }
        refresh()
    }

    fun toggleSelection(entry: RemoteFileEntry) {
        _uiState.update { state ->
            val selected = state.selectedPaths
            state.copy(
                selectedPaths = if (entry.path in selected) {
                    selected - entry.path
                } else {
                    selected + entry.path
                },
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    /**
     * Opens a tapped entry. A link to a folder lists as a link, not a folder, so its
     * target is looked up first; otherwise links like ~/app -> /srv/app were dead ends.
     */
    fun open(entry: RemoteFileEntry) {
        if (entry.isDirectory) return navigateTo(entry.path)
        if (entry.type != RemoteFileType.SYMLINK) return startAction(FileAction.Details(entry))
        viewModelScope.launch {
            val target = (remoteFileSystem.stat(serverId, entry.path) as? VmResult.Success)?.value
            if (target?.isDirectory == true) {
                navigateTo(entry.path)
            } else {
                startAction(FileAction.Details(entry))
            }
        }
    }

    fun startAction(action: FileAction?) {
        _uiState.update { it.copy(pendingAction = action) }
    }

    fun createDirectory(name: String) = mutate { path ->
        remoteFileSystem.createDirectory(serverId, RemotePath.join(path, name))
    }

    fun createFile(name: String) = mutate { path ->
        remoteFileSystem.createFile(serverId, RemotePath.join(path, name))
    }

    fun rename(entry: RemoteFileEntry, newName: String) = mutate { path ->
        remoteFileSystem.rename(serverId, entry.path, RemotePath.join(path, newName))
    }

    fun delete(entries: List<RemoteFileEntry>) {
        _uiState.update { it.copy(isLoading = true, pendingAction = null) }
        viewModelScope.launch {
            var failure: VmError? = null
            entries.forEach { entry ->
                val result = remoteFileSystem.delete(
                    serverId = serverId,
                    path = entry.path,
                    recursive = entry.isDirectory,
                )
                // Keep going after a failure so one permission-denied file does not
                // abandon the rest of a multi-select delete, then report the first.
                if (result is VmResult.Failure && failure == null) failure = result.error
            }
            _uiState.update { it.copy(selectedPaths = emptySet(), error = failure) }
            refresh()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun mutate(block: suspend (String) -> VmResult<Unit>) {
        val path = _uiState.value.path
        _uiState.update { it.copy(isLoading = true, pendingAction = null) }
        viewModelScope.launch {
            when (val result = block(path)) {
                is VmResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
                is VmResult.Success -> refresh()
            }
        }
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
        const val ARG_PATH = "path"
    }
}
