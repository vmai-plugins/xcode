package digital.vmstudio.code.core.ai.claudecode

import digital.vmstudio.code.core.ai.model.AgentEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Translates the Claude Code CLI's `--output-format stream-json` into [AgentEvent]s.
 *
 * **Parsed leniently, on purpose.** Two properties of the real stream make strict
 * `@Serializable` models the wrong tool:
 *
 *  1. The CLI writes non-JSON lines to stdout among the JSON ones — a captured run
 *     produced `[claude-code:unrecognized_model] {...}` between two valid envelopes.
 *     A strict parser would abort the run on a diagnostic line.
 *  2. The envelope gains fields between CLI releases. Decoding into `JsonObject` and
 *     reading the fields actually needed means a new field is ignored rather than
 *     fatal, which matters when the CLI updates independently of the app.
 *
 * Anything unrecognised becomes [AgentEvent.Diagnostic] rather than being dropped:
 * an authentication failure prints a plain-text line, and swallowing it would present
 * as an agent that silently does nothing.
 */
class ClaudeCodeStreamParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /**
     * Parses one line of stdout. Returns the events it produced, which may be empty
     * for envelopes that carry no user-visible information.
     */
    fun parseLine(line: String): List<AgentEvent> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Cheap guard before attempting a parse: most non-protocol lines fail here.
        if (!trimmed.startsWith("{")) {
            return listOf(AgentEvent.Diagnostic(trimmed, isStderr = false))
        }

        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject
            ?: return listOf(AgentEvent.Diagnostic(trimmed, isStderr = false))

        return when (root.string("type")) {
            "system" -> parseSystem(root)
            "assistant" -> parseAssistant(root)
            "user" -> parseUser(root)
            "result" -> listOf(parseResult(root))
            "stream_event" -> parseStreamEvent(root)
            else -> parseUntyped(root, trimmed)
        }
    }

    /**
     * Text fragments as the reply is generated.
     *
     * Only `text_delta` is surfaced. Thinking deltas are skipped: reasoning is shown
     * collapsed and streaming it would expand and re-collapse on every token.
     */
    private fun parseStreamEvent(root: JsonObject): List<AgentEvent> {
        val event = root["event"] as? JsonObject ?: return emptyList()
        if (event.string("type") != "content_block_delta") return emptyList()
        val delta = event["delta"] as? JsonObject ?: return emptyList()
        if (delta.string("type") != "text_delta") return emptyList()
        val text = delta.string("text")?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return listOf(AgentEvent.AssistantDelta(text))
    }

    private fun parseSystem(root: JsonObject): List<AgentEvent> {
        if (root.string("subtype") != "init") return emptyList()
        val sessionId = root.string("session_id") ?: return emptyList()
        return listOf(
            AgentEvent.SessionStarted(
                sessionId = sessionId,
                workingDirectory = root.string("cwd"),
                model = root.string("model"),
                availableTools = (root["tools"] as? JsonArray)
                    ?.mapNotNull { it.stringOrNull() }
                    .orEmpty(),
            ),
        )
    }

    private fun parseAssistant(root: JsonObject): List<AgentEvent> {
        val content = root["message"]?.jsonObjectOrNull()?.get("content") as? JsonArray
            ?: return emptyList()

        return content.mapNotNull { block ->
            val obj = block.jsonObjectOrNull() ?: return@mapNotNull null
            when (obj.string("type")) {
                "text" -> obj.string("text")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { AgentEvent.AssistantMessage(it) }

                "thinking" -> obj.string("thinking")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { AgentEvent.Reasoning(it) }

                "tool_use" -> {
                    val name = obj.string("name") ?: return@mapNotNull null
                    val input = obj["input"]?.jsonObjectOrNull() ?: JsonObject(emptyMap())
                    AgentEvent.ToolStarted(
                        toolUseId = obj.string("id").orEmpty(),
                        name = name,
                        summary = ToolSummary.describe(name, input),
                        argumentsJson = input.toString(),
                        affectedPath = ToolSummary.affectedPath(input),
                    )
                }

                else -> null
            }
        }
    }

    /** Tool results arrive as a `user` turn, which is how the protocol reports them. */
    private fun parseUser(root: JsonObject): List<AgentEvent> {
        val content = root["message"]?.jsonObjectOrNull()?.get("content") as? JsonArray
            ?: return emptyList()

        return content.mapNotNull { block ->
            val obj = block.jsonObjectOrNull() ?: return@mapNotNull null
            if (obj.string("type") != "tool_result") return@mapNotNull null
            AgentEvent.ToolFinished(
                toolUseId = obj.string("tool_use_id").orEmpty(),
                isError = obj["is_error"].asBoolean() ?: false,
                output = obj["content"].flattenToText(),
            )
        }
    }

    private fun parseResult(root: JsonObject): AgentEvent = AgentEvent.Completed(
        sessionId = root.string("session_id"),
        resultText = root.string("result"),
        durationMillis = root["duration_ms"].asLong() ?: 0L,
        costUsd = root["total_cost_usd"].asDouble() ?: 0.0,
        inputTokens = root.usage("input_tokens"),
        outputTokens = root.usage("output_tokens"),
        isError = root["is_error"].asBoolean() ?: false,
    )

    /**
     * An envelope with no `type`. The final summary of a run has been observed
     * without one, so it is recognised by its distinctive fields instead of being
     * discarded along with the session id the caller needs to resume.
     */
    private fun parseUntyped(root: JsonObject, raw: String): List<AgentEvent> =
        if (root.containsKey("total_cost_usd") || root.containsKey("duration_api_ms")) {
            listOf(parseResult(root))
        } else {
            listOf(AgentEvent.Diagnostic(raw, isStderr = false))
        }

    // --- lenient accessors -------------------------------------------------------

    private fun JsonObject.string(key: String): String? = this[key]?.stringOrNull()

    private fun JsonObject.usage(key: String): Long =
        (this["usage"] as? JsonObject)?.get(key).asLong() ?: 0L

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

    private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull

    private fun JsonElement.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * Tool results are a string in some versions and an array of content blocks in
     * others, so both are flattened to display text.
     */
    private fun JsonElement?.flattenToText(): String = when (this) {
        null -> ""
        is JsonPrimitive -> if (isString) content else toString()
        is JsonArray -> jsonArray.joinToString("\n") { element ->
            element.jsonObjectOrNull()?.get("text")?.stringOrNull() ?: element.toString()
        }
        is JsonObject -> jsonObject["text"]?.stringOrNull() ?: toString()
        else -> toString()
    }
}

/** Turns raw tool arguments into something worth showing in a chat transcript. */
internal object ToolSummary {

    fun describe(name: String, input: JsonObject): String {
        val path = affectedPath(input)
        return when (name) {
            "Read" -> path?.let { "Read $it" } ?: "Read a file"
            "Edit" -> path?.let { "Edited $it" } ?: "Edited a file"
            "Write" -> path?.let { "Wrote $it" } ?: "Wrote a file"
            "NotebookEdit" -> path?.let { "Edited $it" } ?: "Edited a notebook"
            "Bash", "PowerShell" -> input.text("command")?.let { "Ran: $it" } ?: "Ran a command"
            "Glob" -> input.text("pattern")?.let { "Found files matching $it" } ?: "Searched files"
            "Grep" -> input.text("pattern")?.let { "Searched for $it" } ?: "Searched code"
            "WebFetch" -> input.text("url")?.let { "Fetched $it" } ?: "Fetched a page"
            "WebSearch" -> input.text("query")?.let { "Searched the web for $it" } ?: "Searched the web"
            "Task" -> input.text("description")?.let { "Delegated: $it" } ?: "Delegated a task"
            "TodoWrite" -> "Updated its task list"
            else -> path?.let { "$name on $it" } ?: name
        }
    }

    /** The file a tool acted on, when it names one. Drives the diff viewer. */
    fun affectedPath(input: JsonObject): String? =
        input.text("file_path") ?: input.text("path") ?: input.text("notebook_path")

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
