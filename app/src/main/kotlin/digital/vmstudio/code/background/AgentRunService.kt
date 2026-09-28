package digital.vmstudio.code.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import digital.vmstudio.code.core.ai.background.AgentRunWatcher
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Watches detached agent runs while any are in flight, and notifies when they end.
 *
 * A foreground service rather than `WorkManager`: periodic work is floored at fifteen
 * minutes, and an agent task that finishes in three would be announced twelve minutes
 * late — late enough that the notification stops being the reason to use the feature.
 * The trade is that Android requires a visible ongoing notice, which is honest anyway,
 * since something really is running on the user's behalf.
 *
 * It stops itself as soon as nothing is running, so it never outlives the work.
 */
@AndroidEntryPoint
class AgentRunService : Service() {

    @Inject lateinit var watcher: AgentRunWatcher

    @Inject lateinit var notifications: AgentRunNotifications

    private val scope = CoroutineScope(SupervisorJob())
    private var pollJob: Job? = null

    /**
     * Servers with runs worth watching; a run can be started on more than one.
     *
     * Mutated from two different threads with no coordination otherwise:
     * [onStartCommand] runs on the main thread, while [pollLoop] runs on this
     * service's own [scope] (a bare [SupervisorJob] with no dispatcher, so its
     * children default to [kotlinx.coroutines.Dispatchers.Default]'s thread pool).
     * A plain [LinkedHashSet] is not thread-safe, so every access - not just the
     * mutations - is guarded by [watchedServersLock].
     */
    private val watchedServersLock = Any()
    private val watchedServers = linkedSetOf<String>()

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        // START_STICKY redelivers with a null intent after a system-initiated kill,
        // so the server id from the original start would otherwise be lost with the
        // killed instance - pollLoop would then find watchedServers empty on its very
        // first iteration and stop itself immediately, never re-reading live state as
        // intended. Restoring the last-known set here is what makes restart actually
        // resume watching rather than silently give up.
        synchronized(watchedServersLock) {
            watchedServers += prefs.getStringSet(KEY_WATCHED_SERVERS, emptySet()).orEmpty()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
        val watchedCount = synchronized(watchedServersLock) {
            if (serverId != null) watchedServers += serverId
            watchedServers.size
        }
        persistWatchedServers()

        notifications.ensureChannels()
        startForegroundSafely(runningCount = watchedCount)

        if (pollJob == null) pollJob = scope.launch { pollLoop() }

        return START_STICKY
    }

    private fun persistWatchedServers() {
        val snapshot = synchronized(watchedServersLock) { watchedServers.toSet() }
        prefs.edit().putStringSet(KEY_WATCHED_SERVERS, snapshot).apply()
    }

    private suspend fun pollLoop() {
        var idleRounds = 0

        while (true) {
            var running = 0

            val snapshot = synchronized(watchedServersLock) { watchedServers.toList() }
            snapshot.forEach { serverId ->
                val result = watcher.poll(serverId)

                result.newlyFinished.forEach(notifications::notifyFinished)
                result.newlyBlocked.forEach(notifications::notifyBlocked)

                watcher.markAnnounced(
                    finishedIds = result.newlyFinished.mapNotNull { it.id },
                    blockedIds = result.newlyBlocked.mapNotNull { it.id },
                )

                running += result.stillRunning.size
                // Nothing left on this server, so stop asking it.
                if (!result.hasWork && !result.hasAnnouncements) {
                    synchronized(watchedServersLock) { watchedServers -= serverId }
                }
            }
            persistWatchedServers()

            if (running > 0) {
                idleRounds = 0
                startForegroundSafely(running)
            } else {
                idleRounds++
            }

            // A couple of empty rounds before quitting, so a run that is briefly
            // between states does not cause the service to stop and immediately
            // need restarting.
            val stillWatching = synchronized(watchedServersLock) { watchedServers.isNotEmpty() }
            if (!stillWatching || idleRounds >= MAX_IDLE_ROUNDS) {
                VmLog.i(LogCategory.AI, TAG, "No agent runs left to watch; stopping")
                stopSelf()
                return
            }

            delay(POLL_INTERVAL_MILLIS)
        }
    }

    /**
     * Promotes to the foreground, tolerating the platform refusing.
     *
     * Android 12+ rejects a foreground start from the background, and 13+ lets the
     * user deny notifications outright. Neither should crash the app: the runs are on
     * the server and continue regardless, so the failure costs the notification only.
     */
    private fun startForegroundSafely(runningCount: Int) {
        val notification = notifications.progressNotification(runningCount.coerceAtLeast(1))
        runCatching {
            ServiceCompat.startForeground(
                this,
                AgentRunNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }.onFailure {
            VmLog.w(LogCategory.AI, TAG, "Could not run in the foreground: ${it.message}")
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AgentRunService"
        private const val EXTRA_SERVER_ID = "serverId"
        private const val PREFS_NAME = "agent_run_service"
        private const val KEY_WATCHED_SERVERS = "watched_servers"

        /**
         * Frequent enough that a short task is announced promptly, sparse enough not
         * to hold an SSH channel open continuously.
         */
        private const val POLL_INTERVAL_MILLIS = 20_000L
        private const val MAX_IDLE_ROUNDS = 2

        /** Starts watching [serverId]; safe to call for a server already watched. */
        fun start(context: Context, serverId: String) {
            val intent = Intent(context, AgentRunService::class.java)
                .putExtra(EXTRA_SERVER_ID, serverId)
            runCatching { context.startForegroundService(intent) }
        }
    }
}
