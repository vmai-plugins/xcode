package digital.vmstudio.code.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * A token in source code with its position and style.
 */
data class SyntaxToken(
    val start: Int,
    val end: Int,
    val style: SpanStyle,
)

/**
 * Transforms source code into tokens for syntax coloring.
 */
interface SyntaxHighlighter {
    fun tokenize(source: String, scheme: ColorScheme): List<SyntaxToken>
}

/**
 * Colors used for syntax highlighting.
 */
data class ColorScheme(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val type: Color,
    val plain: Color,
)
