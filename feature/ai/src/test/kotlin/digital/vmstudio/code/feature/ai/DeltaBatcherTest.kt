package digital.vmstudio.code.feature.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeltaBatcherTest {

    @Test
    fun `add batches multiple chunks within interval`() = runTest {
        val published = mutableListOf<String>()
        val batcher = DeltaBatcher(scope = this, intervalMillis = 50L) { published.add(it) }

        batcher.add("Hello")
        batcher.add(" ")
        batcher.add("world")

        // Before interval completes, nothing published
        assertTrue(published.isEmpty())

        advanceTimeBy(51L)

        assertEquals(listOf("Hello world"), published)
    }

    @Test
    fun `flush publishes immediately without waiting for delay`() = runTest {
        val published = mutableListOf<String>()
        val batcher = DeltaBatcher(scope = this, intervalMillis = 50L) { published.add(it) }

        batcher.add("Quick")
        batcher.flush()

        assertEquals(listOf("Quick"), published)

        // Advancing time should not fire another flush
        advanceTimeBy(100L)
        assertEquals(listOf("Quick"), published)
    }

    @Test
    fun `clear drops pending text and cancels flush`() = runTest {
        val published = mutableListOf<String>()
        val batcher = DeltaBatcher(scope = this, intervalMillis = 50L) { published.add(it) }

        batcher.add("Abandoned text")
        batcher.clear()

        advanceTimeBy(100L)
        assertTrue(published.isEmpty())
    }
}
