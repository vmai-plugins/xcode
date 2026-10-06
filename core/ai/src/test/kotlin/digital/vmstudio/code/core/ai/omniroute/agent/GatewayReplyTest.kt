package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gateway replies that are not plain successes: errors, and output cut off at the limit. */
class GatewayReplyTest {

    @Test
    fun `an error object in an OpenAI stream is a gateway error, not an empty reply`() {
        val assembler = OmniRouteStreamAssembler(OmniRouteDialect.OPENAI_CHAT)
        assembler.accept("""{"error":{"message":"Upstream overloaded","type":"overloaded_error"}}""")
        val turn = assembler.finish() as OmniRouteTurn.GatewayError
        assertEquals("Upstream overloaded", turn.message)
        assertTrue(turn.retryable)
    }

    @Test
    fun `an Anthropic error event is a gateway error`() {
        val assembler = OmniRouteStreamAssembler(OmniRouteDialect.ANTHROPIC_MESSAGES)
        assembler.accept("""{"type":"error","error":{"type":"invalid_request_error","message":"bad"}}""")
        assertTrue(assembler.finish() is OmniRouteTurn.GatewayError)
    }

    @Test
    fun `a stream stopped at the length limit marks its tool calls truncated`() {
        val assembler = OmniRouteStreamAssembler(OmniRouteDialect.OPENAI_CHAT)
        assembler.accept(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1",""" +
                """"function":{"name":"write_file","arguments":"{\"path\":\"a"}}]}}]}""",
        )
        assembler.accept("""{"choices":[{"delta":{},"finish_reason":"length"}]}""")
        val turn = assembler.finish() as OmniRouteTurn.ToolCalls
        assertTrue(turn.truncated)
    }

    @Test
    fun `a truncated round runs nothing and tells the model why`() {
        val round = truncatedRound("", listOf(OmniRouteToolCall("c1", "write_file", "{\"path\":\"a")))
        val call = (round.first() as OmniRouteMessage.Assistant).toolCalls.single()
        assertEquals("{}", call.argumentsJson)
        assertTrue((round[1] as OmniRouteMessage.ToolResult).isError)
    }

    @Test
    fun `a null error field is not an error`() {
        val turn = OmniRouteToolCodec.parseResponse(
            OmniRouteDialect.OPENAI_CHAT,
            """{"error":null,"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}]}""",
        )
        assertEquals("hi", (turn as OmniRouteTurn.Text).text)
    }
}
