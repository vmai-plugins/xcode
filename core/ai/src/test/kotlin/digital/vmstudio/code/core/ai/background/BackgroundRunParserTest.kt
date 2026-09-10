package digital.vmstudio.code.core.ai.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are real `claude agents --json` output captured from Claude Code 2.1.261,
 * not invented shapes — including the blocked-run case, which is what an expired
 * login on the server actually looks like from the app's side.
 */
class BackgroundRunParserTest {

    private val realOutput = """
        [
          {
            "pid": 2950165,
            "cwd": "/home/tripcosmos.co/public_html/wp-content",
            "kind": "interactive",
            "startedAt": 1788665425213,
            "sessionId": "fd12796a-8ee3-4278-8346-c41cfcdb11bf",
            "name": "wp-content-65"
          },
          {
            "pid": 3383524,
            "id": "3f8b10c3",
            "cwd": "/tmp",
            "kind": "background",
            "startedAt": 1788796566408,
            "sessionId": "3f8b10c3-7287-48d2-a09f-d89c8a01a86e",
            "name": "Reply with exactly the word PONG and nothing else.",
            "status": "idle",
            "state": "blocked"
          }
        ]
    """.trimIndent()

    @Test
    fun `parses every session in a real listing`() {
        assertEquals(2, BackgroundRunParser.parse(realOutput).size)
    }

    @Test
    fun `a background run carries the ids the lifecycle commands need`() {
        val run = BackgroundRunParser.parse(realOutput).single { it.kind == BackgroundRunKind.BACKGROUND }

        // The short id addresses logs/stop/rm; the UUID is what --resume takes.
        assertEquals("3f8b10c3", run.id)
        assertEquals("3f8b10c3-7287-48d2-a09f-d89c8a01a86e", run.sessionId)
        assertEquals("/tmp", run.cwd)
        assertEquals(1788796566408L, run.startedAtMillis)
        assertEquals(3383524L, run.pid)
        assertTrue(run.isBackground)
    }

    @Test
    fun `an interactive session is not treated as controllable`() {
        val run = BackgroundRunParser.parse(realOutput).single { it.kind == BackgroundRunKind.INTERACTIVE }

        // No short id, so stop/rm/logs cannot address it — the app must not offer to.
        assertNull(run.id)
        assertFalse(run.isBackground)
    }

    @Test
    fun `a blocked run is reported as blocked rather than as running`() {
        // This is the expired-login case observed on a real server: the run sits
        // waiting forever, and without this it is indistinguishable from slow work.
        val run = BackgroundRunParser.parse(realOutput).single { it.id == "3f8b10c3" }

        assertTrue(run.isBlocked)
        assertFalse(run.isRunning)
    }

    @Test
    fun `the array is found even when the CLI prints noise around it`() {
        val noisy = """
            Starting background service…
            $realOutput
            ✔ Update installed · Restart to apply
        """.trimIndent()

        assertEquals(2, BackgroundRunParser.parse(noisy).size)
    }

    @Test
    fun `an empty listing yields no runs`() {
        assertTrue(BackgroundRunParser.parse("[]").isEmpty())
    }

    @Test
    fun `unparseable output yields no runs rather than throwing`() {
        assertTrue(BackgroundRunParser.parse("claude: command not found").isEmpty())
        assertTrue(BackgroundRunParser.parse("").isEmpty())
        assertTrue(BackgroundRunParser.parse("[{broken").isEmpty())
    }

    @Test
    fun `unknown fields from a newer CLI are ignored`() {
        val future = """[{"id":"abc123","kind":"background","somethingNew":{"a":1}}]"""
        val run = BackgroundRunParser.parse(future).single()

        assertEquals("abc123", run.id)
    }

    @Test
    fun `an unrecognised kind does not become a controllable run`() {
        val run = BackgroundRunParser.parse("""[{"id":"x1","kind":"cloud"}]""").single()

        assertEquals(BackgroundRunKind.UNKNOWN, run.kind)
        assertFalse(run.isBackground)
    }
}
