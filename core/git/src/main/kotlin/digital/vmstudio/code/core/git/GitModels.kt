package digital.vmstudio.code.core.git

/**
 * Git status of a file in a repository.
 */
enum class GitFileStatus {
    UNMODIFIED,
    MODIFIED,
    ADDED,
    DELETED,
    RENAMED,
    COPIED,
    UNTRACKED,
    IGNORED,
    CONFLICTED,
    ;

    /** Short single-letter prefix for display (e.g., "M", "A", "?"). */
    val prefix: String
        get() = when (this) {
            UNMODIFIED -> " "
            MODIFIED -> "M"
            ADDED -> "A"
            DELETED -> "D"
            RENAMED -> "R"
            COPIED -> "C"
            UNTRACKED -> "?"
            IGNORED -> "!"
            CONFLICTED -> "U"
        }
}

/**
 * Status of a single file in a Git working tree.
 */
data class GitFileStatusEntry(
    val path: String,
    val status: GitFileStatus,
    /** Original path for renamed/copied files. */
    val originalPath: String? = null,
)

/**
 * Result of `git status` for a repository.
 */
data class GitStatus(
    val branch: String,
    val upstream: String? = null,
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val staged: List<GitFileStatusEntry> = emptyList(),
    val unstaged: List<GitFileStatusEntry> = emptyList(),
    val untracked: List<GitFileStatusEntry> = emptyList(),
) {
    val hasChanges: Boolean
        get() = staged.isNotEmpty() || unstaged.isNotEmpty() || untracked.isNotEmpty()

    val isAhead: Boolean get() = aheadCount > 0
    val isBehind: Boolean get() = behindCount > 0
    val isSynced: Boolean get() = aheadCount == 0 && behindCount == 0
}

/**
 * A single line in a unified diff.
 */
data class DiffLine(
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null,
    val text: String,
    val type: DiffLineType,
)

enum class DiffLineType {
    ADDED,
    REMOVED,
    UNCHANGED,
    HEADER, // @@ -x,y +a,b @@
    FILE_HEADER, // --- a/file or +++ b/file
}

/**
 * Diff for a single file.
 */
data class FileDiff(
    val oldPath: String?,
    val newPath: String?,
    val isNew: Boolean = false,
    val isDeleted: Boolean = false,
    val isRename: Boolean = false,
    val lines: List<DiffLine> = emptyList(),
) {
    val displayPath: String
        get() = (newPath ?: oldPath ?: "unknown").substringAfterLast('/')

    val additions: Int get() = lines.count { it.type == DiffLineType.ADDED }
    val deletions: Int get() = lines.count { it.type == DiffLineType.REMOVED }
}

/**
 * Result of `git diff` for a repository.
 */
data class GitDiff(
    val files: List<FileDiff> = emptyList(),
) {
    val totalAdditions: Int get() = files.sumOf { it.additions }
    val totalDeletions: Int get() = files.sumOf { it.deletions }
    val hasChanges: Boolean get() = files.isNotEmpty()
}

/**
 * A single commit in the Git log.
 */
data class GitCommit(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val authorEmail: String,
    val timestamp: Long, // epoch seconds
    val parentHashes: List<String> = emptyList(),
)

/**
 * Result of `git log`.
 */
data class GitLog(
    val commits: List<GitCommit> = emptyList(),
    val branch: String = "",
)

/**
 * Result of a Git operation.
 */
sealed interface GitResult<out T> {
    data class Success<T>(val value: T) : GitResult<T>
    data class Failure(val error: GitError) : GitResult<Nothing>
}

/**
 * Errors that can occur during Git operations.
 */
sealed class GitError {
    data class NotARepository(val path: String) : GitError()
    data class CommandFailed(val command: String, val exitCode: Int, val stderr: String) : GitError()
    data class ParseError(val message: String) : GitError()
    data class NetworkError(val message: String) : GitError()
    data class AuthenticationError(val message: String) : GitError()
    data class Unknown(val message: String) : GitError()
}
