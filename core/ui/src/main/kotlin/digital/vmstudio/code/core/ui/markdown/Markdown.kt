package digital.vmstudio.code.core.ui.markdown

/**
 * One rendered piece of an assistant message.
 *
 * A deliberately small subset: the agent's output is Claude's markdown, not arbitrary
 * documents, so fenced code, inline code, emphasis, headings and lists cover
 * essentially all of it. Tables and images are left as plain text rather than
 * half-rendered, because a broken table is worse than an honest one.
 */
sealed interface MarkdownBlock {

    data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock

    data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock

    data class BulletItem(val spans: List<MarkdownSpan>, val ordinal: String?) : MarkdownBlock

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

                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceAtMost(MAX_HEADING)
                    val text = line.drop(level).trim()
                    if (text.isNotEmpty()) blocks += MarkdownBlock.Heading(level, parseSpans(text))
                    index++
                }

                BULLET.matches(line) -> {
                    val text = line.trimStart().drop(2).trim()
                    blocks += MarkdownBlock.BulletItem(parseSpans(text), ordinal = null)
                    index++
                }

                ORDERED.matches(line) -> {
                    val marker = line.trimStart().substringBefore(' ')
                    val text = line.trimStart().substringAfter(' ').trim()
                    blocks += MarkdownBlock.BulletItem(parseSpans(text), ordinal = marker)
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
        var remaining = text

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
    private fun continuesParagraph(line: String): Boolean =
        line.isNotBlank() &&
            !line.startsWith("#") &&
            !line.trimStart().startsWith(FENCE) &&
            !BULLET.matches(line) &&
            !ORDERED.matches(line)

    private const val FENCE = "```"
    private const val MAX_HEADING = 6

    private val BULLET = Regex("""^\s*[-*+]\s+.*""")
    private val ORDERED = Regex("""^\s*\d+[.)]\s+.*""")

    /** Backticks first so emphasis markers inside code are not treated as markup. */
    private val INLINE = Regex("""`[^`]+`|\*\*[^*]+\*\*|__[^_]+__|\*[^*]+\*|_[^_]+_""")
}
