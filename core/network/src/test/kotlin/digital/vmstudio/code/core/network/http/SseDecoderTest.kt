package digital.vmstudio.code.core.network.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseDecoderTest {

    private fun decodeAll(vararg lines: String): List<SseEvent> {
        val decoder = SseDecoder()
        val events = lines.mapNotNull { decoder.decode(it) }.toMutableList()
        decoder.finish()?.let { events += it }
        return events
    }

    @Test
    fun `a single event is dispatched on the blank line`() {
        val events = decodeAll("data: {\"a\":1}", "")

        assertEquals(1, events.size)
        assertEquals("{\"a\":1}", events.first().data)
    }

    @Test
    fun `the space after the colon is framing and not part of the value`() {
        val events = decodeAll("data: hello", "")

        assertEquals("hello", events.first().data)
    }

    @Test
    fun `a value with no leading space is preserved intact`() {
        val events = decodeAll("data:hello", "")

        assertEquals("hello", events.first().data)
    }

    @Test
    fun `multi-line data is joined with newlines`() {
        val events = decodeAll("data: line one", "data: line two", "")

        assertEquals("line one\nline two", events.first().data)
    }

    @Test
    fun `the event name is captured`() {
        val events = decodeAll("event: content_block_delta", "data: {}", "")

        assertEquals("content_block_delta", events.first().name)
    }

    @Test
    fun `comment and heartbeat lines produce nothing`() {
        assertNull(SseDecoder().decode(": keep-alive"))
        assertTrue(decodeAll(": ping", "").isEmpty())
    }

    @Test
    fun `the done sentinel is flagged rather than delivered as data`() {
        val events = decodeAll("data: [DONE]", "")

        assertEquals(1, events.size)
        assertTrue(events.first().isDone)
    }

    @Test
    fun `several events in one stream are separated correctly`() {
        val events = decodeAll(
            "data: first",
            "",
            "data: second",
            "",
            "data: third",
            "",
        )

        assertEquals(listOf("first", "second", "third"), events.map { it.data })
    }

    @Test
    fun `a trailing event without a final blank line is still flushed`() {
        // Servers do not reliably terminate the last event, and dropping it would
        // lose the final chunk of a response.
        val events = decodeAll("data: last chunk")

        assertEquals(1, events.size)
        assertEquals("last chunk", events.first().data)
    }

    @Test
    fun `unknown fields are ignored without breaking the event`() {
        val events = decodeAll("id: 42", "retry: 1000", "data: payload", "")

        assertEquals(1, events.size)
        assertEquals("payload", events.first().data)
    }

    @Test
    fun `a blank line with no pending data dispatches nothing`() {
        assertTrue(decodeAll("", "", "").isEmpty())
    }

    @Test
    fun `json containing a colon is not truncated at it`() {
        val payload = """{"type":"message_delta","usage":{"output_tokens":12}}"""
        val events = decodeAll("data: $payload", "")

        assertEquals(payload, events.first().data)
    }

    @Test
    fun `state resets between events so a name does not leak forward`() {
        val events = decodeAll(
            "event: first_kind",
            "data: one",
            "",
            "data: two",
            "",
        )

        assertEquals("first_kind", events[0].name)
        assertNull("the second event has no name of its own", events[1].name)
        assertNotNull(events[1].data)
    }
}
