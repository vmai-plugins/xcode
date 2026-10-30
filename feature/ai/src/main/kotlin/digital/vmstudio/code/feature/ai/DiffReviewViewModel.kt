package digital.vmstudio.code.feature.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.git.GitDiff
import digital.vmstudio.code.core.git.GitError
import digital.vmstudio.code.core.git.GitResult
import digital.vmstudio.code.core.git.GitService
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiffReviewUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isStaged: Boolean = false,
    val isCommitted: Boolean = false,
    val diff: GitDiff? = null,
    val error: VmError? = null,
    val filePath: String = "",
    val serverId: String = "",
    val branchName: String = "",
    val showCommitSheet: Boolean = false,
)

/**
 * Interactive Git Diff & Merge Deck ViewModel.
 * Supports viewing diffs, staging/unstaging, discarding changes, and instant commits.
 */
@HiltViewModel
class DiffReviewViewModel @Inject constructor(
    private val gitService: GitService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle.get<String>(ARG_SERVER_ID).orEmpty()
    private val filePath: String = savedStateHandle.get<String>(ARG_FILE_PATH).orEmpty()

    private val _uiState = MutableStateFlow(DiffReviewUiState(filePath = filePath, serverId = serverId))
    val uiState: StateFlow<DiffReviewUiState> = _uiState.asStateFlow()

    init {
        if (serverId.isBlank() || filePath.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = VmError.Validation(
                        summary = "Missing diff coordinates",
                        reason = "DiffReview requires both serverId and filePath.",
                    ),
                )
            }
        } else {
            loadDiff()
        }
    }

    fun loadDiff() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            if (RemoteFileSystem.isLocalServer(serverId) ||
                filePath.startsWith("/github/") ||
                filePath.startsWith("/vmhub/") ||
                filePath.startsWith("content://")
            ) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        diff = GitDiff(),
                        error = VmError.Unexpected(
                            summary = "Diff review is not supported for local or virtual files",
                            reason = "Diff review inspects Git working tree changes on a remote SSH server.",
                            suggestedAction = "Review file edits directly in the Code Editor or AI Agent.",
                        ),
                    )
                }
                return@launch
            }

            val repoPath = directoryOf(filePath)
            if (repoPath == null) {
                _uiState.update { it.copy(isLoading = false, error = pathWithoutDirectoryError()) }
                return@launch
            }

            // Check branch and status
            val branch = when (val b = gitService.currentBranch(serverId, repoPath)) {
                is GitResult.Success -> b.value
                is GitResult.Failure -> "HEAD"
            }

            val statusResult = gitService.status(serverId, repoPath)
            val isFileStaged = if (statusResult is GitResult.Success) {
                statusResult.value.staged.any { filePath.isSamePath(it.path) }
            } else false

            when (val result = gitService.diffAll(serverId, repoPath)) {
                is GitResult.Success -> {
                    val fileDiff = result.value.files.find {
                        filePath.isSamePath(it.newPath) || filePath.isSamePath(it.oldPath)
                    }
                    val filteredDiff = if (fileDiff != null) {
                        GitDiff(files = listOf(fileDiff))
                    } else {
                        GitDiff()
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            diff = filteredDiff,
                            branchName = branch,
                            isStaged = isFileStaged,
                        )
                    }
                }
                is GitResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = mapGitError(result.error),
                        )
                    }
                }
            }
        }
    }

    fun stageFile() {
        viewModelScope.launch {
            val repoPath = directoryOf(filePath) ?: return@launch
            _uiState.update { it.copy(isSaving = true) }
            when (val res = gitService.add(serverId, repoPath, listOf(filePath))) {
                is GitResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, isStaged = true) }
                }
                is GitResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false, error = mapGitError(res.error)) }
                }
            }
        }
    }

    fun unstageFile() {
        viewModelScope.launch {
            val repoPath = directoryOf(filePath) ?: return@launch
            _uiState.update { it.copy(isSaving = true) }
            when (val res = gitService.reset(serverId, repoPath, listOf(filePath))) {
                is GitResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, isStaged = false) }
                }
                is GitResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false, error = mapGitError(res.error)) }
                }
            }
        }
    }

    fun commitAndPush(message: String, pushAfter: Boolean = false) {
        viewModelScope.launch {
            val repoPath = directoryOf(filePath) ?: return@launch
            _uiState.update { it.copy(isSaving = true, showCommitSheet = false) }

            // Ensure staged first
            gitService.add(serverId, repoPath, listOf(filePath))

            when (val commitRes = gitService.commit(serverId, repoPath, message)) {
                is GitResult.Success -> {
                    if (pushAfter) {
                        when (val pushRes = gitService.push(serverId, repoPath)) {
                            is GitResult.Success -> _uiState.update {
                                it.copy(isSaving = false, isCommitted = true, diff = GitDiff())
                            }
                            is GitResult.Failure -> _uiState.update {
                                it.copy(isSaving = false, error = mapGitError(pushRes.error))
                            }
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                isCommitted = true,
                                diff = GitDiff(),
                            )
                        }
                    }
                }
                is GitResult.Failure -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = mapGitError(commitRes.error))
                    }
                }
            }
        }
    }

    fun toggleCommitSheet(show: Boolean) {
        _uiState.update { it.copy(showCommitSheet = show) }
    }

    fun reject() {
        viewModelScope.launch {
            if (RemoteFileSystem.isLocalServer(serverId) ||
                filePath.startsWith("/github/") ||
                filePath.startsWith("/vmhub/") ||
                filePath.startsWith("content://")
            ) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = VmError.Unexpected(
                            summary = "Cannot revert virtual file",
                            reason = "Virtual projects do not maintain a remote git index.",
                            suggestedAction = "Use Code Editor or AI Agent to edit or revert changes.",
                        ),
                    )
                }
                return@launch
            }

            val repoPath = directoryOf(filePath)
            if (repoPath == null) {
                _uiState.update { it.copy(error = pathWithoutDirectoryError()) }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true) }
            when (val result = gitService.restoreFile(serverId, repoPath, filePath)) {
                is GitResult.Success -> _uiState.update {
                    it.copy(isSaving = false, diff = GitDiff())
                }
                is GitResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, error = mapGitError(result.error))
                }
            }
        }
    }

    fun retry() {
        loadDiff()
    }

    private fun directoryOf(path: String): String? {
        val index = path.lastIndexOf('/')
        return if (index < 0) null else path.substring(0, index)
    }

    private fun pathWithoutDirectoryError() = VmError.Git(
        summary = "Could not determine which directory to check",
        reason = "\"$filePath\" has no directory component.",
        suggestedAction = "Try again from the transcript.",
        retryable = false,
    )

    private fun mapGitError(error: GitError): VmError = when (error) {
        is GitError.NotARepository -> VmError.Git(
            summary = "Not a Git repository",
            reason = "The directory '${error.path}' is not inside a Git repository.",
            suggestedAction = "Initialize a Git repository with 'git init'.",
            retryable = false,
            repository = error.path,
        )
        is GitError.CommandFailed -> VmError.Git(
            summary = "Git command failed",
            reason = error.stderr.ifBlank {
                "Command '${error.command}' exited with code ${error.exitCode}."
            },
            suggestedAction = "Check that Git is installed on the server.",
            retryable = true,
        )
        is GitError.ParseError -> VmError.Git(
            summary = "Could not parse Git output",
            reason = error.message,
            suggestedAction = "Check the Git version.",
            retryable = true,
        )
        is GitError.NetworkError -> VmError.Git(
            summary = "Git network operation failed",
            reason = error.message,
            suggestedAction = "Check the connection to the Git remote.",
            retryable = true,
        )
        is GitError.AuthenticationError -> VmError.Git(
            summary = "Git authentication failed",
            reason = error.message,
            suggestedAction = "Check your Git credentials on the server.",
            retryable = false,
        )
        is GitError.Unknown -> VmError.Git(
            summary = "Unexpected Git error",
            reason = error.message,
            suggestedAction = "Try again or check server logs.",
            retryable = true,
        )
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
        const val ARG_FILE_PATH = "filePath"
    }
}

/** True when [repoRelative] (as git prints it) names this absolute or relative path. */
internal fun String.isSamePath(repoRelative: String?): Boolean {
    if (repoRelative.isNullOrBlank()) return false
    val relative = repoRelative.removePrefix("./")
    return this == relative || endsWith("/$relative")
}
