package digital.vmstudio.code.core.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The input is Claude's markdown, so these fixtures are shapes the agent actually
 * emits: fenced commands, patches, bulleted plans and inline paths.
 */
class MarkdownParserTest {

    private fun textOf(block: MarkdownBlock): String = when (block) {
        is MarkdownBlock.Paragraph -> block.spans.joinToString("") { it.text }
        is MarkdownBlock.Heading -> block.spans.joinToString("") { it.text }
        is MarkdownBlock.BulletItem -> block.spans.joinToString("") { it.text }
        is MarkdownBlock.CodeBlock -> block.code
    }

    // --- code blocks ---------------------------------------------------------------

    @Test
    fun `a fenced block keeps its code verbatim`() {
        val blocks = MarkdownParser.parse(
            """
            Run this:

            ```bash
            ./gradlew test --info
            ```
            """.trimIndent(),
        )

        val code = blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single()
        assertEquals("./gradlew test --info", code.code)
        assertEquals("bash", code.language)
    }

    @Test
    fun `blank lines inside code are preserved`() {
        // Losing them would corrupt a patch or a multi-command block.
        val blocks = MarkdownParser.parse("```\nfirst\n\nsecond\n```")

        assertEquals("first\n\nsecond", (blocks.single() as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun `an unterminated fence runs to the end rather than being dropped`() {
        // Common mid-run: the stream is cut off before the closing fence arrives.
        val blocks = MarkdownParser.parse("```python\nprint('hi')")

        assertEquals("print('hi')", (blocks.single() as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun `a fence with no language is still a code block`() {
        val block = MarkdownParser.parse("```\nls -la\n```").single()

        assertTrue(block is MarkdownBlock.CodeBlock)
        assertEquals(null, (block as MarkdownBlock.CodeBlock).language)
    }

    // --- inline --------------------------------------------------------------------

    @Test
    fun `inline code is marked and its backticks removed`() {
        val spans = MarkdownParser.parseSpans("Edit `src/Main.kt` now")

        val code = spans.single { it.code }
        assertEquals("src/Main.kt", code.text)
    }

    @Test
    fun `emphasis markers inside inline code stay literal`() {
        // Resolving emphasis first would turn `a ** b` bold and eat the asterisks,
        // silently changing a command the user is meant to copy.
        val spans = MarkdownParser.parseSpans("run `a ** b` please")

        val code = spans.single { it.code }
        assertEquals("a ** b", code.text)
        assertTrue(spans.none { it.bold })
    }

    @Test
    fun `bold and italic are recognised`() {
        val bold = MarkdownParser.parseSpans("this is **important**").single { it.bold }
        assertEquals("important", bold.text)

        val italic = MarkdownParser.parseSpans("this is *subtle*").single { it.italic }
        assertEquals("subtle", italic.text)
    }

    // --- structure -------------------------------------------------------------------

    @Test
    fun `headings carry their level`() {
        val blocks = MarkdownParser.parse("# One\n## Two")

        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals(2, (blocks[1] as MarkdownBlock.Heading).level)
    }

    @Test
    fun `bullets and numbered items are both list items`() {
        val blocks = MarkdownParser.parse("- first\n* second\n1. third")

        assertEquals(3, blocks.size)
        assertEquals(listOf("first", "second", "third"), blocks.map(::textOf))
        assertEquals("1.", (blocks[2] as MarkdownBlock.BulletItem).ordinal)
    }

    @Test
    fun `a soft-wrapped sentence is one paragraph rather than stacked fragments`() {
        val blocks = MarkdownParser.parse("the quick brown\nfox jumps over")

        assertEquals(1, blocks.size)
        assertEquals("the quick brown fox jumps over", textOf(blocks.single()))
    }

    @Test
    fun `a realistic agent reply parses into the expected block types`() {
        val blocks = MarkdownParser.parse(
            """
            I found the issue in `AuthService`.

            ## Changes
            - Added a null check
            - Updated the test

            ```kotlin
            if (token == null) return Unauthorized
            ```
            """.trimIndent(),
        )

        assertEquals(1, blocks.count { it is MarkdownBlock.Heading })
        assertEquals(2, blocks.count { it is MarkdownBlock.BulletItem })
        assertEquals(1, blocks.count { it is MarkdownBlock.CodeBlock })
    }

    @Test
    fun `plain text with no markup survives unchanged`() {
        val blocks = MarkdownParser.parse("just a sentence")

        assertEquals("just a sentence", textOf(blocks.single()))
    }

    @Test
    fun `empty input yields no blocks`() {
        assertTrue(MarkdownParser.parse("").isEmpty())
        assertTrue(MarkdownParser.parse("\n\n").isEmpty())
    }
}
