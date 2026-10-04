package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the agent's `web_fetch` tool: downloads a public page from the device and
 * returns its readable text.
 *
 * Fetched from the phone, not the server, so it works even when the server has no
 * outbound access. Private and loopback addresses are refused: the model chooses
 * the URL, and it must not be able to reach the user's home network through the
 * phone.
 */
@Singleton
class WebFetcher @Inject constructor(
    private val httpClient: VmHttpClient,
) {

    suspend fun fetch(rawUrl: String): VmResult<String> {
        val url = rawUrl.trim().toHttpUrlOrNull()
            ?: return refuse("\"$rawUrl\" is not an http or https URL.")
        if (isPrivateHost(url)) return refuse("Private and local addresses cannot be fetched.")

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
            .build()

        return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> VmResult.Success(readable(result.value).take(MAX_CHARS).let { text ->
                if (text.length == MAX_CHARS) "$text\n\n[truncated at $MAX_CHARS characters]" else text
            })
        }
    }

    private fun refuse(reason: String): VmResult<String> =
        VmResult.Failure(VmError.Network(summary = "Cannot fetch this URL", reason = reason))

    private companion object {
        const val MAX_CHARS = 20_000
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) XCodes/1.0"
    }
}

/** True for loopback, link-local, private-range and `.local` hosts. */
internal fun isPrivateHost(url: HttpUrl): Boolean {
    val host = url.host.lowercase().trim('[', ']')
    return when {
        host == "localhost" || PRIVATE_SUFFIXES.any(host::endsWith) -> true
        // IPv6 literal: loopback, unique-local (fc00::/7) and link-local (fe80::/10).
        host.contains(':') -> host == "::1" || PRIVATE_V6_PREFIXES.any(host::startsWith)
        else -> isPrivateIpv4(host)
    }
}

private fun isPrivateIpv4(host: String): Boolean {
    val octets = host.split('.').mapNotNull { it.toIntOrNull() }
    if (octets.size != IPV4_OCTETS) return false
    return PRIVATE_V4_RANGES.any { (first, second) -> octets[0] == first && octets[1] in second }
}

/**
 * Turns an HTML page into plain text a model can read: drops scripts, styles and
 * markup, keeps line structure, decodes common entities. Non-HTML bodies (JSON,
 * plain text) pass through unchanged.
 */
internal fun readable(body: String): String {
    if (!body.contains("<html", ignoreCase = true) && !body.contains("<body", ignoreCase = true)) {
        return body.trim()
    }
    val title = TITLE.find(body)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()
    val text = body
        .replace(INVISIBLE_BLOCKS, " ")
        .replace(BLOCK_BREAKS, "\n")
        .replace(TAGS, " ")
        .let(::decodeEntities)
        .lines()
        .map { it.replace(SPACES, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
    return if (title.isNullOrEmpty()) text else "# $title\n\n$text"
}

private fun decodeEntities(text: String): String = text
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

private const val IPV4_OCTETS = 4
private val PRIVATE_SUFFIXES = listOf(".local", ".internal", ".localhost")
private val PRIVATE_V6_PREFIXES = listOf("fc", "fd", "fe80")

/** First octet and allowed second-octet range of each private or reserved IPv4 block. */
private val PRIVATE_V4_RANGES: List<Pair<Int, IntRange>> = listOf(
    0 to 0..255,
    10 to 0..255,
    127 to 0..255,
    100 to 64..127,
    169 to 254..254,
    172 to 16..31,
    192 to 168..168,
)
private val TITLE = Regex(
    "<title[^>]*>(.*?)</title>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val INVISIBLE_BLOCKS = Regex(
    "<(script|style|noscript|svg|head)[^>]*>.*?</\\1>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val BLOCK_BREAKS = Regex(
    "<(br|/p|/div|/li|/h[1-6]|/tr|/pre|/section|/article)[^>]*>",
    RegexOption.IGNORE_CASE,
)
private val TAGS = Regex("<[^>]+>")
private val SPACES = Regex("[ \\t\\u00A0]+")
