package digital.vmstudio.code.core.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the unified diff parser handles standard diff output correctly.
 */
class DiffParserTest {

    @Test
    fun `parse empty output returns empty diff`() {
        val result = DiffParser.parse("")
        assertFalse(result.hasChanges)
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun `parse single file addition`() {
        val diff = """
            --- a/old.kt
            +++ b/new.kt
            @@ -1,3 +1,4 @@
             line one
            +inserted line
             line two
             line three
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertTrue(result.hasChanges)
        assertEquals(1, result.files.size)

        val file = result.files[0]
        assertEquals("old.kt", file.oldPath)
        assertEquals("new.kt", file.newPath)
        assertEquals(1, file.additions)
        assertEquals(0, file.deletions)
    }

    @Test
    fun `parse single file deletion`() {
        val diff = """
            --- a/file.kt
            +++ b/file.kt
            @@ -1,3 +1,2 @@
             line one
            -removed line
             line two
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        assertEquals(0, result.files[0].additions)
        assertEquals(1, result.files[0].deletions)
    }

    @Test
    fun `parse multiple files`() {
        val diff = """
            --- a/first.kt
            +++ b/first.kt
            @@ -1,2 +1,2 @@
            -old line
            +new line

            --- a/second.kt
            +++ b/second.kt
            @@ -1,1 +1,2 @@
             existing
            +added
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(2, result.files.size)
        assertEquals("first.kt", result.files[0].displayPath)
        assertEquals("second.kt", result.files[1].displayPath)
    }

    @Test
    fun `parse diff with line numbers`() {
        val diff = """
            --- a/test.kt
            +++ b/test.kt
            @@ -5,3 +5,4 @@
             context
            +added
             more context
        """.trimIndent()

        val result = DiffParser.parse(diff)
        val lines = result.files[0].lines

        // Find the added line
        val addedLine = lines.first { it.type == DiffLineType.ADDED }
        assertEquals(6, addedLine.newLineNumber)
    }

    @Test
    fun `parse diff with multiple hunks`() {
        val diff = """
            --- a/file.kt
            +++ b/file.kt
            @@ -1,3 +1,3 @@
            -first
            +first modified
             second
             third
            @@ -10,2 +10,3 @@
             tenth
            +inserted at ten
             eleventh
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(1, result.files.size)
        assertEquals(2, result.files[0].additions)
        assertEquals(1, result.files[0].deletions)
    }

    @Test
    fun `parse diff with file paths containing directories`() {
        val diff = """
            --- a/src/main/kotlin/Main.kt
            +++ b/src/main/kotlin/Main.kt
            @@ -1,1 +1,1 @@
            -old
            +new
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals("Main.kt", result.files[0].displayPath)
    }

    @Test
    fun `calculate totals across files`() {
        val diff = """
            --- a/a.kt
            +++ b/a.kt
            @@ -1,1 +1,2 @@
            +added
             existing

            --- a/b.kt
            +++ b/b.kt
            @@ -1,2 +1,1 @@
            -removed
             kept
        """.trimIndent()

        val result = DiffParser.parse(diff)
        assertEquals(2, result.files.size)
        // a.kt: one `+added`; b.kt: one `-removed`.
        assertEquals(1, result.totalAdditions)
        assertEquals(1, result.totalDeletions)
    }
}
