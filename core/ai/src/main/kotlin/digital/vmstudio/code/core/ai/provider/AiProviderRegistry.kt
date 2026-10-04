package digital.vmstudio.code.core.ai.provider

import digital.vmstudio.code.core.ai.claudecode.ClaudeCodeCliProvider
import digital.vmstudio.code.core.ai.omniroute.OmniRouteProvider
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which backend a request should use.
 *
 * The user's choice decides, with no silent substitution. Falling back to the other
 * provider when one looks unavailable would be worse than failing: the two differ in
 * capability, not just in transport, and an agentic request quietly answered by a
 * chat-only backend would appear to have run without doing anything.
 */
@Singleton
class AiProviderRegistry @Inject constructor(
    private val claudeCode: ClaudeCodeCliProvider,
    private val omniRoute: OmniRouteProvider,
    private val preferences: UserPreferencesSource,
) {

    suspend fun active(): AiProvider = forId(preferences.preferences.first().aiProviderId)

    fun forKind(kind: AiProviderKind): AiProvider = when (kind) {
        AiProviderKind.CLAUDE_CODE_CLI -> claudeCode
        AiProviderKind.OMNIROUTE -> omniRoute
    }

    fun forId(providerId: String): AiProvider = when (providerId) {
        UserPreferences.PROVIDER_OMNIROUTE -> omniRoute
        // Claude Code is the default: it is the only backend that can actually do
        // agentic work on a project.
        else -> claudeCode
    }

    /** Every provider, for the settings screen and the connection test. */
    fun all(): List<AiProvider> = listOf(claudeCode, omniRoute)

    private val healthCache = ConcurrentHashMap<HealthCacheKey, CachedHealth>()

    /**
     * Checks [provider]'s health, reusing a result from the last [HEALTH_CACHE_TTL_MILLIS]
     * unless [forceRefresh] is set.
     *
     * Without this, opening Settings' connection test and then the agent chat screen
     * — or the reverse — silently re-probed the same server twice for the same
     * answer: both call sites resolve independently and neither knew about the
     * other. A failure is never cached, so a real problem is never hidden behind a
     * stale success, and an explicit "Run test" tap always forces a fresh probe
     * rather than showing something the user didn't just ask for.
     */
    suspend fun checkHealth(
        provider: AiProvider,
        serverId: String?,
        forceRefresh: Boolean = false,
    ): VmResult<AiProviderHealth> {
        val key = HealthCacheKey(provider.kind, serverId)
        if (!forceRefresh) {
            healthCache[key]?.let { cached ->
                val age = System.currentTimeMillis() - cached.checkedAtMillis
                if (age < HEALTH_CACHE_TTL_MILLIS) return VmResult.Success(cached.health)
            }
        }

        val result = provider.checkHealth(serverId)
        when (result) {
            is VmResult.Success ->
                healthCache[key] = CachedHealth(result.value, System.currentTimeMillis())
            // A failure invalidates any cached success rather than being cached
            // itself: the next caller should see this failure too, not a stale
            // "connected" from before whatever just broke.
            is VmResult.Failure -> healthCache.remove(key)
        }
        return result
    }

    private data class HealthCacheKey(val kind: AiProviderKind, val serverId: String?)

    private data class CachedHealth(val health: AiProviderHealth, val checkedAtMillis: Long)

    private companion object {
        /** Long enough to dedupe navigating between screens, short enough that a
         *  just-fixed server or a just-added key is reflected without a manual retry. */
        const val HEALTH_CACHE_TTL_MILLIS = 15_000L
    }
}

/** What a backend is capable of, so the UI can set expectations honestly. */
val AiProvider.supportsTools: Boolean
    get() = kind == AiProviderKind.CLAUDE_CODE_CLI || kind == AiProviderKind.OMNIROUTE

val AiProvider.requiresServer: Boolean
    get() = kind == AiProviderKind.CLAUDE_CODE_CLI

val AiProviderKind.displayName: String
    get() = when (this) {
        AiProviderKind.CLAUDE_CODE_CLI -> "Claude Code on a server"
        AiProviderKind.OMNIROUTE -> "OmniRoute gateway"
    }

val AiProviderKind.capabilitySummary: String
    get() = when (this) {
        AiProviderKind.CLAUDE_CODE_CLI ->
            "Full agent: reads and edits files, runs commands and tests on the server. " +
                "Needs an SSH server with Claude Code installed and signed in."
        AiProviderKind.OMNIROUTE ->
            "Chat by default: answers questions and writes code into the conversation. " +
                "Can optionally read, write and run commands too - turn on \"Tool use\" " +
                "below - but reliability then depends entirely on the selected model."
    }
