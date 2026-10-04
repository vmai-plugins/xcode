package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.model.ConversationTurn
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationHistoryTest {

    @Test
    fun `turns alternate and same-side turns are merged`() {
        val messages = listOf(
            ConversationTurn(fromUser = true, text = "fix the bug"),
            ConversationTurn(fromUser = true, text = "the one in login"),
            ConversationTurn(fromUser = false, text = "Done."),
            ConversationTurn(fromUser = false, text = " "),
            ConversationTurn(fromUser = true, text = "thanks"),
        ).toMessages()

        assertEquals(
            listOf(
                OmniRouteMessage.User("fix the bug\n\nthe one in login"),
                OmniRouteMessage.Assistant(text = "Done.", toolCalls = emptyList()),
                OmniRouteMessage.User("thanks"),
            ),
            messages,
        )
    }
}
