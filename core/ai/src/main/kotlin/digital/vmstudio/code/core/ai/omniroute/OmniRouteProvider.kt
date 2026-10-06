package digital.vmstudio.code.core.ai.omniroute

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.model.ClaudeCodeModels
import digital.vmstudio.code.core.ai.model.ConversationTurn
import digital.vmstudio.code.core.ai.omniroute.agent.OmniRouteAgentLoop
import digital.vmstudio.code.core.ai.omniroute.agent.OmniRouteTurn
import digital.vmstudio.code.core.ai.omniroute.agent.gatewayErrorOf
import digital.vmstudio.code.core.ai.provider.AiProvider
import digital.vmstudio.code.core.ai.provider.AiProviderHealth
import digital.vmstudio.code.core.ai.provider.AiProviderKind
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.network.http.VmHttpException
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Talks to an OmniRoute-style HTTP gateway directly from the device.
 *
 * By default this is a plain assistant: it streams model replies and cannot touch
 * files or run commands. When the user explicitly turns on tool use in Settings
 * (off by default - reliability depends entirely on which model the gateway routes
 * to) and a working directory is set, [run] instead delegates to [OmniRouteAgentLoop],
 * which gives it the same four tools (read/list/write a file, run a command) Claude
 * Code CLI's own tools reach, gated by the same command-safety and file-edit approval
 * the rest of the app uses. Chat mode remains the fallback whenever tool use is off
 * or there is nowhere for a tool to act - no SSH server configured, or a quick
 * question that does not warrant starting a run.
 */
@Singleton
class OmniRouteProvider @Inject constructor(
    private val httpClient: VmHttpClient,
    private val credentialStore: SecureCredentialStore,
    private val preferences: UserPreferencesSource,
    private val agentLoop: OmniRouteAgentLoop,
) : AiProvider {

    override val kind: AiProviderKind = AiProviderKind.OMNIROUTE

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * The dialect found for a base URL. Keyed by URL so switching gateways never
     * reuses the old answer, and cached so a connection test does not send two
     * billed probe requests every time.
     */
    @Volatile
    private var detected: Pair<String, OmniRouteDialect>? = null

    private suspend fun dialectFor(baseUrl: String, key: Secret): OmniRouteDialect {
        detected?.let { (url, dialect) -> if (url == baseUrl) return dialect }
        return detectDialect(baseUrl, key).also { dialect ->
            detected = if (dialect == OmniRouteDialect.UNKNOWN) null else baseUrl to dialect
        }
    }

    override suspend fun checkHealth(serverId: String?): VmResult<AiProviderHealth> {
        val settings = preferences.preferences.first()
        val baseUrl = normalizeBaseUrl(settings.aiBaseUrl)
        if (baseUrl.isEmpty()) {
            return VmResult.Success(
                AiProviderHealth(
                    kind = kind,
                    isAvailable = false,
                    endpoint = baseUrl,
                    diagnosis = "No valid gateway URL is set. Add your OmniRoute address " +
                        "(like https://ai.example.com) in Settings.",
                ),
            )
        }

        val key = resolveApiKey(settings.aiApiKeyCredentialId)
            ?: return VmResult.Success(
                AiProviderHealth(
                    kind = kind,
                    isAvailable = false,
                    endpoint = baseUrl,
                    diagnosis = "No API key is stored. Add one in Settings; it is kept in " +
                        "the Keystore-backed credential store, never in preferences.",
                ),
            )

        val startedAt = System.currentTimeMillis()
        return try {
            val models = fetchModels(baseUrl, key)
            val latency = System.currentTimeMillis() - startedAt

            when (models) {
                is VmResult.Failure -> VmResult.Success(
                    AiProviderHealth(
                        kind = kind,
                        isAvailable = false,
                        endpoint = baseUrl,
                        latencyMillis = latency,
                        diagnosis = models.error.reason ?: models.error.summary,
                    ),
                )

                is VmResult.Success -> {
                    val dialect = dialectFor(baseUrl, key)
                    VmResult.Success(
                        AiProviderHealth(
                            kind = kind,
                            isAvailable = dialect != OmniRouteDialect.UNKNOWN,
                            endpoint = baseUrl,
                            isAuthenticated = true,
                            latencyMillis = latency,
                            availableModels = models.value,
                            version = dialect.displayName,
                            diagnosis = if (dialect == OmniRouteDialect.UNKNOWN) {
                                "The gateway answered but did not respond to either the " +
                                    "Anthropic or the OpenAI chat endpoint. Check which API " +
                                    "it implements."
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        } finally {
            key.wipe()
        }
    }

    override fun run(config: AgentRunConfig): Flow<AgentEvent> = flow {
        val settings = preferences.preferences.first()
        val baseUrl = normalizeBaseUrl(settings.aiBaseUrl)
        // A Claude Code alias means nothing to the gateway; fall back to the model
        // chosen for OmniRoute rather than sending an id it will reject.
        val model = config.model?.takeUnless { it in ClaudeCodeModels.ALIASES }
            ?: settings.aiSelectedModelId

        if (model.isNullOrBlank()) {
            emit(
                AgentEvent.Failed(
                    VmError.Ai(
                        summary = "No model selected",
                        reason = "This provider needs an explicit model.",
                        suggestedAction = "Choose one in Settings after running the " +
                            "connection test.",
                        retryable = false,
                        provider = "omniroute",
                    ),
                ),
            )
            return@flow
        }

        val key = resolveApiKey(settings.aiApiKeyCredentialId)
        if (key == null) {
            emit(
                AgentEvent.Failed(
                    VmError.Authentication(
                        summary = "No API key is stored",
                        suggestedAction = "Add the gateway's API key in Settings.",
                    ),
                ),
            )
            return@flow
        }

        val dialect = dialectFor(baseUrl, key)

        // The agent loop needs somewhere to act and the user's consent (the Tool
        // use switch, on by default); otherwise this is a plain chat.
        val canUseTools = settings.aiToolsEnabled &&
            config.serverId.isNotBlank() &&
            config.workingDirectory.isNotBlank()
        if (canUseTools) {
            try {
                emitAll(agentLoop.run(baseUrl, key, dialect, model, config, settings.agentAutonomyLevel))
            } finally {
                key.wipe()
            }
            return@flow
        }

        try {
            val request = buildChatRequest(baseUrl, key, dialect, model, plainChatMessages(config))
            val text = StringBuilder()
            var inputTokens = 0L
            var outputTokens = 0L
            val startedAt = System.currentTimeMillis()
            var streamError: OmniRouteTurn.GatewayError? = null

            httpClient.stream(request).collect { event ->
                // An error object inside a 200 stream used to end as an empty reply.
                (runCatching { json.parseToJsonElement(event.data) }.getOrNull() as? JsonObject)
                    ?.let(::gatewayErrorOf)?.let { streamError = it }
                // Shown as it arrives, like the agent's replies, instead of all at
                // once at the end behind "Thinking…".
                ChatStreamDecoder.textDelta(dialect, event.data)?.takeIf { it.isNotEmpty() }?.let { delta ->
                    text.append(delta)
                    emit(AgentEvent.AssistantDelta(delta))
                }
                ChatStreamDecoder.usage(event.data)?.let { (input, output) ->
                    if (input > 0) inputTokens = input
                    if (output > 0) outputTokens = output
                }
            }

            if (text.isNotEmpty()) emit(AgentEvent.AssistantMessage(text.toString()))
            streamError?.let { error ->
                emit(
                    AgentEvent.Failed(
                        VmError.Ai(
                            summary = "The gateway reported an error",
                            reason = error.message,
                            retryable = error.retryable,
                            provider = "omniroute",
                        ),
                    ),
                )
                return@flow
            }
            emit(
                AgentEvent.Completed(
                    sessionId = null,
                    resultText = text.toString().takeIf { it.isNotEmpty() },
                    durationMillis = System.currentTimeMillis() - startedAt,
                    // The gateway bills upstream; the app does not attempt to price
                    // a request it cannot see the rate card for.
                    costUsd = 0.0,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    isError = false,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (httpFailure: VmHttpException) {
            emit(AgentEvent.Failed(httpFailure.error))
        } catch (throwable: Throwable) {
            emit(
                AgentEvent.Failed(
                    VmError.Ai(
                        summary = "The provider request failed",
                        reason = throwable.message,
                        provider = "omniroute",
                        cause = throwable,
                    ),
                ),
            )
        } finally {
            key.wipe()
        }
    }

    /**
     * Fetches the gateway's current model list, for the live model picker.
     *
     * Separate from [checkHealth] so a model refresh costs one `GET /v1/models`
     * and not the dialect probes a full health check sends.
     */
    suspend fun listModels(): VmResult<List<String>> {
        val settings = preferences.preferences.first()
        val baseUrl = normalizeBaseUrl(settings.aiBaseUrl)
        if (baseUrl.isEmpty()) {
            return VmResult.Failure(
                VmError.Ai(
                    summary = "No gateway URL is set",
                    suggestedAction = "Add your OmniRoute address in Settings.",
                    retryable = false,
                    provider = "omniroute",
                ),
            )
        }
        val key = resolveApiKey(settings.aiApiKeyCredentialId)
            ?: return VmResult.Failure(
                VmError.Authentication(
                    summary = "No API key is stored",
                    suggestedAction = "Add the gateway's API key in Settings.",
                ),
            )
        return try {
            fetchModels(baseUrl, key)
        } finally {
            key.wipe()
        }
    }

    /**
     * Everything a server-side run needs to call the gateway itself: the address,
     * the wire dialect and the key. The caller owns [GatewayAccess.key] and must
     * wipe it.
     */
    suspend fun gatewayAccess(): VmResult<GatewayAccess> {
        val settings = preferences.preferences.first()
        val baseUrl = normalizeBaseUrl(settings.aiBaseUrl)
        if (baseUrl.isEmpty()) {
            return VmResult.Failure(
                VmError.Ai(
                    summary = "No gateway URL is set",
                    suggestedAction = "Add your OmniRoute address in Settings.",
                    retryable = false,
                    provider = "omniroute",
                ),
            )
        }
        val key = resolveApiKey(settings.aiApiKeyCredentialId)
            ?: return VmResult.Failure(
                VmError.Authentication(
                    summary = "No API key is stored",
                    suggestedAction = "Add the gateway's API key in Settings.",
                ),
            )
        val dialect = dialectFor(baseUrl, key)
        if (dialect == OmniRouteDialect.UNKNOWN) {
            key.wipe()
            return VmResult.Failure(
                VmError.Ai(
                    summary = "The gateway's API type could not be detected",
                    suggestedAction = "Run the connection test in Settings.",
                    provider = "omniroute",
                ),
            )
        }
        return VmResult.Success(GatewayAccess(baseUrl, dialect, key))
    }

    // --- internals ---------------------------------------------------------------

    private suspend fun resolveApiKey(credentialId: String?): Secret? {
        val id = credentialId ?: return null
        return when (val result = credentialStore.read(id)) {
            is VmResult.Success -> result.value.registerForRedaction()
            is VmResult.Failure -> null
        }
    }

    private suspend fun fetchModels(baseUrl: String, key: Secret): VmResult<List<String>> {
        // Try the OpenAI-style bearer header first, then the Anthropic-style
        // x-api-key only if that is rejected. Sending both at once would hand the
        // key, in two forms, to an endpoint whose identity is not yet confirmed —
        // and a mistyped base URL is exactly when that matters.
        // A large page size, and paging when the gateway says there is more: an
        // Anthropic-style list returns 20 per page by default, and only the first
        // page was ever read.
        fun request(dialect: OmniRouteDialect, after: String?) = Request.Builder()
            .url(
                "$baseUrl/v1/models?limit=$MODELS_PAGE_SIZE" +
                    after?.let { "&after_id=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty(),
            )
            .get()
            .applyOmniRouteAuth(key, dialect)
            .build()

        val retry = RetryPolicy(maxAttempts = 2)
        val bearer = httpClient.execute(request(OmniRouteDialect.OPENAI_CHAT, null), retry)
        val dialect = if (bearer is VmResult.Failure && bearer.error is VmError.Authentication) {
            OmniRouteDialect.ANTHROPIC_MESSAGES
        } else {
            OmniRouteDialect.OPENAI_CHAT
        }
        var page = if (dialect == OmniRouteDialect.OPENAI_CHAT) {
            bearer
        } else {
            httpClient.execute(request(dialect, null), retry)
        }
        val ids = mutableListOf<String>()
        repeat(MAX_MODEL_PAGES) {
            val body = when (val current = page) {
                is VmResult.Failure -> return if (ids.isEmpty()) current else VmResult.Success(ids.distinct())
                is VmResult.Success -> current.value
            }
            ids += ChatStreamDecoder.parseModels(body)
            val cursor = ChatStreamDecoder.nextModelsCursor(body) ?: return VmResult.Success(ids.distinct())
            page = httpClient.execute(request(dialect, cursor), retry)
        }
        return VmResult.Success(ids.distinct())
    }

    /**
     * Determines the dialect by asking each endpoint to do the smallest possible
     * thing and seeing which one answers.
     *
     * A 404 rules a dialect out; anything else — including a 400 about the body —
     * means the route exists, which is what is being tested.
     */
    private suspend fun detectDialect(baseUrl: String, key: Secret): OmniRouteDialect {
        suspend fun probe(dialect: OmniRouteDialect): Boolean {
            val body = buildJsonObject {
                put("model", "probe")
                put("max_tokens", 1)
                put("stream", false)
                put(
                    "messages",
                    json.parseToJsonElement("""[{"role":"user","content":"ping"}]"""),
                )
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/${dialect.chatPath}")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .applyOmniRouteAuth(key, dialect)
                .build()

            return when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 1))) {
                is VmResult.Success -> true
                is VmResult.Failure -> {
                    val status = (result.error as? VmError.Network)?.statusCode
                    status != null && status != 404
                }
            }
        }

        return when {
            probe(OmniRouteDialect.ANTHROPIC_MESSAGES) -> OmniRouteDialect.ANTHROPIC_MESSAGES
            probe(OmniRouteDialect.OPENAI_CHAT) -> OmniRouteDialect.OPENAI_CHAT
            else -> OmniRouteDialect.UNKNOWN
        }.also {
            VmLog.i(LogCategory.AI, TAG, "Gateway dialect detected: ${it.displayName}")
        }
    }

    private fun buildChatRequest(
        baseUrl: String,
        key: Secret,
        dialect: OmniRouteDialect,
        model: String,
        turns: List<ConversationTurn>,
    ): Request {
        val messages = JsonArray(
            turns.map { turn ->
                buildJsonObject {
                    put("role", if (turn.fromUser) "user" else "assistant")
                    put("content", turn.text)
                }
            },
        )

        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", messages)
            // Required by the Anthropic API and harmless to OpenAI, which treats it
            // as an optional cap.
            put("max_tokens", DEFAULT_MAX_TOKENS)
        }.toString()

        return Request.Builder()
            .url("$baseUrl/${dialect.chatPath}")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .applyOmniRouteAuth(key, dialect)
            .header("Accept", "text/event-stream")
            .build()
    }

    private companion object {
        const val TAG = "OmniRouteProvider"
        const val DEFAULT_MAX_TOKENS = 8192
        const val MODELS_PAGE_SIZE = 1000
        const val MAX_MODEL_PAGES = 20
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * The conversation for a chat without tools: earlier turns, then the prompt. It
 * used to send only the prompt, so every follow-up started from nothing. Turns from
 * the same side are merged, because the Anthropic dialect requires the roles to
 * alternate.
 */
internal fun plainChatMessages(config: AgentRunConfig): List<ConversationTurn> =
    (config.history + ConversationTurn(fromUser = true, text = config.prompt)).alternating()

/**
 * Drops blank turns, merges neighbours from the same side (the Anthropic dialect
 * requires roles to alternate: a reply split around tool calls, or a prompt that
 * never got an answer, broke that) and starts with the user, as both dialects expect.
 */
internal fun List<ConversationTurn>.alternating(): List<ConversationTurn> {
    val merged = mutableListOf<ConversationTurn>()
    filter { it.text.isNotBlank() }.forEach { turn ->
        val last = merged.lastOrNull()
        if (last != null && last.fromUser == turn.fromUser) {
            merged[merged.lastIndex] = last.copy(text = last.text + "\n\n" + turn.text)
        } else {
            merged += turn
        }
    }
    return merged.dropWhile { !it.fromUser }
}

/**
 * Accepts the gateway address however it was typed: trailing slashes and a
 * trailing `/v1` are dropped, because every path this provider builds already
 * starts with `v1/`. Without this, `https://host/v1` produced `/v1/v1/models`.
 */
internal fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/').removeSuffix("/v1").trimEnd('/')
    if (trimmed.isEmpty()) return ""
    // "192.168.1.5:20128" or "ai.example.com" typed without a scheme made OkHttp
    // throw on every request, which crashed the app on each launch.
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    return withScheme.takeIf { it.toHttpUrlOrNull() != null }.orEmpty()
}

/** Gateway connection details handed to a server-side run. */
class GatewayAccess(val baseUrl: String, val dialect: OmniRouteDialect, val key: Secret)
