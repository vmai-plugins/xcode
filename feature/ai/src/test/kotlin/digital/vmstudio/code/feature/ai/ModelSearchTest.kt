package digital.vmstudio.code.feature.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSearchTest {

    private val ids = listOf(
        "auto/best-coding",
        "openai/gpt-5",
        "google/gemini-3.8-pro",
        "google/gemma-3",
        "glm-5.3",
    )

    @Test
    fun `blank query keeps everything in order`() {
        assertEquals(ids, filterModels(ids, "  "))
    }

    @Test
    fun `every word must match, in any order and case`() {
        assertEquals(listOf("google/gemini-3.8-pro"), filterModels(ids, "PRO gemini"))
    }

    @Test
    fun `a name that starts with the word comes first`() {
        assertEquals("glm-5.3", filterModels(listOf("openai/x-glm", "glm-5.3"), "glm").first())
    }

    @Test
    fun `no match gives an empty list`() {
        assertEquals(emptyList<String>(), filterModels(ids, "claude"))
    }

    @Test
    fun `ids with a vendor are grouped, ids without are flat`() {
        assertEquals(listOf("auto", "google", "openai", "other"), groupModels(ids).map { it.first })
        assertEquals(listOf(null), groupModels(listOf("glm-5.3", "kimi")).map { it.first })
    }

    @Test
    fun `chip shows the last part of a gateway id`() {
        assertEquals("best-coding", modelChipLabel("auto/best-coding"))
        assertEquals("glm-5.3", modelChipLabel("glm-5.3"))
    }
}
