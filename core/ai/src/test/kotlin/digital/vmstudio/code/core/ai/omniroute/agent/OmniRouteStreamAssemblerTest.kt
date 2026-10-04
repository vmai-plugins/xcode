package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniRouteStreamAssemblerTest {

    @Test
    fun `openai text deltas stream and assemble`() {
        val a = OmniRouteStreamAssembler(OmniRouteDialect.OPENAI_CHAT)
        assertEquals("Hel", a.accept("""{"choices":[{"delta":{"content":"Hel"}}]}"""))
        assertEquals("lo", a.accept("""{"choices":[{"delta":{"content":"lo"}}]}"""))
        a.accept("""{"choices":[],"usage":{"prompt_tokens":5,"completion_tokens":2}}""")
        assertEquals(OmniRouteTurn.Text("Hello", 5L to 2L), a.finish())
    }

    @Test
    fun `openai tool call arguments are joined across chunks`() {
        val a = OmniRouteStreamAssembler(OmniRouteDialect.OPENAI_CHAT)
        a.accept(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1",""" +
                """"function":{"name":"read_file","arguments":"{\"pa"}}]}}]}""",
        )
        a.accept(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,""" +
                """"function":{"arguments":"th\":\"a\"}"}}]}}]}""",
        )
        val turn = a.finish() as OmniRouteTurn.ToolCalls
        assertEquals(listOf(OmniRouteToolCall("c1", "read_file", """{"path":"a"}""")), turn.calls)
    }

    @Test
    fun `anthropic text and tool use assemble`() {
        val a = OmniRouteStreamAssembler(OmniRouteDialect.ANTHROPIC_MESSAGES)
        a.accept("""{"type":"message_start","message":{"usage":{"input_tokens":9}}}""")
        a.accept("""{"type":"content_block_start","index":0,"content_block":{"type":"text"}}""")
        val textDelta = """{"type":"content_block_delta","index":0,""" +
            """"delta":{"type":"text_delta","text":"Checking"}}"""
        assertEquals("Checking", a.accept(textDelta))
        a.accept(
            """{"type":"content_block_start","index":1,""" +
                """"content_block":{"type":"tool_use","id":"t1","name":"list_directory"}}""",
        )
        a.accept(
            """{"type":"content_block_delta","index":1,""" +
                """"delta":{"type":"input_json_delta","partial_json":"{\"path\":\".\"}"}}""",
        )
        a.accept("""{"type":"message_delta","usage":{"output_tokens":4}}""")
        val turn = a.finish() as OmniRouteTurn.ToolCalls
        assertEquals("Checking", a.textSoFar)
        assertEquals(OmniRouteToolCall("t1", "list_directory", """{"path":"."}"""), turn.calls.single())
        assertEquals(9L to 4L, turn.usage)
    }

    @Test
    fun `an empty stream is reported as a parse failure`() {
        val turn = OmniRouteStreamAssembler(OmniRouteDialect.OPENAI_CHAT).finish()
        assertTrue(turn is OmniRouteTurn.ParseFailed)
    }

    @Test
    fun `streaming body switches stream on and asks for usage`() {
        val body = OmniRouteStreamAssembler.streamingBody(
            OmniRouteDialect.OPENAI_CHAT,
            """{"model":"m","stream":false}""",
        )
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("include_usage"))
    }
}
