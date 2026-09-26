package digital.vmstudio.code.core.ai.claudecode

import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt is arbitrary user text placed into a command that runs on the user's
 * server. These tests exist because getting the quoting wrong here is remote code
 * execution, not a formatting bug.
 */
class ClaudeCodeCommandBuilderTest {

    private fun config(
        prompt: String = "add rate limiting",
        workingDirectory: String = "/srv/app",
        permissionMode: AgentPermissionMode = AgentPermissionMode.PLAN,
        resumeSessionId: String? = null,
        allowedTools: List<String> = emptyList(),
        disallowedTools: List<String> = emptyList(),
        restricted: Boolean = false,
        model: String? = null,
    ) = AgentRunConfig(
        serverId = "srv-1",
        workingDirectory = workingDirectory,
        prompt = prompt,
        resumeSessionId = resumeSessionId,
        permissionMode = permissionMode,
        allowedTools = allowedTools,
        disallowedTools = disallowedTools,
        restricted = restricted,
        model = model,
    )

    // --- injection resistance ----------------------------------------------------

    @Test
    fun `command substitution in a prompt is neutralised`() {
        val command = ClaudeCodeCommandBuilder.build(
            config(prompt = "fix \$(rm -rf /) please"),
        )

        assertTrue(
            "the substitution must remain inside single quotes",
            command.contains("'fix \$(rm -rf /) please'"),
        )
    }

    @Test
    fun `backticks in a prompt are neutralised`() {
        val command = ClaudeCodeCommandBuilder.build(config(prompt = "run `whoami` now"))

        assertTrue(command.contains("'run `whoami` now'"))
    }

    @Test
    fun `a quote in the prompt cannot break out of quoting`() {
        val command = ClaudeCodeCommandBuilder.build(
            config(prompt = "it's broken; rm -rf /tmp"),
        )

        // The POSIX idiom: close, escaped quote, reopen. The semicolon that follows
        // must still be inside quoting rather than becoming a command separator.
        assertTrue(command.contains("'it'\\''s broken; rm -rf /tmp'"))
        assertFalse(
            "no bare separator may escape",
            command.contains("; rm -rf /tmp' "),
        )
    }

    @Test
    fun `a newline in the prompt stays quoted`() {
        val command = ClaudeCodeCommandBuilder.build(config(prompt = "line one\nrm -rf /"))

        assertTrue(command.contains("'line one\nrm -rf /'"))
    }

    @Test
    fun `the working directory is quoted too`() {
        val command = ClaudeCodeCommandBuilder.build(
            config(workingDirectory = "/srv/my app; touch /tmp/pwned"),
        )

        assertTrue(command.startsWith("cd '/srv/my app; touch /tmp/pwned' &&"))
    }

    @Test
    fun `shellQuote handles the adversarial cases directly`() {
        assertEquals("'plain'", ClaudeCodeCommandBuilder.shellQuote("plain"))
        assertEquals("''", ClaudeCodeCommandBuilder.shellQuote(""))
        assertEquals("'a'\\''b'", ClaudeCodeCommandBuilder.shellQuote("a'b"))
        assertEquals("'\$HOME'", ClaudeCodeCommandBuilder.shellQuote("\$HOME"))
        assertEquals("'a\\b'", ClaudeCodeCommandBuilder.shellQuote("a\\b"))
    }

    // --- flag construction --------------------------------------------------------

    @Test
    fun `a baseline run has the flags the stream protocol requires`() {
        val command = ClaudeCodeCommandBuilder.build(config())

        assertTrue(command.contains("--print"))
        assertTrue(command.contains("--output-format stream-json"))
        // The CLI rejects stream-json without --verbose; omitting it fails every run.
        assertTrue(command.contains("--verbose"))
        assertTrue(command.contains("--permission-mode plan"))
    }

    @Test
    fun `permission modes map to the cli spelling`() {
        assertTrue(
            ClaudeCodeCommandBuilder.build(config(permissionMode = AgentPermissionMode.ACCEPT_EDITS))
                .contains("--permission-mode acceptEdits"),
        )
        assertTrue(
            ClaudeCodeCommandBuilder.build(config(permissionMode = AgentPermissionMode.MANUAL))
                .contains("--permission-mode default"),
        )
        assertTrue(
            ClaudeCodeCommandBuilder.build(config(permissionMode = AgentPermissionMode.BYPASS))
                .contains("--permission-mode bypassPermissions"),
        )
    }

    @Test
    fun `resuming passes the session id`() {
        val command = ClaudeCodeCommandBuilder.build(config(resumeSessionId = "abc-123"))

        assertTrue(command.contains("--resume 'abc-123'"))
    }

    @Test
    fun `a new run does not pass resume`() {
        assertFalse(ClaudeCodeCommandBuilder.build(config()).contains("--resume"))
    }

    @Test
    fun `tool lists are passed and quoted`() {
        val command = ClaudeCodeCommandBuilder.build(
            config(
                allowedTools = listOf("Edit", "Bash(git *)"),
                disallowedTools = listOf("WebFetch"),
            ),
        )

        assertTrue(command.contains("--allowedTools 'Edit' 'Bash(git *)'"))
        assertTrue(command.contains("--disallowedTools 'WebFetch'"))
    }

    @Test
    fun `restricted mode is passed when requested`() {
        assertTrue(ClaudeCodeCommandBuilder.build(config(restricted = true)).contains("--restricted"))
        assertFalse(ClaudeCodeCommandBuilder.build(config()).contains("--restricted"))
    }

    @Test
    fun `a blank model is omitted rather than passed empty`() {
        assertFalse(ClaudeCodeCommandBuilder.build(config(model = "  ")).contains("--model"))
        assertTrue(
            ClaudeCodeCommandBuilder.build(config(model = "claude-sonnet-4-5"))
                .contains("--model 'claude-sonnet-4-5'"),
        )
    }

    @Test
    fun `the prompt is the final positional argument`() {
        val command = ClaudeCodeCommandBuilder.build(config(prompt = "do the thing"))

        assertTrue(command.trimEnd().endsWith("'do the thing'"))
    }

    // --- no credentials on the command line ---------------------------------------

    @Test
    fun `the command never carries an auth token`() {
        val command = ClaudeCodeCommandBuilder.build(config())

        // A token passed as a command prefix would be visible in the server's process
        // list to every other user on the box. The environment is the server's own.
        assertFalse(command.contains("ANTHROPIC_API_KEY"))
        assertFalse(command.contains("ANTHROPIC_AUTH_TOKEN"))
        assertFalse(command.contains("ANTHROPIC_BASE_URL"))
    }
}
