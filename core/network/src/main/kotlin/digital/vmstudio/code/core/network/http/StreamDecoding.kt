package digital.vmstudio.code.core.network.http

/**
 * Incremental decoder for Server-Sent Events.
 *
 * Written as a fed-a-line-at-a-time state machine rather than a whole-body parser
 * because a streaming response arrives in arbitrary chunks and must be surfaced as
 * it lands; a decoder that needed the complete body would defeat the point of
 * streaming.
 *
 * Handles the parts of the SSE grammar that language APIs actually use: `data:`
 * accumulation across multiple lines, `event:` naming, blank-line dispatch, comment
 * lines, and the `[DONE]` sentinel both major dialects append.
 */
class SseDecoder {

    private val data = StringBuilder()
    private var eventName: String? = null

    /**
     * Feeds one line. Returns a completed event when the line closed one, otherwise
     * null.
     */
    fun decode(line: String): SseEvent? {
        // A comment or heartbeat. Servers send these to keep proxies from closing an
        // idle connection, and they carry no payload.
        if (line.startsWith(":")) return null

        if (line.isBlank()) return dispatch()

        val separator = line.indexOf(':')
        val field = if (separator < 0) line else line.substring(0, separator)
        val value = when {
            separator < 0 -> ""
            // A single leading space after the colon is part of the framing, not
            // the value.
            line.length > separator + 1 && line[separator + 1] == ' ' ->
                line.substring(separator + 2)
            else -> line.substring(separator + 1)
        }

        when (field) {
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
            "event" -> eventName = value
            // id and retry are accepted and ignored: nothing here reconnects with a
            // Last-Event-ID, so honouring them would be pretence.
            else -> Unit
        }
        return null
    }

    /** Flushes a trailing event when the stream ends without a final blank line. */
    fun finish(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        if (data.isEmpty()) {
            eventName = null
            return null
        }
        val payload = data.toString()
        val name = eventName
        data.setLength(0)
        eventName = null
        return SseEvent(name = name, data = payload, isDone = payload.trim() == DONE_SENTINEL)
    }

    private companion object {
        const val DONE_SENTINEL = "[DONE]"
    }
}

data class SseEvent(
    val name: String?,
    val data: String,
    /** True for the terminator both OpenAI- and Anthropic-style APIs send. */
    val isDone: Boolean,
)
