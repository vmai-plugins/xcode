package digital.vmstudio.code.core.ssh.command

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single gate every remote command passes through.
 *
 * Its existence is the point: [CommandSafety] is pure analysis with no opinion about
 * what to do with its verdict, and without something that consults it the analysis is
 * inert. Everything that runs a command — a configured build step, the terminal, and
 * in due course each agent tool — goes through here, so the policy is enforced in one
 * place and cannot be forgotten at a new call site.
 */
interface CommandGuard {

    /** Classifies a command without running or prompting. */
    suspend fun assess(
        serverId: String,
        command: String,
        requestedByAgent: Boolean = false,
    ): VmResult<CommandAssessment>

    /**
     * Classifies, refuses if blocked, and prompts if the policy requires it.
     *
     * Returns the assessment when the command may proceed, or a
     * [VmError.PermissionDenied] when it may not.
     */
    suspend fun authorize(
        serverId: String,
        command: String,
        requestedByAgent: Boolean = false,
    ): VmResult<CommandAssessment>

    /** [authorize] followed by execution. The only sanctioned way to run a command. */
    suspend fun run(
        serverId: String,
        command: String,
        limits: CommandLimits = CommandLimits(),
        requestedByAgent: Boolean = false,
    ): VmResult<CommandResult>
}

@Singleton
class DefaultCommandGuard @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connectionManager: SshConnectionManager,
    private val approvalGate: CommandApprovalGate,
    private val preferences: UserPreferencesSource,
) : CommandGuard {

    override suspend fun assess(
        serverId: String,
        command: String,
        requestedByAgent: Boolean,
    ): VmResult<CommandAssessment> = serverRepository.get(serverId).flatMap { server ->
        VmResult.Success(CommandSafety.assess(command, contextFor(server, requestedByAgent)))
    }

    override suspend fun authorize(
        serverId: String,
        command: String,
        requestedByAgent: Boolean,
    ): VmResult<CommandAssessment> = serverRepository.get(serverId).flatMap { server ->
        val assessment = CommandSafety.assess(command, contextFor(server, requestedByAgent))

        // Blocked commands are refused outright and never prompted for. Offering a
        // confirmation would imply there is a correct answer other than no.
        if (assessment.isBlocked) {
            VmLog.w(
                LogCategory.SECURITY,
                TAG,
                "Refused blocked command on ${server.name}: ${assessment.summary}",
            )
            return@flatMap VmResult.Failure(
                VmError.PermissionDenied(
                    summary = "This command is blocked",
                    reason = assessment.summary,
                    suggestedAction = "If you genuinely need to do this, run it directly on " +
                        "the server where the consequences are visible.",
                    subject = command.take(120),
                ),
            )
        }

        if (!assessment.requiresConfirmation) {
            return@flatMap VmResult.Success(assessment)
        }

        val approved = approvalGate.request(
            CommandApprovalRequest(
                command = command,
                assessment = assessment,
                serverId = server.id,
                serverName = server.name,
                environment = server.environment,
                requestedByAgent = requestedByAgent,
            ),
        )

        VmLog.i(
            LogCategory.SECURITY,
            TAG,
            "Approval for ${assessment.risk} command on ${server.name}: " +
                if (approved) "granted" else "denied",
        )

        if (approved) {
            VmResult.Success(assessment)
        } else {
            VmResult.Failure(
                VmError.PermissionDenied(
                    summary = "Command not run",
                    reason = "You did not approve it.",
                    subject = command.take(120),
                ),
            )
        }
    }

    override suspend fun run(
        serverId: String,
        command: String,
        limits: CommandLimits,
        requestedByAgent: Boolean,
    ): VmResult<CommandResult> = authorize(serverId, command, requestedByAgent).flatMap {
        connectionManager.withSession(serverId) { session ->
            session.execute(command, limits)
        }
    }

    /**
     * Builds the policy context for a command.
     *
     * `projectRoot` is null until projects exist, which disables path-confinement
     * checks. That is a real current limitation rather than an oversight: with no
     * project there is no root to confine to, and inventing one would either block
     * legitimate work or give false assurance.
     */
    private suspend fun contextFor(server: Server, requestedByAgent: Boolean): CommandContext {
        val current = preferences.preferences.first()
        return CommandContext(
            projectRoot = null,
            environment = server.environment,
            confirmDestructive = current.confirmDestructiveCommands,
            proposedByAgent = requestedByAgent,
        )
    }

    private companion object {
        const val TAG = "CommandGuard"
    }
}
