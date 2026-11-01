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

data class EditorTab(
    val serverId: String,
    val filePath: String,
    val fileName: String,
    val isDirty: Boolean = false,
)

data class EditorUiState(
    val snapshot: EditorSnapshot? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: VmError? = null,
    val saveError: VmError? = null,
    val serverName: String = "",
    val filePath: String = "",
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabKey: String = "",
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

    private val serverId: String = savedStateHandle.get<String>(ARG_SERVER_ID).orEmpty()
    private val initialFilePath: String = savedStateHandle.get<String>(ARG_FILE_PATH).orEmpty()

    private val isLoading = MutableStateFlow(true)
    private val isSaving = MutableStateFlow(false)
    private val loadError = MutableStateFlow<VmError?>(null)
    private val saveError = MutableStateFlow<VmError?>(null)

    private data class TabSession(
        val serverId: String,
        val filePath: String,
        val editor: EditorState,
        var loadedModifiedAtSeconds: Long?,
    )

    private val tabs = MutableStateFlow<Map<String, TabSession>>(emptyMap())
    private val activeTabKey = MutableStateFlow<String>("")

    /** The active editor; never observed directly. */
    private val currentEditor: EditorState?
        get() = tabs.value[activeTabKey.value]?.editor

    private val currentTabSession: TabSession?
        get() = tabs.value[activeTabKey.value]

    /**
     * A value-typed view of the active editor.
     */
    private val snapshot = MutableStateFlow<EditorSnapshot?>(null)

    val uiState: StateFlow<EditorUiState> = combine(
        combine(isLoading, isSaving) { loading, saving -> loading to saving },
        combine(loadError, saveError) { load, save -> load to save },
        snapshot,
        combine(tabs, activeTabKey) { allTabs, activeKey -> allTabs to activeKey },
        serverRepository.observe(serverId),
    ) { (loading, saving), (loadErr, saveErr), snap, (allTabs, activeKey), server ->
        val currentSession = allTabs[activeKey]
        val activePath = currentSession?.filePath ?: initialFilePath
        val activeServerId = currentSession?.serverId ?: serverId
        val tabList = allTabs.values.map { session ->
            EditorTab(
                serverId = session.serverId,
                filePath = session.filePath,
                fileName = session.filePath.substringAfterLast('/'),
                isDirty = session.editor.isDirty,
            )
        }
        EditorUiState(
            snapshot = snap,
            isLoading = loading,
            isSaving = saving,
            loadError = loadErr,
            saveError = saveErr,
            serverName = server?.name.orEmpty().ifBlank {
                when {
                    activePath.startsWith("/github/") -> "GitHub (Zero-Clone)"
                    activePath.startsWith("/vmhub/") -> "VM Project Hub"
                    activeServerId == RemoteFileSystem.LOCAL_SERVER_ID -> "This Device"
                    else -> ""
                }
            },
            filePath = activePath,
            openTabs = tabList,
            activeTabKey = activeKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorUiState(),
    )

    private fun publishSnapshot() {
        snapshot.value = currentEditor?.snapshot()
    }

    init {
        if (serverId.isBlank() || initialFilePath.isBlank()) {
            loadError.value = VmError.Validation(
                summary = "Missing file coordinates",
                reason = "Editor requires both a valid serverId and filePath.",
            )
            isLoading.value = false
        } else {
            loadFile(serverId, initialFilePath)
        }
    }

    fun openFile(targetServerId: String, targetFilePath: String) {
        val key = "$targetServerId:$targetFilePath"
        if (tabs.value.containsKey(key)) {
            switchTab(key)
        } else {
            loadFile(targetServerId, targetFilePath)
        }
    }

    fun switchTab(key: String) {
        if (tabs.value.containsKey(key)) {
            activeTabKey.value = key
            publishSnapshot()
        }
    }

    fun closeTab(key: String) {
        val currentTabs = tabs.value
        if (!currentTabs.containsKey(key)) return
        val newTabs = currentTabs.toMutableMap().apply { remove(key) }
        tabs.value = newTabs
        if (activeTabKey.value == key) {
            val nextKey = newTabs.keys.firstOrNull().orEmpty()
            activeTabKey.value = nextKey
            publishSnapshot()
        }
    }

    fun loadFile(targetServerId: String = serverId, targetFilePath: String = initialFilePath) {
        val tabKey = "$targetServerId:$targetFilePath"
        viewModelScope.launch {
            isLoading.value = true
            loadError.value = null
            try {
                if (targetFilePath.startsWith("/github/")) {
                    val parts = targetFilePath.removePrefix("/github/").split("/").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val repoFullName = "${parts[0]}/${parts[1]}"
                        val relativePath = parts.drop(2).joinToString("/")
                        when (val result = gitHubRepositoryClient.getFileContent(repoFullName, relativePath)) {
                            is VmResult.Success -> {
                                val newEditor = EditorState(
                                    initialContent = result.value,
                                    fileName = targetFilePath.substringAfterLast('/'),
                                )
                                val session = TabSession(
                                    serverId = targetServerId,
                                    filePath = targetFilePath,
                                    editor = newEditor,
                                    loadedModifiedAtSeconds = null,
                                )
                                tabs.value = tabs.value + (tabKey to session)
                                activeTabKey.value = tabKey
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
                            reason = "A GitHub file path must include owner, repository, and file path: $targetFilePath",
                        )
                        return@launch
                    }
                }
                when (val result = remoteFileSystem.readText(targetServerId, targetFilePath, maxBytes = MAX_EDIT_BYTES)) {
                    is VmResult.Success -> {
                        val newEditor = EditorState(
                            initialContent = result.value,
                            fileName = targetFilePath.substringAfterLast('/'),
                        )
                        val session = TabSession(
                            serverId = targetServerId,
                            filePath = targetFilePath,
                            editor = newEditor,
                            loadedModifiedAtSeconds = currentModifiedAtSeconds(targetServerId, targetFilePath),
                        )
                        tabs.value = tabs.value + (tabKey to session)
                        activeTabKey.value = tabKey
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
        currentEditor?.updateTextFieldValue(newValue)
        publishSnapshot()
    }

    fun commitChange() {
        if (isUndoRedoActive) {
            isUndoRedoActive = false
            return
        }
        currentEditor?.commitChange()
        publishSnapshot()
    }

    fun undo() {
        isUndoRedoActive = true
        currentEditor?.undo()
        publishSnapshot()
    }

    fun redo() {
        isUndoRedoActive = true
        currentEditor?.redo()
        publishSnapshot()
    }

    fun findNext(query: String, forward: Boolean = true) {
        val current = currentEditor?.textFieldValue ?: return
        if (query.isEmpty()) return
        val text = current.text
        val currentOffset = (if (forward) current.selection.max else current.selection.min).coerceIn(0, text.length)
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
        val current = currentEditor?.textFieldValue ?: return
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
            val min = range.min.coerceIn(0, text.length)
            val max = range.max.coerceIn(min, text.length)
            if (max > min && text.substring(min, max).equals(target, ignoreCase = true)) {
                val replaced = text.replaceRange(min, max, replacement)
                val searchStart = min + replacement.length
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
        val current = currentEditor?.textFieldValue ?: return
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
        val session = currentTabSession ?: return
        val state = session.editor
        val targetFilePath = session.filePath
        val targetServerId = session.serverId
        viewModelScope.launch {
            isSaving.value = true
            saveError.value = null
            try {
                val isGitHub = targetFilePath.startsWith("/github/")
                val loadedAt = session.loadedModifiedAtSeconds
                val remoteAt = if (overwrite || isGitHub) null else currentModifiedAtSeconds(targetServerId, targetFilePath)
                if (loadedAt != null && remoteAt != null && remoteAt != loadedAt) {
                    saveError.value = VmError.FileSystem(
                        summary = CONFLICT_SUMMARY,
                        reason = "It was modified after this editor last loaded it - " +
                            "saving now would overwrite those changes.",
                        suggestedAction = "Overwrite to keep your version, or go back and reopen the file " +
                            "to see theirs.",
                        path = targetFilePath,
                    )
                    return@launch
                }

                val text = state.snapshot().textFieldValue.text
                if (targetFilePath.startsWith("/github/")) {
                    val parts = targetFilePath.removePrefix("/github/").split("/").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val repoFullName = "${parts[0]}/${parts[1]}"
                        val relativePath = parts.drop(2).joinToString("/")
                        when (val result = gitHubRepositoryClient.putFileContent(
                            repoFullName = repoFullName,
                            path = relativePath,
                            content = text,
                            commitMessage = "Update ${targetFilePath.substringAfterLast('/')} via X-Codes Editor",
                        )) {
                            is VmResult.Success -> {
                                state.markSaved(text)
                                publishSnapshot()
                                runCatching {
                                    activityRepository.record(
                                        category = ActivityCategory.FILE,
                                        title = "Saved ${targetFilePath.substringAfterLast('/')}",
                                        detail = targetFilePath,
                                        serverId = targetServerId,
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
                            reason = "A GitHub file path must include owner, repository, and file path: $targetFilePath",
                        )
                        return@launch
                    }
                }
                when (val result = remoteFileSystem.writeText(targetServerId, targetFilePath, text)) {
                    is VmResult.Success -> {
                        state.markSaved(text)
                        session.loadedModifiedAtSeconds = currentModifiedAtSeconds(targetServerId, targetFilePath)
                        publishSnapshot()
                        runCatching {
                            activityRepository.record(
                                category = ActivityCategory.FILE,
                                title = "Saved ${targetFilePath.substringAfterLast('/')}",
                                detail = targetFilePath,
                                serverId = targetServerId,
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
    private suspend fun currentModifiedAtSeconds(targetServerId: String = serverId, targetFilePath: String = initialFilePath): Long? =
        when (val result = remoteFileSystem.stat(targetServerId, targetFilePath)) {
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
