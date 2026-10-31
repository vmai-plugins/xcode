package digital.vmstudio.code.core.ai.background

/**
 * Bridges long-running agent streaming runs in :core:ai to foreground services in :app,
 * ensuring Android Doze mode and OS battery restrictions do not terminate active SSE streams.
 */
interface AgentExecutionKeepAlive {
    fun onExecutionStarted()
    fun onExecutionStopped()
}
