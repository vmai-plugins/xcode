package digital.vmstudio.code.core.ai.omniroute

import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What the picker shows for the gateway: its models and how fresh the list is. */
data class OmniRouteModels(
    val ids: List<String> = emptyList(),
    /** Ids the gateway marks as free, plus those named that way (`…:free`). */
    val freeIds: Set<String> = emptySet(),
    val syncedAtMillis: Long = 0L,
    /** A URL and a key are both stored, so a sync can be attempted. */
    val isConfigured: Boolean = false,
)

/**
 * The gateway's model list, kept in sync with the gateway itself.
 *
 * The list is persisted (so the picker works offline and after process death) and
 * refreshed from `GET /v1/models` whenever it is older than [STALE_AFTER_MILLIS],
 * or on demand. Only ever what the gateway reported: nothing is hardcoded.
 */
@Singleton
class OmniRouteModelCatalog @Inject constructor(
    private val provider: OmniRouteProvider,
    private val preferences: UserPreferencesRepository,
) {
    private val syncLock = Mutex()

    val models: Flow<OmniRouteModels> = preferences.preferences
        .map { prefs ->
            val effectiveIds = prefs.aiAvailableModelIds.ifEmpty { BUILTIN_TIER_MODELS }
            val effectiveFreeIds = prefs.aiFreeModelIds +
                digital.vmstudio.code.core.common.preferences.UserPreferences.DEFAULT_FREE_AI_MODELS +
                effectiveIds.filter(GatewayModelPricing::isFreeModelId)
            OmniRouteModels(
                ids = prioritizeSmartModels(effectiveIds),
                freeIds = effectiveFreeIds,
                syncedAtMillis = prefs.aiModelsSyncedAtMillis,
                isConfigured = prefs.aiBaseUrl.isNotBlank(),
            )
        }
        .distinctUntilChanged()

    /**
     * Refreshes the list from the gateway. An empty answer keeps the previous list:
     * a gateway that returned nothing has not shown that it serves nothing.
     */
    suspend fun sync(): VmResult<List<String>> = syncLock.withLock {
        val result = provider.listModels()
        if (result is VmResult.Success && result.value.ids.isNotEmpty()) {
            val ids = prioritizeSmartModels(result.value.ids)
            val freeIds = result.value.freeIds + ids.filter(GatewayModelPricing::isFreeModelId)
            preferences.setAiModels(ids, freeIds)
            val currentPrefs = preferences.preferences.first()
            if (currentPrefs.aiSelectedModelId.isNullOrBlank() || currentPrefs.aiSelectedModelId !in ids) {
                val preferred = PREFERRED_DEFAULT_MODELS.firstOrNull { it in ids } ?: ids.first()
                preferences.setAiModel(preferred, currentPrefs.aiModelPreset)
            }
        }
        result.map { prioritizeSmartModels(it.ids) }
    }

    /** Syncs when a gateway is configured and the stored list is stale or missing free models. */
    suspend fun syncIfStale() {
        val current = models.first()
        val age = System.currentTimeMillis() - current.syncedAtMillis
        if (current.isConfigured && (age > STALE_AFTER_MILLIS || current.freeIds.isEmpty())) {
            sync()
        }
    }

    companion object {
        const val STALE_AFTER_MILLIS: Long = 24 * 60 * 60 * 1000L

        private val PREFERRED_DEFAULT_MODELS = listOf(
            "auto/best-coding",
            "auto/coding:free",
            "auto/best-free",
            "opus",
        )

        private val SMART_PRIORITY_ORDER = listOf(
            "auto/best-coding",
            "auto/coding:free",
            "auto/best-free",
            "auto/best-reasoning",
            "auto/best-fast",
            "auto/best-vision",
            "auto/best-chat",
            "auto/best-coding-fast",
            "auto/pro-coding",
            "auto/pro-reasoning",
            "auto/coding",
            "auto/fast",
            "opus",
            "opus-nvidia",
            "Kimi Coding",
        )

        val BUILTIN_TIER_MODELS = SMART_PRIORITY_ORDER + listOf(
            "groq/openai/gpt-oss-120b",
            "groq/qwen/qwen3.8-27b",
            "groq/moonshotai/kimi-k2-instruct-0905",
            "antigravity/claude-3-7-sonnet",
            "antigravity/gemini-2.0-flash",
        )

        fun prioritizeSmartModels(ids: List<String>): List<String> {
            if (ids.isEmpty()) return ids
            val priorityIndex = SMART_PRIORITY_ORDER.withIndex().associate { (i, id) -> id to i }
            return ids.distinct().sortedWith(
                compareBy<String> { id ->
                    priorityIndex[id]
                        ?: if (id.startsWith("auto/")) SMART_PRIORITY_ORDER.size else SMART_PRIORITY_ORDER.size + 1
                }.thenBy { it.lowercase() },
            )
        }
    }
}
