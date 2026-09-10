package digital.vmstudio.code.core.ai.provider

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.flow.Flow

/** How an agent backend is reached. */
enum class AiProviderKind {
    /** Claude Code CLI driven over SSH on the development machine. */
    CLAUDE_CODE_CLI,

    /** An HTTP gateway the app calls directly. */
    OMNIROUTE,
}

/** Result of a provider health check, for the connection-test screen. */
data class AiProviderHealth(
    val kind: AiProviderKind,
    val isAvailable: Boolean,
    val version: String? = null,
    val endpoint: String? = null,
    val isAuthenticated: Boolean = false,
    val latencyMillis: Long? = null,
    val availableModels: List<String> = emptyList(),
    /** What to do about it when [isAvailable] or [isAuthenticated] is false. */
    val diagnosis: String? = null,
)

/**
 * A backend that can run an agent task.
 *
 * The abstraction exists because the two backends differ in kind, not just in
 * transport: the CLI provider delegates the whole agent loop — planning, tools,
 * context management — to a program running next to the code, while an HTTP provider
 * would require the app to implement that loop itself. Both are reduced to the same
 * event stream so nothing above this interface has to know which it is talking to.
 */
interface AiProvider {

    val kind: AiProviderKind

    /** Cheap enough to run on opening the AI screen. */
    suspend fun checkHealth(serverId: String?): VmResult<AiProviderHealth>

    /**
     * Runs a task, emitting progress as it happens.
     *
     * Cancelling collection must terminate the underlying work rather than orphan it.
     */
    fun run(config: AgentRunConfig): Flow<AgentEvent>
}
