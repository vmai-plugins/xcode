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
import digital.vmstudio.code.core.git.GitHubRepositoryClient
import digital.vmstudio.code.core.database.entity.ActivityCategory
import digital.vmstudio.code.core.database.repository.ActivityRepository
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.repository.ServerRepository
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

    /** The last save was refused because the file changed on the server since loading. */
    val hasConflict: Boolean get() = saveError?.summary == EditorViewModel.CONFLICT_SUMMARY
}

/**
 * Loads and saves remote files through SFTP, and manages editor state
 * (undo/redo, dirty tracking) for the editing screen.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
    private val gitHubRepositoryClient: GitHubRepositoryClient,
    private val serverRepository: ServerRepository,
    private val activityRepository: ActivityRepository,
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
            serverName = server?.name.orEmpty().ifBlank {
                when {
                    filePath.startsWith("/github/") -> "GitHub (Zero-Clone)"
                    filePath.startsWith("/vmhub/") -> "VM Project Hub"
                    serverId == RemoteFileSystem.LOCAL_SERVER_ID -> "This Device"
                    else -> ""
                }
            },
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
            try {
                if (filePath.startsWith("/github/")) {
                    val parts = filePath.removePrefix("/github/").split("/").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val repoFullName = "${parts[0]}/${parts[1]}"
                        val relativePath = parts.drop(2).joinToString("/")
                        when (val result = gitHubRepositoryClient.getFileContent(repoFullName, relativePath)) {
                            is VmResult.Success -> {
                                editor = EditorState(
                                    initialContent = result.value,
                                    fileName = filePath.substringAfterLast('/'),
                                )
                                loadedModifiedAtSeconds = null
                                publishSnapshot()
                                return@launch
                            }
                            is VmResult.Failure -> {
                                loadError.value = result.error
                                return@launch
                            }
                        }
                    } else {
                        loadError.value = VmError.FileSystem(
                            summary = "Invalid GitHub file path",
                            reason = "A GitHub file path must include owner, repository, and file path: $filePath",
                        )
                        return@launch
                    }
                }
                // The editor keeps whole copies of the text for undo and redraws every
                // line, so it opens files up to 1 MiB rather than the 8 MiB read limit.
                when (val result = remoteFileSystem.readText(serverId, filePath, maxBytes = MAX_EDIT_BYTES)) {
                    is VmResult.Success -> {
                        editor = EditorState(
                            initialContent = result.value,
                            fileName = filePath.substringAfterLast('/'),
                        )
                        loadedModifiedAtSeconds = currentModifiedAtSeconds()
                        publishSnapshot()
                    }
                    is VmResult.Failure -> {
                        loadError.value = result.error
                    }
                }
            } finally {
                isLoading.value = false
            }
        }
    }

    private var isUndoRedoActive = false

    fun onTextFieldValueChange(newValue: TextFieldValue) {
        isUndoRedoActive = false
        editor?.updateTextFieldValue(newValue)
        publishSnapshot()
    }

    fun commitChange() {
        if (isUndoRedoActive) {
            isUndoRedoActive = false
            return
        }
        editor?.commitChange()
        publishSnapshot()
    }

    fun undo() {
        isUndoRedoActive = true
        editor?.undo()
        publishSnapshot()
    }

    fun redo() {
        isUndoRedoActive = true
        editor?.redo()
        publishSnapshot()
    }

    fun findNext(query: String, forward: Boolean = true) {
        val current = editor?.textFieldValue ?: return
        if (query.isEmpty()) return
        val text = current.text
        val currentOffset = if (forward) current.selection.max else current.selection.min
        val index = if (forward) {
            val next = text.indexOf(query, currentOffset, ignoreCase = true)
            if (next >= 0) next else text.indexOf(query, 0, ignoreCase = true)
        } else {
            val prev = if (currentOffset > 0) {
                text.lastIndexOf(query, currentOffset - 1, ignoreCase = true)
            } else -1
            if (prev >= 0) prev else text.lastIndexOf(query, text.length, ignoreCase = true)
        }
        if (index >= 0) {
            val newSelection = androidx.compose.ui.text.TextRange(index, index + query.length)
            onTextFieldValueChange(current.copy(selection = newSelection))
        }
    }

    fun replace(target: String, replacement: String, all: Boolean = false) {
        val current = editor?.textFieldValue ?: return
        if (target.isEmpty()) return
        val text = current.text
        var newSelection = current.selection
        val newText = if (all) {
            val replaced = text.replace(target, replacement, ignoreCase = true)
            newSelection = androidx.compose.ui.text.TextRange(
                current.selection.start.coerceIn(0, replaced.length),
                current.selection.end.coerceIn(0, replaced.length),
            )
            replaced
        } else {
            val range = current.selection
            if (range.length > 0 && text.substring(range.min, range.max).equals(target, ignoreCase = true)) {
                val replaced = text.replaceRange(range.min, range.max, replacement)
                val searchStart = range.min + replacement.length
                val nextMatch = replaced.indexOf(target, searchStart, ignoreCase = true).takeIf { it >= 0 }
                    ?: replaced.indexOf(target, 0, ignoreCase = true).takeIf { it >= 0 }
                newSelection = if (nextMatch != null) {
                    androidx.compose.ui.text.TextRange(nextMatch, nextMatch + target.length)
                } else {
                    androidx.compose.ui.text.TextRange(searchStart.coerceIn(0, replaced.length))
                }
                replaced
            } else {
                findNext(target, forward = true)
                return
            }
        }
        if (newText != text) {
            onTextFieldValueChange(current.copy(text = newText, selection = newSelection, composition = null))
            commitChange()
        }
    }

    fun goToLine(lineNumber: Int) {
        val current = editor?.textFieldValue ?: return
        val lines = current.text.split("\n")
        val targetLine = (lineNumber - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        var offset = 0
        for (i in 0 until targetLine) {
            offset += lines[i].length + 1
        }
        val newOffset = offset.coerceIn(0, current.text.length)
        onTextFieldValueChange(
            current.copy(
                selection = androidx.compose.ui.text.TextRange(newOffset),
                composition = null,
            ),
        )
    }

    /** [overwrite] saves even though the file changed on the server since it was loaded. */
    fun save(overwrite: Boolean = false, onSuccess: (() -> Unit)? = null) {
        val state = editor ?: return
        viewModelScope.launch {
            isSaving.value = true
            saveError.value = null
            try {
                val isGitHub = filePath.startsWith("/github/")
                val loadedAt = loadedModifiedAtSeconds
                val remoteAt = if (overwrite || isGitHub) null else currentModifiedAtSeconds()
                if (loadedAt != null && remoteAt != null && remoteAt != loadedAt) {
                    saveError.value = VmError.FileSystem(
                        summary = CONFLICT_SUMMARY,
                        reason = "It was modified after this editor last loaded it - " +
                            "saving now would overwrite those changes.",
                        suggestedAction = "Overwrite to keep your version, or go back and reopen the file " +
                            "to see theirs.",
                        path = filePath,
                    )
                    return@launch
                }

                val text = state.snapshot().textFieldValue.text
                if (filePath.startsWith("/github/")) {
                    val parts = filePath.removePrefix("/github/").split("/").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val repoFullName = "${parts[0]}/${parts[1]}"
                        val relativePath = parts.drop(2).joinToString("/")
                        when (val result = gitHubRepositoryClient.putFileContent(
                            repoFullName = repoFullName,
                            path = relativePath,
                            content = text,
                            commitMessage = "Update ${filePath.substringAfterLast('/')} via X-Codes Editor",
                        )) {
                            is VmResult.Success -> {
                                state.markSaved(text)
                                publishSnapshot()
                                runCatching {
                                    activityRepository.record(
                                        category = ActivityCategory.FILE,
                                        title = "Saved ${filePath.substringAfterLast('/')}",
                                        detail = filePath,
                                        serverId = serverId,
                                    )
                                }
                                onSuccess?.invoke()
                                return@launch
                            }
                            is VmResult.Failure -> {
                                saveError.value = result.error
                                return@launch
                            }
                        }
                    } else {
                        saveError.value = VmError.FileSystem(
                            summary = "Invalid GitHub file path",
                            reason = "A GitHub file path must include owner, repository, and file path: $filePath",
                        )
                        return@launch
                    }
                }
                when (val result = remoteFileSystem.writeText(serverId, filePath, text)) {
                    is VmResult.Success -> {
                        state.markSaved(text)
                        loadedModifiedAtSeconds = currentModifiedAtSeconds()
                        publishSnapshot()
                        runCatching {
                            activityRepository.record(
                                category = ActivityCategory.FILE,
                                title = "Saved ${filePath.substringAfterLast('/')}",
                                detail = filePath,
                                serverId = serverId,
                            )
                        }
                        onSuccess?.invoke()
                    }
                    is VmResult.Failure -> {
                        saveError.value = result.error
                    }
                }
            } finally {
                isSaving.value = false
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
        internal const val CONFLICT_SUMMARY = "This file changed on the server"
        private const val MAX_EDIT_BYTES = 1L shl 20
    }
}
