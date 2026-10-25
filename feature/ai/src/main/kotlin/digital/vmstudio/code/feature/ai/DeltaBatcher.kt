package digital.vmstudio.code.feature.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Batches high-frequency streaming text deltas to prevent Compose UI thrashing
 * and frame drops during fast LLM token generation.
 */
class DeltaBatcher(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = 50L,
    private val onPublish: (String) -> Unit,
) {
    private val sb = StringBuilder()
    private var flushJob: Job? = null

    fun add(text: String) {
        synchronized(this) {
            sb.append(text)
            if (flushJob == null || flushJob?.isActive != true) {
                flushJob = scope.launch {
                    delay(intervalMillis)
                    flush()
                }
            }
        }
    }

    fun flush() {
        val textToPublish = synchronized(this) {
            flushJob?.cancel()
            flushJob = null
            val text = sb.toString()
            sb.setLength(0)
            text
        }
        if (textToPublish.isNotEmpty()) {
            onPublish(textToPublish)
        }
    }

    fun clear() {
        synchronized(this) {
            flushJob?.cancel()
            flushJob = null
            sb.setLength(0)
        }
    }
}
