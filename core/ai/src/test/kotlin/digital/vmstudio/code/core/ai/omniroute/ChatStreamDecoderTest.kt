package digital.vmstudio.code.core.ai.omniroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway's dialect is unknown until it can be probed live, so both are
 * implemented and both are tested against the chunk shapes each API actually emits.
 */
class ChatStreamDecoderTest {

    // --- Anthropic Messages ------------------------------------------------------

    @Test
    fun `anthropic text delta is extracted`() {
        val delta = ChatStreamDecoder.textDelta(
            OmniRouteDialect.ANTHROPIC_MESSAGES,
            """{"type":"content_block_delta","index":0,""" +
                """"delta":{"type":"text_delta","text":"Hello"}}""",
        )

        assertEquals("Hello", delta)
    }

    @Test
    fun `anthropic non-text events yield no delta`() {
        assertNull(
            ChatStreamDecoder.textDelta(
                OmniRouteDialect.ANTHROPIC_MESSAGES,
                """{"type":"message_start","message":{"id":"msg_1"}}""",
            ),
        )
        assertNull(
            ChatStreamDecoder.textDelta(
                OmniRouteDialect.ANTHROPIC_MESSAGES,
                """{"type":"content_block_stop","index":0}""",
            ),
        )
    }

    // --- OpenAI chat completions --------------------------------------------------

    @Test
    fun `openai streaming delta is extracted`() {
        val delta = ChatStreamDecoder.textDelta(
            OmniRouteDialect.OPENAI_CHAT,
            """{"id":"c1","choices":[{"index":0,"delta":{"content":"Hello"}}]}""",
        )

        assertEquals("Hello", delta)
    }

    @Test
    fun `openai non-streamed message shape is also handled`() {
        val delta = ChatStreamDecoder.textDelta(
            OmniRouteDialect.OPENAI_CHAT,
            """{"choices":[{"message":{"role":"assistant","content":"Full reply"}}]}""",
        )

        assertEquals("Full reply", delta)
    }

    @Test
    fun `an openai chunk with no content yields nothing`() {
        assertNull(
            ChatStreamDecoder.textDelta(
                OmniRouteDialect.OPENAI_CHAT,
                """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            ),
        )
    }

    // --- unknown dialect falls back to trying both --------------------------------

    @Test
    fun `an unknown dialect still decodes either shape`() {
        assertEquals(
            "A",
            ChatStreamDecoder.textDelta(
                OmniRouteDialect.UNKNOWN,
                """{"choices":[{"delta":{"content":"A"}}]}""",
            ),
        )
        assertEquals(
            "B",
            ChatStreamDecoder.textDelta(
                OmniRouteDialect.UNKNOWN,
                """{"delta":{"type":"text_delta","text":"B"}}""",
            ),
        )
    }

    // --- robustness ----------------------------------------------------------------

    @Test
    fun `malformed json yields no delta rather than throwing`() {
        assertNull(ChatStreamDecoder.textDelta(OmniRouteDialect.OPENAI_CHAT, "{not json"))
        assertNull(ChatStreamDecoder.textDelta(OmniRouteDialect.ANTHROPIC_MESSAGES, ""))
    }

    @Test
    fun `an empty choices array does not throw`() {
        assertNull(
            ChatStreamDecoder.textDelta(OmniRouteDialect.OPENAI_CHAT, """{"choices":[]}"""),
        )
    }

    // --- usage ----------------------------------------------------------------------

    @Test
    fun `anthropic usage naming is understood`() {
        val usage = ChatStreamDecoder.usage(
            """{"type":"message_delta","usage":{"input_tokens":120,"output_tokens":45}}""",
        )

        assertEquals(120L to 45L, usage)
    }

    @Test
    fun `openai usage naming is understood`() {
        val usage = ChatStreamDecoder.usage(
            """{"usage":{"prompt_tokens":300,"completion_tokens":80,"total_tokens":380}}""",
        )

        assertEquals(300L to 80L, usage)
    }

    @Test
    fun `a chunk without usage returns null`() {
        assertNull(ChatStreamDecoder.usage("""{"choices":[{"delta":{"content":"x"}}]}"""))
    }

    // --- model listing ---------------------------------------------------------------

    @Test
    fun `models are parsed from the shared data array shape`() {
        val models = ChatStreamDecoder.parseModels(
            """{"data":[{"id":"claude-sonnet-4-5"},{"id":"gpt-4o"}]}""",
        )

        assertEquals(listOf("claude-sonnet-4-5", "gpt-4o"), models)
    }

    @Test
    fun `a bare array of model objects is accepted`() {
        val models = ChatStreamDecoder.parseModels("""[{"id":"a"},{"id":"b"}]""")

        assertEquals(listOf("a", "b"), models)
    }

    @Test
    fun `a list of plain strings is accepted`() {
        assertEquals(listOf("a", "b"), ChatStreamDecoder.parseModels("""["a","b"]"""))
    }

    @Test
    fun `a models key is accepted as well as data`() {
        assertEquals(
            listOf("m1"),
            ChatStreamDecoder.parseModels("""{"models":[{"name":"m1"}]}"""),
        )
    }

    @Test
    fun `an unparseable body yields an empty list rather than throwing`() {
        assertTrue(ChatStreamDecoder.parseModels("<html>502 Bad Gateway</html>").isEmpty())
        assertTrue(ChatStreamDecoder.parseModels("").isEmpty())
    }

    // --- dialect routing ---------------------------------------------------------------

    @Test
    fun `each dialect targets its own chat path`() {
        assertEquals("v1/messages", OmniRouteDialect.ANTHROPIC_MESSAGES.chatPath)
        assertEquals("v1/chat/completions", OmniRouteDialect.OPENAI_CHAT.chatPath)
    }

    @Test
    fun `a paged model list gives the cursor for the next page`() {
        assertEquals(
            "m20",
            ChatStreamDecoder.nextModelsCursor("""{"data":[{"id":"m20"}],"has_more":true,"last_id":"m20"}"""),
        )
        assertEquals(null, ChatStreamDecoder.nextModelsCursor("""{"data":[{"id":"a"}],"has_more":false}"""))
        assertEquals(null, ChatStreamDecoder.nextModelsCursor("""{"data":[{"id":"a"}]}"""))
    }

    @Test
    fun `models that cannot chat are left out of the list`() {
        val models = ChatStreamDecoder.parseModels(
            """{"data":[{"id":"gpt-4o"},{"id":"text-embedding-3-small"},{"id":"whisper-1"},""" +
                """{"id":"gpt-4o-mini-tts"},{"id":"deepseek-chat"},{"id":"x","type":"embedding"}]}""",
        )
        assertEquals(listOf("gpt-4o", "deepseek-chat"), models)
    }
}
