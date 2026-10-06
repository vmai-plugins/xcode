package digital.vmstudio.code.feature.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingDraftTest {

    private var counter = 0
    private fun id() = "item-${counter++}"

    @Test
    fun `deltas grow one draft`() {
        val list = emptyList<TranscriptItem>()
            .appendDelta("Hel", "draft", ::id)
            .appendDelta("lo", "draft", ::id)
        assertEquals(listOf(TranscriptItem.StreamingText("draft", "Hello")), list)
    }

    @Test
    fun `an unclosed draft before a tool call never leaves two drafts with one key`() {
        val tool = TranscriptItem.ToolCall(
            id = "tool-1",
            name = "read_file",
            summary = "Read a.txt",
            affectedPath = null,
            isRunning = false,
        )
        val list = emptyList<TranscriptItem>()
            .appendDelta("Looking first.", "draft", ::id)
            .plus(tool)
            .appendDelta("Done.", "draft", ::id)
        assertEquals(1, list.count { it.id == "draft" })
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertEquals(TranscriptItem.AssistantText("item-0", "Looking first."), list[0])
    }

    @Test
    fun `a whitespace-only draft is dropped, not kept as an empty reply`() {
        val list = listOf<TranscriptItem>(TranscriptItem.StreamingText("draft", "\n\n"))
            .plus(TranscriptItem.UserPrompt("p", "next question"))
            .appendDelta("Answer", "draft", ::id)
        assertEquals(listOf("p", "draft"), list.map { it.id })
    }
}
