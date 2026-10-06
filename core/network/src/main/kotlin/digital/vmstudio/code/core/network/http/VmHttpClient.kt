package digital.vmstudio.code.core.network.http

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.MODEL_PROBLEM_SUMMARY
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.net.NetworkMonitor
import digital.vmstudio.code.core.common.result.VmResult
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Retry policy for a request. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoffMillis: Long = 500,
    val maxBackoffMillis: Long = 8_000,
) {
    companion object {
        /** Streaming requests are not retried: a partial response was already shown. */
        val NONE = RetryPolicy(maxAttempts = 1)
    }
}

/**
 * The app's HTTP layer.
 *
 * Thin on purpose. Retrofit buys little for an API whose main operation is a
 * streaming, tool-calling POST, and direct OkHttp keeps full control over SSE
 * framing, cancellation and error mapping — the three things that actually matter
 * here.
 */
@Singleton
class VmHttpClient @Inject constructor(
    private val client: OkHttpClient,
    private val networkMonitor: NetworkMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Executes a request, retrying transient failures with exponential backoff.
     *
     * Only 5xx, 408 and 429 are retried. Retrying a 4xx would repeat a request the
     * server has already rejected on its merits, and retrying a 401 can lock an
     * account out.
     */
    suspend fun execute(
        request: Request,
        policy: RetryPolicy = RetryPolicy(),
    ): VmResult<String> = withContext(ioDispatcher) {
        offlineCheck()?.let { return@withContext VmResult.Failure(it) }

        var lastError: VmError? = null
        repeat(policy.maxAttempts) { attempt ->
            try {
                val response = runInterruptible { client.newCall(request).execute() }
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) return@withContext VmResult.Success(body)

                    val error = mapHttpError(it, body, request)
                    if (!it.isRetryable(request)) return@withContext VmResult.Failure(error)
                    lastError = error
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val error = mapTransportError(throwable, request)
                if (!error.retryable || !transportErrorIsSafeToRetry(throwable, request)) {
                    return@withContext VmResult.Failure(error)
                }
                lastError = error
            }

            if (attempt < policy.maxAttempts - 1) {
                delay(backoffFor(attempt, policy))
            }
        }

        VmResult.Failure(lastError ?: VmError.Network(summary = "Request failed"))
    }

    /**
     * Streams a Server-Sent Events response line by line.
     *
     * Not retried: by the time a stream fails, partial output has usually reached the
     * user, and silently restarting would duplicate it.
     */
    fun stream(request: Request): Flow<SseEvent> = flow {
        offlineCheck()?.let { throw VmHttpException(it) }

        val response = runInterruptible { client.newCall(request).execute() }
        response.use {
            if (!it.isSuccessful) {
                throw VmHttpException(mapHttpError(it, it.body?.string().orEmpty(), request))
            }

            val source = it.body?.source() ?: throw VmHttpException(
                VmError.Network(
                    summary = "Empty response",
                    reason = "The server returned no body for a streaming request.",
                    endpoint = request.url.redactedForDisplay(),
                ),
            )

            val decoder = SseDecoder()
            while (true) {
                val line = runInterruptible { source.readUtf8Line() } ?: break
                val event = decoder.decode(line) ?: continue
                if (event.isDone) return@use
                emit(event)
            }
            decoder.finish()?.takeIf { event -> !event.isDone }?.let { event -> emit(event) }
        }
    }.flowOn(ioDispatcher)

    /**
     * Refuses before dialling when the device is offline.
     *
     * The specification requires the app never to pretend a remote operation
     * succeeded; failing fast with a specific offline error also beats a 30-second
     * DNS timeout the user has to sit through.
     */
    private suspend fun offlineCheck(): VmError? {
        val online = networkMonitor.isOnlineNow() ?: networkMonitor.isOnline.first()
        return if (online) null else VmError.Offline()
    }

    /**
     * A non-idempotent request (a POST that may have been processed before the
     * failure response) is only retried on 429, where the server is explicitly
     * saying it did not act and to back off. A 5xx after a POST could mean the
     * write already landed, so resending it risks a duplicate. Idempotent methods
     * retry on the full transient set.
     */
    private fun Response.isRetryable(request: Request): Boolean {
        if (code == 429) return true
        val idempotent = request.method in IDEMPOTENT_METHODS
        return idempotent && (code >= 500 || code == 408)
    }

    /**
     * For a transport-level failure, retrying a non-idempotent request is only safe
     * when the request provably never reached the server. An unresolved host is the
     * one clear case; a timeout or reset may have occurred after the server received
     * and acted on the bytes.
     */
    private fun transportErrorIsSafeToRetry(throwable: Throwable, request: Request): Boolean {
        if (request.method in IDEMPOTENT_METHODS) return true
        return throwable is UnknownHostException
    }

    private fun backoffFor(attempt: Int, policy: RetryPolicy): Long {
        val exponential = policy.initialBackoffMillis * 2.0.pow(attempt).toLong()
        // Jitter so several failed requests do not resynchronise into a thundering
        // herd against a server that is already struggling.
        val jitter = (0..(exponential / 4).toInt().coerceAtLeast(1)).random().toLong()
        return (exponential + jitter).coerceAtMost(policy.maxBackoffMillis)
    }

    private fun mapHttpError(response: Response, body: String, request: Request): VmError {
        val endpoint = request.url.redactedForDisplay()
        // The gateway's own sentence, not the JSON around it: the chat showed
        // {"error":{"type":"server_error","message":"…"}} verbatim.
        val excerpt = (gatewayMessage(body) ?: body).take(400).takeIf { it.isNotBlank() }
        val modelProblem = excerpt?.let(::mentionsModelProblem) == true

        return when (response.code) {
            401, 403 -> VmError.Authentication(
                summary = "The AI provider rejected the credentials",
                reason = excerpt ?: "Status ${response.code}.",
                suggestedAction = "Check the API key in Settings, and that it is valid " +
                    "for this endpoint.",
                realm = endpoint,
            )

            404 -> VmError.Network(
                summary = "Endpoint not found",
                reason = "The server has no handler at this path.",
                suggestedAction = "Check the base URL. A gateway that only implements " +
                    "the OpenAI API will not answer Anthropic paths, and the reverse.",
                retryable = false,
                statusCode = 404,
                endpoint = endpoint,
            )

            429 -> VmError.Network(
                summary = "Rate limited",
                reason = excerpt ?: "The provider is throttling requests.",
                suggestedAction = "Wait a moment and try again.",
                retryable = true,
                statusCode = 429,
                endpoint = endpoint,
            )

            in 500..599 -> VmError.Network(
                summary = if (modelProblem) MODEL_PROBLEM_SUMMARY else "The provider is unavailable",
                reason = if (response.code == 502) {
                    "The gateway could not reach its upstream service."
                } else {
                    excerpt ?: "Status ${response.code}."
                },
                suggestedAction = if (modelProblem) {
                    MODEL_PROBLEM_ACTION
                } else {
                    "This is a problem on the server side. Check the gateway is running and try again."
                },
                retryable = true,
                statusCode = response.code,
                endpoint = endpoint,
            )

            else -> VmError.Network(
                summary = if (modelProblem) MODEL_PROBLEM_SUMMARY else "Request failed",
                reason = excerpt ?: "Status ${response.code}.",
                suggestedAction = if (modelProblem) MODEL_PROBLEM_ACTION else null,
                retryable = false,
                statusCode = response.code,
                endpoint = endpoint,
            )
        }
    }

    /** The `error.message` of an OpenAI- or Anthropic-style error body, or null. */
    private fun gatewayMessage(body: String): String? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val error = root["error"]
        val message = ((error as? JsonObject)?.get("message") ?: root["message"]) as? JsonPrimitive
        return message?.takeIf { it.isString }?.content ?: (error as? JsonPrimitive)?.content
    }

    private fun mapTransportError(throwable: Throwable, request: Request): VmError {
        val endpoint = request.url.redactedForDisplay()
        return when (throwable) {
            is UnknownHostException -> VmError.Network(
                summary = "Host not found",
                reason = "${request.url.host} could not be resolved.",
                suggestedAction = "Check the base URL for typos, and your connection.",
                retryable = true,
                endpoint = endpoint,
            )

            is SocketTimeoutException -> VmError.Network(
                summary = "The provider timed out",
                reason = "No response within the request timeout.",
                suggestedAction = "The provider may be overloaded. Try again.",
                retryable = true,
                endpoint = endpoint,
            )

            // Android refuses plain http:// unless the app opts in; saying so beats a
            // generic "check your connection" that retrying can never fix.
            is UnknownServiceException -> VmError.Network(
                summary = "Plain http:// is blocked",
                reason = "Android only allows encrypted connections to ${request.url.host}.",
                suggestedAction = "Use the gateway's https:// address.",
                retryable = false,
                endpoint = endpoint,
                cause = throwable,
            )

            is SSLHandshakeException -> VmError.Network(
                summary = "Secure connection failed",
                reason = throwable.message,
                suggestedAction = "Check that the address is https:// and the server's " +
                    "certificate is valid.",
                retryable = false,
                endpoint = endpoint,
                cause = throwable,
            )

            is ConnectException -> VmError.Network(
                summary = "Could not reach the gateway",
                reason = "Nothing answered at ${request.url.host}:${request.url.port}.",
                suggestedAction = "Check the address and port, and that the gateway is running.",
                retryable = true,
                endpoint = endpoint,
                cause = throwable,
            )

            is IOException -> VmError.Network(
                summary = "Connection failed",
                reason = throwable.message,
                suggestedAction = "Check your connection and try again.",
                retryable = true,
                endpoint = endpoint,
                cause = throwable,
            )

            else -> VmError.Unexpected(reason = throwable.message, cause = throwable)
        }
    }
}

private val IDEMPOTENT_METHODS = setOf("GET", "HEAD", "OPTIONS", "PUT", "DELETE")

/** Carries a structured error out of a streaming flow, where returns are unavailable. */
class VmHttpException(val error: VmError) : IOException(error.summary)

/**
 * Endpoint string for display and logs, without query parameters.
 *
 * Some gateways accept an API key as a query parameter; including the query in an
 * error message would write that key into the diagnostics buffer.
 */
private fun okhttp3.HttpUrl.redactedForDisplay(): String =
    "$scheme://$host${if (port != -1 && port != 443 && port != 80) ":$port" else ""}$encodedPath"

private const val MODEL_PROBLEM_ACTION =
    "Free models come and go on the provider's side. Pick another model (tap the model name below)."

/** The gateway is saying the chosen model (or combo) is gone, unknown or down. */
private fun mentionsModelProblem(message: String): Boolean {
    val text = message.lowercase()
    return "unavailable" in text || "not a valid" in text || "no such model" in text ||
        ("unknown" in text && "model" in text) || "unknown built-in" in text
}
