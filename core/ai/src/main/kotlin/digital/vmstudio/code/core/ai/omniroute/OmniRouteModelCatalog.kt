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
            OmniRouteModels(
                ids = prefs.aiAvailableModelIds,
                freeIds = prefs.aiFreeModelIds +
                    prefs.aiAvailableModelIds.filter(GatewayModelPricing::isFreeModelId),
                syncedAtMillis = prefs.aiModelsSyncedAtMillis,
                isConfigured = prefs.aiBaseUrl.isNotBlank() && prefs.aiApiKeyCredentialId != null,
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
            preferences.setAiModels(result.value.ids, result.value.freeIds)
        }
        result.map { it.ids }
    }

    /** Syncs only when a gateway is configured and the stored list has gone stale. */
    suspend fun syncIfStale() {
        val current = models.first()
        val age = System.currentTimeMillis() - current.syncedAtMillis
        if (current.isConfigured && age > STALE_AFTER_MILLIS) sync()
    }

    companion object {
        const val STALE_AFTER_MILLIS: Long = 15 * 60 * 1000L
    }
}
