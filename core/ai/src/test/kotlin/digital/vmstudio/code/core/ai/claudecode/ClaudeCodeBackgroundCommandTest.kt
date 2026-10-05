package digital.vmstudio.code.core.ai.claudecode

import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The background commands run on the user's server with a user-supplied prompt and
 * path, so the quoting matters here for the same reason it does in the foreground
 * builder. The flag combinations were verified against Claude Code 2.1.261.
 */
class ClaudeCodeBackgroundCommandTest {

    @Test
    fun `a background start never passes print or output-format`() {
        val command = ClaudeCodeCommandBuilder.backgroundStartCommand(
            workingDirectory = "/srv/app",
            prompt = "add rate limiting",
            permissionMode = AgentPermissionMode.PLAN,
        )

        // The CLI refuses --bg with --print: "--print never starts the interactive
        // session that `claude agents` attaches to, so the job would be
        // unattachable." Passing either would fail every background run.
        assertFalse(command.contains("--print"))
        assertFalse(command.contains("--output-format"))
        assertTrue(command.contains("--bg"))
    }

    @Test
    fun `a background start cds into the project and passes the prompt positionally`() {
        val command = ClaudeCodeCommandBuilder.backgroundStartCommand(
            workingDirectory = "/srv/app",
            prompt = "do the thing",
            permissionMode = AgentPermissionMode.ACCEPT_EDITS,
        )

        assertTrue(command.startsWith("cd '/srv/app' &&"))
        assertTrue(command.contains("--permission-mode acceptEdits"))
        assertTrue(command.contains("'do the thing'"))
    }

    @Test
    fun `injection in a background prompt is neutralised`() {
        val command = ClaudeCodeCommandBuilder.backgroundStartCommand(
            workingDirectory = "/srv/app",
            prompt = "fix ${'$'}(rm -rf /) it's broken; whoami",
            permissionMode = AgentPermissionMode.PLAN,
        )

        assertTrue(command.contains("'fix ${'$'}(rm -rf /) it'\\''s broken; whoami'"))
    }

    @Test
    fun `injection in the working directory is neutralised`() {
        val command = ClaudeCodeCommandBuilder.backgroundStartCommand(
            workingDirectory = "/srv/app; touch /tmp/pwned",
            prompt = "hi",
            permissionMode = AgentPermissionMode.PLAN,
        )

        assertTrue(command.startsWith("cd '/srv/app; touch /tmp/pwned' &&"))
    }

    @Test
    fun `the agents listing includes finished runs`() {
        val command = ClaudeCodeCommandBuilder.agentsJsonCommand()

        // --all matters: a run that finished while the app was closed must still be
        // reportable, which is the entire point of background execution.
        assertTrue(command.contains("agents"))
        assertTrue(command.contains("--json"))
        assertTrue(command.contains("--all"))
        assertFalse(command.contains("--cwd"))
    }

    @Test
    fun `the agents listing can be scoped to a project directory`() {
        val command = ClaudeCodeCommandBuilder.agentsJsonCommand("/srv/my app")

        assertTrue(command.contains("--cwd '/srv/my app'"))
    }

    @Test
    fun `lifecycle commands quote the run id`() {
        assertTrue(ClaudeCodeCommandBuilder.logsCommand("3f8b10c3").contains("logs '3f8b10c3'"))
        assertTrue(ClaudeCodeCommandBuilder.stopCommand("3f8b10c3").contains("stop '3f8b10c3'"))
        assertTrue(ClaudeCodeCommandBuilder.removeCommand("3f8b10c3").contains("rm '3f8b10c3'"))
    }

    @Test
    fun `a hostile run id cannot break out of its quoting`() {
        val command = ClaudeCodeCommandBuilder.stopCommand("x'; rm -rf /; echo '")

        assertTrue(command.contains("'x'\\''; rm -rf /; echo '\\'''"))
    }

    @Test
    fun `background commands carry no credentials`() {
        val command = ClaudeCodeCommandBuilder.backgroundStartCommand(
            workingDirectory = "/srv/app",
            prompt = "hi",
            permissionMode = AgentPermissionMode.PLAN,
        )

        assertFalse(command.contains("ANTHROPIC_API_KEY"))
        assertFalse(command.contains("ANTHROPIC_AUTH_TOKEN"))
    }

    @Test
    fun `claude is looked up where installers put it, not only on the bare ssh PATH`() {
        val command = ClaudeCodeCommandBuilder.versionCommand()
        assertTrue(command.contains("\$HOME/.local/bin"))
        assertTrue(command.contains(".nvm/versions/node/*/bin"))
        assertTrue(command.contains("\$PATH\" claude --version"))
    }
}
