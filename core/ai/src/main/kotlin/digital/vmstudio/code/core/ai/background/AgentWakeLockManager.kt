package digital.vmstudio.code.core.ai.background

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the CPU awake and Wi-Fi radio active during long-running agent streaming executions,
 * preventing Android Doze mode and screen locks from terminating active SSE connections.
 */
@Singleton
class AgentWakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keepAlive: Optional<AgentExecutionKeepAlive>,
) {
    private val powerManager = context.getSystemService<PowerManager>()
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val lock = Any()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var activeHolders = 0

    fun acquire() {
        synchronized(lock) {
            activeHolders++
            if (activeHolders == 1) {
                runCatching {
                    keepAlive.ifPresent { it.onExecutionStarted() }
                }.onFailure {
                    VmLog.w(LogCategory.AI, TAG, "Failed to notify keepAlive start: ${it.message}")
                }
                runCatching {
                    if (wakeLock == null) {
                        wakeLock = powerManager?.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            WAKE_LOCK_TAG,
                        )?.apply {
                            setReferenceCounted(false)
                        }
                    }
                    wakeLock?.acquire(MAX_WAKE_LOCK_TIMEOUT_MILLIS)

                    if (wifiLock == null) {
                        @Suppress("DEPRECATION")
                        wifiLock = wifiManager?.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            WIFI_LOCK_TAG,
                        )?.apply {
                            setReferenceCounted(false)
                        }
                    }
                    wifiLock?.acquire()
                    VmLog.d(LogCategory.AI, TAG, "Acquired WakeLock & WifiLock for active agent run")
                }.onFailure {
                    VmLog.w(LogCategory.AI, TAG, "Failed to acquire wake lock: ${it.message}")
                }
            }
        }
    }

    fun release() {
        synchronized(lock) {
            if (activeHolders > 0) {
                activeHolders--
            }
            if (activeHolders == 0) {
                runCatching {
                    keepAlive.ifPresent { it.onExecutionStopped() }
                }.onFailure {
                    VmLog.w(LogCategory.AI, TAG, "Failed to notify keepAlive stop: ${it.message}")
                }
                runCatching {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                    }
                    if (wifiLock?.isHeld == true) {
                        wifiLock?.release()
                    }
                    VmLog.d(LogCategory.AI, TAG, "Released WakeLock & WifiLock")
                }.onFailure {
                    VmLog.w(LogCategory.AI, TAG, "Failed to release wake lock: ${it.message}")
                }
            }
        }
    }

    private companion object {
        const val TAG = "AgentWakeLockManager"
        const val WAKE_LOCK_TAG = "VMStudioCode:AgentExecution"
        const val WIFI_LOCK_TAG = "VMStudioCode:AgentWifi"
        const val MAX_WAKE_LOCK_TIMEOUT_MILLIS = 15 * 60 * 1000L
    }
}
