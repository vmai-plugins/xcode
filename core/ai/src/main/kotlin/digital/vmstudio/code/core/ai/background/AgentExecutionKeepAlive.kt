package digital.vmstudio.code.core.ai.background

/**
 * Coordinates foreground execution keep-alive during active agent turn generations.
 *
 * This keeps the application process promoted to the foreground via a Foreground Service
 * with [android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC], preventing
 * Android Doze mode and OS network suspension from killing active SSE streams when the phone
 * screen is locked or turned off.
 */
interface AgentExecutionKeepAlive {
    fun onExecutionStarted()
    fun onExecutionStopped()
}
