package digital.vmstudio.code.feature.files

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemotePath
import digital.vmstudio.code.core.sftp.model.RemoteSortOrder
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
    private val transfers: FileTransfers,
    serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private val shareChannel = Channel<File>(Channel.BUFFERED)

    /** Downloaded files for the screen to hand to the Android share sheet, once each. */
    val shareRequests: Flow<File> = shareChannel.receiveAsFlow()

    private val serverId: String get() = _uiState.value.serverId

    /** Directories visited, so Back walks up rather than leaving the screen. */
    private val history = ArrayDeque<String>()

    private var openJob: Job? = null
    private var refreshJob: Job? = null
    private var previewJob: Job? = null
    private var liveJob: Job? = null

    init {
        // A starting folder (a project's or a chat's), when the caller has one.
        val startPath = savedStateHandle.get<String>(ARG_PATH)?.takeIf { it.isNotBlank() }
        savedStateHandle.get<String>(ARG_SERVER_ID)?.takeIf { it.isNotBlank() }
            ?.let { openServer(it, startPath) }

        viewModelScope.launch {
            serverRepository.servers.collect { servers ->
                val options = servers.map {
                    ServerOption(
                        id = it.id,
                        name = it.name.ifBlank { it.displayTarget },
                        target = it.displayTarget,
                        lastConnectedAtMillis = it.lastConnectedAtMillis,
                    )
                }
                _uiState.update { it.copy(servers = options) }
                // The drawer route has no server argument: pick one as soon as the
                // list is known, and again if one is added from the empty state.
                if (serverId.isEmpty()) {
                    val pick = pickDefaultServerId(options)
                    if (pick != null) {
                        openServer(pick, startPath = null)
                    } else {
                        _uiState.update { it.copy(noServer = true, isLoading = false) }
                    }
                }
            }
        }
    }

    /** Switches the browser to another saved server, starting at its home folder. */
    fun selectServer(id: String) {
        if (id != serverId) openServer(id, startPath = null)
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

    /**
     * Re-lists the current folder. [quiet] is the live tick: it shows no spinner,
     * never interrupts a listing already in flight, and is skipped while a
     * user-started load or change is running so it cannot race it.
     */
    fun refresh(quiet: Boolean = false) {
        val state = _uiState.value
        if (state.path.isEmpty() || state.serverId.isEmpty()) return
        if (quiet && (state.isLoading || refreshJob?.isActive == true)) return
        if (!quiet) {
            _uiState.update { it.copy(isLoading = true) }
            // A slow listing that lands after the user moved on must not overwrite
            // the folder now shown, so the old request is dropped and stale results
            // ignored (see applyListing).
            refreshJob?.cancel()
        }
        refreshJob = viewModelScope.launch {
            val result = remoteFileSystem.list(state.serverId, state.path, state.options)
            _uiState.update { applyListing(it, state, result, quiet) }
        }
    }

    /**
     * Turns live updates on while the screen is visible and off when it is not, so
     * a backgrounded app does not keep a connection busy listing a folder.
     */
    fun setLive(active: Boolean) {
        liveJob?.cancel()
        liveJob = if (!active) {
            null
        } else {
            viewModelScope.launch {
                while (isActive) {
                    refresh(quiet = true)
                    delay(LIVE_REFRESH_MILLIS)
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
        if (entry.type != RemoteFileType.SYMLINK) return showFile(entry)
        viewModelScope.launch {
            val target = (remoteFileSystem.stat(serverId, entry.path) as? VmResult.Success)?.value
            when {
                target?.isDirectory == true -> navigateTo(entry.path)
                // Previewed by the target's type and size, under the link's own name.
                target != null -> showFile(target.copy(name = entry.name, path = entry.path))
                else -> showFile(entry)
            }
        }
    }

    fun startAction(action: FileAction?) {
        if (action is FileAction.Details) return showFile(action.entry)
        previewJob?.cancel()
        _uiState.update { it.copy(pendingAction = action, preview = null) }
    }

    fun createDirectory(name: String) = mutate { server, path ->
        remoteFileSystem.createDirectory(server, RemotePath.join(path, name))
    }

    fun createFile(name: String) = mutate { server, path ->
        remoteFileSystem.createFile(server, RemotePath.join(path, name))
    }

    fun rename(entry: RemoteFileEntry, newName: String) = mutate { server, path ->
        remoteFileSystem.rename(server, entry.path, RemotePath.join(path, newName))
    }

    fun delete(entries: List<RemoteFileEntry>) {
        val server = serverId
        _uiState.update { it.copy(isLoading = true, pendingAction = null) }
        viewModelScope.launch {
            var failure: VmError? = null
            entries.forEach { entry ->
                val result = remoteFileSystem.delete(
                    serverId = server,
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

    /**
     * Downloads a file to the phone's cache and asks the screen to share it. An image
     * already fetched for its preview is shared as is rather than fetched again.
     */
    fun share(entry: RemoteFileEntry) {
        val state = _uiState.value
        val previewed = (state.preview as? PreviewContent.Image)?.file
        if (previewed != null && (state.pendingAction as? FileAction.Details)?.entry == entry) {
            shareChannel.trySend(previewed)
            return
        }
        if (state.transfer != null || state.serverId.isEmpty()) return
        val server = state.serverId
        viewModelScope.launch {
            val label = "Downloading ${entry.name}"
            setTransfer(TransferStatus(label))
            val result = transfers.download(server, entry) { setTransfer(TransferStatus(label, it)) }
            setTransfer(null)
            when (result) {
                is VmResult.Success -> shareChannel.send(result.value)
                is VmResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    /**
     * Uploads a document picked on the phone into the current folder. A name already
     * taken there gets a " (n)" suffix rather than overwriting the server's file.
     */
    fun upload(uri: Uri) {
        val state = _uiState.value
        if (state.transfer != null || state.serverId.isEmpty() || state.path.isEmpty()) return
        val server = state.serverId
        val folder = state.path
        viewModelScope.launch {
            setTransfer(TransferStatus("Preparing upload"))
            val local = when (val copied = transfers.copyToCache(uri)) {
                is VmResult.Success -> copied.value
                is VmResult.Failure -> return@launch finishUpload(copied.error)
            }
            try {
                val error = when (val name = freeName(server, folder, local.name)) {
                    is VmResult.Failure -> name.error
                    is VmResult.Success -> {
                        val label = "Uploading ${name.value}"
                        setTransfer(TransferStatus(label, 0f))
                        transfers.upload(server, local, RemotePath.join(folder, name.value)) {
                            setTransfer(TransferStatus(label, it))
                        }
                    }
                }
                finishUpload(error)
            } finally {
                withContext(NonCancellable) { transfers.discard(local) }
            }
        }
    }

    private fun finishUpload(error: VmError?) {
        _uiState.update { it.copy(transfer = null, error = error ?: it.error) }
        refresh()
    }

    private suspend fun freeName(server: String, folder: String, name: String): VmResult<String> {
        for (candidate in candidateNames(name)) {
            when (val exists = remoteFileSystem.exists(server, RemotePath.join(folder, candidate))) {
                is VmResult.Failure -> return exists
                is VmResult.Success -> if (!exists.value) return VmResult.Success(candidate)
            }
        }
        return VmResult.Failure(
            VmError.FileSystem(summary = "Could not find a free name for $name", path = folder),
        )
    }

    private fun setTransfer(status: TransferStatus?) {
        _uiState.update { it.copy(transfer = status) }
    }

    /** Opens the file sheet and, for previewable types, starts loading the preview. */
    private fun showFile(entry: RemoteFileEntry) {
        previewJob?.cancel()
        val kind = entry.previewKind()
        _uiState.update {
            it.copy(
                pendingAction = FileAction.Details(entry),
                preview = if (kind == PreviewKind.NONE) null else PreviewContent.Loading(),
            )
        }
        if (kind == PreviewKind.NONE) return
        val server = serverId
        previewJob = viewModelScope.launch {
            val content = if (kind == PreviewKind.TEXT) {
                transfers.loadText(server, entry)
            } else {
                transfers.loadImage(server, entry) { fraction ->
                    _uiState.update {
                        if (it.preview is PreviewContent.Loading) {
                            it.copy(preview = PreviewContent.Loading(fraction))
                        } else {
                            it
                        }
                    }
                }
            }
            // The sheet may have been closed or switched to another file meanwhile.
            _uiState.update {
                val stillOpen = (it.pendingAction as? FileAction.Details)?.entry == entry
                if (stillOpen) it.copy(preview = content) else it
            }
        }
    }

    /** Points the browser at [id], forgetting everything about the previous server. */
    private fun openServer(id: String, startPath: String?) {
        history.clear()
        openJob?.cancel()
        refreshJob?.cancel()
        previewJob?.cancel()
        _uiState.update {
            FilesUiState(serverId = id, servers = it.servers, options = it.options, transfer = it.transfer)
        }
        openJob = viewModelScope.launch {
            if (startPath != null) return@launch navigateTo(startPath, recordHistory = false)
            when (val home = remoteFileSystem.homeDirectory(id)) {
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

    private fun mutate(block: suspend (server: String, path: String) -> VmResult<Unit>) {
        val server = serverId
        val path = _uiState.value.path
        _uiState.update { it.copy(isLoading = true, pendingAction = null) }
        viewModelScope.launch {
            when (val result = block(server, path)) {
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
