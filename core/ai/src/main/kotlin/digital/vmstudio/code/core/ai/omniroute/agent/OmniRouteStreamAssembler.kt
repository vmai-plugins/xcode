package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

/**
 * Rebuilds one model turn from a streamed response, so the reply can be shown as
 * it is written while tool calls are still assembled exactly.
 *
 * Feed every SSE `data:` payload to [accept], which returns any text to show now;
 * call [finish] at the end for the complete turn. Handles both wire dialects:
 * OpenAI `choices[].delta` chunks and Anthropic `content_block_*` events.
 */
class OmniRouteStreamAssembler(private val dialect: OmniRouteDialect) {

    private class ToolDraft(var id: String? = null, var name: String? = null) {
        val arguments = StringBuilder()
    }

    private val text = StringBuilder()
    private val tools = sortedMapOf<Int, ToolDraft>()
    private var inputTokens = 0L
    private var outputTokens = 0L
    private var sawAnything = false

    /** Text the model has written so far in this turn. */
    val textSoFar: String get() = text.toString()

    fun accept(data: String): String? {
        val root = runCatching { json.parseToJsonElement(data) }.getOrNull() as? JsonObject ?: return null
        sawAnything = true
        return when (dialect) {
            OmniRouteDialect.ANTHROPIC_MESSAGES -> acceptAnthropic(root)
            OmniRouteDialect.OPENAI_CHAT, OmniRouteDialect.UNKNOWN -> acceptOpenAi(root)
        }
    }

    fun finish(): OmniRouteTurn {
        val usage = (inputTokens to outputTokens).takeIf { inputTokens > 0 || outputTokens > 0 }
        val calls = tools.entries.mapNotNull { (index, draft) ->
            val name = draft.name ?: return@mapNotNull null
            OmniRouteToolCall(
                id = draft.id ?: "call_$index",
                name = name,
                argumentsJson = draft.arguments.toString().ifBlank { "{}" },
            )
        }
        return when {
            calls.isNotEmpty() -> OmniRouteTurn.ToolCalls(calls, usage)
            text.isNotEmpty() -> OmniRouteTurn.Text(text.toString(), usage)
            sawAnything -> OmniRouteTurn.Text("", usage)
            else -> OmniRouteTurn.ParseFailed("The stream carried no model output.")
        }
    }

    private fun acceptOpenAi(root: JsonObject): String? {
        (root["usage"] as? JsonObject)?.let { usage ->
            usage.long("prompt_tokens")?.let { inputTokens = it }
            usage.long("completion_tokens")?.let { outputTokens = it }
        }
        val delta = ((root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("delta") as? JsonObject ?: return null

        (delta["tool_calls"] as? JsonArray)?.forEach { element ->
            val call = element as? JsonObject ?: return@forEach
            val index = call.long("index")?.toInt() ?: tools.size
            val draft = tools.getOrPut(index) { ToolDraft() }
            call.string("id")?.let { draft.id = it }
            (call["function"] as? JsonObject)?.let { function ->
                function.string("name")?.let { draft.name = it }
                function.string("arguments")?.let(draft.arguments::append)
            }
        }
        return delta.string("content")?.also(text::append)
    }

    private fun acceptAnthropic(root: JsonObject): String? {
        val index = root.long("index")?.toInt()
        return when (root.string("type")) {
            "message_start" -> {
                val usage = (root["message"] as? JsonObject)?.get("usage") as? JsonObject
                usage?.long("input_tokens")?.let { inputTokens = it }
                null
            }
            "content_block_start" -> {
                val block = root["content_block"] as? JsonObject
                if (index != null && block?.string("type") == "tool_use") {
                    tools[index] = ToolDraft(id = block.string("id"), name = block.string("name"))
                }
                null
            }
            "content_block_delta" -> anthropicDelta(index, root["delta"] as? JsonObject)
            "message_delta" -> {
                ((root["usage"] as? JsonObject)?.long("output_tokens"))?.let { outputTokens = it }
                null
            }
            else -> null
        }
    }

    private fun anthropicDelta(index: Int?, delta: JsonObject?): String? = when (delta?.string("type")) {
        "text_delta" -> delta.string("text")?.also(text::append)
        "input_json_delta" -> {
            val draft = index?.let(tools::get)
            delta.string("partial_json")?.let { draft?.arguments?.append(it) }
            null
        }
        else -> null
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The same request body with streaming switched on (and usage requested). */
        fun streamingBody(dialect: OmniRouteDialect, body: String): String {
            val root = json.parseToJsonElement(body) as JsonObject
            val updated = root.toMutableMap()
            updated["stream"] = JsonPrimitive(true)
            if (dialect != OmniRouteDialect.ANTHROPIC_MESSAGES) {
                updated["stream_options"] = kotlinx.serialization.json.buildJsonObject {
                    put("include_usage", true)
                }
            }
            return JsonObject(updated).toString()
        }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
