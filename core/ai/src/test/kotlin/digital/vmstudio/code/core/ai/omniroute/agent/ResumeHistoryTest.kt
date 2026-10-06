package digital.vmstudio.code.core.ai.omniroute.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A run that stops early must still leave a history the gateway accepts next time. */
class ResumeHistoryTest {

    private val call = OmniRouteToolCall(id = "call_1", name = "run_command", argumentsJson = "{}")

    @Test
    fun `an unanswered tool call gets an error result and the model speaks last`() {
        val history = listOf(
            OmniRouteMessage.User("fix it"),
            OmniRouteMessage.Assistant(text = null, toolCalls = listOf(call)),
        ).closedForResume()

        val result = history[2] as OmniRouteMessage.ToolResult
        assertEquals("call_1", result.toolCallId)
        assertTrue(result.isError)
        assertTrue(history.last() is OmniRouteMessage.Assistant)
    }

    @Test
    fun `a finished history is kept as it is`() {
        val finished = listOf(
            OmniRouteMessage.User("hi"),
            OmniRouteMessage.Assistant(text = "hello", toolCalls = emptyList()),
        )
        assertEquals(finished, finished.closedForResume())
    }
}
