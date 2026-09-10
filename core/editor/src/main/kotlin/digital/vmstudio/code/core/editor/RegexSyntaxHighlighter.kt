package digital.vmstudio.code.core.editor

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Regex-based syntax highlighter for C-like languages.
 *
 * This is a pragmatic, not a parser: it handles the common cases (keywords,
 * strings, numbers, comments, function calls) well enough for readable code.
 */
class RegexSyntaxHighlighter(
    internal val keywords: Set<String>,
    private val stringDelimiters: List<Char> = listOf('"', '\''),
    private val lineCommentPrefix: String? = "//",
    private val blockCommentStart: String? = "/*",
    private val blockCommentEnd: String? = "*/",
    private val numberPattern: Regex = Regex("""\b\d+(\.\d+)?([eE][+-]?\d+)?\b"""),
    private val functionCallPattern: Regex = Regex("""\b([A-Za-z_]\w*)\s*\("""),
    private val typePattern: Regex = Regex("""\b[A-Z][A-Za-z0-9_]*\b"""),
) : SyntaxHighlighter {

    override fun tokenize(source: String, scheme: ColorScheme): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0

        while (i < source.length) {
            // Line comment
            if (lineCommentPrefix != null && source.startsWith(lineCommentPrefix, i)) {
                val end = source.indexOf('\n', i).let { if (it == -1) source.length else it }
                tokens += SyntaxToken(i, end, SpanStyle(color = scheme.comment))
                i = end
                continue
            }

            // Block comment
            if (blockCommentStart != null && blockCommentEnd != null &&
                source.startsWith(blockCommentStart, i)
            ) {
                val end = source.indexOf(blockCommentEnd, i + blockCommentStart.length)
                val realEnd = if (end == -1) source.length else end + blockCommentEnd.length
                tokens += SyntaxToken(i, realEnd, SpanStyle(color = scheme.comment))
                i = realEnd
                continue
            }

            // String literal
            if (source[i] in stringDelimiters) {
                val (end, ok) = scanString(source, i, source[i])
                if (ok) {
                    tokens += SyntaxToken(i, end, SpanStyle(color = scheme.string))
                    i = end
                    continue
                }
            }

            // Number
            val numberMatch = numberPattern.matchAt(source, i)
            if (numberMatch != null && numberMatch.range.first == i) {
                tokens += SyntaxToken(numberMatch.range.first, numberMatch.range.last + 1, SpanStyle(color = scheme.number))
                i = numberMatch.range.last + 1
                continue
            }

            // Word (keyword, type, or plain)
            if (source[i].isLetter() || source[i] == '_') {
                // Scan forward from i only: indexOfFirst without a start offset
                // searches the whole source, returning an index *before* the
                // current position for every word after the first, which would
                // crash substring(i, wordEnd).
                val wordEnd = (i until source.length)
                    .firstOrNull { !source[it].isLetterOrDigit() && source[it] != '_' }
                    ?: source.length
                val word = source.substring(i, wordEnd)
                val style = when {
                    word in keywords -> SpanStyle(color = scheme.keyword, fontWeight = FontWeight.Bold)
                    typePattern.matches(word) -> SpanStyle(color = scheme.type)
                    else -> null
                }
                if (style != null) {
                    tokens += SyntaxToken(i, wordEnd, style)
                }
                i = wordEnd
                continue
            }

            // Function call
            val funcMatch = functionCallPattern.matchAt(source, i)
            if (funcMatch != null && funcMatch.range.first == i) {
                val nameStart = source.indexOf(funcMatch.groupValues[1], i)
                val nameEnd = nameStart + funcMatch.groupValues[1].length
                tokens += SyntaxToken(nameStart, nameEnd, SpanStyle(color = scheme.function))
                i = nameEnd
                continue
            }

            i++
        }

        return tokens
    }

    private fun scanString(source: String, start: Int, delimiter: Char): Pair<Int, Boolean> {
        var i = start + 1
        while (i < source.length) {
            if (source[i] == '\\') {
                i += 2
                continue
            }
            if (source[i] == delimiter) {
                return (i + 1) to true
            }
            i++
        }
        return source.length to false
    }
}
