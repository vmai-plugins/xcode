package digital.vmstudio.code.core.ai.provider

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.flow.Flow

/** How the agent backend is reached. */
enum class AiProviderKind { OMNIROUTE }

/** Result of a provider health check, for the connection-test screen. */
data class AiProviderHealth(
    val kind: AiProviderKind,
    val isAvailable: Boolean,
    val version: String? = null,
    val endpoint: String? = null,
    val isAuthenticated: Boolean = false,
    val latencyMillis: Long? = null,
    val availableModels: List<String> = emptyList(),
    val freeModels: Set<String> = emptySet(),
    /** What to do about it when [isAvailable] or [isAuthenticated] is false. */
    val diagnosis: String? = null,
)

/**
 * An AI backend that can run an agent task.
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
