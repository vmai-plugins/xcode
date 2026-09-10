package digital.vmstudio.code.core.ai.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The short id is only ever reported in a human-facing banner, so reading it back is
 * the one unavoidable piece of screen-scraping in the background flow. These fixtures
 * are verbatim Claude Code 2.1.261 output.
 */
class StartBannerTest {

    /** Mirrors `BackgroundAgentRunner.STARTED_ID`. */
    private val pattern = Regex(
        """backgrounded\b\W*\b([0-9a-f]{6,})\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun idFrom(output: String) = pattern.find(output)?.groupValues?.getOrNull(1)

    @Test
    fun `reads the id from the real start banner`() {
        val banner = """
            Starting background service…
            backgrounded · d11107ef
              claude agents             list sessions
              claude attach d11107ef    open in this terminal
              claude logs d11107ef      show recent output
              claude stop d11107ef      stop this session
        """.trimIndent()

        assertEquals("d11107ef", idFrom(banner))
    }

    @Test
    fun `survives the separator changing`() {
        // The middot is decorative; the id is what matters.
        assertEquals("3f8b10c3", idFrom("backgrounded 3f8b10c3"))
        assertEquals("3f8b10c3", idFrom("backgrounded - 3f8b10c3"))
    }

    @Test
    fun `captures the whole id rather than a suffix of it`() {
        // Regression: a greedy separator backtracked into the id and captured
        // "8b10c3", which stop or rm would have aimed at a different session.
        assertEquals("3f8b10c3", idFrom("backgrounded 3f8b10c3"))
        assertEquals("d11107ef", idFrom("backgrounded · d11107ef"))
        assertEquals("abcdef01", idFrom("backgrounded · abcdef01"))
    }

    @Test
    fun `returns nothing when the start failed rather than inventing an id`() {
        val refusal = "--bg and --print conflict: --print never starts the interactive " +
            "session that `claude agents` attaches to, so the job would be unattachable."

        // A wrong id here would address someone else's session with stop or rm, so a
        // failed start must yield null and surface as an error.
        assertNull(idFrom(refusal))
        assertNull(idFrom("claude: command not found"))
        assertNull(idFrom(""))
    }
}
