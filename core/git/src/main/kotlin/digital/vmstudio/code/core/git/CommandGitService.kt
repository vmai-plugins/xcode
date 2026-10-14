package digital.vmstudio.code.core.git

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.shellQuotePath
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
        val command = "git rev-parse --verify HEAD >/dev/null 2>&1 && git diff HEAD || git diff --cached 2>/dev/null || git diff"
        return execute(serverId, repoPath, command).map { DiffParser.parse(it.stdout) }
    }

    override suspend fun add(serverId: String, repoPath: String, files: List<String>): GitResult<Unit> {
        val fileArgs = if (files.isEmpty()) "." else files.joinToString(" ") { it.shellQuoted() }
        return execute(serverId, repoPath, "git add $fileArgs").map { }
    }

    override suspend fun reset(serverId: String, repoPath: String, files: List<String>): GitResult<Unit> {
        val fileArgs = if (files.isEmpty()) "" else " -- " + files.joinToString(" ") { it.shellQuoted() }
        return execute(serverId, repoPath, "git reset$fileArgs").map { }
    }

    override suspend fun commit(serverId: String, repoPath: String, message: String): GitResult<Unit> {
        return execute(serverId, repoPath, "git commit -m ${message.shellQuoted()}").map { }
    }

    override suspend fun log(serverId: String, repoPath: String, maxCount: Int): GitResult<GitLog> {
        val format = "%H%n%h%n%an%n%ae%n%at%n%P%n%s%n---COMMIT_END---"
        return execute(serverId, repoPath, "git log -n $maxCount --pretty=format:\"$format\"")
            .map { GitParsers.parseLog(it.stdout) }
    }

    override suspend fun currentBranch(serverId: String, repoPath: String): GitResult<String> {
        val branchResult = execute(serverId, repoPath, "git branch --show-current")
        return when (branchResult) {
            is GitResult.Success -> {
                val branch = branchResult.value.stdout.trim()
                if (branch.isNotEmpty()) {
                    GitResult.Success(branch)
                } else {
                    // Detached HEAD or rebasing: fallback to short commit hash or HEAD
                    execute(serverId, repoPath, "git rev-parse --short HEAD")
                        .map { it.stdout.trim().ifEmpty { "HEAD" } }
                }
            }
            is GitResult.Failure -> branchResult
        }
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

    override suspend fun restoreFile(serverId: String, repoPath: String, filePath: String): GitResult<Unit> {
        val normalized = filePath.trim().replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.split('/').any { it == ".." }) {
            return GitResult.Failure(
                GitError.CommandFailed("restoreFile", -1, "Invalid relative file path for restore: $filePath"),
            )
        }
        return execute(serverId, repoPath, "git checkout HEAD -- ${normalized.shellQuoted()} 2>/dev/null || rm -f ${normalized.shellQuoted()}").map { }
    }

    override suspend fun discoverRepositories(
        serverId: String,
        searchRoots: List<String>,
        maxDepth: Int,
    ): GitResult<List<DiscoveredGitRepo>> {
        val validRoots = searchRoots.filter { it.isNotBlank() }
        if (validRoots.isEmpty()) return GitResult.Success(emptyList())
        val roots = validRoots.joinToString(" ") { shellQuotePath(it) }
        val script = "for r in $roots; do if [ -d \"\$r\" ]; then find \"\$r\" -maxdepth $maxDepth -name .git -type d 2>/dev/null; fi; done"
        return when (val outcome = connectionManager.withSession(serverId) { session ->
            session.execute(script)
        }) {
            is VmResult.Success -> {
                val lines = outcome.value.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val repos = lines.mapNotNull { gitPath ->
                    val repoPath = gitPath.removeSuffix("/.git").removeSuffix("/.git/")
                    if (repoPath.isNotBlank()) {
                        val name = repoPath.substringAfterLast('/').ifBlank { repoPath }
                        DiscoveredGitRepo(path = repoPath, name = name)
                    } else null
                }.distinctBy { it.path }
                GitResult.Success(repos)
            }
            is VmResult.Failure -> GitResult.Failure(
                GitError.CommandFailed("discoverRepositories", -1, outcome.error.summary),
            )
        }
    }

    private suspend fun execute(
        serverId: String,
        repoPath: String,
        command: String,
    ): GitResult<SshCommandResult> {
        val fullCommand = "cd ${shellQuotePath(repoPath)} && $command"
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
                    val err = result.stderr.ifBlank { result.stdout }.ifBlank { "Git command exited with code $exitCode" }
                    GitResult.Failure(GitError.CommandFailed(command, exitCode, err))
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

/**
 * Quotes a value for safe interpolation into a POSIX shell command line.
 *
 * Every command in this file is built by string interpolation rather than an
 * argv-array exec (the SSH layer only offers a single command string), so a
 * repo path, filename, or commit message containing `"`, `` ` ``, `$`, or `\`
 * could otherwise break out of a naive double-quoted wrapper and run arbitrary
 * shell on the remote server - filenames and commit messages both come from
 * repository content an attacker can influence (a crafted filename in a
 * cloned/pulled repo, a crafted commit message), not just the user's own
 * input. Single-quoting is POSIX-safe for arbitrary bytes: the only character
 * that needs escaping inside single quotes is a single quote itself, which is
 * closed, escaped, and reopened (`'\''`).
 */
private fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"
