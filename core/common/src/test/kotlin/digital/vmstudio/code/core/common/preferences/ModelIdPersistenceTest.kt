package digital.vmstudio.code.core.common.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure half of the synced-model persistence: the list is stored
 * newline-packed after sanitising, because a gateway answer is untrusted input
 * and the preferences file must not be injectable or unbounded.
 */
class ModelIdPersistenceTest {

    @Test
    fun `trims whitespace and drops blank ids`() {
        val result = sanitizeModelIds(listOf("  claude-sonnet-4-5  ", "   ", ""))

        assertEquals(listOf("claude-sonnet-4-5"), result)
    }

    @Test
    fun `deduplicates while preserving first-seen order`() {
        val result = sanitizeModelIds(
            listOf("b-model", "a-model", "b-model", "a-model", "c-model"),
        )

        assertEquals(listOf("b-model", "a-model", "c-model"), result)
    }

    @Test
    fun `caps the list at the sync limit`() {
        val many = (1..MAX_SYNCED_MODEL_IDS + 50).map { "model-$it" }

        val result = sanitizeModelIds(many)

        assertEquals(MAX_SYNCED_MODEL_IDS, result.size)
        assertEquals("model-1", result.first())
    }

    @Test
    fun `keeps a large gateway's full list`() {
        val many = (1..1_500).map { "model-$it" }
        assertEquals(1_500, sanitizeModelIds(many).size)
    }

    @Test
    fun `drops ids that are unreasonably long`() {
        val garbage = "x".repeat(MAX_MODEL_ID_LENGTH + 1)
        val result = sanitizeModelIds(listOf(garbage, "good-model"))

        assertEquals(listOf("good-model"), result)
    }

    @Test
    fun `parses a persisted string back into the same list`() {
        val models = listOf("claude-sonnet-4-5", "deepseek-r1", "llama-3")

        val persisted = sanitizeModelIds(models).joinToString(MODEL_ID_SEPARATOR)

        assertEquals(models, parseModelIds(persisted))
    }

    @Test
    fun `parsing null or empty storage yields an empty list`() {
        assertTrue(parseModelIds(null).isEmpty())
        assertTrue(parseModelIds("").isEmpty())
        assertTrue(parseModelIds("  \n  \n").isEmpty())
    }
}
