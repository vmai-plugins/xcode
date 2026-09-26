package digital.vmstudio.code.core.ssh.apps

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that a remote command's own exit code, not just the SSH round trip,
 * decides whether a state-changing action reports success.
 */
class AppProcessManagerTest {

    private val guard = mockk<CommandGuard>()
    private val manager = AppProcessManager(guard)
    private val app = AppProject(name = "my-app", path = "/home/user/apps/my-app")

    @Test
    fun `start fails when the remote command exits non-zero`() = runTest {
        coEvery {
            guard.run(any(), any(), any(), any())
        } returns VmResult.Success(
            CommandResult(
                command = "pm2 start ...",
                exitCode = 1,
                stdout = "",
                stderr = "no start script found",
                durationMillis = 1,
            ),
        )

        val result = manager.start("server-1", app)

        assert(result is VmResult.Failure)
    }

    @Test
    fun `start succeeds when the remote command exits zero`() = runTest {
        coEvery {
            guard.run(any(), any(), any(), any())
        } returns VmResult.Success(
            CommandResult(
                command = "pm2 start ...",
                exitCode = 0,
                stdout = "started",
                stderr = "",
                durationMillis = 1,
            ),
        )

        val result = manager.start("server-1", app)

        assert(result is VmResult.Success)
        assertEquals(0, (result as VmResult.Success).value.exitCode)
    }

    @Test
    fun `stop is not exit-code checked, since its command always exits zero by design`() = runTest {
        coEvery {
            guard.run(any(), any(), any(), any())
        } returns VmResult.Success(
            CommandResult(
                command = "pm2 stop ... || true",
                exitCode = 0,
                stdout = "",
                stderr = "",
                durationMillis = 1,
            ),
        )

        val result = manager.stop("server-1", app)

        assert(result is VmResult.Success)
    }

    @Test
    fun `logs pass through a non-zero exit rather than becoming a failure`() = runTest {
        coEvery {
            guard.run(any(), any(), any(), any())
        } returns VmResult.Success(
            CommandResult(
                command = "pm2 logs ...",
                exitCode = 1,
                stdout = "last output before the crash",
                stderr = "",
                durationMillis = 1,
            ),
        )

        val result = manager.logs("server-1", app)

        // The log viewer should still see this output rather than a bare error panel.
        assert(result is VmResult.Success)
        assertEquals("last output before the crash", (result as VmResult.Success).value.stdout)
    }
}
