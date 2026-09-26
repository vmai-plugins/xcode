package digital.vmstudio.code.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import androidx.compose.ui.text.input.TextFieldValue
import digital.vmstudio.code.core.editor.EditorSnapshot
import digital.vmstudio.code.core.editor.EditorState
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val snapshot: EditorSnapshot? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: VmError? = null,
    val saveError: VmError? = null,
    val serverName: String = "",
    val filePath: String = "",
) {
    val canSave: Boolean get() = snapshot?.isDirty == true && !isSaving
}

/**
 * Loads and saves remote files through SFTP, and manages editor state
 * (undo/redo, dirty tracking) for the editing screen.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
    private val serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "Editor requires a serverId"
    }
    private val filePath: String = requireNotNull(savedStateHandle[ARG_FILE_PATH]) {
        "Editor requires a filePath"
    }

    private val isLoading = MutableStateFlow(true)
    private val isSaving = MutableStateFlow(false)
    private val loadError = MutableStateFlow<VmError?>(null)
    private val saveError = MutableStateFlow<VmError?>(null)

    /** The mutable editor; never observed directly. */
    private var editor: EditorState? = null

    /**
     * The remote file's modification time as of the last successful load or save.
     *
     * save() otherwise writes unconditionally: if the AI agent, another device, or
     * a second editor tab changes this file between this screen's load and its
     * save, the user's save would silently clobber those changes with no warning.
     * Null when unknown (stat failed, or SFTP reported 0 - "unknown" per
     * RemoteFileEntry's own doc) skips the check rather than blocking a save on an
     * unrelated stat failure.
     */
    private var loadedModifiedAtSeconds: Long? = null

    /**
     * A value-typed view of [editor]. [EditorState] is identity-compared, so it
     * cannot drive a StateFlow; every mutation re-publishes a fresh snapshot here.
     */
    private val snapshot = MutableStateFlow<EditorSnapshot?>(null)

    val uiState: StateFlow<EditorUiState> = combine(
        combine(isLoading, isSaving) { loading, saving -> loading to saving },
        combine(loadError, saveError) { load, save -> load to save },
        snapshot,
        serverRepository.observe(serverId),
    ) { (loading, saving), (loadErr, saveErr), snap, server ->
        EditorUiState(
            snapshot = snap,
            isLoading = loading,
            isSaving = saving,
            loadError = loadErr,
            saveError = saveErr,
            serverName = server?.name.orEmpty(),
            filePath = filePath,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorUiState(),
    )

    private fun publishSnapshot() {
        snapshot.value = editor?.snapshot()
    }

    init {
        loadFile()
    }

    fun loadFile() {
        viewModelScope.launch {
            isLoading.value = true
            loadError.value = null
            when (val result = remoteFileSystem.readText(serverId, filePath)) {
                is VmResult.Success -> {
                    editor = EditorState(
                        initialContent = result.value,
                        fileName = filePath.substringAfterLast('/'),
                    )
                    loadedModifiedAtSeconds = currentModifiedAtSeconds()
                    publishSnapshot()
                    isLoading.value = false
                }
                is VmResult.Failure -> {
                    loadError.value = result.error
                    isLoading.value = false
                }
            }
        }
    }

    fun onTextFieldValueChange(newValue: TextFieldValue) {
        editor?.updateTextFieldValue(newValue)
        publishSnapshot()
    }

    fun commitChange() {
        editor?.commitChange()
        publishSnapshot()
    }

    fun undo() {
        editor?.undo()
        publishSnapshot()
    }

    fun redo() {
        editor?.redo()
        publishSnapshot()
    }

    fun save() {
        val state = editor ?: return
        viewModelScope.launch {
            isSaving.value = true
            saveError.value = null

            val loadedAt = loadedModifiedAtSeconds
            val remoteAt = currentModifiedAtSeconds()
            if (loadedAt != null && remoteAt != null && remoteAt != loadedAt) {
                saveError.value = VmError.FileSystem(
                    summary = "This file changed on the server",
                    reason = "It was modified after this editor last loaded it - " +
                        "saving now would overwrite those changes.",
                    suggestedAction = "Reload to see the current version, or save a copy elsewhere first.",
                    path = filePath,
                )
                isSaving.value = false
                return@launch
            }

            val text = state.snapshot().textFieldValue.text
            when (val result = remoteFileSystem.writeText(serverId, filePath, text)) {
                is VmResult.Success -> {
                    state.markSaved()
                    loadedModifiedAtSeconds = currentModifiedAtSeconds()
                    publishSnapshot()
                    isSaving.value = false
                }
                is VmResult.Failure -> {
                    saveError.value = result.error
                    isSaving.value = false
                }
            }
        }
    }

    /** Best-effort; null (not stat's own "unknown" 0) when the stat call itself fails. */
    private suspend fun currentModifiedAtSeconds(): Long? =
        when (val result = remoteFileSystem.stat(serverId, filePath)) {
            is VmResult.Success -> result.value.modifiedAtSeconds.takeIf { it != 0L }
            is VmResult.Failure -> null
        }

    fun clearSaveError() {
        saveError.value = null
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
        const val ARG_FILE_PATH = "filePath"
    }
}
