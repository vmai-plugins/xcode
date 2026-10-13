package digital.vmstudio.code.core.ai.provider

import digital.vmstudio.code.core.ai.omniroute.OmniRouteProvider
import digital.vmstudio.code.core.common.result.VmResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the app's sole AI backend.
 */
@Singleton
class AiProviderRegistry @Inject constructor(
    private val omniRoute: OmniRouteProvider,
) {

    suspend fun active(): AiProvider = omniRoute

    fun forKind(kind: AiProviderKind): AiProvider = omniRoute

    fun forId(providerId: String): AiProvider = omniRoute

    fun all(): List<AiProvider> = listOf(omniRoute)

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
    get() = true

val AiProvider.requiresServer: Boolean
    get() = false

val AiProviderKind.displayName: String
    get() = "OmniRoute"

val AiProviderKind.capabilitySummary: String
    get() = "OmniRoute models with optional project tools, using your API key."
