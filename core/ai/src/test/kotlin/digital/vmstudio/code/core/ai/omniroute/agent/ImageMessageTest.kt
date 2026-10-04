package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.model.ImageAttachment
import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMessageTest {

    private val history = listOf(
        OmniRouteMessage.User("What is in this screenshot?", listOf(ImageAttachment("image/png", "QUJD"))),
    )

    private fun body(dialect: OmniRouteDialect) = OmniRouteToolCodec.buildRequestBody(
        dialect = dialect,
        model = "m",
        systemPrompt = "sys",
        history = history,
        maxTokens = 10,
        tools = emptyList(),
    )

    @Test
    fun `openai carries the image as a data url`() {
        assertTrue(body(OmniRouteDialect.OPENAI_CHAT).contains("data:image/png;base64,QUJD"))
    }

    @Test
    fun `anthropic carries the image as a base64 source block`() {
        val json = body(OmniRouteDialect.ANTHROPIC_MESSAGES)
        assertTrue(json.contains("\"media_type\":\"image/png\""))
        assertTrue(json.contains("\"data\":\"QUJD\""))
    }
}
