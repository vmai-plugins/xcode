package digital.vmstudio.code.core.ai.omniroute

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Which wire format a gateway speaks.
 *
 * A gateway that fronts several model vendors is usually one or the other, and there
 * is no reliable way to tell from its name. Rather than guessing and shipping a
 * client that fails against half of them, both are implemented and the dialect is
 * detected once during the health check.
 */
enum class OmniRouteDialect {
    /** `POST /v1/messages`, `x-api-key`, `anthropic-version` header. */
    ANTHROPIC_MESSAGES,

    /** `POST /v1/chat/completions`, `Authorization: Bearer`. */
    OPENAI_CHAT,

    UNKNOWN,
    ;

    val chatPath: String
        get() = when (this) {
            ANTHROPIC_MESSAGES -> "v1/messages"
            OPENAI_CHAT, UNKNOWN -> "v1/chat/completions"
        }

    val displayName: String
        get() = when (this) {
            ANTHROPIC_MESSAGES -> "Anthropic Messages"
            OPENAI_CHAT -> "OpenAI Chat Completions"
            UNKNOWN -> "Unknown"
        }
}

/**
 * Extracts assistant text from a streamed chunk of either dialect.
 *
 * Pure and dialect-aware so the difference between the two APIs is confined to this
 * one function rather than spreading through the provider.
 */
object ChatStreamDecoder {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Text delta carried by [payload], or null when the chunk carries none. */
    fun textDelta(dialect: OmniRouteDialect, payload: String): String? {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
            ?: return null

        return when (dialect) {
            OmniRouteDialect.ANTHROPIC_MESSAGES -> anthropicDelta(root)
            OmniRouteDialect.OPENAI_CHAT, OmniRouteDialect.UNKNOWN ->
                // An unknown dialect tries both, so a gateway that turns out to be
                // the other kind still renders rather than showing nothing.
                openAiDelta(root) ?: anthropicDelta(root)
        }
    }

    /** Token usage from a terminal chunk, as `input to output`, when reported. */
    fun usage(payload: String): Pair<Long, Long>? {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
            ?: return null
        val usage = root["usage"] as? JsonObject ?: return null

        // Anthropic names them input/output; OpenAI names them prompt/completion.
        val input = usage.long("input_tokens") ?: usage.long("prompt_tokens")
        val output = usage.long("output_tokens") ?: usage.long("completion_tokens")
        if (input == null && output == null) return null
        return (input ?: 0L) to (output ?: 0L)
    }

    /** Model ids from a `GET /v1/models` body. Both dialects use `data[].id`. */
    fun parseModels(body: String): List<String> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()

        val array = when (root) {
            is JsonObject -> root["data"] as? JsonArray ?: root["models"] as? JsonArray
            is JsonArray -> root
            else -> null
        } ?: return emptyList()

        return array.mapNotNull { element ->
            when (element) {
                is JsonObject -> element.string("id") ?: element.string("name")
                is JsonPrimitive -> element.takeIf { it.isString }?.content
                else -> null
            }
        }
    }

    private fun anthropicDelta(root: JsonObject): String? {
        // content_block_delta carries {"delta":{"type":"text_delta","text":"..."}}
        val delta = root["delta"] as? JsonObject ?: return null
        return delta.string("text")
    }

    private fun openAiDelta(root: JsonObject): String? {
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        // Streaming uses `delta`; a non-streamed response uses `message`.
        val holder = first["delta"] as? JsonObject ?: first["message"] as? JsonObject
        return holder?.string("content")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
}
