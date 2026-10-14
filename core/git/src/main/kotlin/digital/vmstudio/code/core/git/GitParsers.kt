package digital.vmstudio.code.core.git

/**
 * Parses Git command output into model objects.
 */
internal object GitParsers {

    fun parseStatus(output: String, branch: String): GitStatus {
        val staged = mutableListOf<GitFileStatusEntry>()
        val unstaged = mutableListOf<GitFileStatusEntry>()
        val untracked = mutableListOf<GitFileStatusEntry>()
        var aheadCount = 0
        var behindCount = 0
        var upstream: String? = null
        var currentBranch = branch

        for (line in output.lines()) {
            if (line.isBlank()) continue

            // Parse branch info from first line: ## branch...upstream [ahead N, behind M]
            if (line.startsWith("## ")) {
                val branchInfo = line.removePrefix("## ")
                val aheadMatch = Regex("ahead (\\d+)").find(branchInfo)
                val behindMatch = Regex("behind (\\d+)").find(branchInfo)
                aheadCount = aheadMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                behindCount = behindMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                upstream = if (branchInfo.contains("...")) {
                    branchInfo.substringAfter("...").substringBefore(" [").ifBlank { null }
                } else {
                    null
                }
                if (currentBranch.isBlank() || currentBranch == "HEAD") {
                    val candidate = branchInfo.substringBefore("...").substringBefore(" [").trim()
                    if (candidate.isNotBlank() && !candidate.startsWith("HEAD (no branch)")) {
                        currentBranch = candidate
                    }
                }
                continue
            }

            // Parse porcelain status: XY PATH or XY ORIG -> RENAME
            if (line.length < 3) continue
            val indexStatus = line[0]
            val workTreeStatus = line[1]
            val pathPart = line.substring(2).trimStart()

            val (rawOriginal, rawDest) = if (pathPart.contains(" -> ")) {
                pathPart.substringBefore(" -> ").trim() to pathPart.substringAfter(" -> ").trim()
            } else {
                null to pathPart.trim()
            }

            val unquote: (String) -> String = { raw ->
                if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2) {
                    raw.substring(1, raw.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\t", "\t")
                        .replace("\\n", "\n")
                } else {
                    raw
                }
            }

            val path = unquote(rawDest)
            val originalPath = rawOriginal?.let(unquote)

            val isConflict = indexStatus == 'U' || workTreeStatus == 'U' ||
                (indexStatus == 'A' && workTreeStatus == 'A') ||
                (indexStatus == 'D' && workTreeStatus == 'D')

            if (isConflict) {
                unstaged += GitFileStatusEntry(path, GitFileStatus.CONFLICTED)
                continue
            }

            // Staged changes (index)
            when (indexStatus) {
                'A' -> staged += GitFileStatusEntry(path, GitFileStatus.ADDED)
                'M' -> staged += GitFileStatusEntry(path, GitFileStatus.MODIFIED)
                'D' -> staged += GitFileStatusEntry(path, GitFileStatus.DELETED)
                'R' -> staged += GitFileStatusEntry(path, GitFileStatus.RENAMED, originalPath = originalPath)
                'C' -> staged += GitFileStatusEntry(path, GitFileStatus.COPIED, originalPath = originalPath)
            }

            // Unstaged changes (working tree)
            when (workTreeStatus) {
                '?' -> untracked += GitFileStatusEntry(path, GitFileStatus.UNTRACKED)
                '!' -> untracked += GitFileStatusEntry(path, GitFileStatus.IGNORED)
                'M' -> unstaged += GitFileStatusEntry(path, GitFileStatus.MODIFIED)
                'D' -> unstaged += GitFileStatusEntry(path, GitFileStatus.DELETED)
                'A' -> unstaged += GitFileStatusEntry(path, GitFileStatus.ADDED)
            }
        }

        return GitStatus(
            branch = currentBranch,
            upstream = upstream,
            aheadCount = aheadCount,
            behindCount = behindCount,
            staged = staged,
            unstaged = unstaged,
            untracked = untracked,
        )
    }

    fun parseLog(output: String): GitLog {
        val commits = mutableListOf<GitCommit>()
        val blocks = output.split("---COMMIT_END---")

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size >= 6 && lines[0].isNotBlank()) {
                commits += GitCommit(
                    hash = lines[0].trim(),
                    shortHash = lines.getOrNull(1)?.trim().orEmpty(),
                    author = lines.getOrNull(2)?.trim().orEmpty(),
                    authorEmail = lines.getOrNull(3)?.trim().orEmpty(),
                    timestamp = lines.getOrNull(4)?.trim()?.toLongOrNull() ?: 0L,
                    parentHashes = lines.getOrNull(5)?.trim()?.split(" ")?.filter { it.isNotBlank() }.orEmpty(),
                    message = if (lines.size > 6) lines.drop(6).joinToString("\n").trim() else "",
                )
            }
        }

        return GitLog(commits = commits)
    }
}


