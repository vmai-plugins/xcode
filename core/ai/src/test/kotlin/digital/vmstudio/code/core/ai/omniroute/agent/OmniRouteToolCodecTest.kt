package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both wire dialects are exercised because a gateway can present as either, and
 * getting the request shape or the response parse wrong for one of them means every
 * tool call silently fails against half the gateways in the wild.
 */
class OmniRouteToolCodecTest {

    // --- request building ----------------------------------------------------------

    @Test
    fun `openai request includes a system message, history and function-shaped tools`() {
        val body = OmniRouteToolCodec.buildRequestBody(
            dialect = OmniRouteDialect.OPENAI_CHAT,
            model = "some-model",
            systemPrompt = "You are helpful.",
            history = listOf(OmniRouteMessage.User("List the files.")),
            maxTokens = 1024,
        )

        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("You are helpful."))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"type\":\"function\""))
        assertTrue(body.contains("\"name\":\"read_file\""))
        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun `anthropic request carries system as a top-level field and tools without a wrapper`() {
        val body = OmniRouteToolCodec.buildRequestBody(
            dialect = OmniRouteDialect.ANTHROPIC_MESSAGES,
            model = "some-model",
            systemPrompt = "You are helpful.",
            history = listOf(OmniRouteMessage.User("List the files.")),
            maxTokens = 1024,
        )

        assertTrue(body.contains("\"system\":\"You are helpful.\""))
        assertTrue(body.contains("\"input_schema\""))
        assertTrue(body.contains("\"name\":\"list_directory\""))
        // No "type":"function" wrapper - that is the OpenAI shape only.
        assertTrue(!body.contains("\"type\":\"function\""))
    }

    @Test
    fun `anthropic request batches consecutive tool results into one user message`() {
        val history = listOf(
            OmniRouteMessage.User("Read both files."),
            OmniRouteMessage.Assistant(
                text = null,
                toolCalls = listOf(
                    OmniRouteToolCall("call_1", "read_file", """{"path":"a.txt"}"""),
                    OmniRouteToolCall("call_2", "read_file", """{"path":"b.txt"}"""),
                ),
            ),
            OmniRouteMessage.ToolResult("call_1", "read_file", "contents of a", isError = false),
            OmniRouteMessage.ToolResult("call_2", "read_file", "contents of b", isError = false),
        )

        val body = OmniRouteToolCodec.buildRequestBody(
            dialect = OmniRouteDialect.ANTHROPIC_MESSAGES,
            model = "some-model",
            systemPrompt = "sys",
            history = history,
            maxTokens = 1024,
        )

        // Both tool_result blocks must land inside the same user message's content
        // array, not as two separate consecutive user messages (which Anthropic's
        // API rejects): one "user" role for the initial prompt, one for the batched
        // results - never three.
        assertEquals(2, Regex("\"role\":\"user\"").findAll(body).count())
        assertEquals(2, Regex("\"tool_result\"").findAll(body).count())
        assertTrue(body.contains("\"tool_use_id\":\"call_1\""))
        assertTrue(body.contains("\"tool_use_id\":\"call_2\""))
    }

    // --- response parsing ------------------------------------------------------------

    @Test
    fun `openai response with no tool calls yields text`() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"All done."}}],
             "usage":{"prompt_tokens":10,"completion_tokens":5}}
        """.trimIndent()

        val turn = OmniRouteToolCodec.parseResponse(OmniRouteDialect.OPENAI_CHAT, body)

        check(turn is OmniRouteTurn.Text)
        assertEquals("All done.", turn.text)
        assertEquals(10L to 5L, turn.usage)
    }

    @Test
    fun `openai response with tool calls yields parsed calls`() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                {"id":"call_1","type":"function","function":{"name":"read_file","arguments":"{\"path\":\"a.txt\"}"}}
            ]}}]}
        """.trimIndent()

        val turn = OmniRouteToolCodec.parseResponse(OmniRouteDialect.OPENAI_CHAT, body)

        check(turn is OmniRouteTurn.ToolCalls)
        assertEquals(1, turn.calls.size)
        assertEquals("read_file", turn.calls.first().name)
        assertEquals("call_1", turn.calls.first().id)
        assertTrue(turn.calls.first().argumentsJson.contains("a.txt"))
    }

    @Test
    fun `anthropic response with a tool_use block yields a parsed call`() {
        val body = """
            {"content":[
                {"type":"text","text":"Let me check."},
                {"type":"tool_use","id":"toolu_1","name":"list_directory","input":{"path":"."}}
            ],
             "usage":{"input_tokens":20,"output_tokens":8}}
        """.trimIndent()

        val turn = OmniRouteToolCodec.parseResponse(OmniRouteDialect.ANTHROPIC_MESSAGES, body)

        check(turn is OmniRouteTurn.ToolCalls)
        assertEquals(1, turn.calls.size)
        assertEquals("list_directory", turn.calls.first().name)
        assertEquals("toolu_1", turn.calls.first().id)
        assertEquals(20L to 8L, turn.usage)
    }

    @Test
    fun `anthropic response with only text blocks yields text`() {
        val body = """{"content":[{"type":"text","text":"Done."}]}"""

        val turn = OmniRouteToolCodec.parseResponse(OmniRouteDialect.ANTHROPIC_MESSAGES, body)

        check(turn is OmniRouteTurn.Text)
        assertEquals("Done.", turn.text)
    }

    @Test
    fun `malformed response yields ParseFailed rather than throwing`() {
        val turn = OmniRouteToolCodec.parseResponse(OmniRouteDialect.OPENAI_CHAT, "not json at all")

        assertTrue(turn is OmniRouteTurn.ParseFailed)
    }

    @Test
    fun `all 12 tools produce valid schemas and appear in wire payloads`() {
        assertEquals(12, OmniRouteTool.entries.size)

        for (tool in OmniRouteTool.entries) {
            val openAiJson = tool.toOpenAiToolJson()
            assertEquals("function", openAiJson["type"]?.toString()?.trim('"'))
            val fn = openAiJson["function"] as kotlinx.serialization.json.JsonObject
            assertEquals(tool.toolName, fn["name"]?.toString()?.trim('"'))

            val anthropicJson = tool.toAnthropicToolJson()
            assertEquals(tool.toolName, anthropicJson["name"]?.toString()?.trim('"'))
            assertTrue(anthropicJson.containsKey("input_schema"))
        }

        val openAiBody = OmniRouteToolCodec.buildRequestBody(
            dialect = OmniRouteDialect.OPENAI_CHAT,
            model = "test-model",
            systemPrompt = "sys",
            history = listOf(OmniRouteMessage.User("hi")),
            maxTokens = 500,
        )
        for (tool in OmniRouteTool.entries) {
            assertTrue("Expected OpenAI payload to include ${tool.toolName}", openAiBody.contains("\"name\":\"${tool.toolName}\""))
        }
    }
}
