package digital.vmstudio.code.core.git

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Git service implementation that executes Git commands over SSH.
 */
@Singleton
class CommandGitService @Inject constructor(
    private val connectionManager: SshConnectionManager,
) : GitService {

    override suspend fun isRepository(serverId: String, path: String): GitResult<Boolean> {
        return execute(serverId, path, "git rev-parse --is-inside-work-tree")
            .map { it.stdout.trim() == "true" }
    }

    override suspend fun status(serverId: String, repoPath: String): GitResult<GitStatus> {
        val branchResult = execute(serverId, repoPath, "git branch --show-current")
        val branch = when (branchResult) {
            is GitResult.Success -> branchResult.value.stdout.trim()
            is GitResult.Failure -> return branchResult
        }

        val statusResult = execute(serverId, repoPath, "git status --porcelain=v1 -u --branch")
        val status = when (statusResult) {
            is GitResult.Success -> statusResult.value.stdout
            is GitResult.Failure -> return statusResult
        }

        return GitResult.Success(GitParsers.parseStatus(status, branch))
    }

    override suspend fun diffUnstaged(serverId: String, repoPath: String): GitResult<GitDiff> {
        return execute(serverId, repoPath, "git diff").map { DiffParser.parse(it.stdout) }
    }

    override suspend fun diffStaged(serverId: String, repoPath: String): GitResult<GitDiff> {
        return execute(serverId, repoPath, "git diff --cached").map { DiffParser.parse(it.stdout) }
    }

    override suspend fun diffAll(serverId: String, repoPath: String): GitResult<GitDiff> {
        return execute(serverId, repoPath, "git diff HEAD").map { DiffParser.parse(it.stdout) }
    }

    override suspend fun add(serverId: String, repoPath: String, files: List<String>): GitResult<Unit> {
        val fileArgs = if (files.isEmpty()) "." else files.joinToString(" ") { "\"$it\"" }
        return execute(serverId, repoPath, "git add $fileArgs").map { }
    }

    override suspend fun reset(serverId: String, repoPath: String, files: List<String>): GitResult<Unit> {
        val fileArgs = if (files.isEmpty()) "" else " -- " + files.joinToString(" ") { "\"$it\"" }
        return execute(serverId, repoPath, "git reset$fileArgs").map { }
    }

    override suspend fun commit(serverId: String, repoPath: String, message: String): GitResult<Unit> {
        val escapedMessage = message.replace("\"", "\\\"")
        return execute(serverId, repoPath, "git commit -m \"$escapedMessage\"").map { }
    }

    override suspend fun log(serverId: String, repoPath: String, maxCount: Int): GitResult<GitLog> {
        val format = "%H%n%h%n%an%n%ae%n%at%n%P%n%s%n---COMMIT_END---"
        return execute(serverId, repoPath, "git log -n $maxCount --pretty=format:\"$format\"")
            .map { GitParsers.parseLog(it.stdout) }
    }

    override suspend fun currentBranch(serverId: String, repoPath: String): GitResult<String> {
        return execute(serverId, repoPath, "git branch --show-current").map { it.stdout.trim() }
    }

    override suspend fun fetch(serverId: String, repoPath: String): GitResult<Unit> {
        return execute(serverId, repoPath, "git fetch").map { }
    }

    override suspend fun pull(serverId: String, repoPath: String): GitResult<Unit> {
        return execute(serverId, repoPath, "git pull").map { }
    }

    override suspend fun push(serverId: String, repoPath: String): GitResult<Unit> {
        return execute(serverId, repoPath, "git push").map { }
    }

    private suspend fun execute(
        serverId: String,
        repoPath: String,
        command: String,
    ): GitResult<SshCommandResult> {
        val fullCommand = "cd \"$repoPath\" && $command"
        // withSession's block returns VmResult by contract; the Git layer then maps
        // that outcome onto GitResult so callers see Git-specific error types.
        return when (val outcome = connectionManager.withSession(serverId) { session ->
            session.execute(fullCommand)
        }) {
            is VmResult.Success -> {
                val result = outcome.value
                val exitCode = result.exitCode ?: -1
                if (exitCode == 0) {
                    GitResult.Success(SshCommandResult(exitCode, result.stdout, result.stderr))
                } else {
                    GitResult.Failure(GitError.CommandFailed(command, exitCode, result.stderr))
                }
            }
            is VmResult.Failure -> GitResult.Failure(
                GitError.CommandFailed(command, exitCode = -1, stderr = outcome.error.summary),
            )
        }
    }
}

private data class SshCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

private fun <T, R> GitResult<T>.map(transform: (T) -> R): GitResult<R> = when (this) {
    is GitResult.Success -> GitResult.Success(transform(value))
    is GitResult.Failure -> this
}
