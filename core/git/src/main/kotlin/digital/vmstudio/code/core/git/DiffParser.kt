package digital.vmstudio.code.core.git

/**
 * Parses unified diff output into [FileDiff] objects.
 */
object DiffParser {

    private val FILE_HEADER_OLD = Regex("""^--- (?:[a-zA-Z]\/|\/)?(.+)""")
    private val FILE_HEADER_NEW = Regex("""^\+\+\+ (?:[a-zA-Z]\/|\/)?(.+)""")
    private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*""")

    private fun cleanPath(raw: String?): String? {
        if (raw == null) return null
        val strippedTab = raw.substringBefore('\t').trim()
        return if (strippedTab.startsWith("\"") && strippedTab.endsWith("\"") && strippedTab.length >= 2) {
            strippedTab.substring(1, strippedTab.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        } else {
            strippedTab
        }
    }

    private val DIFF_GIT_HEADER = Regex("""^diff --git (?:a/|/)?(.+?) (?:b/|/)?(.+)$""")

    /**
     * Parses a complete diff output (potentially containing multiple files).
     */
    fun parse(diffOutput: String): GitDiff {
        if (diffOutput.isBlank()) return GitDiff()

        val files = mutableListOf<FileDiff>()
        val lines = diffOutput.lines()
        var i = 0

        var pendingNew = false
        var pendingDeleted = false
        var pendingRename = false
        var pendingOldPath: String? = null
        var pendingNewPath: String? = null
        var pendingBinaryInfo: String? = null
        var hasEmittedCurrent = false

        fun flushPendingIfNeeded() {
            if (hasEmittedCurrent) return
            val oldPath = if (pendingNew) null else pendingOldPath
            val newPath = if (pendingDeleted) null else pendingNewPath
            if (oldPath != null || newPath != null || pendingRename || pendingBinaryInfo != null) {
                val isRename = pendingRename || (oldPath != null && newPath != null && oldPath != newPath)
                val diffLines = if (pendingBinaryInfo != null) {
                    listOf(DiffLine(text = pendingBinaryInfo ?: "Binary files differ", type = DiffLineType.HEADER))
                } else {
                    emptyList()
                }
                files += FileDiff(
                    oldPath = oldPath,
                    newPath = newPath,
                    isNew = pendingNew,
                    isDeleted = pendingDeleted,
                    isRename = isRename,
                    lines = diffLines,
                )
            }
            pendingNew = false
            pendingDeleted = false
            pendingRename = false
            pendingOldPath = null
            pendingNewPath = null
            pendingBinaryInfo = null
            hasEmittedCurrent = false
        }

        while (i < lines.size) {
            val line = lines[i]

            if (line.startsWith("diff --git")) {
                flushPendingIfNeeded()
                hasEmittedCurrent = false
                val match = DIFF_GIT_HEADER.matchEntire(line)
                if (match != null) {
                    pendingOldPath = cleanPath(match.groupValues[1])
                    pendingNewPath = cleanPath(match.groupValues[2])
                }
                i++
                continue
            }

            if (line.startsWith("new file mode")) {
                pendingNew = true
                i++
                continue
            } else if (line.startsWith("deleted file mode")) {
                pendingDeleted = true
                i++
                continue
            } else if (line.startsWith("rename from ")) {
                pendingRename = true
                pendingOldPath = cleanPath(line.removePrefix("rename from "))
                i++
                continue
            } else if (line.startsWith("rename to ")) {
                pendingRename = true
                pendingNewPath = cleanPath(line.removePrefix("rename to "))
                i++
                continue
            } else if (line.startsWith("Binary files ") && line.endsWith(" differ")) {
                pendingBinaryInfo = line
                i++
                continue
            }

            if (line.startsWith("--- ") && i + 1 < lines.size && lines[i + 1].startsWith("+++ ")) {
                val rawOldMatch = FILE_HEADER_OLD.matchEntire(line)?.groupValues?.get(1)
                val rawNewMatch = FILE_HEADER_NEW.matchEntire(lines[i + 1])?.groupValues?.get(1)
                val rawOld = cleanPath(rawOldMatch) ?: pendingOldPath
                val rawNew = cleanPath(rawNewMatch) ?: pendingNewPath
                i += 2

                val isNewFile = rawOld == "dev/null" || rawOld == "/dev/null" || pendingNew
                val isDeletedFile = rawNew == "dev/null" || rawNew == "/dev/null" || pendingDeleted
                val oldPath = if (isNewFile && !pendingRename) null else rawOld
                val newPath = if (isDeletedFile && !pendingRename) null else rawNew

                val diffLines = mutableListOf<DiffLine>()
                val isNew = isNewFile
                val isDeleted = isDeletedFile
                val isRename = pendingRename || (oldPath != null && newPath != null && oldPath != newPath)

                // Parse hunks
                while (i < lines.size && !lines[i].startsWith("--- ") &&
                    !lines[i].startsWith("diff --git")
                ) {
                    val hunkMatch = HUNK_HEADER.matchEntire(lines[i])
                    if (hunkMatch != null) {
                        diffLines += DiffLine(text = lines[i], type = DiffLineType.HEADER)
                        i++
                        i = parseHunk(lines, i, diffLines)
                    } else {
                        i++
                    }
                }

                files += FileDiff(oldPath, newPath, isNew, isDeleted, isRename, calculateLineNumbers(diffLines))
                hasEmittedCurrent = true
                pendingNew = false
                pendingDeleted = false
                pendingRename = false
                pendingOldPath = null
                pendingNewPath = null
                pendingBinaryInfo = null
            } else {
                i++
            }
        }

        flushPendingIfNeeded()
        return GitDiff(files = files)
    }

    private fun parseHunk(lines: List<String>, startIndex: Int, output: MutableList<DiffLine>): Int {
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("diff --git") -> break
                line.startsWith("--- ") && (i + 1 < lines.size && lines[i + 1].startsWith("+++ ")) -> break
                line.startsWith("+") -> { output += DiffLine(text = line.drop(1), type = DiffLineType.ADDED); i++ }
                line.startsWith("-") -> { output += DiffLine(text = line.drop(1), type = DiffLineType.REMOVED); i++ }
                line.startsWith(" ") -> { output += DiffLine(text = line.drop(1), type = DiffLineType.UNCHANGED); i++ }
                line.startsWith("\\") -> i++ // "\ No newline at end of file"
                HUNK_HEADER.containsMatchIn(line) -> break
                else -> break
            }
        }
        return i
    }

    private fun calculateLineNumbers(lines: List<DiffLine>): List<DiffLine> {
        var oldLine = 0
        var newLine = 0
        val result = mutableListOf<DiffLine>()

        for (line in lines) {
            when (line.type) {
                DiffLineType.HEADER -> {
                    val match = HUNK_HEADER.matchEntire(line.text)
                    if (match != null) {
                        oldLine = match.groupValues[1].toInt()
                        newLine = match.groupValues[3].toInt()
                    }
                    result += line
                }
                DiffLineType.UNCHANGED -> {
                    result += line.copy(oldLineNumber = oldLine, newLineNumber = newLine)
                    oldLine++; newLine++
                }
                DiffLineType.ADDED -> {
                    result += line.copy(newLineNumber = newLine)
                    newLine++
                }
                DiffLineType.REMOVED -> {
                    result += line.copy(oldLineNumber = oldLine)
                    oldLine++
                }
                else -> result += line
            }
        }
        return result
    }
}
