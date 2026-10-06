package digital.vmstudio.code.core.ui.markdown

/**
 * One rendered piece of an assistant message.
 *
 * The subset models actually write: fenced and inline code, emphasis, links,
 * headings, nested lists, quotes, rules and tables. Gateway models (GPT, Gemini…)
 * lean on tables and `<br>` far more than Claude does; left unparsed, they showed
 * as walls of pipes and tags.
 */
sealed interface MarkdownBlock {

    data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock

    data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock

    /** [depth] is the nesting level, 0 for a top-level item. */
    data class BulletItem(
        val spans: List<MarkdownSpan>,
        val ordinal: String?,
        val depth: Int = 0,
    ) : MarkdownBlock

    data class Quote(val spans: List<MarkdownSpan>) : MarkdownBlock

    /** A `---` line. */
    data object Rule : MarkdownBlock

    /** A pipe table; every row has as many cells as [header]. */
    data class Table(
        val header: List<List<MarkdownSpan>>,
        val rows: List<List<List<MarkdownSpan>>>,
    ) : MarkdownBlock

    /**
     * A fenced code block.
     *
     * The most important block type here: the agent writes commands and patches the
     * user needs verbatim, so this is what makes them readable and copyable rather
     * than reflowed into prose.
     */
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock
}

/** Inline formatting within a line. */
data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    /** Set for a `[text](url)` link. */
    val url: String? = null,
)

/**
 * Parses the markdown subset the agent emits.
 *
 * Hand-written rather than pulled from a library: the input is a known, narrow
 * dialect, and the alternative was a dependency whose rendering could not be styled
 * to match the design system without fighting it.
 */
object MarkdownParser {

    fun parse(source: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = source.lines()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]

            when {
                line.trimStart().startsWith(FENCE) -> {
                    val language = line.trimStart().removePrefix(FENCE).trim().ifBlank { null }
                    val body = mutableListOf<String>()
                    index++
                    // An unterminated fence runs to the end rather than swallowing the
                    // rest as prose: a truncated stream is common mid-run.
                    while (index < lines.size && !lines[index].trimStart().startsWith(FENCE)) {
                        body += lines[index]
                        index++
                    }
                    index++
                    blocks += MarkdownBlock.CodeBlock(body.joinToString("\n"), language)
                }

                isTableStart(lines, index) -> index = readTable(lines, index, blocks)

                RULE.matches(line) -> {
                    blocks += MarkdownBlock.Rule
                    index++
                }

                line.trimStart().startsWith(">") -> index = readQuote(lines, index, blocks)

                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceAtMost(MAX_HEADING)
                    val text = line.drop(level).trim()
                    if (text.isNotEmpty()) blocks += MarkdownBlock.Heading(level, parseSpans(text))
                    index++
                }

                BULLET.matches(line) || ORDERED.matches(line) -> {
                    blocks += listItem(line)
                    index++
                }

                line.isBlank() -> index++

                else -> {
                    // Consecutive non-blank lines form one paragraph, so a soft-wrapped
                    // sentence is not rendered as several stacked fragments.
                    val paragraph = mutableListOf<String>()
                    while (index < lines.size && continuesParagraph(lines[index])) {
                        paragraph += lines[index]
                        index++
                    }
                    blocks += MarkdownBlock.Paragraph(parseSpans(paragraph.joinToString(" ")))
                }
            }
        }

        return blocks
    }

    /**
     * Splits a line into styled spans.
     *
     * Inline code is resolved before emphasis, so `**` inside backticks stays literal
     * — otherwise a snippet like `a ** b` would silently turn bold and lose characters.
     */
    fun parseSpans(text: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>()
        // Models use HTML line breaks inside table cells and lists.
        var remaining = text.replace(LINE_BREAK, "\n")

        while (remaining.isNotEmpty()) {
            val match = INLINE.find(remaining)
            if (match == null) {
                spans += MarkdownSpan(remaining)
                break
            }

            if (match.range.first > 0) {
                spans += MarkdownSpan(remaining.substring(0, match.range.first))
            }

            val token = match.value
            when {
                token.startsWith("`") -> spans += MarkdownSpan(token.trim('`'), code = true)
                token.startsWith("[") ->
                    spans += MarkdownSpan(match.groupValues[1], url = match.groupValues[2])
                token.startsWith("**") -> spans += MarkdownSpan(token.removeSurrounding("**"), bold = true)
                token.startsWith("__") -> spans += MarkdownSpan(token.removeSurrounding("__"), bold = true)
                token.startsWith("*") -> spans += MarkdownSpan(token.removeSurrounding("*"), italic = true)
                token.startsWith("_") -> spans += MarkdownSpan(token.removeSurrounding("_"), italic = true)
            }

            remaining = remaining.substring(match.range.last + 1)
        }

        return spans.filter { it.text.isNotEmpty() }
    }

    /** A line that is plain prose, rather than the start of some other block. */
    private fun continuesParagraph(line: String): Boolean {
        val trimmed = line.trimStart()
        return line.isNotBlank() &&
            !line.startsWith("#") &&
            !trimmed.startsWith(FENCE) &&
            !trimmed.startsWith("|") &&
            !trimmed.startsWith(">") &&
            !RULE.matches(line) &&
            !BULLET.matches(line) &&
            !ORDERED.matches(line)
    }

    /** Reads a table starting at [start]; returns the index after it. */
    private fun readTable(lines: List<String>, start: Int, blocks: MutableList<MarkdownBlock>): Int {
        val header = splitRow(lines[start])
        val rows = mutableListOf<List<List<MarkdownSpan>>>()
        var index = start + 2
        while (index < lines.size && lines[index].trimStart().startsWith("|")) {
            val cells = splitRow(lines[index])
            rows += List(header.size) { cells.getOrElse(it) { emptyList() } }
            index++
        }
        blocks += MarkdownBlock.Table(header, rows)
        return index
    }

    /** Reads consecutive `>` lines as one quote; returns the index after them. */
    private fun readQuote(lines: List<String>, start: Int, blocks: MutableList<MarkdownBlock>): Int {
        val quoted = mutableListOf<String>()
        var index = start
        while (index < lines.size && lines[index].trimStart().startsWith(">")) {
            quoted += lines[index].trimStart().removePrefix(">").trim()
            index++
        }
        blocks += MarkdownBlock.Quote(parseSpans(quoted.joinToString("\n")))
        return index
    }

    /** A `-`/`*`/`+` bullet or a `1.` item, with its nesting depth. */
    private fun listItem(line: String): MarkdownBlock.BulletItem {
        val trimmed = line.trimStart()
        val ordinal = trimmed.substringBefore(' ').takeIf { ORDERED.matches(line) }
        return MarkdownBlock.BulletItem(
            spans = parseSpans(trimmed.substringAfter(' ').trim()),
            ordinal = ordinal,
            depth = depthOf(line),
        )
    }

    /** A header row directly followed by a `|---|---|` separator. */
    private fun isTableStart(lines: List<String>, index: Int): Boolean =
        lines[index].trimStart().startsWith("|") &&
            index + 1 < lines.size &&
            TABLE_SEPARATOR.matches(lines[index + 1])

    private fun splitRow(line: String): List<List<MarkdownSpan>> =
        line.trim().removePrefix("|").removeSuffix("|")
            .split("|")
            .map { parseSpans(it.trim()) }

    /** Two spaces (or a tab) of indent per nesting level. */
    private fun depthOf(line: String): Int {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
            .sumOf { if (it == '\t') TAB_WIDTH else 1 }
        return (indent / INDENT_PER_LEVEL).coerceAtMost(MAX_DEPTH)
    }

    private const val FENCE = "```"
    private const val MAX_HEADING = 6
    private const val TAB_WIDTH = 4
    private const val INDENT_PER_LEVEL = 2
    private const val MAX_DEPTH = 3

    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
    private val LINE_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    private val BULLET = Regex("""^\s*[-*+]\s+.*""")
    private val ORDERED = Regex("""^\s*\d+[.)]\s+.*""")

    /**
     * Backticks first so emphasis markers inside code are not treated as markup.
     * Underscore emphasis needs a non-word character on both sides, so names like
     * `file_name_here` are not turned italic.
     */
    private val INLINE = Regex(
        listOf(
            """`[^`]+`""",
            """\[([^\]]+)]\((\S+?)\)""",
            """\*\*[^*]+\*\*""",
            """(?<!\w)__[^_]+__(?!\w)""",
            """\*[^*\s][^*]*\*""",
            """(?<!\w)_[^_]+_(?!\w)""",
        ).joinToString("|"),
    )
}
