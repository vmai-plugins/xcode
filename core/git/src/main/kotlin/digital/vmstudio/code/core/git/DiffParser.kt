package digital.vmstudio.code.core.git

/**
 * Parses unified diff output into [FileDiff] objects.
 */
object DiffParser {

    private val FILE_HEADER_OLD = Regex("""^--- a?\/(.+)""")
    private val FILE_HEADER_NEW = Regex("""^\+\+\+ b?\/(.+)""")
    private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*""")

    /**
     * Parses a complete diff output (potentially containing multiple files).
     */
    fun parse(diffOutput: String): GitDiff {
        if (diffOutput.isBlank()) return GitDiff()

        val files = mutableListOf<FileDiff>()
        val lines = diffOutput.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            if (line.startsWith("--- ") && i + 1 < lines.size && lines[i + 1].startsWith("+++ ")) {
                val oldPath = FILE_HEADER_OLD.matchEntire(line)?.groupValues?.get(1)
                val newPath = FILE_HEADER_NEW.matchEntire(lines[i + 1])?.groupValues?.get(1)
                i += 2

                val diffLines = mutableListOf<DiffLine>()
                var isNew = false
                var isDeleted = false
                var isRename = false

                // Check for file mode changes
                if (i < lines.size && lines[i].startsWith("new file mode")) {
                    isNew = true; i++
                } else if (i < lines.size && lines[i].startsWith("deleted file mode")) {
                    isDeleted = true; i++
                } else if (i < lines.size && lines[i].startsWith("rename from")) {
                    isRename = true; i += 2
                }

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
            } else {
                i++
            }
        }

        return GitDiff(files = files)
    }

    private fun parseHunk(lines: List<String>, startIndex: Int, output: MutableList<DiffLine>): Int {
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            when {
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
