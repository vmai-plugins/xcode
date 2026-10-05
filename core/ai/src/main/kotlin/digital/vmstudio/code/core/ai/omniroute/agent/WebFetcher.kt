package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the agent's `web_fetch` tool: downloads a public page from the device and
 * returns its readable text.
 *
 * Fetched from the phone, not the server, so it works even when the server has no
 * outbound access. The model chooses the URL, so it must not be able to reach the
 * user's home network through the phone: private hosts are refused by name, every
 * DNS answer is checked (a public name can resolve to a private address), and
 * redirects are followed by hand so each hop gets the same checks.
 */
@Singleton
class WebFetcher @Inject constructor(
    baseClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val client = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(PublicOnlyDns)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(rawUrl: String): VmResult<String> = withContext(ioDispatcher) {
        var url = rawUrl.trim().toHttpUrlOrNull()
            ?: return@withContext refuse("\"$rawUrl\" is not an http or https URL.")

        repeat(MAX_REDIRECTS + 1) {
            if (isPrivateHost(url)) return@withContext refuse(PRIVATE_REFUSAL)
            val result = try {
                runInterruptible { client.newCall(request(url)).execute() }.use { response ->
                    when {
                        response.isRedirect -> response.header("Location")?.let(url::resolve)
                            ?: return@withContext refuse("The page redirected without a location.")
                        !response.isSuccessful ->
                            return@withContext refuse("The server answered HTTP ${response.code}.")
                        else -> return@withContext VmResult.Success(readable(readCapped(response)).limited())
                    }
                }
            } catch (blocked: PrivateAddressException) {
                return@withContext refuse(blocked.message)
            } catch (io: IOException) {
                return@withContext refuse(io.message ?: "The page could not be reached.")
            }
            url = result
        }
        refuse("Too many redirects.")
    }

    private fun request(url: HttpUrl) = Request.Builder()
        .url(url)
        .get()
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
        .build()

    /** Reads at most [MAX_BODY_BYTES]: a huge page is cut, not loaded whole into memory. */
    private fun readCapped(response: Response): String {
        val source = response.body?.source() ?: return ""
        source.request(MAX_BODY_BYTES)
        val buffer = source.buffer
        return buffer.readUtf8(minOf(buffer.size, MAX_BODY_BYTES))
    }

    private fun String.limited(): String =
        if (length <= MAX_CHARS) this else take(MAX_CHARS) + "\n\n[truncated at $MAX_CHARS characters]"

    private fun refuse(reason: String?): VmResult<String> =
        VmResult.Failure(VmError.Network(summary = "Cannot fetch this URL", reason = reason))

    private companion object {
        const val MAX_CHARS = 20_000
        const val PRIVATE_REFUSAL = "Private and local addresses cannot be fetched."
        const val MAX_BODY_BYTES = 1L * 1024 * 1024
        const val MAX_REDIRECTS = 5
        const val CALL_TIMEOUT_SECONDS = 30L
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) XCodes/1.0"
    }
}

private class PrivateAddressException(host: String) :
    IOException("$host resolves to a private or local address and cannot be fetched.")

/** Resolves normally, then refuses the host if any address it maps to is private. */
private object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.any(::isPrivateAddress)) throw PrivateAddressException(hostname)
        return addresses
    }
}

internal fun isPrivateAddress(address: InetAddress): Boolean {
    val bytes = address.address
    val first = bytes[0].toInt() and BYTE_MASK
    val second = bytes.getOrNull(1)?.toInt()?.and(BYTE_MASK) ?: 0
    return address.isLoopbackAddress ||
        address.isAnyLocalAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress ||
        // Carrier-grade NAT (100.64.0.0/10) and IPv6 unique-local (fc00::/7).
        (bytes.size == IPV4_OCTETS && first == CGNAT_FIRST && second in CGNAT_SECOND) ||
        (bytes.size > IPV4_OCTETS && (first and ULA_MASK) == ULA_PREFIX)
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
private const val BYTE_MASK = 0xFF
private const val CGNAT_FIRST = 100
private val CGNAT_SECOND = 64..127
private const val ULA_MASK = 0xFE
private const val ULA_PREFIX = 0xFC
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
