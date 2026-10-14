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

    private class ToolDraft(
        var id: String? = null,
        var name: String? = null,
        var initialInput: String? = null,
    ) {
        val arguments = StringBuilder()
    }

    private val text = StringBuilder()

    /** Thinking the model streamed and the chat has not shown yet. */
    private val pendingReasoning = StringBuilder()
    private val tools = sortedMapOf<Int, ToolDraft>()
    private var inputTokens = 0L
    private var outputTokens = 0L
    private var sawAnything = false
    private var lengthStop = false
    private var gatewayError: OmniRouteTurn.GatewayError? = null

    /** Text the model has written so far in this turn. */
    val textSoFar: String get() = text.toString()

    fun accept(data: String): String? {
        val root = runCatching { json.parseToJsonElement(data) }.getOrNull() as? JsonObject ?: return null
        sawAnything = true
        gatewayErrorOf(root)?.let {
            gatewayError = it
            return null
        }
        return when (dialect) {
            OmniRouteDialect.ANTHROPIC_MESSAGES -> acceptAnthropic(root)
            OmniRouteDialect.OPENAI_CHAT, OmniRouteDialect.UNKNOWN -> acceptOpenAi(root)
        }
    }

    fun finish(): OmniRouteTurn {
        val usage = (inputTokens to outputTokens).takeIf { inputTokens > 0 || outputTokens > 0 }
        val calls = tools.entries.mapNotNull { (_, draft) ->
            val name = draft.name ?: return@mapNotNull null
            OmniRouteToolCall(
                id = draft.id.orEmpty(),
                name = name,
                argumentsJson = draft.arguments.toString().ifBlank { draft.initialInput ?: "{}" },
            )
        }
        gatewayError?.let { return it }
        return when {
            calls.isNotEmpty() -> OmniRouteTurn.ToolCalls(calls, usage, lengthStop)
            text.isNotBlank() -> OmniRouteTurn.Text(text.toString(), usage, lengthStop)
            else -> OmniRouteTurn.ParseFailed("The stream carried no model output.")
        }
    }

    private fun acceptOpenAi(root: JsonObject): String? {
        (root["usage"] as? JsonObject)?.let { usage ->
            usage.long("prompt_tokens")?.let { inputTokens = it }
            usage.long("completion_tokens")?.let { outputTokens = it }
        }
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        if (isLengthStop(choice?.string("finish_reason"))) lengthStop = true
        val delta = (choice?.get("delta") as? JsonObject)
            ?: (choice?.get("message") as? JsonObject)
            ?: return null

        (delta["tool_calls"] as? JsonArray)?.forEach { element ->
            val call = element as? JsonObject ?: return@forEach
            val callId = call.string("id")
            val function = call["function"] as? JsonObject
            val funcName = function?.string("name")
            val index = call.long("index")?.toInt() ?: when {
                callId != null -> tools.entries.firstOrNull { it.value.id == callId }?.key
                    ?: if (tools.values.lastOrNull()?.name == null && tools.isNotEmpty()) {
                        tools.keys.last()
                    } else {
                        tools.size
                    }
                funcName != null && tools.values.lastOrNull()?.name != null -> tools.size
                else -> tools.keys.lastOrNull() ?: 0
            }
            val draft = tools.getOrPut(index) { ToolDraft() }
            callId?.let { draft.id = it }
            function?.let { fn ->
                funcName?.let { draft.name = it }
                when (val rawArgs = fn["arguments"]) {
                    is JsonPrimitive -> if (rawArgs.isString) draft.arguments.append(rawArgs.content)
                    is JsonObject -> if (draft.arguments.isEmpty()) draft.arguments.append(rawArgs.toString())
                    else -> Unit
                }
            }
        }
        // Reasoning models (and gateways in front of them) stream their thinking
        // under one of these names before any content; without it the chat sat
        // blank for the whole think.
        (delta.string("reasoning_content") ?: delta.string("reasoning"))?.let(pendingReasoning::append)
        return delta.string("content")?.also(text::append)
    }

    /** Thinking streamed since the last call, or null; the caller shows it once. */
    fun takeReasoning(): String? = pendingReasoning.toString().takeIf { it.isNotBlank() }
        ?.also { pendingReasoning.setLength(0) }

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
                when (block?.string("type")) {
                    "tool_use" -> {
                        val slot = index ?: tools.size
                        val initial = (block["input"] as? JsonObject)
                            ?.takeIf { it.isNotEmpty() }
                            ?.toString()
                        tools[slot] = ToolDraft(
                            id = block.string("id"),
                            name = block.string("name"),
                            initialInput = initial,
                        )
                        null
                    }
                    "text" -> block.string("text")?.takeIf { it.isNotEmpty() }?.also(text::append)
                    "thinking" -> {
                        block.string("thinking")?.takeIf { it.isNotEmpty() }?.let(pendingReasoning::append)
                        null
                    }
                    else -> null
                }
            }
            "content_block_delta" -> anthropicDelta(index, root["delta"] as? JsonObject)
            "message_delta" -> {
                ((root["usage"] as? JsonObject)?.long("output_tokens"))?.let { outputTokens = it }
                if (isLengthStop((root["delta"] as? JsonObject)?.string("stop_reason"))) lengthStop = true
                null
            }
            else -> null
        }
    }

    private fun anthropicDelta(index: Int?, delta: JsonObject?): String? = when (delta?.string("type")) {
        "text_delta" -> delta.string("text")?.also(text::append)
        "thinking_delta" -> {
            delta.string("thinking")?.let(pendingReasoning::append)
            null
        }
        "input_json_delta" -> {
            val draft = (index ?: tools.keys.lastOrNull())?.let(tools::get)
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
