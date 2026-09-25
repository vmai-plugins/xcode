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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiffReviewUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val diff: GitDiff? = null,
    val error: VmError? = null,
    val filePath: String = "",
    val serverId: String = "",
)

/**
 * ViewModel for the diff review screen.
 *
 * Loads the current file content and compares it against a baseline (Git HEAD
 * or the original file) to produce a diff for review.
 */
@HiltViewModel
class DiffReviewViewModel @Inject constructor(
    private val gitService: GitService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "DiffReview requires a serverId"
    }
    private val filePath: String = requireNotNull(savedStateHandle[ARG_FILE_PATH]) {
        "DiffReview requires a filePath"
    }

    private val _uiState = MutableStateFlow(DiffReviewUiState(filePath = filePath, serverId = serverId))
    val uiState: StateFlow<DiffReviewUiState> = _uiState.asStateFlow()

    init {
        loadDiff()
    }

    fun loadDiff() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Get the diff for the specific file
            when (val result = gitService.diffAll(serverId, filePath.substringBeforeLast('/'))) {
                is GitResult.Success -> {
                    // Filter to just this file
                    val fileDiff = result.value.files.find {
                        it.newPath == filePath || it.oldPath == filePath
                    }
                    val filteredDiff = if (fileDiff != null) {
                        GitDiff(files = listOf(fileDiff))
                    } else {
                        GitDiff()
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        diff = filteredDiff,
                    )
                }
                is GitResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = mapGitError(result.error),
                    )
                }
            }
        }
    }

    fun accept() {
        // The reviewed changes are already the file's current content on the
        // server, so accepting requires no server-side action.
    }

    fun reject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val repoPath = filePath.substringBeforeLast('/')
            when (val result = gitService.restoreFile(serverId, repoPath, filePath)) {
                is GitResult.Success -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    diff = GitDiff(),
                )
                is GitResult.Failure -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = mapGitError(result.error),
                )
            }
        }
    }

    fun retry() {
        loadDiff()
    }

    private fun mapGitError(error: GitError): VmError = when (error) {
        is GitError.NotARepository -> VmError.Git(
            summary = "Not a Git repository",
            reason = "The directory '${error.path}' is not inside a Git repository.",
            suggestedAction = "Initialize a Git repository with 'git init' or navigate to a repository.",
            retryable = false,
            repository = error.path,
        )
        is GitError.CommandFailed -> VmError.Git(
            summary = "Git command failed",
            reason = error.stderr.ifBlank {
                "Command '${error.command}' exited with code ${error.exitCode}."
            },
            suggestedAction = "Check that Git is installed on the server and the file path is correct.",
            retryable = true,
        )
        is GitError.ParseError -> VmError.Git(
            summary = "Could not parse Git output",
            reason = error.message,
            suggestedAction = "The installed Git may be a non-standard variant; check its version.",
            retryable = true,
        )
        is GitError.NetworkError -> VmError.Git(
            summary = "Git network operation failed",
            reason = error.message,
            suggestedAction = "Check the connection to the Git remote and try again.",
            retryable = true,
        )
        is GitError.AuthenticationError -> VmError.Git(
            summary = "Git authentication failed",
            reason = error.message,
            suggestedAction = "Check your Git remote credentials on the server.",
            retryable = false,
        )
        is GitError.Unknown -> VmError.Git(
            summary = "Unexpected Git error",
            reason = error.message,
            suggestedAction = "Try again or check the server logs.",
            retryable = true,
        )
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
        const val ARG_FILE_PATH = "filePath"
    }
}
