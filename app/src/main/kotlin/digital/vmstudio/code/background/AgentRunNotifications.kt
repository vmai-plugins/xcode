package digital.vmstudio.code.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.MainActivity
import digital.vmstudio.code.R
import digital.vmstudio.code.core.ai.background.BackgroundRun
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications for detached agent runs.
 *
 * Two channels rather than one: a silent, low-importance channel for the ongoing
 * "still working" notice the foreground service must show, and a default-importance
 * one for the completions the user actually wants to be interrupted by. Putting both
 * on one channel would either make progress buzz the phone or make completion silent.
 */
@Singleton
class AgentRunNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            "Agent runs in progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while an agent task is running on one of your servers."
            setShowBadge(false)
        }

        val finished = NotificationChannel(
            CHANNEL_FINISHED,
            "Agent runs finished",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tells you when an agent task has finished or needs attention."
        }

        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(finished)
    }

    /** The ongoing notice a foreground service is required to display. */
    fun progressNotification(runningCount: Int) = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_notification_agent)
        .setContentTitle(
            if (runningCount == 1) "1 agent task running" else "$runningCount agent tasks running",
        )
        .setContentText("Running on your server. You can close the app.")
        .setContentIntent(openApp())
        .setOngoing(true)
        .setSilent(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun notifyFinished(run: BackgroundRun) {
        val id = run.id ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_notification_agent)
            .setContentTitle("Agent task finished")
            .setContentText(run.name ?: id)
            .setStyle(NotificationCompat.BigTextStyle().bigText(run.name ?: id))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        post(id.hashCode(), notification)
    }

    /**
     * A blocked run gets its own wording because it is not finished and will not
     * finish: something on the server, typically an expired login, needs the user.
     */
    fun notifyBlocked(run: BackgroundRun) {
        val id = run.id ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_notification_agent)
            .setContentTitle("Agent task needs attention")
            .setContentText(run.name ?: id)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${run.name ?: id}\n\nIt is waiting on the server — often a expired " +
                        "sign-in. Open the run to see its output.",
                ),
            )
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        post(id.hashCode(), notification)
    }

    /**
     * Posts only when permitted.
     *
     * On Android 13+ the user can decline notifications entirely; posting anyway
     * throws. The run itself is unaffected, so this fails quietly rather than
     * breaking a task the user did want.
     */
    private fun post(id: Int, notification: android.app.Notification) {
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_PROGRESS = "agent_runs_progress"
        const val CHANNEL_FINISHED = "agent_runs_finished"
        const val FOREGROUND_NOTIFICATION_ID = 4201
    }
}
