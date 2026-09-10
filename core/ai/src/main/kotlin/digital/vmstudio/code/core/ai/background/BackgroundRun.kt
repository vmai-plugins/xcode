package digital.vmstudio.code.core.ai.background

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Whether a session is one the user is attached to, or one running detached. */
enum class BackgroundRunKind { INTERACTIVE, BACKGROUND, UNKNOWN }

/**
 * A Claude Code session on the server, as `claude agents --json` reports it.
 *
 * Field names mirror the CLI's output rather than being renamed, so a change in the
 * CLI shows up as a parse gap here rather than as a silently wrong mapping.
 */
data class BackgroundRun(
    /** Short id, e.g. `3f8b10c3`. Only background sessions have one. */
    val id: String?,
    /** Full session UUID, the value `--resume` takes. */
    val sessionId: String?,
    val cwd: String?,
    /** Derived by the CLI from the prompt; used as a title. */
    val name: String?,
    val kind: BackgroundRunKind,
    val status: String?,
    val state: String?,
    val startedAtMillis: Long,
    val pid: Long?,
) {
    /** Only a detached run can be stopped, removed or polled by short id. */
    val isBackground: Boolean get() = kind == BackgroundRunKind.BACKGROUND && id != null

    /**
     * Whether the run is waiting on something it cannot resolve by itself.
     *
     * Observed against a real server: a run whose login had expired sat at
     * `state: "blocked"` indefinitely rather than failing, which from the app's side
     * is indistinguishable from slow work unless it is surfaced.
     */
    val isBlocked: Boolean get() = state.equals("blocked", ignoreCase = true)

    val isRunning: Boolean
        get() = !isBlocked && !status.equals("done", ignoreCase = true) &&
            !state.equals("exited", ignoreCase = true)
}

/**
 * Parses `claude agents --json` output.
 *
 * Lenient for the same reason the stream parser is: the command writes warnings and
 * update notices to the same stream as the JSON, so the array is located rather than
 * assumed to start at byte zero, and an unparseable payload yields no runs instead of
 * throwing.
 */
object BackgroundRunParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(output: String): List<BackgroundRun> {
        val array = extractArray(output) ?: return emptyList()
        return array.mapNotNull { element -> (element as? JsonObject)?.toRun() }
    }

    /**
     * Finds the JSON array within otherwise noisy output.
     *
     * The CLI prepends things like "Starting background service…" and appends update
     * notices; anchoring on the first `[` and last `]` survives both.
     */
    private fun extractArray(output: String): JsonArray? {
        val start = output.indexOf('[')
        val end = output.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val candidate = output.substring(start, end + 1)
        return runCatching { json.parseToJsonElement(candidate) }.getOrNull() as? JsonArray
    }

    private fun JsonObject.toRun() = BackgroundRun(
        id = string("id"),
        sessionId = string("sessionId"),
        cwd = string("cwd"),
        name = string("name"),
        kind = when (string("kind")?.lowercase()) {
            "background" -> BackgroundRunKind.BACKGROUND
            "interactive" -> BackgroundRunKind.INTERACTIVE
            else -> BackgroundRunKind.UNKNOWN
        },
        status = string("status"),
        state = string("state"),
        startedAtMillis = long("startedAt") ?: 0L,
        pid = long("pid"),
    )

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
}
