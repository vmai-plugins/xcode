package digital.vmstudio.code.core.ai.model

import digital.vmstudio.code.core.common.error.VmError

/**
 * What the UI observes while an agent runs.
 *
 * Provider-neutral on purpose. The Claude Code CLI's `stream-json` shape and a
 * future OmniRoute HTTP stream are wire formats; both are translated into these
 * events at their boundary, so the chat screen, the task record and the diff viewer
 * never learn which provider produced them.
 */
sealed interface AgentEvent {

    /** First event of a run. Carries the id needed to resume the conversation. */
    data class SessionStarted(
        val sessionId: String,
        val workingDirectory: String?,
        val model: String?,
        val availableTools: List<String>,
    ) : AgentEvent

    /** A complete assistant message. */
    data class AssistantMessage(val text: String) : AgentEvent

    /**
     * A fragment of the reply as it is generated.
     *
     * Emitted alongside [AssistantMessage], not instead of it: the complete message
     * still arrives and remains authoritative. The UI shows deltas as they land so
     * the reply appears to type rather than materialising in one block, then swaps in
     * the finished text.
     */
    data class AssistantDelta(val text: String) : AgentEvent

    /** Reasoning the model chose to surface. Rendered distinctly from the answer. */
    data class Reasoning(val text: String) : AgentEvent

    /**
     * The agent invoked a tool.
     *
     * [summary] is a short human rendering derived from the arguments, so the chat
     * can say "Edited src/Main.kt" rather than printing a JSON blob.
     */
    data class ToolStarted(
        val toolUseId: String,
        val name: String,
        val summary: String,
        val argumentsJson: String,
        val affectedPath: String? = null,
    ) : AgentEvent

    data class ToolFinished(
        val toolUseId: String,
        val isError: Boolean,
        val output: String,
    ) : AgentEvent

    /** Terminal event of a successful run. */
    data class Completed(
        val sessionId: String?,
        val resultText: String?,
        val durationMillis: Long,
        val costUsd: Double,
        val inputTokens: Long,
        val outputTokens: Long,
        val isError: Boolean,
    ) : AgentEvent

    /** Terminal event of a failed run. */
    data class Failed(val error: VmError) : AgentEvent

    /**
     * A line the provider emitted that was not part of the protocol.
     *
     * Kept rather than discarded because the CLI writes diagnostics and warnings to
     * stdout alongside its JSON, and silently swallowing them is how an
     * authentication or configuration failure turns into an agent that appears to do
     * nothing at all.
     */
    data class Diagnostic(val line: String, val isStderr: Boolean) : AgentEvent
}

/** How much freedom the agent has for a run. Maps onto the provider's own controls. */
enum class AgentPermissionMode {
    /** Propose a plan; make no changes. */
    PLAN,

    /** Apply file edits without asking; still ask for other tools. */
    ACCEPT_EDITS,

    /** Ask before anything with an effect. */
    MANUAL,

    /** No prompting. Only offered for throwaway or sandboxed servers. */
    BYPASS,
}

/** Configuration for a single agent run. */
data class AgentRunConfig(
    val serverId: String,
    /** Absolute path on the server. The agent's filesystem scope. */
    val workingDirectory: String,
    val prompt: String,
    /** Resume an existing conversation, or null to start a new one. */
    val resumeSessionId: String? = null,
    val permissionMode: AgentPermissionMode = AgentPermissionMode.PLAN,
    /** Tool allowlist, e.g. `Edit`, `Bash(git *)`. Empty means the provider default. */
    val allowedTools: List<String> = emptyList(),
    val disallowedTools: List<String> = emptyList(),
    /** Removes the command-running tools entirely. */
    val restricted: Boolean = false,
    val model: String? = null,
    /**
     * Context window at which the CLI auto-compacts, in tokens.
     *
     * The one agent limit this app can actually enforce: the loop and the tool calls
     * belong to Claude Code, but the context budget is a flag it accepts.
     */
    val autoCompactTokens: Int? = null,
)
