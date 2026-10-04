package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.ai.model.ImageAttachment
import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
    data class Text(val text: String, val usage: Pair<Long, Long>?) : OmniRouteTurn
    data class ToolCalls(val calls: List<OmniRouteToolCall>, val usage: Pair<Long, Long>?) : OmniRouteTurn
    data class ParseFailed(val reason: String) : OmniRouteTurn
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
        putJsonArray("tools") {
            tools.forEach { add(it.toOpenAiToolJson()) }
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
            put("content", text)
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
            val id = call.stringOrNull("id") ?: return@mapNotNull null
            val name = function.stringOrNull("name") ?: return@mapNotNull null
            val arguments = function.stringOrNull("arguments") ?: "{}"
            OmniRouteToolCall(id, name, arguments)
        }.orEmpty()

        if (toolCalls.isNotEmpty()) return OmniRouteTurn.ToolCalls(toolCalls, usage)

        val text = message.stringOrNull("content").orEmpty()
        return OmniRouteTurn.Text(text, usage)
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
        putJsonArray("tools") {
            tools.forEach { add(it.toAnthropicToolJson()) }
        }
        putJsonArray("messages") {
            // Anthropic has no "tool" role: a tool result is a user-turn content
            // block. Consecutive tool results for the same round of parallel calls
            // must land in one user message, not one each - the API rejects two
            // consecutive messages with the same role.
            var index = 0
            while (index < history.size) {
                val message = history[index]
                if (message is OmniRouteMessage.ToolResult) {
                    val batch = mutableListOf<OmniRouteMessage.ToolResult>()
                    while (index < history.size) {
                        val next = history[index] as? OmniRouteMessage.ToolResult ?: break
                        batch += next
                        index++
                    }
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                batch.forEach { result -> add(result.toAnthropicToolResultJson()) }
                            }
                        },
                    )
                } else {
                    add(message.toAnthropicJson())
                    index++
                }
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
                "tool_use" -> {
                    val id = block.stringOrNull("id")
                    val name = block.stringOrNull("name")
                    if (id != null && name != null) {
                        val input = (block["input"] as? JsonObject) ?: JsonObject(emptyMap())
                        toolCalls += OmniRouteToolCall(id, name, input.toString())
                    }
                }
            }
        }

        if (toolCalls.isNotEmpty()) return OmniRouteTurn.ToolCalls(toolCalls, usage)
        return OmniRouteTurn.Text(text.toString(), usage)
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
