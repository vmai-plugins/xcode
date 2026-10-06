package digital.vmstudio.code.core.ai.omniroute.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTrimTest {

    private fun call(id: String) = OmniRouteMessage.Assistant(
        text = null,
        toolCalls = listOf(OmniRouteToolCall(id, "read_file", "{}")),
    )

    private fun result(id: String, size: Int) = OmniRouteMessage.ToolResult(
        toolCallId = id,
        toolName = "read_file",
        content = "x".repeat(size),
        isError = false,
    )

    @Test
    fun `history under budget is returned unchanged`() {
        val history = listOf(OmniRouteMessage.User("hi"))
        assertSame(history, trimHistory(history, budget = 100))
    }

    @Test
    fun `old tool results are shortened first, recent ones kept`() {
        val history = listOf(
            OmniRouteMessage.User("task"),
            call("a"),
            result("a", 5_000),
            call("b"),
            result("b", 5_000),
        )
        val trimmed = trimHistory(history, budget = 7_000, keepRecent = 2)
        val first = trimmed[2] as OmniRouteMessage.ToolResult
        val last = trimmed[4] as OmniRouteMessage.ToolResult
        assertTrue(first.content.length < 2_000)
        assertEquals(5_000, last.content.length)
    }

    @Test
    fun `whole turns are dropped from the front and the rest starts at a user message`() {
        val history = listOf(
            OmniRouteMessage.User("old " + "y".repeat(5_000)),
            OmniRouteMessage.Assistant(text = "z".repeat(5_000), toolCalls = emptyList()),
            OmniRouteMessage.User("new question"),
        )
        val trimmed = trimHistory(history, budget = 1_000, keepRecent = 1)
        assertEquals(listOf<OmniRouteMessage>(OmniRouteMessage.User("new question")), trimmed)
    }

    @Test
    fun `tool output is capped with a note`() {
        val capped = capToolOutput("a".repeat(50), limit = 10)
        assertTrue(capped.startsWith("aaaaaaaaaa\n\n[output truncated: 40 more characters]"))
    }

    @Test
    fun `one long task drops its oldest tool rounds and keeps the prompt`() {
        val rounds = (1..20).flatMap { listOf(call("c$it"), result("c$it", 1_000)) }
        val history = listOf(OmniRouteMessage.User("build it")) + rounds

        val trimmed = trimHistory(history, budget = 8_000)

        assertEquals(OmniRouteMessage.User("build it"), trimmed.first())
        assertTrue(trimmed.sumOf { if (it is OmniRouteMessage.ToolResult) it.content.length else 0 } <= 8_000)
        // Every remaining result still follows the call that produced it.
        trimmed.filterIsInstance<OmniRouteMessage.ToolResult>().forEach { r ->
            val callIndex = trimmed.indexOfFirst { m ->
                m is OmniRouteMessage.Assistant && m.toolCalls.any { it.id == r.toolCallId }
            }
            assertTrue(callIndex in 0 until trimmed.indexOf(r))
        }
    }
}
