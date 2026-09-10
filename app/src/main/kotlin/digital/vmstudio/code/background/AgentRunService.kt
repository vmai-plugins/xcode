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

    /** Servers with runs worth watching; a run can be started on more than one. */
    private val watchedServers = linkedSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
        if (serverId != null) watchedServers += serverId

        notifications.ensureChannels()
        startForegroundSafely(runningCount = watchedServers.size)

        if (pollJob == null) pollJob = scope.launch { pollLoop() }

        // Redelivery would re-run the last intent after a kill; the watcher re-reads
        // live state on every poll, so restarting plainly is enough.
        return START_STICKY
    }

    private suspend fun pollLoop() {
        var idleRounds = 0

        while (true) {
            var running = 0

            watchedServers.toList().forEach { serverId ->
                val result = watcher.poll(serverId)

                result.newlyFinished.forEach(notifications::notifyFinished)
                result.newlyBlocked.forEach(notifications::notifyBlocked)

                val announced = (result.newlyFinished + result.newlyBlocked).mapNotNull { it.id }
                watcher.markAnnounced(announced)

                running += result.stillRunning.size
                // Nothing left on this server, so stop asking it.
                if (!result.hasWork && !result.hasAnnouncements) watchedServers -= serverId
            }

            if (running > 0) {
                idleRounds = 0
                startForegroundSafely(running)
            } else {
                idleRounds++
            }

            // A couple of empty rounds before quitting, so a run that is briefly
            // between states does not cause the service to stop and immediately
            // need restarting.
            if (watchedServers.isEmpty() || idleRounds >= MAX_IDLE_ROUNDS) {
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
