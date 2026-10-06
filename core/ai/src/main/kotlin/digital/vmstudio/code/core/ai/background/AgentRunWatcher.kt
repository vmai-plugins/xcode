package digital.vmstudio.code.core.ai.background

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.agentRunStore: DataStore<Preferences> by preferencesDataStore(
    name = "agent_run_watcher",
)

/** What a poll found, expressed as the thing the caller cares about. */
data class WatchResult(
    /** Runs that finished since the last poll and have not been announced. */
    val newlyFinished: List<BackgroundRun>,
    /** Runs that are stuck waiting on something, e.g. an expired login. */
    val newlyBlocked: List<BackgroundRun>,
    /** Runs still working; the watcher keeps polling while this is non-empty. */
    val stillRunning: List<BackgroundRun>,
    /** The server could not be asked (no signal, SSH down); nothing is known. */
    val unreachable: Boolean = false,
) {
    val hasWork: Boolean get() = stillRunning.isNotEmpty()
    val hasAnnouncements: Boolean get() = newlyFinished.isNotEmpty() || newlyBlocked.isNotEmpty()
}

/**
 * Detects when detached runs change state, so a completion can be announced once.
 *
 * The server is the source of truth for *what* the runs are, but not for what the
 * user has already been told — that is local, and it is why this keeps a small store
 * of announced ids. Without it, every poll after a run finished would re-announce it,
 * and a process restart would announce work the user saw yesterday.
 */
@Singleton
class AgentRunWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runner: BackgroundAgentRunner,
) {

    private val store = context.agentRunStore

    /**
     * Polls one server and reports the transitions worth telling the user about.
     *
     * A failure to reach the server is reported as "nothing changed" rather than as
     * "everything finished": losing the network must not fire a wave of completion
     * notifications for runs that are still going.
     */
    suspend fun poll(serverId: String): WatchResult {
        val runs = when (val result = runner.list(serverId)) {
            is VmResult.Success -> result.value.filter { it.isBackground }
            is VmResult.Failure ->
                return WatchResult(emptyList(), emptyList(), emptyList(), unreachable = true)
        }

        // Kept as two separate records, not one: a run that was blocked (announced,
        // id remembered) and then resumed and finished is !isRunning && !isBlocked
        // by the time of a later poll. A single shared "announced" set would already
        // contain its id from the blocked notification and would then wrongly
        // exclude it here too - the user would be told it is blocked and never hear
        // anything else, because its completion could never be announced.
        val announcedFinished = announcedIds(ANNOUNCED_FINISHED)
        val announcedBlocked = announcedIds(ANNOUNCED_BLOCKED)
        val finished = runs.filter { !it.isRunning && !it.isBlocked && it.id !in announcedFinished }
        val blocked = runs.filter { it.isBlocked && it.id !in announcedBlocked }
        val running = runs.filter { it.isRunning }

        return WatchResult(
            newlyFinished = finished,
            newlyBlocked = blocked,
            stillRunning = running,
        )
    }

    /** Records that the user has been told about these runs, per announcement kind. */
    suspend fun markAnnounced(
        finishedIds: Collection<String> = emptyList(),
        blockedIds: Collection<String> = emptyList(),
    ) {
        if (finishedIds.isEmpty() && blockedIds.isEmpty()) return
        store.edit { preferences ->
            if (finishedIds.isNotEmpty()) {
                preferences[ANNOUNCED_FINISHED] = merge(preferences[ANNOUNCED_FINISHED], finishedIds)
            }
            if (blockedIds.isNotEmpty()) {
                preferences[ANNOUNCED_BLOCKED] = merge(preferences[ANNOUNCED_BLOCKED], blockedIds)
            }
        }
    }

    /**
     * Treats everything currently finished or blocked as already seen.
     *
     * Called the first time a server is watched so the user is not greeted with
     * notifications for runs that completed, or were already stuck, long before
     * they enabled this.
     */
    suspend fun baseline(serverId: String) {
        val runs = when (val result = runner.list(serverId)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return
        }
        markAnnounced(
            finishedIds = runs.filter { !it.isRunning && !it.isBlocked }.mapNotNull { it.id },
            blockedIds = runs.filter { it.isBlocked }.mapNotNull { it.id },
        )
    }

    suspend fun forget(runIds: Collection<String>) {
        if (runIds.isEmpty()) return
        val remove = runIds.toSet()
        store.edit { preferences ->
            preferences[ANNOUNCED_FINISHED] = (preferences[ANNOUNCED_FINISHED] ?: emptySet()) - remove
            preferences[ANNOUNCED_BLOCKED] = (preferences[ANNOUNCED_BLOCKED] ?: emptySet()) - remove
        }
    }

    /** Newest kept: a set has no order, so the oldest ids are the ones trimmed off. */
    private fun merge(existing: Set<String>?, incoming: Collection<String>): Set<String> =
        (existing.orEmpty() + incoming).toList().takeLast(MAX_REMEMBERED).toSet()

    private suspend fun announcedIds(key: Preferences.Key<Set<String>>): Set<String> =
        store.data.first()[key] ?: emptySet()

    private companion object {
        val ANNOUNCED_FINISHED = stringSetPreferencesKey("announced_finished_run_ids")
        val ANNOUNCED_BLOCKED = stringSetPreferencesKey("announced_blocked_run_ids")

        /**
         * Bounded so the record cannot grow without limit. Old ids falling off is
         * harmless: the CLI stops listing a removed run long before that.
         */
        const val MAX_REMEMBERED = 200
    }
}
