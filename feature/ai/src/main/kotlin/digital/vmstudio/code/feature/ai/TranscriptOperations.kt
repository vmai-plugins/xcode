package digital.vmstudio.code.feature.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Transcript list operations.
 *
 * Extracted from the ViewModel because they are list transformations, not screen
 * logic: they take a transcript and return a new one, with no reference to state,
 * coroutines or the provider. Keeping them here also keeps the ViewModel focused on
 * the run lifecycle it actually coordinates.
 */

/**
 * Grows the in-progress reply so it appears to type.
 *
 * The streamed text is held as a single item that is replaced, rather than one item
 * per token: a list that gained an entry per character would thrash the lazy column
 * and jump the scroll on every frame.
 */
internal fun List<TranscriptItem>.appendDelta(
    text: String,
    streamingId: String,
    nextId: () -> String,
): List<TranscriptItem> {
    val existing = lastOrNull() as? TranscriptItem.StreamingText
    if (existing != null) {
        return toMutableList().also { updated ->
            updated[updated.lastIndex] = existing.copy(text = existing.text + text)
        }
    }
    // A draft that is no longer last was never closed: the model streamed only
    // whitespace before a tool call, or a failed stream was retried without one.
    // Two drafts would share a list key and crash the transcript, so the old one
    // is settled as a finished reply (or dropped when blank) under its own id.
    val settled = mapNotNull { item ->
        if (item is TranscriptItem.StreamingText) {
            item.text.takeIf { it.isNotBlank() }?.let { TranscriptItem.AssistantText(nextId(), it) }
        } else {
            item
        }
    }
    return settled + TranscriptItem.StreamingText(streamingId, text)
}

/**
 * Removes the streaming draft once the finished message arrives.
 *
 * The complete message is authoritative, so a dropped or duplicated delta cannot
 * corrupt what the user ends up reading.
 */
internal fun List<TranscriptItem>.dropStreamingDraft(): List<TranscriptItem> =
    filterNot { it is TranscriptItem.StreamingText }

/**
 * Marks a tool call finished in place, so it appears once transitioning from running
 * to done rather than twice.
 *
 * Falls back to the last running call when the provider reports a result without an
 * id, which some CLI versions do for the final tool in a run.
 */
internal fun List<TranscriptItem>.finishToolCall(
    toolItemId: String?,
    isError: Boolean,
    output: String?,
): List<TranscriptItem> {
    // An id that names something other than the running ToolCall (a stale id, or a
    // collision if the id scheme ever changes) falls back to the same "last running
    // call" search used when the provider gives no id at all, rather than silently
    // dropping the finish event and leaving that call stuck showing "running" forever.
    val index = toolItemId
        ?.let { id -> indexOfLast { it.id == id && it is TranscriptItem.ToolCall } }
        ?.takeIf { it >= 0 }
        ?: indexOfLast { it is TranscriptItem.ToolCall && it.isRunning }
    if (index < 0) return this

    val target = this[index] as TranscriptItem.ToolCall
    return toMutableList().also { updated ->
        updated[index] = target.copy(
            isRunning = false,
            isError = isError,
            output = output?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Collects streamed text and hands it on at most every [intervalMillis].
 *
 * Models stream dozens of small chunks a second; publishing each one rebuilt the
 * transcript, re-ran the whole chat screen and re-laid out the full reply per
 * chunk, which stuttered on long replies. Batched, the reply still types smoothly.
 * Single-threaded by design: everything runs on the ViewModel's main scope.
 */
internal class DeltaBatcher(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = DELTA_BATCH_MILLIS,
    private val publish: (String) -> Unit,
) {
    private val pending = StringBuilder()
    private var flushJob: Job? = null

    fun add(text: String) {
        pending.append(text)
        if (flushJob == null) {
            flushJob = scope.launch {
                delay(intervalMillis)
                flushJob = null
                flush()
            }
        }
    }

    /** Publishes what is waiting now; called before any other event, to keep order. */
    fun flush() {
        flushJob?.cancel()
        flushJob = null
        if (pending.isEmpty()) return
        val text = pending.toString()
        pending.setLength(0)
        publish(text)
    }

    /** Drops what is waiting, for a run that was abandoned. */
    fun clear() {
        flushJob?.cancel()
        flushJob = null
        pending.setLength(0)
    }
}

private const val DELTA_BATCH_MILLIS = 50L
