package digital.vmstudio.code.core.git

/**
 * Git operations for a remote repository.
 *
 * All operations execute Git commands on the remote server via SSH, so the
 * server must have Git installed. The interface is designed for the app's
 * needs (status, diff, commit, log) and is not a complete Git API.
 */
interface GitService {

    /**
     * Checks if the given path is inside a Git repository.
     */
    suspend fun isRepository(serverId: String, path: String): GitResult<Boolean>

    /**
     * Gets the status of the working tree.
     */
    suspend fun status(serverId: String, repoPath: String): GitResult<GitStatus>

    /**
     * Gets the diff for unstaged changes (working tree vs index).
     */
    suspend fun diffUnstaged(serverId: String, repoPath: String): GitResult<GitDiff>

    /**
     * Gets the diff for staged changes (index vs HEAD).
     */
    suspend fun diffStaged(serverId: String, repoPath: String): GitResult<GitDiff>

    /**
     * Gets the diff between the working tree and HEAD (all changes).
     */
    suspend fun diffAll(serverId: String, repoPath: String): GitResult<GitDiff>

    /**
     * Stages files for commit. Empty list stages all changes.
     */
    suspend fun add(serverId: String, repoPath: String, files: List<String> = emptyList()): GitResult<Unit>

    /**
     * Unstages files. Empty list unstages all.
     */
    suspend fun reset(serverId: String, repoPath: String, files: List<String> = emptyList()): GitResult<Unit>

    /**
     * Creates a commit with the given message.
     */
    suspend fun commit(serverId: String, repoPath: String, message: String): GitResult<Unit>

    /**
     * Gets the commit log.
     */
    suspend fun log(serverId: String, repoPath: String, maxCount: Int = 50): GitResult<GitLog>

    /**
     * Gets the current branch name.
     */
    suspend fun currentBranch(serverId: String, repoPath: String): GitResult<String>

    /**
     * Fetches from the remote.
     */
    suspend fun fetch(serverId: String, repoPath: String): GitResult<Unit>

    /**
     * Pulls from the remote (fetch + merge).
     */
    suspend fun pull(serverId: String, repoPath: String): GitResult<Unit>

    /**
     * Pushes to the remote.
     */
    suspend fun push(serverId: String, repoPath: String): GitResult<Unit>
}
