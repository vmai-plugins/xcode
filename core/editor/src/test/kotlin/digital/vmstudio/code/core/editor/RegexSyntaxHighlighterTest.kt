package digital.vmstudio.code.core.editor

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexSyntaxHighlighterTest {

    private val scheme = ColorScheme(
        keyword = Color(0xFFCC7832),
        string = Color(0xFF6A8759),
        number = Color(0xFF6897BB),
        comment = Color(0xFF808080),
        type = Color(0xFFA9B7C6),
        function = Color(0xFFFFC66D),
        plain = Color(0xFFA9B7C6),
    )

    @Test
    fun `function calls are highlighted with function color`() {
        val tokens = SyntaxHighlighters.kotlin.tokenize("println(\"hello\")", scheme)
        assertTrue(tokens.isNotEmpty())
        val funcToken = tokens.first { it.start == 0 && it.end == 7 }
        assertEquals(scheme.function, funcToken.style.color)
    }

    @Test
    fun `hex and binary numbers are highlighted as single numbers`() {
        val hexTokens = SyntaxHighlighters.kotlin.tokenize("val x = 0xFF", scheme)
        val hexNumber = hexTokens.first { it.style.color == scheme.number }
        assertEquals(8, hexNumber.start)
        assertEquals(12, hexNumber.end)

        val binTokens = SyntaxHighlighters.kotlin.tokenize("val b = 0b1010", scheme)
        val binNumber = binTokens.first { it.style.color == scheme.number }
        assertEquals(8, binNumber.start)
        assertEquals(14, binNumber.end)
    }

    @Test
    fun `keywords before parentheses are prioritized over function calls`() {
        val tokens = SyntaxHighlighters.kotlin.tokenize("if (x > 0) {}", scheme)
        val ifToken = tokens.first { it.start == 0 && it.end == 2 }
        assertEquals(scheme.keyword, ifToken.style.color)
    }
}
