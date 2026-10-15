package digital.vmstudio.code.core.ai.omniroute

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentRunConfig
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
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.network.http.VmHttpException
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
    private val wakeLockManager: digital.vmstudio.code.core.ai.background.AgentWakeLockManager,
) : AiProvider {

    override val kind: AiProviderKind = AiProviderKind.OMNIROUTE

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * The dialect found for a base URL. Keyed by URL so switching gateways never
     * reuses the old answer, and cached so a connection test does not send two
     * billed probe requests every time.
     */
    private val dialectCache = java.util.concurrent.ConcurrentHashMap<String, OmniRouteDialect>()

    private suspend fun dialectFor(
        baseUrl: String,
        key: Secret,
        preferredModel: String? = null,
    ): OmniRouteDialect {
        dialectCache[baseUrl]?.let { return it }
        return detectDialect(baseUrl, key, preferredModel).also { dialect ->
            if (dialect != OmniRouteDialect.UNKNOWN) {
                dialectCache[baseUrl] = dialect
            }
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

        val key = resolveApiKey(settings.aiApiKeyCredentialId, baseUrl)
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
            val modelsResult = fetchModels(baseUrl, key)
            val latency = System.currentTimeMillis() - startedAt

            when (modelsResult) {
                is VmResult.Failure -> VmResult.Success(
                    AiProviderHealth(
                        kind = kind,
                        isAvailable = false,
                        endpoint = baseUrl,
                        latencyMillis = latency,
                        diagnosis = modelsResult.error.reason ?: modelsResult.error.summary,
                    ),
                )

                is VmResult.Success -> {
                    val sortedIds = OmniRouteModelCatalog.prioritizeSmartModels(modelsResult.value.ids)
                    val freeIds = modelsResult.value.freeIds +
                        sortedIds.filter(GatewayModelPricing::isFreeModelId)
                    val dialect = if (sortedIds.any { it.startsWith("auto/") } ||
                        baseUrl.contains("vmstudio.digital", ignoreCase = true)
                    ) {
                        OmniRouteDialect.OPENAI_CHAT.also { dialectCache[baseUrl] = it }
                    } else {
                        val detected = dialectFor(baseUrl, key, sortedIds.firstOrNull())
                        if (detected == OmniRouteDialect.UNKNOWN && sortedIds.isNotEmpty()) {
                            OmniRouteDialect.OPENAI_CHAT.also { dialectCache[baseUrl] = it }
                        } else {
                            detected
                        }
                    }
                    VmResult.Success(
                        AiProviderHealth(
                            kind = kind,
                            isAvailable = dialect != OmniRouteDialect.UNKNOWN,
                            endpoint = baseUrl,
                            isAuthenticated = true,
                            latencyMillis = latency,
                            availableModels = sortedIds,
                            freeModels = freeIds,
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
        val model = config.model ?: settings.aiSelectedModelId

        if (baseUrl.isEmpty()) {
            emit(noBaseUrlError())
            return@flow
        }

        if (model.isNullOrBlank()) {
            emit(noModelSelectedError())
            return@flow
        }

        val key = resolveApiKey(settings.aiApiKeyCredentialId, baseUrl)
        if (key == null) {
            emit(noApiKeyError())
            return@flow
        }

        if (baseUrl.contains("vmstudio.digital", ignoreCase = true) || model.startsWith("auto/")) {
            dialectCache.putIfAbsent(baseUrl, OmniRouteDialect.OPENAI_CHAT)
        }
        val dialect = dialectFor(baseUrl, key, model)

        val isGitHubWorkspace = config.workingDirectory.startsWith("/github/")
        val isVmHubWorkspace = config.workingDirectory.startsWith("/vmhub/")
        val isLocalWorkspace = digital.vmstudio.code.core.sftp.fs.RemoteFileSystem.isLocalServer(config.serverId)
        val canUseTools = settings.aiToolsEnabled &&
            config.workingDirectory.isNotBlank() &&
            (config.serverId.isNotBlank() || isGitHubWorkspace || isVmHubWorkspace || isLocalWorkspace)

        // auto/best-free is a conversational combo where targets do not guarantee tool calling.
        // For tool-assisted workspace runs, use auto/coding:free (the 100% free tool-capable coding combo).
        val effectiveModel = if (canUseTools && (model == "auto/best-free" || model == "best-free")) {
            if (settings.aiAvailableModelIds.contains("auto/coding:free")) "auto/coding:free" else "auto/best-coding"
        } else {
            model
        }
        val effectiveFallback = selectFallbackModel(effectiveModel, settings.aiAvailableModelIds, canUseTools)

        wakeLockManager.acquire()
        try {
            if (canUseTools) {
                runToolAgent(baseUrl, key, dialect, effectiveModel, effectiveFallback, config, settings.agentAutonomyLevel)
            } else {
                runPlainChat(baseUrl, key, dialect, model, effectiveFallback, config)
            }
        } finally {
            wakeLockManager.release()
            key.wipe()
        }
    }

    @Suppress("LongParameterList")
    private suspend fun FlowCollector<AgentEvent>.runToolAgent(
        baseUrl: String,
        key: Secret,
        dialect: OmniRouteDialect,
        model: String,
        fallbackModel: String?,
        config: AgentRunConfig,
        autonomyLevel: AgentAutonomyLevel,
    ) {
        var emittedAny = false
        var failureEvent: AgentEvent.Failed? = null
        agentLoop.run(baseUrl, key, dialect, model, config, autonomyLevel, fallbackModel).collect { event ->
            if (event is AgentEvent.Failed) {
                failureEvent = event
            } else {
                if (event !is AgentEvent.SessionStarted) emittedAny = true
                emit(event)
            }
        }
        failureEvent?.let { failure ->
            val status = (failure.error as? VmError.Network)?.statusCode
            if (isRecoverableFailure(status, failure.error) && !emittedAny && fallbackModel != null) {
                emit(
                    AgentEvent.AssistantDelta(
                        "\n[Model $model unavailable; routing via fallback $fallbackModel...]\n\n",
                    ),
                )
                emitAll(
                    agentLoop.run(
                        baseUrl,
                        key,
                        dialect,
                        fallbackModel,
                        config,
                        autonomyLevel,
                        fallbackModel = null,
                    ),
                )
            } else {
                emit(failure)
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun FlowCollector<AgentEvent>.runPlainChat(
        baseUrl: String,
        key: Secret,
        dialect: OmniRouteDialect,
        model: String,
        fallbackModel: String?,
        config: AgentRunConfig,
    ) {
        try {
            executeChatStream(baseUrl, key, dialect, model, config)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (httpFailure: VmHttpException) {
            val status = (httpFailure.error as? VmError.Network)?.statusCode
            if (isRecoverableFailure(status, httpFailure.error) && fallbackModel != null) {
                emit(
                    AgentEvent.AssistantDelta(
                        "\n[Model $model unavailable; routing via fallback $fallbackModel...]\n\n",
                    ),
                )
                try {
                    executeChatStream(baseUrl, key, dialect, fallbackModel, config)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (fallbackFailure: Throwable) {
                    VmLog.w(LogCategory.AI, TAG, "Fallback plain chat also failed", fallbackFailure)
                    emit(AgentEvent.Failed(httpFailure.error))
                }
            } else {
                emit(AgentEvent.Failed(httpFailure.error))
            }
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
        }
    }

    private suspend fun FlowCollector<AgentEvent>.executeChatStream(
        baseUrl: String,
        key: Secret,
        dialect: OmniRouteDialect,
        model: String,
        config: AgentRunConfig,
    ) {
        val turns = plainChatMessages(config)
        val request = buildChatRequest(baseUrl, key, dialect, model, turns, config.images, stream = true)
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var inputTokens = 0L
        var outputTokens = 0L
        val startedAt = System.currentTimeMillis()
        var streamError: OmniRouteTurn.GatewayError? = null

        try {
            httpClient.stream(request).collect { event ->
                (runCatching { json.parseToJsonElement(event.data) }.getOrNull() as? JsonObject)
                    ?.let(::gatewayErrorOf)?.let { streamError = it }
                ChatStreamDecoder.reasoningDelta(dialect, event.data)?.takeIf { it.isNotEmpty() }?.let { rDelta ->
                    reasoning.append(rDelta)
                }
                ChatStreamDecoder.textDelta(dialect, event.data)?.takeIf { it.isNotEmpty() }?.let { delta ->
                    if (reasoning.isNotEmpty()) {
                        emit(AgentEvent.Reasoning(reasoning.toString()))
                        reasoning.setLength(0)
                    }
                    text.append(delta)
                    emit(AgentEvent.AssistantDelta(delta))
                }
                ChatStreamDecoder.usage(event.data)?.let { (input, output) ->
                    if (input > 0) inputTokens = input
                    if (output > 0) outputTokens = output
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (streamFailure: Throwable) {
            val netError = (streamFailure as? VmHttpException)?.error
            val status = (netError as? VmError.Network)?.statusCode
            if (isRecoverableFailure(status, netError) || status in setOf(401, 403, 404)) throw streamFailure
            if (text.isEmpty()) {
                val fallbackReq = buildChatRequest(baseUrl, key, dialect, model, turns, config.images, stream = false)
                when (val nonStream = httpClient.execute(fallbackReq, RetryPolicy(maxAttempts = 2))) {
                    is VmResult.Failure -> throw streamFailure
                    is VmResult.Success -> {
                        val parsed = digital.vmstudio.code.core.ai.omniroute.agent.OmniRouteToolCodec.parseResponse(
                            dialect,
                            nonStream.value,
                        )
                        when (parsed) {
                            is OmniRouteTurn.Text -> {
                                text.append(parsed.text)
                                if (parsed.text.isNotEmpty()) emit(AgentEvent.AssistantDelta(parsed.text))
                                parsed.usage?.let { (inp, out) ->
                                    inputTokens = inp
                                    outputTokens = out
                                }
                            }
                            is OmniRouteTurn.GatewayError -> streamError = parsed
                            else -> throw streamFailure
                        }
                    }
                }
            }
        }

        if (reasoning.isNotEmpty()) emit(AgentEvent.Reasoning(reasoning.toString()))
        if (text.isNotEmpty()) emit(AgentEvent.AssistantMessage(text.toString()))
        streamError?.let { error ->
            throw VmHttpException(
                VmError.Ai(
                    summary = "The gateway reported an error",
                    reason = error.message,
                    retryable = error.retryable,
                    provider = "omniroute",
                ),
            )
        }
        emit(
            AgentEvent.Completed(
                sessionId = null,
                resultText = text.toString().takeIf { it.isNotEmpty() },
                durationMillis = System.currentTimeMillis() - startedAt,
                costUsd = 0.0,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                isError = false,
            ),
        )
    }

    private fun noBaseUrlError(): AgentEvent.Failed = AgentEvent.Failed(
        VmError.Ai(
            summary = "No gateway URL is set",
            reason = "OmniRoute requires a valid HTTP/HTTPS gateway URL.",
            suggestedAction = "Add your OmniRoute address in Settings.",
            retryable = false,
            provider = "omniroute",
        ),
    )

    private fun noModelSelectedError(): AgentEvent.Failed = AgentEvent.Failed(
        VmError.Ai(
            summary = "No model selected",
            reason = "This provider needs an explicit model.",
            suggestedAction = "Choose one in Settings after running the connection test.",
            retryable = false,
            provider = "omniroute",
        ),
    )

    private fun noApiKeyError(): AgentEvent.Failed = AgentEvent.Failed(
        VmError.Authentication(
            summary = "No API key is stored",
            suggestedAction = "Add the gateway's API key in Settings.",
        ),
    )

    /**
     * Fetches the gateway's current model list, for the live model picker.
     *
     * Separate from [checkHealth] so a model refresh costs one `GET /v1/models`
     * and not the dialect probes a full health check sends.
     */
    suspend fun listModels(): VmResult<GatewayModelList> {
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
        val key = resolveApiKey(settings.aiApiKeyCredentialId, baseUrl)
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
    suspend fun gatewayAccess(preferredModel: String? = null): VmResult<GatewayAccess> {
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
        val key = resolveApiKey(settings.aiApiKeyCredentialId, baseUrl)
            ?: return VmResult.Failure(
                VmError.Authentication(
                    summary = "No API key is stored",
                    suggestedAction = "Add the gateway's API key in Settings.",
                ),
            )
        val modelHint = preferredModel ?: settings.aiSelectedModelId
        val dialect = dialectFor(baseUrl, key, modelHint)
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

    private suspend fun resolveApiKey(credentialId: String?, baseUrl: String = ""): Secret? {
        if (credentialId != null) {
            when (val result = credentialStore.read(credentialId)) {
                is VmResult.Success -> return result.value.registerForRedaction()
                is VmResult.Failure -> Unit
            }
        }
        val isDefaultGateway = baseUrl.isBlank() ||
            baseUrl.contains("vmstudio.digital", ignoreCase = true)
        return if (isDefaultGateway) {
            Secret.of(DEFAULT_GATEWAY_API_KEY).registerForRedaction()
        } else {
            null
        }
    }

    private suspend fun fetchModels(baseUrl: String, key: Secret): VmResult<GatewayModelList> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchModelsInternal(baseUrl, key) }

    private suspend fun fetchModelsInternal(baseUrl: String, key: Secret): VmResult<GatewayModelList> {
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
        val free = mutableSetOf<String>()
        fun collected() = VmResult.Success(GatewayModelList(ids.distinct(), free))
        repeat(MAX_MODEL_PAGES) {
            val body = when (val current = page) {
                is VmResult.Failure -> return if (ids.isEmpty()) current else collected()
                is VmResult.Success -> current.value
            }
            ids += ChatStreamDecoder.parseModels(body)
            free += GatewayModelPricing.parseFreeModels(body)
            val cursor = ChatStreamDecoder.nextModelsCursor(body) ?: return collected()
            page = httpClient.execute(request(dialect, cursor), retry)
        }
        return collected()
    }

    /**
     * Determines the dialect by asking each endpoint to do the smallest possible
     * thing and seeing which one answers.
     *
     * A 404 rules a dialect out unless the body is a JSON model-not-found error;
     * anything else — including a 400 about the body — means the route exists.
     */
    private suspend fun detectDialect(
        baseUrl: String,
        key: Secret,
        preferredModel: String? = null,
    ): OmniRouteDialect {
        val probeModel = preferredModel?.takeIf { it.isNotBlank() } ?: "probe"
        suspend fun probe(dialect: OmniRouteDialect): Boolean {
            val body = buildJsonObject {
                put("model", probeModel)
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
                    val reason = result.error.reason.orEmpty()
                    status in VALID_DIALECT_STATUSES ||
                        (status == 404 && reason.contains("model", ignoreCase = true))
                }
            }
        }

        val dialectsToProbe = if (probeModel.contains("claude", ignoreCase = true) ||
            probeModel.contains("anthropic", ignoreCase = true)
        ) {
            listOf(OmniRouteDialect.ANTHROPIC_MESSAGES, OmniRouteDialect.OPENAI_CHAT)
        } else {
            listOf(OmniRouteDialect.OPENAI_CHAT, OmniRouteDialect.ANTHROPIC_MESSAGES)
        }

        var detectedDialect = OmniRouteDialect.UNKNOWN
        for (dialect in dialectsToProbe) {
            if (probe(dialect)) {
                detectedDialect = dialect
                break
            }
        }

        return detectedDialect.also {
            VmLog.i(LogCategory.AI, TAG, "Gateway dialect detected: ${it.displayName}")
        }
    }

    private fun buildChatRequest(
        baseUrl: String,
        key: Secret,
        dialect: OmniRouteDialect,
        model: String,
        turns: List<ConversationTurn>,
        images: List<digital.vmstudio.code.core.ai.model.ImageAttachment> = emptyList(),
        stream: Boolean = true,
    ): Request {
        val lastUserIndex = turns.indexOfLast { it.fromUser }
        val messages = JsonArray(
            turns.mapIndexed { index, turn ->
                val attachImages = if (index == lastUserIndex) images else emptyList()
                buildJsonObject {
                    put("role", if (turn.fromUser) "user" else "assistant")
                    if (attachImages.isEmpty()) {
                        put("content", turn.text)
                    } else if (dialect == OmniRouteDialect.ANTHROPIC_MESSAGES) {
                        putJsonArray("content") {
                            attachImages.forEach { image ->
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
                            add(buildJsonObject { put("type", "text"); put("text", turn.text) })
                        }
                    } else {
                        putJsonArray("content") {
                            add(buildJsonObject { put("type", "text"); put("text", turn.text) })
                            attachImages.forEach { image ->
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
            },
        )

        val body = buildJsonObject {
            put("model", model)
            put("stream", stream)
            if (stream && dialect != OmniRouteDialect.ANTHROPIC_MESSAGES) {
                putJsonObject("stream_options") {
                    put("include_usage", true)
                }
            }
            put("messages", messages)
            // Required by the Anthropic API and harmless to OpenAI, which treats it
            // as an optional cap.
            put("max_tokens", DEFAULT_MAX_TOKENS)
        }.toString()

        return Request.Builder()
            .url("$baseUrl/${dialect.chatPath}")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .applyOmniRouteAuth(key, dialect)
            .apply { if (stream) header("Accept", "text/event-stream") }
            .build()
    }

    private companion object {
        const val TAG = "OmniRouteProvider"
        const val DEFAULT_GATEWAY_API_KEY = "sk-fdcfc87a62a78521-a3cc2b-917e354e"
        const val DEFAULT_MAX_TOKENS = 8192
        const val MODELS_PAGE_SIZE = 1000
        const val MAX_MODEL_PAGES = 20
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val VALID_DIALECT_STATUSES = setOf(200, 400, 401, 403, 422, 429)
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_BAD_GATEWAY = 502
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_GATEWAY_TIMEOUT = 504

        fun isRecoverableStatus(status: Int?): Boolean =
            status == HTTP_TOO_MANY_REQUESTS ||
                status == HTTP_PAYLOAD_TOO_LARGE ||
                (status != null && status in 500..HTTP_GATEWAY_TIMEOUT)

        fun isRecoverableFailure(status: Int?, error: VmError?): Boolean {
            if (isRecoverableStatus(status)) return true
            val reason = listOfNotNull(error?.summary, error?.reason).joinToString(" ").lowercase()
            return reason.contains("not available in the active live catalog") ||
                reason.contains("maximum combo retry limit") ||
                reason.contains("model_not_found") ||
                reason.contains("model not found") ||
                reason.contains("rate limit") ||
                reason.contains("quota") ||
                reason.contains("tokens per minute") ||
                reason.contains("tpm") ||
                reason.contains("reduce your message size") ||
                reason.contains("overloaded") ||
                reason.contains("unavailable") ||
                reason.contains("capability_mismatch") ||
                reason.contains("supports tool calling") ||
                reason.contains("capability mismatch") ||
                reason.contains("tool calling") ||
                reason.contains("no active connection")
        }

        fun selectFallbackModel(
            primaryModel: String,
            availableModels: List<String>,
            requiresTools: Boolean = false,
        ): String? {
            val candidates = if (GatewayModelPricing.isFreeModelId(primaryModel)) {
                if (requiresTools) {
                    listOf(
                        "auto/coding:free",
                        "groq/openai/gpt-oss-120b",
                        "groq/qwen/qwen3.8-27b",
                        "auto/best-coding",
                    )
                } else {
                    listOf(
                        "auto/coding:free",
                        "auto/best-free",
                        "groq/openai/gpt-oss-120b",
                        "groq/qwen/qwen3.8-27b",
                        "auto/best-coding",
                    )
                }
            } else {
                listOf(
                    "auto/best-coding",
                    "opus",
                    "auto/best-fast",
                    "auto/coding:free",
                    "groq/openai/gpt-oss-120b",
                )
            }
            return candidates.firstOrNull {
                it != primaryModel &&
                    (!requiresTools || (it != "auto/best-free" && it != "best-free")) &&
                    (availableModels.isEmpty() || it in availableModels)
            } ?: availableModels.firstOrNull {
                it != primaryModel && (!requiresTools || (it != "auto/best-free" && it != "best-free"))
            }
        }
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

/** The gateway's chat models, and which of them it marks as free. */
data class GatewayModelList(val ids: List<String>, val freeIds: Set<String>)

/** Gateway connection details handed to a server-side run. */
class GatewayAccess(val baseUrl: String, val dialect: OmniRouteDialect, val key: Secret)

