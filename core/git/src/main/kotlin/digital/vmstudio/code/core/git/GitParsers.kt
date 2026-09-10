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

        for (line in output.lines()) {
            if (line.isBlank()) continue

            // Parse branch info from first line: ## branch...upstream [ahead N, behind M]
            if (line.startsWith("## ")) {
                val branchInfo = line.removePrefix("## ")
                val aheadMatch = Regex("ahead (\\d+)").find(branchInfo)
                val behindMatch = Regex("behind (\\d+)").find(branchInfo)
                aheadCount = aheadMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                behindCount = behindMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                upstream = branchInfo.substringAfter("...").substringBefore(" [").ifBlank { null }
                continue
            }

            // Parse porcelain status: XY PATH or XY ORIG -> RENAME
            if (line.length < 4) continue
            val indexStatus = line[0]
            val workTreeStatus = line[1]
            val pathPart = line.substring(3)

            val path = if (pathPart.contains(" -> ")) {
                pathPart.substringAfter(" -> ")
            } else {
                pathPart
            }

            when {
                workTreeStatus == '?' -> {
                    untracked += GitFileStatusEntry(path, GitFileStatus.UNTRACKED)
                }
                indexStatus == 'A' || workTreeStatus == 'A' -> {
                    staged += GitFileStatusEntry(path, GitFileStatus.ADDED)
                }
                indexStatus == 'D' || workTreeStatus == 'D' -> {
                    staged += GitFileStatusEntry(path, GitFileStatus.DELETED)
                }
                indexStatus == 'M' || workTreeStatus == 'M' -> {
                    if (indexStatus != ' ' && indexStatus != '?') {
                        staged += GitFileStatusEntry(path, GitFileStatus.MODIFIED)
                    }
                    if (workTreeStatus != ' ') {
                        unstaged += GitFileStatusEntry(path, GitFileStatus.MODIFIED)
                    }
                }
                indexStatus == 'R' -> {
                    staged += GitFileStatusEntry(path, GitFileStatus.RENAMED)
                }
                indexStatus == 'C' -> {
                    staged += GitFileStatusEntry(path, GitFileStatus.COPIED)
                }
                indexStatus == 'U' || workTreeStatus == 'U' -> {
                    staged += GitFileStatusEntry(path, GitFileStatus.CONFLICTED)
                }
            }
        }

        return GitStatus(
            branch = branch,
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
            if (lines.size >= 7) {
                commits += GitCommit(
                    hash = lines[0].trim(),
                    shortHash = lines[1].trim(),
                    author = lines[2].trim(),
                    authorEmail = lines[3].trim(),
                    timestamp = lines[4].trim().toLongOrNull() ?: 0L,
                    parentHashes = lines[5].trim().split(" ").filter { it.isNotBlank() },
                    message = lines.drop(6).joinToString("\n").trim(),
                )
            }
        }

        return GitLog(commits = commits)
    }
}


