package digital.vmstudio.code.core.ssh.recipes

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandResult
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.connection.SshSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that [DefaultRecipeExecutor] routes every recipe command through the
 * [CommandGuard], and that a denied step stops the run rather than continuing.
 */
class RecipeExecutorTest {

    private val commandGuard = mockk<CommandGuard>()
    private val connectionManager = mockk<SshConnectionManager>()
    private val executor = DefaultRecipeExecutor(commandGuard, connectionManager)

    private val sampleRecipe = Recipe(
        id = "test-recipe",
        name = "Test Recipe",
        description = "A test recipe",
        estimatedSeconds = 60,
        statusProbe = "test -f /tmp/installed && echo yes",
        steps = listOf(
            RecipeStep(progressWeight = 0.5, label = "Step 1", command = "echo step1"),
            RecipeStep(progressWeight = 0.5, label = "Step 2", command = "echo step2"),
        ),
    )

    @Test
    fun `run routes all steps through command guard`() = runTest {
        coEvery {
            commandGuard.run(any(), any(), any(), requestedByAgent = true)
        } returns VmResult.Success(
            CommandResult(
                command = "echo step1",
                exitCode = 0,
                stdout = "ok",
                stderr = "",
                durationMillis = 1,
            ),
        )

        val progressSteps = mutableListOf<Pair<Double, String>>()

        val result = executor.run(
            serverId = "server-1",
            recipe = sampleRecipe,
            onProgress = { fraction, label -> progressSteps.add(fraction to label) },
        )

        assert(result is VmResult.Success)
        coVerify(exactly = 2) { commandGuard.run(any(), any(), any(), requestedByAgent = true) }
        assertEquals(2, progressSteps.size)
        assertEquals(0.5, progressSteps[0].first, 0.001)
        assertEquals("Step 1", progressSteps[0].second)
        assertEquals(1.0, progressSteps[1].first, 0.001)
        assertEquals("Step 2", progressSteps[1].second)
    }

    @Test
    fun `run fails when a step's command exits non-zero, even though the SSH call succeeded`() = runTest {
        coEvery {
            commandGuard.run(any(), any(), any(), requestedByAgent = true)
        } returns VmResult.Success(
            CommandResult(
                command = "echo step1",
                exitCode = 1,
                stdout = "",
                stderr = "command not found",
                durationMillis = 1,
            ),
        )

        val progressSteps = mutableListOf<Pair<Double, String>>()

        val result = executor.run(
            serverId = "server-1",
            recipe = sampleRecipe,
            onProgress = { fraction, label -> progressSteps.add(fraction to label) },
        )

        assert(result is VmResult.Failure)
        // Stops at the first failing step rather than running the second one.
        coVerify(exactly = 1) { commandGuard.run(any(), any(), any(), requestedByAgent = true) }
        assertEquals(0, progressSteps.size)
    }

    @Test
    fun `run stops when command guard refuses a step`() = runTest {
        coEvery {
            commandGuard.run(any(), any(), any(), requestedByAgent = true)
        } returns VmResult.Failure(
            digital.vmstudio.code.core.common.error.VmError.PermissionDenied(
                summary = "This command is blocked",
                reason = "Destructive command",
                suggestedAction = "Run it directly on the server",
                subject = "rm -rf /",
            ),
        )

        val progressSteps = mutableListOf<Pair<Double, String>>()

        val result = executor.run(
            serverId = "server-1",
            recipe = sampleRecipe,
            onProgress = { fraction, label -> progressSteps.add(fraction to label) },
        )

        assert(result is VmResult.Failure)
        coVerify(exactly = 1) { commandGuard.run(any(), any(), any(), requestedByAgent = true) }
        assertEquals(0, progressSteps.size)
    }

    @Test
    fun `checkStatus returns INSTALLED when probe succeeds`() = runTest {
        val session = mockk<SshSession>()
        coEvery { session.execute(any()) } returns VmResult.Success(
            CommandResult(
                command = sampleRecipe.statusProbe!!,
                exitCode = 0,
                stdout = "installed",
                stderr = "",
                durationMillis = 1,
            ),
        )
        coEvery { connectionManager.withSession<String>(any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[1] as suspend (SshSession) -> VmResult<String>
            block(session)
        }

        val result = executor.checkStatus("server-1", sampleRecipe)

        assert(result is VmResult.Success)
        assertEquals(RecipeInstallStatus.INSTALLED, (result as VmResult.Success).value)
    }

    @Test
    fun `checkStatus returns NOT_INSTALLED when probe fails`() = runTest {
        val session = mockk<SshSession>()
        coEvery { session.execute(any()) } returns VmResult.Success(
            CommandResult(
                command = sampleRecipe.statusProbe!!,
                exitCode = 1,
                stdout = "",
                stderr = "not found",
                durationMillis = 1,
            ),
        )
        coEvery { connectionManager.withSession<String>(any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[1] as suspend (SshSession) -> VmResult<String>
            block(session)
        }

        val result = executor.checkStatus("server-1", sampleRecipe)

        assert(result is VmResult.Success)
        assertEquals(RecipeInstallStatus.NOT_INSTALLED, (result as VmResult.Success).value)
    }

    @Test
    fun `checkStatus returns UNKNOWN when recipe has no probe`() = runTest {
        val recipeWithoutProbe = sampleRecipe.copy(statusProbe = null)

        val result = executor.checkStatus("server-1", recipeWithoutProbe)

        assert(result is VmResult.Success)
        assertEquals(RecipeInstallStatus.UNKNOWN, (result as VmResult.Success).value)
    }
}
