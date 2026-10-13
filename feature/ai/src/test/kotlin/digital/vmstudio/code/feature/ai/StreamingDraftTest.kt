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

    @Test
    fun `settleStreamingDraft turns partial draft into AssistantText`() {
        val list = listOf<TranscriptItem>(
            TranscriptItem.UserPrompt("p-1", "How are you?"),
            TranscriptItem.StreamingText("draft", "I am doing well"),
        ).settleStreamingDraft(::id)

        assertEquals(
            listOf(
                TranscriptItem.UserPrompt("p-1", "How are you?"),
                TranscriptItem.AssistantText("item-0", "I am doing well"),
            ),
            list,
        )
    }

    @Test
    fun `settleStreamingDraft drops blank draft`() {
        val list = listOf<TranscriptItem>(
            TranscriptItem.UserPrompt("p-1", "Hello"),
            TranscriptItem.StreamingText("draft", "   \n\t  "),
        ).settleStreamingDraft(::id)

        assertEquals(
            listOf(TranscriptItem.UserPrompt("p-1", "Hello")),
            list,
        )
    }

    @Test
    fun `no seeded streaming item on new run after stop settles draft`() {
        val stoppedTranscript = listOf<TranscriptItem>(
            TranscriptItem.UserPrompt("p-1", "Run something"),
            TranscriptItem.StreamingText("draft", "Starting work"),
        ).settleStreamingDraft(::id) + TranscriptItem.Diagnostic("diag-1", "Stopped by you", false)

        // On new run, next prompt is added and new deltas start with clean draft
        val newRunTranscript = (stoppedTranscript + TranscriptItem.UserPrompt("p-2", "Try again"))
            .appendDelta("Fresh output", "draft", ::id)

        assertEquals(1, newRunTranscript.count { it is TranscriptItem.StreamingText })
        val currentDraft = newRunTranscript.filterIsInstance<TranscriptItem.StreamingText>().single()
        assertEquals("Fresh output", currentDraft.text)
    }
}
