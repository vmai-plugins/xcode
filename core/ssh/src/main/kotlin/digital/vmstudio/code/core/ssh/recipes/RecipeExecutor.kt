package digital.vmstudio.code.core.ssh.recipes

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.mapError
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a [Recipe]'s steps through the [CommandGuard], so every command a recipe
 * issues is subject to the same approval policy as a manually typed one.
 *
 * A recipe is not a privileged channel: if the user denies a destructive step,
 * execution stops and the returned error explains why. The caller is responsible
 * for surfacing partial-progress state.
 */
interface RecipeExecutor {
    /**
     * Runs [recipe] against the server [serverId], emitting progress after each step.
     *
     * @param onProgress invoked after each step with cumulative fraction (0..1) and label
     * @return success when all steps complete, or the first failure
     */
    suspend fun run(
        serverId: String,
        recipe: Recipe,
        onProgress: suspend (fraction: Double, label: String) -> Unit = { _, _ -> },
    ): VmResult<Unit>

    /**
     * Checks whether [recipe] appears to be installed on the server.
     *
     * The check is a single read-only probe — it does not go through the approval
     * gate because it makes no change. Unknown is returned as [RecipeInstallStatus.UNKNOWN],
     * never as a fabricated "not installed".
     */
    suspend fun checkStatus(serverId: String, recipe: Recipe): VmResult<RecipeInstallStatus>
}

@Singleton
class DefaultRecipeExecutor @Inject constructor(
    private val commandGuard: CommandGuard,
    private val connectionManager: SshConnectionManager,
) : RecipeExecutor {

    override suspend fun run(
        serverId: String,
        recipe: Recipe,
        onProgress: suspend (fraction: Double, label: String) -> Unit,
    ): VmResult<Unit> {
        var cumulative = 0.0

        for (step in recipe.steps) {
            val result = commandGuard.run(
                serverId = serverId,
                command = step.command,
                requestedByAgent = true,
            )

            when (result) {
                is VmResult.Failure -> return result
                is VmResult.Success -> {
                    cumulative += step.progressWeight
                    onProgress(cumulative.coerceIn(0.0, 1.0), step.label)
                }
            }
        }

        return VmResult.Success(Unit)
    }

    override suspend fun checkStatus(
        serverId: String,
        recipe: Recipe,
    ): VmResult<RecipeInstallStatus> {
        val probe = recipe.statusProbe ?: return VmResult.Success(RecipeInstallStatus.UNKNOWN)

        return connectionManager.withSession(serverId) { session ->
            when (val result = session.execute(probe)) {
                is VmResult.Failure -> result.mapError { it }
                is VmResult.Success -> when {
                    result.value.isSuccess && result.value.stdout.trim().isNotEmpty() ->
                        VmResult.Success(RecipeInstallStatus.INSTALLED)
                    result.value.exitCode != null && result.value.exitCode != 0 ->
                        VmResult.Success(RecipeInstallStatus.NOT_INSTALLED)
                    else ->
                        VmResult.Success(RecipeInstallStatus.UNKNOWN)
                }
            }
        }
    }
}
