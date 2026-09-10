package digital.vmstudio.code.core.ai.claudecode

import digital.vmstudio.code.core.ai.model.AgentEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeCodeStreamParserTest {

    private val parser = ClaudeCodeStreamParser()

    private inline fun <reified T : AgentEvent> parseOne(line: String): T {
        val events = parser.parseLine(line)
        assertEquals("expected exactly one event from: $line", 1, events.size)
        return events.first() as T
    }

    // --- session init -------------------------------------------------------------

    @Test
    fun `init envelope yields the session id needed to resume`() {
        val event = parseOne<AgentEvent.SessionStarted>(
            """{"type":"system","subtype":"init","cwd":"/srv/app",""" +
                """"session_id":"2913c2af-d0e7-481c-b7dd-7609c97a8757",""" +
                """"model":"claude-sonnet-4-5","tools":["Bash","Edit","Read"]}""",
        )

        assertEquals("2913c2af-d0e7-481c-b7dd-7609c97a8757", event.sessionId)
        assertEquals("/srv/app", event.workingDirectory)
        assertEquals("claude-sonnet-4-5", event.model)
        assertEquals(listOf("Bash", "Edit", "Read"), event.availableTools)
    }

    @Test
    fun `a system envelope that is not init produces nothing`() {
        assertTrue(
            parser.parseLine("""{"type":"system","subtype":"other","session_id":"x"}""").isEmpty(),
        )
    }

    // --- assistant content ---------------------------------------------------------

    @Test
    fun `assistant text is extracted`() {
        val event = parseOne<AgentEvent.AssistantMessage>(
            """{"type":"assistant","message":{"role":"assistant","content":""" +
                """[{"type":"text","text":"I will add rate limiting."}]}}""",
        )

        assertEquals("I will add rate limiting.", event.text)
    }

    @Test
    fun `thinking blocks are surfaced separately from the answer`() {
        val event = parseOne<AgentEvent.Reasoning>(
            """{"type":"assistant","message":{"content":""" +
                """[{"type":"thinking","thinking":"Check the middleware first."}]}}""",
        )

        assertEquals("Check the middleware first.", event.text)
    }

    @Test
    fun `blank text blocks are ignored`() {
        assertTrue(
            parser.parseLine(
                """{"type":"assistant","message":{"content":[{"type":"text","text":"  "}]}}""",
            ).isEmpty(),
        )
    }

    @Test
    fun `a message with several blocks yields an event per block`() {
        val events = parser.parseLine(
            """{"type":"assistant","message":{"content":[""" +
                """{"type":"text","text":"Editing now."},""" +
                """{"type":"tool_use","id":"tu_1","name":"Edit",""" +
                """"input":{"file_path":"/srv/app/src/Main.kt"}}]}}""",
        )

        assertEquals(2, events.size)
        assertTrue(events[0] is AgentEvent.AssistantMessage)
        assertTrue(events[1] is AgentEvent.ToolStarted)
    }

    // --- tools ---------------------------------------------------------------------

    @Test
    fun `tool use carries a readable summary and the affected path`() {
        val event = parseOne<AgentEvent.ToolStarted>(
            """{"type":"assistant","message":{"content":[{"type":"tool_use",""" +
                """"id":"tu_9","name":"Edit","input":{"file_path":"/srv/app/src/Main.kt"}}]}}""",
        )

        assertEquals("tu_9", event.toolUseId)
        assertEquals("Edit", event.name)
        assertEquals("Edited /srv/app/src/Main.kt", event.summary)
        assertEquals("/srv/app/src/Main.kt", event.affectedPath)
    }

    @Test
    fun `bash tool summarises the command rather than the json`() {
        val event = parseOne<AgentEvent.ToolStarted>(
            """{"type":"assistant","message":{"content":[{"type":"tool_use",""" +
                """"id":"tu_2","name":"Bash","input":{"command":"./gradlew test"}}]}}""",
        )

        assertEquals("Ran: ./gradlew test", event.summary)
        assertEquals(null, event.affectedPath)
    }

    @Test
    fun `an unknown tool still produces a usable summary`() {
        val event = parseOne<AgentEvent.ToolStarted>(
            """{"type":"assistant","message":{"content":[{"type":"tool_use",""" +
                """"id":"tu_3","name":"SomeFutureTool","input":{}}]}}""",
        )

        assertEquals("SomeFutureTool", event.summary)
    }

    @Test
    fun `tool results arrive as a user turn`() {
        val event = parseOne<AgentEvent.ToolFinished>(
            """{"type":"user","message":{"content":[{"type":"tool_result",""" +
                """"tool_use_id":"tu_9","is_error":false,"content":"3 files changed"}]}}""",
        )

        assertEquals("tu_9", event.toolUseId)
        assertFalse(event.isError)
        assertEquals("3 files changed", event.output)
    }

    @Test
    fun `a tool result given as content blocks is flattened`() {
        val event = parseOne<AgentEvent.ToolFinished>(
            """{"type":"user","message":{"content":[{"type":"tool_result",""" +
                """"tool_use_id":"tu_9","content":[{"type":"text","text":"line one"},""" +
                """{"type":"text","text":"line two"}]}]}}""",
        )

        assertEquals("line one\nline two", event.output)
    }

    @Test
    fun `an errored tool result is flagged`() {
        val event = parseOne<AgentEvent.ToolFinished>(
            """{"type":"user","message":{"content":[{"type":"tool_result",""" +
                """"tool_use_id":"tu_4","is_error":true,"content":"permission denied"}]}}""",
        )

        assertTrue(event.isError)
    }

    // --- completion -----------------------------------------------------------------

    @Test
    fun `result envelope carries session id cost and usage`() {
        val event = parseOne<AgentEvent.Completed>(
            """{"type":"result","subtype":"success","is_error":false,"duration_ms":8421,""" +
                """"session_id":"abc-123","total_cost_usd":0.0412,""" +
                """"result":"Added rate limiting.",""" +
                """"usage":{"input_tokens":15230,"output_tokens":842}}""",
        )

        assertEquals("abc-123", event.sessionId)
        assertEquals(8421L, event.durationMillis)
        assertEquals(0.0412, event.costUsd, 0.00001)
        assertEquals(15230L, event.inputTokens)
        assertEquals(842L, event.outputTokens)
        assertEquals("Added rate limiting.", event.resultText)
        assertFalse(event.isError)
    }

    @Test
    fun `a summary envelope without a type is still recognised as completion`() {
        // Observed in a real capture: the closing envelope carried cost and session
        // fields but no "type", and discarding it would lose the resume id.
        val event = parseOne<AgentEvent.Completed>(
            """{"duration_api_ms":0,"stop_reason":"stop_sequence","session_id":"xyz-9",""" +
                """"total_cost_usd":0.5,"usage":{"input_tokens":10,"output_tokens":20}}""",
        )

        assertEquals("xyz-9", event.sessionId)
        assertEquals(0.5, event.costUsd, 0.00001)
    }

    // --- robustness ------------------------------------------------------------------

    @Test
    fun `a non-json diagnostic line is kept rather than dropped`() {
        // Captured verbatim from a real run: the CLI writes this to stdout among the
        // JSON. Dropping it would hide a misconfiguration behind a silent agent.
        val event = parseOne<AgentEvent.Diagnostic>(
            """[claude-code:unrecognized_model] {"model":"auto/best-coding"}""",
        )

        assertTrue(event.line.startsWith("[claude-code:"))
        assertFalse(event.isStderr)
    }

    @Test
    fun `malformed json becomes a diagnostic instead of aborting the run`() {
        val event = parseOne<AgentEvent.Diagnostic>("""{"type":"assistant","message":""")

        assertTrue(event.line.contains("assistant"))
    }

    @Test
    fun `blank lines are ignored`() {
        assertTrue(parser.parseLine("").isEmpty())
        assertTrue(parser.parseLine("   ").isEmpty())
    }

    @Test
    fun `unknown envelope types do not throw`() {
        val events = parser.parseLine("""{"type":"some_future_envelope","payload":{"a":1}}""")

        assertEquals(1, events.size)
        assertTrue(events.first() is AgentEvent.Diagnostic)
    }

    @Test
    fun `unexpected extra fields are tolerated`() {
        val event = parseOne<AgentEvent.SessionStarted>(
            """{"type":"system","subtype":"init","session_id":"s1","cwd":"/a",""" +
                """"tools":[],"a_field_from_a_newer_cli":{"nested":true}}""",
        )

        assertEquals("s1", event.sessionId)
    }

    @Test
    fun `a text delta is surfaced so the reply can appear to type`() {
        val event = parseOne<AgentEvent.AssistantDelta>(
            """{"type":"stream_event","event":{"type":"content_block_delta",""" +
                """"delta":{"type":"text_delta","text":"Hel"}}}""",
        )

        assertEquals("Hel", event.text)
    }

    @Test
    fun `thinking deltas are not streamed`() {
        // Reasoning renders collapsed; streaming it would expand and re-collapse the
        // block on every token.
        assertTrue(
            parser.parseLine(
                """{"type":"stream_event","event":{"type":"content_block_delta",""" +
                    """"delta":{"type":"thinking_delta","thinking":"hmm"}}}""",
            ).isEmpty(),
        )
    }

    @Test
    fun `non-delta stream events are ignored`() {
        assertTrue(
            parser.parseLine("""{"type":"stream_event","event":{"type":"message_start"}}""")
                .isEmpty(),
        )
        assertTrue(parser.parseLine("""{"type":"stream_event"}""").isEmpty())
    }
}
