package digital.vmstudio.code.background

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * Returns a callback that begins watching a server's detached runs.
 *
 * On Android 13+ the notification permission is requested at the moment it becomes
 * useful — when a run is actually detached — rather than at launch. Asking on first
 * open, before the user has any background work, is the request most likely to be
 * declined, and a denial is sticky.
 *
 * The watcher starts either way. A declined permission costs the notification, not
 * the run: the work is on the server and finishes regardless, and the app still shows
 * it on the agent screen.
 */
@Composable
fun rememberAgentRunWatchStarter(): (serverId: String) -> Unit {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Either way the service starts; see the note above. */ },
    )

    return remember(context, permissionLauncher) {
        { serverId: String ->
            if (needsNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            AgentRunService.start(context, serverId)
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) != PackageManager.PERMISSION_GRANTED
}
