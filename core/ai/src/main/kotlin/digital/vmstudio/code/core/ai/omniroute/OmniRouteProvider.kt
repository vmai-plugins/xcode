package digital.vmstudio.code.core.ai.omniroute

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.omniroute.agent.OmniRouteAgentLoop
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    @Volatile
    private var detectedDialect: OmniRouteDialect = OmniRouteDialect.UNKNOWN

    override suspend fun checkHealth(serverId: String?): VmResult<AiProviderHealth> {
        val settings = preferences.preferences.first()
        val baseUrl = settings.aiBaseUrl.trimEnd('/')

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
                    val dialect = detectDialect(baseUrl, key)
                    detectedDialect = dialect
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
        val baseUrl = settings.aiBaseUrl.trimEnd('/')
        val model = config.model ?: settings.aiSelectedModelId

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

        val dialect = detectedDialect.takeIf { it != OmniRouteDialect.UNKNOWN }
            ?: detectDialect(baseUrl, key).also { detectedDialect = it }

        // Tool use is opt-in and needs somewhere to act: without a working directory
        // there is no project for read_file/write_file/run_command to act on, so this
        // falls back to plain chat exactly as it would if the toggle were off.
        if (settings.aiToolsEnabled && config.workingDirectory.isNotBlank()) {
            try {
                emitAll(agentLoop.run(baseUrl, key, dialect, model, config, settings.agentAutonomyLevel))
            } finally {
                key.wipe()
            }
            return@flow
        }

        try {
            val request = buildChatRequest(baseUrl, key, dialect, model, config.prompt)
            val text = StringBuilder()
            var inputTokens = 0L
            var outputTokens = 0L
            val startedAt = System.currentTimeMillis()

            httpClient.stream(request).collect { event ->
                ChatStreamDecoder.textDelta(dialect, event.data)?.let(text::append)
                ChatStreamDecoder.usage(event.data)?.let { (input, output) ->
                    if (input > 0) inputTokens = input
                    if (output > 0) outputTokens = output
                }
            }

            if (text.isNotEmpty()) emit(AgentEvent.AssistantMessage(text.toString()))
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
        fun request(dialect: OmniRouteDialect) = Request.Builder()
            .url("$baseUrl/v1/models")
            .get()
            .applyOmniRouteAuth(key, dialect)
            .build()

        val bearer = httpClient.execute(request(OmniRouteDialect.OPENAI_CHAT), RetryPolicy(maxAttempts = 2))
        val result = when {
            bearer is VmResult.Failure && bearer.error is VmError.Authentication ->
                httpClient.execute(request(OmniRouteDialect.ANTHROPIC_MESSAGES), RetryPolicy(maxAttempts = 2))
            else -> bearer
        }

        return when (result) {
            is VmResult.Failure -> result
            is VmResult.Success -> VmResult.Success(ChatStreamDecoder.parseModels(result.value))
        }
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
        prompt: String,
    ): Request {
        val messages = JsonArray(
            listOf(
                buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                },
            ),
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
        const val DEFAULT_MAX_TOKENS = 4096
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
