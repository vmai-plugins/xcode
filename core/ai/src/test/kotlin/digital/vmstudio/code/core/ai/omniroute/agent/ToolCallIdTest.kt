package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallIdTest {

    private var counter = 0
    private fun fresh() = "fresh-${counter++}"

    @Test
    fun `ids the gateway repeats across turns are replaced`() {
        val history = listOf(
            OmniRouteMessage.Assistant(null, listOf(OmniRouteToolCall("call_0", "read_file", "{}"))),
        )
        val calls = listOf(OmniRouteToolCall("call_0", "list_directory", "{}"))
            .withUniqueIds(usedToolCallIds(history), ::fresh)
        assertNotEquals("call_0", calls.single().id)
    }

    @Test
    fun `blank ids get one, unique ids are kept`() {
        val calls = listOf(
            OmniRouteToolCall("", "read_file", "{}"),
            OmniRouteToolCall("toolu_abc", "grep_search", "{}"),
            OmniRouteToolCall("", "web_fetch", "{}"),
        ).withUniqueIds(mutableSetOf(), ::fresh)
        assertEquals("toolu_abc", calls[1].id)
        assertTrue(calls.all { it.id.isNotBlank() })
        assertEquals(3, calls.map { it.id }.toSet().size)
    }

    @Test
    fun `a tool call without an id is no longer dropped`() {
        val response = """
            {"choices":[{"message":{"content":null,"tool_calls":[
              {"type":"function","function":{"name":"read_file","arguments":"{}"}}
            ]}}]}
        """.trimIndent()
        val turn = OmniRouteToolCodec.parseResponse(OmniRouteDialect.OPENAI_CHAT, response)
        assertTrue(turn is OmniRouteTurn.ToolCalls)
    }
}
