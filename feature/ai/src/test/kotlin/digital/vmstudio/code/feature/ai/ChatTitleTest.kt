package digital.vmstudio.code.feature.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTitleTest {

    @Test
    fun `short prompt is the title`() {
        assertEquals("Fix the login bug", chatTitle("  Fix the login bug  "))
    }

    @Test
    fun `only the first non-empty line is used`() {
        assertEquals("Add tests", chatTitle("\n\nAdd tests\nfor the billing module"))
    }

    @Test
    fun `long prompt is cut on a word boundary`() {
        val prompt = "Refactor the payment service so that retries use exponential backoff everywhere"
        val title = chatTitle(prompt)
        assertEquals("Refactor the payment service so that retries…", title)
    }

    @Test
    fun `blank prompt gets a fallback`() {
        assertEquals("New chat", chatTitle("   "))
    }
}
