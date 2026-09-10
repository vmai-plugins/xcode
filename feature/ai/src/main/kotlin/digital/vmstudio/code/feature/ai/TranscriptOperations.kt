package digital.vmstudio.code.feature.ai

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
): List<TranscriptItem> {
    val existing = lastOrNull() as? TranscriptItem.StreamingText
        ?: return this + TranscriptItem.StreamingText(streamingId, text)

    return toMutableList().also { updated ->
        updated[updated.lastIndex] = existing.copy(text = existing.text + text)
    }
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
    val index = if (toolItemId != null) {
        indexOfFirst { it.id == toolItemId }
    } else {
        indexOfLast { it is TranscriptItem.ToolCall && it.isRunning }
    }
    if (index < 0) return this

    val target = this[index] as? TranscriptItem.ToolCall ?: return this
    return toMutableList().also { updated ->
        updated[index] = target.copy(
            isRunning = false,
            isError = isError,
            output = output?.takeIf { it.isNotBlank() },
        )
    }
}
