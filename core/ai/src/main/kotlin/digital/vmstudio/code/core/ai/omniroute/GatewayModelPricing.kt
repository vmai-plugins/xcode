package digital.vmstudio.code.core.ai.omniroute

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Which gateway models cost nothing, read from the same `/v1/models` body the ids
 * come from, so the picker can say so. Gateways mark this in different ways, and
 * all of them are accepted.
 */
object GatewayModelPricing {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * The ids a `GET /v1/models` body marks as free: an explicit flag (`free`,
     * `is_free`, a "free" tier or tag), zero prompt and completion pricing
     * (OpenRouter-style `pricing`), or an id the naming convention marks (`:free`).
     */
    fun parseFreeModels(body: String): Set<String> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptySet()
        val array = when (root) {
            is JsonObject -> root["data"] as? JsonArray ?: root["models"] as? JsonArray
            is JsonArray -> root
            else -> null
        } ?: return emptySet()
        return array.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val id = element.string("id") ?: element.string("name") ?: return@mapNotNull null
                    id.takeIf { isFreeEntry(element) || isFreeModelId(id) }
                }
                is JsonPrimitive -> element.takeIf { it.isString }?.content?.takeIf(::isFreeModelId)
                else -> null
            }
        }.toSet()
    }

    /** Free by name alone: `vendor/model:free`, `…-free`, or a `free/` route. */
    fun isFreeModelId(id: String): Boolean {
        val lower = id.lowercase()
        return lower.endsWith(":free") || lower.endsWith("-free") || lower.startsWith("free/") ||
            lower.contains("/free/")
    }

    private fun isFreeEntry(entry: JsonObject): Boolean {
        val flag = listOf("free", "is_free").any { key ->
            (entry[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true
        }
        val tier = listOf("tier", "plan", "pricing_tier").any { key ->
            entry.string(key)?.equals("free", ignoreCase = true) == true
        }
        val tagged = (entry["tags"] as? JsonArray)?.any {
            (it as? JsonPrimitive)?.content?.equals("free", ignoreCase = true) == true
        } == true
        val pricing = entry["pricing"] as? JsonObject
        val zeroPrice = pricing != null && listOf("prompt", "completion").all { key ->
            (pricing[key] as? JsonPrimitive)?.content?.toDoubleOrNull() == 0.0
        }
        return flag || tier || tagged || zeroPrice
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
