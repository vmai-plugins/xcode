package digital.vmstudio.code.core.ai.claudecode

import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.AgentRunConfig

/**
 * Builds the shell command that starts a Claude Code run on the remote host.
 *
 * **Every interpolated value is shell-quoted.** The prompt is arbitrary user text
 * being placed into a command that runs on the developer's server; concatenating it
 * unquoted would turn a prompt containing a backtick or `$(...)` into remote code
 * execution. [shellQuote] is the only way values enter the string, and it is tested
 * against the specific constructs that would escape.
 *
 * **Credentials are deliberately absent.** Pointing the CLI at an alternative
 * gateway needs `ANTHROPIC_BASE_URL` and an auth token, but passing a token as a
 * command prefix would expose it in the server's process list to every other user on
 * the box. The environment is left to the server's own configuration, so the app
 * never holds or transmits the AI credential at all.
 */
internal object ClaudeCodeCommandBuilder {

    fun build(config: AgentRunConfig, binary: String = DEFAULT_BINARY): String {
        val parts = mutableListOf<String>()

        // `cd` into the project first so the CLI's own working-directory scoping,
        // CLAUDE.md discovery and relative paths all resolve as the user expects.
        parts += "cd"
        parts += shellQuote(config.workingDirectory)
        parts += "&&"

        parts += binary
        parts += "< /dev/null"
        parts += "--print"
        parts += "--output-format"
        parts += "stream-json"
        // The CLI rejects stream-json without it.
        parts += "--verbose"

        config.resumeSessionId?.let {
            parts += "--resume"
            parts += shellQuote(it)
        }

        parts += "--permission-mode"
        parts += config.permissionMode.cliValue()

        if (config.restricted) parts += "--restricted"

        if (config.allowedTools.isNotEmpty()) {
            parts += "--allowedTools"
            config.allowedTools.forEach { parts += shellQuote(it) }
        }

        if (config.disallowedTools.isNotEmpty()) {
            parts += "--disallowedTools"
            config.disallowedTools.forEach { parts += shellQuote(it) }
        }

        config.model?.takeIf { it.isNotBlank() }?.let {
            parts += "--model"
            parts += shellQuote(it)
        }

        config.autoCompactTokens?.takeIf { it in AUTOCOMPACT_MIN..AUTOCOMPACT_MAX }?.let {
            // The CLI accepts 100k-1M; anything outside is rejected, so an
            // out-of-range preference is dropped rather than failing the run.
            parts += "--autocompact"
            parts += it.toString()
        }

        config.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            parts += "--append-system-prompt"
            parts += shellQuote(it)
        }

        // The prompt goes last, as the positional argument.
        parts += shellQuote(config.prompt)

        return parts.joinToString(" ")
    }

    /**
     * Starts a run detached, so it survives the app being closed.
     *
     * Deliberately does **not** pass `--print` or `--output-format`: the CLI refuses
     * the combination outright — "--print never starts the interactive session that
     * `claude agents` attaches to, so the job would be unattachable". A background
     * run is therefore a TUI session whose output is read back with [logsCommand]
     * and rendered, not a JSON event stream like [build] produces.
     */
    fun backgroundStartCommand(
        workingDirectory: String,
        prompt: String,
        permissionMode: AgentPermissionMode,
        binary: String = DEFAULT_BINARY,
    ): String = buildList {
        add("cd")
        add(shellQuote(workingDirectory))
        add("&&")
        add(binary)
        add("--bg")
        add("--permission-mode")
        add(permissionMode.cliValue())
        // The prompt is the positional argument; quoted because it is arbitrary
        // user text being placed into a command that runs on the user's server.
        add(shellQuote(prompt))
    }.joinToString(" ") + " 2>&1"

    /**
     * Lists sessions as JSON. `--all` includes finished ones, which is what lets a
     * run that completed while the app was closed still be reported.
     */
    fun agentsJsonCommand(
        workingDirectory: String? = null,
        binary: String = DEFAULT_BINARY,
    ): String = buildList {
        add(binary)
        add("agents")
        add("--json")
        add("--all")
        workingDirectory?.let {
            // Scopes the listing to one project rather than every session on the box.
            add("--cwd")
            add(shellQuote(it))
        }
    }.joinToString(" ") + " 2>&1"

    /** A background session's recent output, as raw ANSI terminal rendering. */
    fun logsCommand(runId: String, binary: String = DEFAULT_BINARY): String =
        "$binary logs ${shellQuote(runId)} 2>&1"

    /** Stops a run. Its conversation survives, so `--resume` still works after. */
    fun stopCommand(runId: String, binary: String = DEFAULT_BINARY): String =
        "$binary stop ${shellQuote(runId)} 2>&1"

    /** Deletes a run and its record. Only safe once it has stopped. */
    fun removeCommand(runId: String, binary: String = DEFAULT_BINARY): String =
        "$binary rm ${shellQuote(runId)} 2>&1"

    /** Probes the CLI without starting a session. */
    fun versionCommand(binary: String = DEFAULT_BINARY): String =
        "$binary --version 2>&1 || echo __VM_CLAUDE_MISSING__"

    /**
     * Reports authentication state as JSON. Used by the health check so the AI
     * screen can say "installed but not signed in" rather than failing on the first
     * real prompt with an opaque error.
     */
    fun authStatusCommand(binary: String = DEFAULT_BINARY): String =
        "$binary auth status 2>&1 || echo __VM_CLAUDE_AUTH_FAILED__"

    /**
     * Wraps a value in single quotes, which suppress every form of shell expansion.
     * A literal single quote is closed, escaped and reopened, the standard POSIX
     * idiom, because nothing can escape a character inside single quotes.
     */
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * The CLI's `--permission-mode` only accepts `default`, `plan`, `acceptEdits` and
     * `bypassPermissions` - "manual" is not one of its values, so every run started
     * in [AgentPermissionMode.MANUAL] failed at the CLI's own argument parsing before
     * a single event could be emitted. `default` is its actual name for
     * ask-before-every-tool-call.
     */
    private fun AgentPermissionMode.cliValue(): String = when (this) {
        AgentPermissionMode.PLAN -> "plan"
        AgentPermissionMode.ACCEPT_EDITS -> "acceptEdits"
        AgentPermissionMode.MANUAL -> "default"
        AgentPermissionMode.BYPASS -> "bypassPermissions"
    }

    const val DEFAULT_BINARY = "claude"
    const val MISSING_MARKER = "__VM_CLAUDE_MISSING__"
    const val AUTH_FAILED_MARKER = "__VM_CLAUDE_AUTH_FAILED__"

    /** The CLI documents --autocompact as accepting 100k-1M tokens. */
    const val AUTOCOMPACT_MIN = 100_000
    const val AUTOCOMPACT_MAX = 1_000_000

    /**
     * VM Studio ecosystem prompt: informs the agent of available repositories,
     * build tools on VPS 2, and concise mobile formatting.
     */
    const val DEFAULT_HUB_PROMPT =
        "You are running inside VM Studio X-Codes. Be brief: do not narrate every step, only state findings and results clearly. For any job with 3 or more steps, first create a task list with your task tools and keep the status of each task current. Android and Flutter apps can be built and published on the build VPS with `/usr/local/bin/build-and-publish-apk <slug> release` (slugs: onlinepuja-customer, onlinepuja-partner, mynearby-shop, mynearby-vendor, mynearby-delivery, mynearby-partner, mynearby-core-ai, agents-app, xcode)."
}
