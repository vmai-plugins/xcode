package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.model.ImageAttachment
import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One tool call the model asked for. [argumentsJson] is always a JSON object as text. */
data class OmniRouteToolCall(val id: String, val name: String, val argumentsJson: String)

/** One turn of conversation, in a shape both dialects can render. */
sealed interface OmniRouteMessage {
    data class User(val text: String, val images: List<ImageAttachment> = emptyList()) : OmniRouteMessage
    data class Assistant(val text: String?, val toolCalls: List<OmniRouteToolCall>) : OmniRouteMessage
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val content: String,
        val isError: Boolean,
    ) : OmniRouteMessage
}

/** What a completed (non-streamed) model turn produced. */
sealed interface OmniRouteTurn {
    /** [truncated]: the model hit its output-token limit, so the text stops early. */
    data class Text(
        val text: String,
        val usage: Pair<Long, Long>?,
        val truncated: Boolean = false,
    ) : OmniRouteTurn

    /** [truncated]: cut off at the token limit; the last call's arguments are incomplete. */
    data class ToolCalls(
        val calls: List<OmniRouteToolCall>,
        val usage: Pair<Long, Long>?,
        val truncated: Boolean = false,
        val text: String? = null,
    ) : OmniRouteTurn

    data class ParseFailed(val reason: String) : OmniRouteTurn

    /** The gateway answered with an error object (possibly inside a 200 stream). */
    data class GatewayError(val message: String, val retryable: Boolean) : OmniRouteTurn
}

/**
 * An error object a gateway returned in place of a reply: OpenAI-style
 * `{"error":{"message":…}}` or Anthropic-style `{"type":"error","error":{…}}`,
 * sometimes inside a stream that began with HTTP 200. Unread, it looked like an
 * empty successful answer.
 */
internal fun gatewayErrorOf(root: JsonObject): OmniRouteTurn.GatewayError? {
    val error = root["error"]?.takeUnless { it is JsonNull } ?: return null
    val obj = error as? JsonObject
    val message = (obj?.get("message") as? JsonPrimitive)?.content
        ?: (error as? JsonPrimitive)?.content
        ?: error.toString()
    val type = ((obj?.get("type") ?: obj?.get("code")) as? JsonPrimitive)?.content.orEmpty()
    val combined = "$type $message"
    val retryable = RETRYABLE_ERROR_TYPES.any { combined.contains(it, ignoreCase = true) }
    return OmniRouteTurn.GatewayError(message.ifBlank { type.ifBlank { "Unknown error" } }, retryable)
}

private val RETRYABLE_ERROR_TYPES = listOf(
    "overloaded", "rate_limit", "rate limit", "quota", "timeout", "server_error", "unavailable",
    "tokens per minute", "tpm", "reduce your message", "payload too large",
    "capability_mismatch", "capability", "tool calling",
)

/** OpenAI's `finish_reason` and Anthropic's `stop_reason` for an output-limit stop. */
internal fun isLengthStop(reason: String?): Boolean {
    val r = reason?.lowercase() ?: return false
    return r == "length" || r == "max_tokens" || r == "max_output_tokens" || r == "model_length" || r == "token_limit"
}

/**
 * Builds a tool-capable chat request and parses its (non-streamed) response, for
 * either wire dialect [OmniRouteProvider][digital.vmstudio.code.core.ai.omniroute.OmniRouteProvider]
 * already detects.
 *
 * Non-streaming on purpose: assembling a tool call correctly from streamed deltas
 * across two dialects and models of unknown quality is a real source of bugs for
 * little benefit in a turn that is a tool round trip, not a long answer.
 */
object OmniRouteToolCodec {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildRequestBody(
        dialect: OmniRouteDialect,
        model: String,
        systemPrompt: String,
        history: List<OmniRouteMessage>,
        maxTokens: Int,
        tools: List<OmniRouteTool> = OmniRouteTool.entries,
    ): String = when (dialect) {
        OmniRouteDialect.ANTHROPIC_MESSAGES ->
            buildAnthropicRequest(model, systemPrompt, history, maxTokens, tools)
        OmniRouteDialect.OPENAI_CHAT, OmniRouteDialect.UNKNOWN ->
            buildOpenAiRequest(model, systemPrompt, history, maxTokens, tools)
    }

    fun parseResponse(dialect: OmniRouteDialect, body: String): OmniRouteTurn {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return OmniRouteTurn.ParseFailed("The gateway's response was not a JSON object.")

        gatewayErrorOf(root)?.let { return it }
        return when (dialect) {
            OmniRouteDialect.ANTHROPIC_MESSAGES -> parseAnthropicResponse(root)
            OmniRouteDialect.OPENAI_CHAT, OmniRouteDialect.UNKNOWN -> parseOpenAiResponse(root)
        }
    }

    // --- OpenAI dialect ------------------------------------------------------------

    private fun buildOpenAiRequest(
        model: String,
        systemPrompt: String,
        history: List<OmniRouteMessage>,
        maxTokens: Int,
        tools: List<OmniRouteTool>,
    ): String = buildJsonObject {
        put("model", model)
        put("stream", false)
        put("max_tokens", maxTokens)
        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                tools.forEach { add(it.toOpenAiToolJson()) }
            }
        }
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                },
            )
            history.forEach { message -> add(message.toOpenAiJson()) }
        }
    }.toString()

    private fun OmniRouteMessage.toOpenAiJson(): JsonObject = when (this) {
        is OmniRouteMessage.User -> buildJsonObject {
            put("role", "user")
            if (images.isEmpty()) {
                put("content", text)
            } else {
                putJsonArray("content") {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                    images.forEach { image ->
                        add(
                            buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:${image.mediaType};base64,${image.base64Data}")
                                }
                            },
                        )
                    }
                }
            }
        }

        is OmniRouteMessage.Assistant -> buildJsonObject {
            put("role", "assistant")
            put("content", text ?: "")
            if (toolCalls.isNotEmpty()) {
                putJsonArray("tool_calls") {
                    toolCalls.forEach { call ->
                        add(
                            buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", call.name)
                                    put("arguments", call.argumentsJson)
                                }
                            },
                        )
                    }
                }
            }
        }

        is OmniRouteMessage.ToolResult -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", toolCallId)
            put("name", toolName)
            put("content", content)
        }
    }

    private fun parseOpenAiResponse(root: JsonObject): OmniRouteTurn {
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return OmniRouteTurn.ParseFailed("The response had no choices.")
        val message = choice["message"] as? JsonObject
            ?: return OmniRouteTurn.ParseFailed("The response's choice had no message.")
        val usage = usageOf(root, inputKey = "prompt_tokens", outputKey = "completion_tokens")

        val toolCalls = (message["tool_calls"] as? JsonArray)?.mapNotNull { element ->
            val call = element as? JsonObject ?: return@mapNotNull null
            val function = call["function"] as? JsonObject ?: return@mapNotNull null
            val id = call.stringOrNull("id").orEmpty()
            val name = function.stringOrNull("name") ?: return@mapNotNull null
            val arguments = when (val raw = function["arguments"]) {
                is JsonPrimitive -> raw.takeIf { it.isString }?.content ?: "{}"
                is JsonObject -> raw.toString()
                else -> "{}"
            }
            OmniRouteToolCall(id, name, arguments)
        }.orEmpty()

        val truncated = isLengthStop(choice.stringOrNull("finish_reason"))
        val rawText = message.stringOrNull("content").orEmpty()
        val reasoning = message.stringOrNull("reasoning_content")
            ?: message.stringOrNull("reasoning")
            ?: ""
        val text = when {
            rawText.isNotBlank() -> rawText
            reasoning.isNotBlank() -> reasoning
            else -> ""
        }
        if (toolCalls.isNotEmpty()) {
            return OmniRouteTurn.ToolCalls(
                calls = toolCalls,
                usage = usage,
                truncated = truncated,
                text = text.takeIf { it.isNotBlank() },
            )
        }

        if (text.isBlank()) {
            return OmniRouteTurn.ParseFailed("The gateway response carried neither text nor tool calls.")
        }
        return OmniRouteTurn.Text(text, usage, truncated)
    }

    // --- Anthropic dialect -----------------------------------------------------------

    private fun buildAnthropicRequest(
        model: String,
        systemPrompt: String,
        history: List<OmniRouteMessage>,
        maxTokens: Int,
        tools: List<OmniRouteTool>,
    ): String = buildJsonObject {
        put("model", model)
        put("stream", false)
        put("max_tokens", maxTokens)
        put("system", systemPrompt)
        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                tools.forEach { add(it.toAnthropicToolJson()) }
            }
        }
        putJsonArray("messages") {
            // Anthropic has no "tool" role: a tool result is a user-turn content
            // block. Consecutive tool results for the same round of parallel calls,
            // or any adjacent turns with the same role, must be merged into one
            // message because the API strictly rejects consecutive identical roles.
            val mergedMessages = mutableListOf<Pair<String, MutableList<JsonElement>>>()
            fun appendRoleBlocks(role: String, blocks: List<JsonElement>) {
                val last = mergedMessages.lastOrNull()
                if (last != null && last.first == role) {
                    last.second.addAll(blocks)
                } else {
                    mergedMessages += role to blocks.toMutableList()
                }
            }

            var index = 0
            while (index < history.size) {
                val message = history[index]
                if (message is OmniRouteMessage.ToolResult) {
                    val batch = mutableListOf<JsonElement>()
                    while (index < history.size) {
                        val next = history[index] as? OmniRouteMessage.ToolResult ?: break
                        batch += next.toAnthropicToolResultJson()
                        index++
                    }
                    appendRoleBlocks("user", batch)
                } else {
                    val rendered = message.toAnthropicJson()
                    val role = rendered.stringOrNull("role") ?: "user"
                    val blocks = (rendered["content"] as? JsonArray)?.toList().orEmpty()
                    appendRoleBlocks(role, blocks)
                    index++
                }
            }

            mergedMessages
                .dropWhile { it.first != "user" }
                .forEach { (role, blocks) ->
                    add(
                        buildJsonObject {
                            put("role", role)
                            put("content", JsonArray(blocks))
                        },
                    )
                }
        }
    }.toString()

    private fun OmniRouteMessage.toAnthropicJson(): JsonObject = when (this) {
        is OmniRouteMessage.User -> buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                images.forEach { image ->
                    add(
                        buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", image.mediaType)
                                put("data", image.base64Data)
                            }
                        },
                    )
                }
                add(buildJsonObject { put("type", "text"); put("text", text) })
            }
        }

        is OmniRouteMessage.Assistant -> buildJsonObject {
            put("role", "assistant")
            putJsonArray("content") {
                text?.takeIf { it.isNotBlank() }?.let {
                    add(buildJsonObject { put("type", "text"); put("text", it) })
                }
                toolCalls.forEach { call ->
                    add(
                        buildJsonObject {
                            put("type", "tool_use")
                            put("id", call.id)
                            put("name", call.name)
                            put("input", parseArgumentsOrEmpty(call.argumentsJson))
                        },
                    )
                }
                if (text.isNullOrBlank() && toolCalls.isEmpty()) {
                    add(buildJsonObject { put("type", "text"); put("text", "(no reply)") })
                }
            }
        }

        is OmniRouteMessage.ToolResult ->
            error("ToolResult is batched by buildAnthropicRequest, not rendered alone.")
    }

    private fun OmniRouteMessage.ToolResult.toAnthropicToolResultJson(): JsonObject = buildJsonObject {
        put("type", "tool_result")
        put("tool_use_id", toolCallId)
        put("content", content)
        put("is_error", isError)
    }

    private fun parseAnthropicResponse(root: JsonObject): OmniRouteTurn {
        val blocks = root["content"] as? JsonArray
            ?: return OmniRouteTurn.ParseFailed("The response had no content blocks.")
        val usage = usageOf(root, inputKey = "input_tokens", outputKey = "output_tokens")

        val toolCalls = mutableListOf<OmniRouteToolCall>()
        val text = StringBuilder()
        for (element in blocks) {
            val block = element as? JsonObject ?: continue
            when (block.stringOrNull("type")) {
                "text" -> block.stringOrNull("text")?.let(text::append)
                "thinking" -> block.stringOrNull("thinking")?.let { thinkingContent ->
                    if (text.isEmpty()) text.append(thinkingContent)
                }
                "tool_use" -> {
                    val id = block.stringOrNull("id").orEmpty()
                    val name = block.stringOrNull("name")
                    if (name != null) {
                        val arguments = when (val raw = block["input"]) {
                            is JsonObject -> raw.toString()
                            is JsonPrimitive -> if (raw.isString) raw.content else raw.toString()
                            else -> "{}"
                        }
                        toolCalls += OmniRouteToolCall(id, name, arguments)
                    }
                }
            }
        }

        val truncated = isLengthStop(root.stringOrNull("stop_reason"))
        val contentText = text.toString()
        if (toolCalls.isNotEmpty()) return OmniRouteTurn.ToolCalls(toolCalls, usage, truncated, text = contentText.takeIf { it.isNotBlank() })
        if (contentText.isBlank()) {
            return OmniRouteTurn.ParseFailed("The gateway response carried neither text nor tool calls.")
        }
        return OmniRouteTurn.Text(contentText, usage, truncated)
    }

    // --- shared ----------------------------------------------------------------------

    private fun parseArgumentsOrEmpty(argumentsJson: String): JsonElement =
        runCatching { json.parseToJsonElement(argumentsJson) }.getOrDefault(JsonObject(emptyMap()))
}

private fun usageOf(root: JsonObject, inputKey: String, outputKey: String): Pair<Long, Long>? {
    val usage = root["usage"] as? JsonObject ?: return null
    val input = usage.longOrNull(inputKey) ?: return null
    val output = usage.longOrNull(outputKey) ?: return null
    return input to output
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

/**
 * Gives every tool call an id no earlier call in the conversation used.
 *
 * Some gateways send no id at all, others number calls from zero on every turn
 * ("call_0" again and again). The id pairs a call with its result, and it keys the
 * call's row in the chat, so a repeat both confuses the model and crashed the
 * transcript. Blank or already-used ids are replaced; the history then carries the
 * new ids, so calls and results still match.
 */
internal fun List<OmniRouteToolCall>.withUniqueIds(
    used: MutableSet<String>,
    freshId: () -> String,
): List<OmniRouteToolCall> = map { call ->
    val id = call.id.takeIf { it.isNotBlank() && it !in used }
        ?: generateSequence(freshId).first { it !in used }
    used += id
    if (id == call.id) call else call.copy(id = id)
}

/** Ids of every tool call already in [history]. */
internal fun usedToolCallIds(history: List<OmniRouteMessage>): MutableSet<String> =
    history.filterIsInstance<OmniRouteMessage.Assistant>()
        .flatMap { message -> message.toolCalls.map { it.id } }
        .toMutableSet()

/**
 * Trims conversation history to fit within [budget] characters, dropping whole
 * old turns from the front first and shortening tool results if needed.
 *
 * Strategy:
 * 1. Measure the current character length of all messages.
 * 2. If already under budget, return unchanged.
 * 3. Drop complete user→assistant→tool-result turns from the front until we're close.
 * 4. If still over budget, shorten older tool results (keeping [keepRecent] most recent).
 * 5. Always ensure the history starts with a user message.
 */
internal fun trimHistory(
    history: List<OmniRouteMessage>,
    budget: Int = DEFAULT_HISTORY_BUDGET,
    keepRecent: Int = DEFAULT_KEEP_RECENT_TURNS,
): List<OmniRouteMessage> {
    // Fast path: already under budget
    if (history.joinToString { messageTextLength(it) }.length <= budget) {
        return history
    }

    // Work with a mutable copy
    val result = history.toMutableList()

    // Step 1: Drop complete turns from the front
    while (result.size > keepRecent * 3 && messageTextLength(result) > budget) {
        // Find the first complete turn (user + assistant + possible tool result)
        val firstUserIndex = result.indexOfFirst { it is OmniRouteMessage.User }
        if (firstUserIndex < 0 || firstUserIndex >= result.size - keepRecent * 3) break

        val nextUserIndex = result.indexOfFirst(firstUserIndex + 1) { it is OmniRouteMessage.User }
        val turnEnd = if (nextUserIndex > 0) nextUserIndex - 1 else result.size - 1
        result.subList(firstUserIndex, turnEnd + 1).clear()

        if (messageTextLength(result) <= budget) break
    }

    // Step 2: If still over budget, shorten tool results (oldest first)
    if (messageTextLength(result) > budget) {
        val toolResults = result.mapIndexedNotNull { index, msg ->
            (msg as? OmniRouteMessage.ToolResult)?.let { index to it }
        }

        // Sort by position (oldest first) and shorten all but the most recent [keepRecent]
        val resultsToShorten = toolResults.dropLast(keepRecent)
        for ((index, toolResult) in resultsToShorten) {
            if (messageTextLength(result) <= budget) break
            val shortened = capToolOutput(toolResult.content, maxOf(100, budget / 20))
            result[index] = toolResult.copy(content = shortened)
        }
    }

    // Ensure history starts with a user message
    val firstUser = result.indexOfFirst { it is OmniRouteMessage.User }
    if (firstUser > 0) {
        result.subList(0, firstUser).clear()
    }

    return result
}

/**
 * Caps tool output to [limit] characters, preserving the beginning and end with
 * an ellipsis indicator in the middle.
 */
internal fun capToolOutput(content: String, limit: Int = DEFAULT_TOOL_OUTPUT_LIMIT): String {
    if (content.length <= limit || limit <= 0) return content

    val keepEach = limit / 2
    return buildString {
        append(content, 0, minOf(keepEach, content.length))
        append("\n\n...[truncated: ${content.length - minOf(keepEach, content.length) - (content.length - keepEach).coerceAtLeast(0)} characters omitted]...\n\n")
        append(content, maxOf(0, content.length - keepEach), content.length)
    }.take(limit)
}

private fun messageTextLength(msg: OmniRouteMessage): Int = when (msg) {
    is OmniRouteMessage.User -> msg.text.length
    is OmniRouteMessage.Assistant -> (msg.text?.length ?: 0) + msg.toolCalls.sumOf { it.argumentsJson.length }
    is OmniRouteMessage.ToolResult -> msg.content.length
}

private fun messageTextLength(history: List<OmniRouteMessage>): Int =
    history.sumOf { messageTextLength(it) }

private fun <T> List<T>.indexOfFirst(start: Int, predicate: (T) -> Boolean): Int {
    for (i in start until size) {
        if (predicate(this[i])) return i
    }
    return -1
}

private const val DEFAULT_HISTORY_BUDGET = 12_000
private const val DEFAULT_KEEP_RECENT_TURNS = 3
private const val DEFAULT_TOOL_OUTPUT_LIMIT = 2_000
