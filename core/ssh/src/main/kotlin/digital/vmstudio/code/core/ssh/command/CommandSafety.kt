package digital.vmstudio.code.core.ssh.command

import digital.vmstudio.code.core.database.entity.ServerEnvironment

/**
 * How dangerous a command is judged to be.
 *
 * The ordering matters: a compound command takes the highest risk of any of its
 * parts, so `ls && rm -rf /` is never treated as a listing.
 */
enum class CommandRisk {
    /** Read-only or clearly reversible. */
    SAFE,

    /** Changes state, but recoverably: installs, migrations, service starts. */
    CAUTION,

    /** Can destroy data or interrupt a service. Always confirmed. */
    DESTRUCTIVE,

    /**
     * Refused outright. Reserved for commands whose only plausible outcome is
     * catastrophic and which no confirmation dialog can make safe.
     */
    BLOCKED,
    ;

    fun atLeast(other: CommandRisk): CommandRisk = if (ordinal >= other.ordinal) this else other
}

/** One reason a command was flagged. */
data class CommandFinding(
    val rule: String,
    val risk: CommandRisk,
    /** What actually matched, so the user can see why. */
    val matchedText: String,
    val explanation: String,
)

data class CommandAssessment(
    val command: String,
    val risk: CommandRisk,
    val findings: List<CommandFinding>,
    val requiresConfirmation: Boolean,
) {
    val isBlocked: Boolean get() = risk == CommandRisk.BLOCKED

    /** One-line rationale for the approval prompt. */
    val summary: String
        get() = when {
            findings.isEmpty() -> "No risky operations detected."
            else -> findings.first().explanation
        }
}

/**
 * Where and under what policy a command is about to run.
 */
data class CommandContext(
    /** Absolute path the command is confined to; null disables path checks. */
    val projectRoot: String? = null,
    val environment: ServerEnvironment = ServerEnvironment.UNSPECIFIED,
    /** When false, the user has explicitly opted out of destructive prompts. */
    val confirmDestructive: Boolean = true,
    /** True when the AI agent proposed this rather than the user typing it. */
    val proposedByAgent: Boolean = false,
)

/**
 * Classifies shell commands before they run.
 *
 * This is the single chokepoint the specification requires: the same analysis
 * applies whether a command came from the user, from a project's configured build
 * step, or from the AI agent. It is pure and dependency-free so it can be tested
 * exhaustively, which matters more here than anywhere else in the app.
 *
 * **Design stance.** This is a safety net against mistakes, not a sandbox against a
 * determined adversary — anyone with a shell can obfuscate past any matcher. Its
 * job is to catch the `rm -rf /` typed into the wrong window and the agent that
 * proposes `git reset --hard` on production, and to do so without crying wolf on
 * ordinary work. Rules are therefore deliberately specific; a matcher that flagged
 * every `rm` would be ignored within a day.
 */
@Suppress("TooManyFunctions") // One cohesive analyser; splitting it would scatter the ruleset.
object CommandSafety {

    fun assess(command: String, context: CommandContext = CommandContext()): CommandAssessment {
        val segments = splitSegments(command)
        val findings = mutableListOf<CommandFinding>()

        segments.forEach { segment ->
            findings += analyseSegment(segment, context)
        }

        // Command substitution — `$(...)` and backticks — runs a command whose text
        // segmentation would otherwise hide inside a single argument, so
        // `echo $(rm -rf /)` would escape every per-segment rule. Each substitution
        // body is analysed as its own command.
        extractSubstitutions(command).forEach { inner ->
            splitSegments(inner).forEach { segment ->
                findings += analyseSegment(segment, context)
            }
            findings += analyseWholeCommand(inner, context)
        }

        // Some constructs are defined by the operators that segmentation removes:
        // a fork bomb is nothing but pipes and semicolons, and `curl … | sh` is
        // dangerous precisely because of the pipe. Those rules see the raw command.
        findings += analyseWholeCommand(command, context)

        // A command that leaves the project sandbox is judged on its own terms,
        // independently of what it does.
        findings += pathEscapeFindings(command, context)

        var risk = findings.fold(CommandRisk.SAFE) { acc, finding -> acc.atLeast(finding.risk) }

        // Production raises the stakes: something merely cautious elsewhere gets a
        // confirmation here, because the cost of being wrong is different.
        if (context.environment == ServerEnvironment.PRODUCTION && risk == CommandRisk.CAUTION) {
            risk = CommandRisk.DESTRUCTIVE
            findings += CommandFinding(
                rule = "production-environment",
                risk = CommandRisk.DESTRUCTIVE,
                matchedText = command.take(80),
                explanation = "This server is marked as production, so state-changing " +
                    "commands are confirmed.",
            )
        }

        val requiresConfirmation = when (risk) {
            CommandRisk.SAFE -> false
            // A cautious command is confirmed only when the agent proposed it; a
            // user who typed `npm install` does not need to be asked whether they
            // meant it.
            CommandRisk.CAUTION -> context.proposedByAgent
            // The preference can suppress the prompt for a user's own command, but
            // never for one the agent proposed: the specification requires that the
            // agent can never bypass confirmation.
            CommandRisk.DESTRUCTIVE -> context.confirmDestructive || context.proposedByAgent
            CommandRisk.BLOCKED -> true
        }

        return CommandAssessment(
            command = command,
            risk = risk,
            findings = findings.sortedByDescending { it.risk.ordinal },
            requiresConfirmation = requiresConfirmation,
        )
    }

    // --- segmentation ----------------------------------------------------------

    /**
     * Splits on shell operators while respecting quoting, so a literal `;` inside
     * `echo "a; b"` does not create a phantom segment, and `rm -rf /` hidden after
     * a `&&` is still analysed.
     */
    internal fun splitSegments(command: String): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        var quote: Char? = null

        while (index < command.length) {
            val char = command[index]
            when {
                quote != null -> {
                    if (char == '\\' && quote == '"' && index + 1 < command.length) {
                        current.append(char).append(command[index + 1])
                        index++
                    } else {
                        if (char == quote) quote = null
                        current.append(char)
                    }
                }

                char == '\'' || char == '"' -> {
                    quote = char
                    current.append(char)
                }

                char == '\\' && index + 1 < command.length -> {
                    current.append(char).append(command[index + 1])
                    index++
                }

                char == ';' || char == '\n' -> {
                    segments.addSegment(current)
                }

                char == '&' && index + 1 < command.length && command[index + 1] == '&' -> {
                    segments.addSegment(current)
                    index++
                }

                char == '|' && index + 1 < command.length && command[index + 1] == '|' -> {
                    segments.addSegment(current)
                    index++
                }

                char == '|' -> segments.addSegment(current)

                else -> current.append(char)
            }
            index++
        }
        segments.addSegment(current)
        return segments
    }

    /**
     * Pulls out the bodies of `$(...)` and `` `...` `` command substitutions,
     * including nested ones, so each can be analysed as a command in its own right.
     * Text inside single quotes is skipped because the shell performs no
     * substitution there.
     */
    internal fun extractSubstitutions(command: String): List<String> {
        val results = mutableListOf<String>()
        var index = 0
        var inSingleQuote = false

        while (index < command.length) {
            val char = command[index]
            index = when {
                inSingleQuote -> {
                    if (char == '\'') inSingleQuote = false
                    index + 1
                }
                char == '\'' -> { inSingleQuote = true; index + 1 }
                char == '\\' -> index + 2
                char == '`' -> consumeBacktick(command, index, results)
                char == '$' && command.getOrNull(index + 1) == '(' ->
                    consumeDollarParen(command, index, results)
                else -> index + 1
            }
        }
        return results
    }

    /** Returns the index to resume from after a `` `...` `` span, capturing its body. */
    private fun consumeBacktick(command: String, start: Int, out: MutableList<String>): Int {
        val end = command.indexOf('`', start + 1)
        if (end < 0) return command.length
        val body = command.substring(start + 1, end)
        out += body
        out += extractSubstitutions(body)
        return end + 1
    }

    /** Returns the index to resume from after a `$(...)` span, capturing its body. */
    private fun consumeDollarParen(command: String, start: Int, out: MutableList<String>): Int {
        var depth = 1
        var scan = start + 2
        while (scan < command.length && depth > 0) {
            when (command[scan]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth == 0) break
            scan++
        }
        if (depth != 0) return command.length
        val body = command.substring(start + 2, scan)
        out += body
        out += extractSubstitutions(body)
        return scan + 1
    }

    private fun MutableList<String>.addSegment(builder: StringBuilder) {
        val text = builder.toString().trim()
        if (text.isNotEmpty()) add(text)
        builder.setLength(0)
    }

    // --- rules -----------------------------------------------------------------

    private fun analyseWholeCommand(
        command: String,
        context: CommandContext,
    ): List<CommandFinding> {
        val normalised = command.replace(Regex("\\s+"), " ").trim()
        val applicable = if (context.proposedByAgent) {
            WHOLE_COMMAND_RULES + AGENT_ONLY_WHOLE_COMMAND_RULES
        } else {
            WHOLE_COMMAND_RULES
        }
        return applicable.mapNotNull { rule ->
            rule.pattern.find(normalised)?.let { match ->
                CommandFinding(
                    rule = rule.id,
                    risk = rule.risk,
                    matchedText = match.value,
                    explanation = rule.explanation,
                )
            }
        }
    }

    private fun analyseSegment(segment: String, context: CommandContext): List<CommandFinding> {
        val findings = mutableListOf<CommandFinding>()
        val normalised = segment.replace(Regex("\\s+"), " ").trim()

        findings += analyseRemove(normalised)

        RULES.forEach { rule ->
            val match = rule.pattern.find(normalised) ?: return@forEach
            findings += CommandFinding(
                rule = rule.id,
                risk = rule.risk,
                matchedText = match.value,
                explanation = rule.explanation,
            )
        }

        if (context.proposedByAgent) {
            AGENT_ONLY_RULES.forEach { rule ->
                val match = rule.pattern.find(normalised) ?: return@forEach
                findings += CommandFinding(
                    rule = rule.id,
                    risk = rule.risk,
                    matchedText = match.value,
                    explanation = rule.explanation,
                )
            }
        }

        return findings
    }

    /**
     * Analyses `rm` by parsing its flags rather than pattern-matching the string.
     *
     * A regex cannot do this correctly: flags may appear in any order, may be
     * bundled (`-rf`), split (`-r -f`), or spelled long (`--recursive --force`),
     * and `rm --no-preserve-root -rf /` puts a long flag before the short ones.
     * Reading the invocation the way the shell does removes a whole class of
     * bypasses that would otherwise each need their own pattern.
     */
    private fun analyseRemove(segment: String): List<CommandFinding> {
        val tokens = tokenise(segment)
        val commandIndex = tokens.indexOfFirst { it == "rm" || it.endsWith("/rm") }
        if (commandIndex < 0) return emptyList()

        // Only treat `rm` as the command being run, not as an argument to
        // something else (`grep rm history.txt`).
        val precedingIsPrefix = commandIndex == 0 ||
            tokens[commandIndex - 1] in COMMAND_PREFIXES
        if (!precedingIsPrefix) return emptyList()

        var recursive = false
        var force = false
        var noPreserveRoot = false
        val operands = mutableListOf<String>()

        tokens.drop(commandIndex + 1).forEach { token ->
            when {
                token == "--" -> Unit
                token == "--no-preserve-root" -> noPreserveRoot = true
                token == "--recursive" -> recursive = true
                token == "--force" -> force = true
                token.startsWith("--") -> Unit
                token.startsWith("-") && token.length > 1 -> {
                    token.drop(1).forEach { flag ->
                        when (flag) {
                            'r', 'R' -> recursive = true
                            'f' -> force = true
                        }
                    }
                }
                else -> operands += token.trim('"', '\'')
            }
        }

        val targetsRoot = operands.any { it == "/" || it == "/*" }
        return when {
            targetsRoot && recursive -> listOf(
                CommandFinding(
                    rule = "rm-rf-root",
                    risk = CommandRisk.BLOCKED,
                    matchedText = segment.take(120),
                    explanation = if (noPreserveRoot) {
                        "Deletes the entire filesystem, with the safety guard explicitly disabled."
                    } else {
                        "Deletes the entire filesystem from the root directory."
                    },
                ),
            )

            recursive && force -> listOf(
                CommandFinding(
                    rule = "rm-recursive-force",
                    risk = CommandRisk.DESTRUCTIVE,
                    matchedText = segment.take(120),
                    explanation = "Recursively deletes files without prompting; " +
                        "this cannot be undone.",
                ),
            )

            recursive -> listOf(
                CommandFinding(
                    rule = "rm-recursive",
                    risk = CommandRisk.DESTRUCTIVE,
                    matchedText = segment.take(120),
                    explanation = "Recursively deletes a directory tree.",
                ),
            )

            else -> emptyList()
        }
    }

    /** Splits a segment into shell words, keeping quoted spans intact. */
    private fun tokenise(segment: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var index = 0

        while (index < segment.length) {
            val char = segment[index]
            when {
                quote != null -> {
                    if (char == quote) quote = null else current.append(char)
                }
                char == '\'' || char == '"' -> quote = char
                char == '\\' && index + 1 < segment.length -> {
                    current.append(segment[index + 1])
                    index++
                }
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.setLength(0)
                    }
                }
                else -> current.append(char)
            }
            index++
        }
        if (current.isNotEmpty()) tokens += current.toString()
        return tokens
    }

    /** Wrappers after which the next token is still the command being run. */
    private val COMMAND_PREFIXES = setOf(
        "sudo", "doas", "env", "nohup", "time", "xargs", "nice", "ionice",
    )

    private fun pathEscapeFindings(
        command: String,
        context: CommandContext,
    ): List<CommandFinding> {
        val root = context.projectRoot?.trimEnd('/') ?: return emptyList()
        val findings = mutableListOf<CommandFinding>()

        PROTECTED_PATHS.forEach { path ->
            // Word boundary on the path so `/etc` matches `/etc/passwd` but not a
            // project directory that merely happens to contain the letters.
            val pattern = Regex("(?<![\\w/])${Regex.escape(path)}(?=/|\\s|$)")
            val match = pattern.find(command) ?: return@forEach
            if (root.startsWith(path)) return@forEach
            findings += CommandFinding(
                rule = "protected-path",
                risk = CommandRisk.DESTRUCTIVE,
                matchedText = match.value,
                explanation = "Touches $path, which is outside the project at $root.",
            )
        }

        return findings
    }

    private data class Rule(
        val id: String,
        val risk: CommandRisk,
        val pattern: Regex,
        val explanation: String,
    )

    private fun rule(id: String, risk: CommandRisk, pattern: String, explanation: String) =
        Rule(id, risk, Regex(pattern, RegexOption.IGNORE_CASE), explanation)

    /**
     * Paths that are never part of a project and whose modification breaks the
     * host rather than the app.
     */
    private val PROTECTED_PATHS = listOf(
        "/etc", "/root", "/boot", "/sys", "/proc", "/dev", "/bin", "/sbin",
        "/usr/bin", "/usr/sbin", "/lib", "/var/lib",
    )

    /**
     * Rules evaluated against the unsegmented command, for constructs whose danger
     * lies in the shell operators themselves.
     */
    private val WHOLE_COMMAND_RULES: List<Rule> = listOf(
        rule(
            id = "fork-bomb",
            risk = CommandRisk.BLOCKED,
            pattern = ":\\(\\)\\s*\\{.*\\|.*&.*\\}\\s*;?\\s*:",
            explanation = "This is a fork bomb: it will exhaust the host's process table.",
        ),
    )

    private val AGENT_ONLY_WHOLE_COMMAND_RULES: List<Rule> = listOf(
        rule(
            id = "remote-script-execution",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\b(curl|wget)\\b[^|]*\\|\\s*(sudo\\s+)?[a-z]*sh\\b",
            explanation = "Downloads a script and executes it immediately; the contents are " +
                "not visible for review.",
        ),
    )

    private val RULES: List<Rule> = listOf(
        // --- blocked -----------------------------------------------------------
        // `rm` is handled by analyseRemove, which parses flags instead of matching
        // text, because flag order and bundling defeat any single pattern.
        rule(
            id = "overwrite-block-device",
            risk = CommandRisk.BLOCKED,
            pattern = "\\b(dd|cat|cp)\\b[^|;]*\\bof=/dev/(sd|nvme|vd|hd|mmcblk)",
            explanation = "Writes directly to a block device, destroying the disk contents.",
        ),
        rule(
            id = "mkfs",
            risk = CommandRisk.BLOCKED,
            pattern = "\\bmkfs(\\.[a-z0-9]+)?\\b",
            explanation = "Formats a filesystem, erasing everything on it.",
        ),

        // --- destructive -------------------------------------------------------
        rule(
            id = "drop-database",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bdrop\\s+(database|schema|table)\\b",
            explanation = "Drops a database object, destroying its data permanently.",
        ),
        rule(
            id = "truncate-table",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\btruncate\\s+(table\\s+)?[\\w.\"`]+",
            explanation = "Empties a table irreversibly.",
        ),
        rule(
            id = "sql-delete-without-where",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bdelete\\s+from\\s+[\\w.\"`]+\\s*(;|\"|'|$)",
            explanation = "Deletes every row: there is no WHERE clause.",
        ),
        rule(
            id = "git-reset-hard",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bgit\\s+reset\\s+(--\\w+\\s+)*--hard\\b",
            explanation = "Discards all uncommitted changes in the working tree.",
        ),
        rule(
            id = "git-clean-force",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bgit\\s+clean\\b[^|;]*-[a-z]*f",
            explanation = "Deletes untracked files, including ones never committed anywhere.",
        ),
        rule(
            id = "git-push-force",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bgit\\s+push\\b[^|;]*(--force(?!-with-lease)|\\s-f\\b)",
            explanation = "Force-push overwrites remote history and can destroy others' commits. " +
                "Consider --force-with-lease.",
        ),
        rule(
            id = "git-branch-delete-force",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bgit\\s+branch\\s+(-D|--delete\\s+--force)\\b",
            explanation = "Deletes a branch even if it has unmerged commits.",
        ),
        rule(
            id = "docker-prune",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bdocker\\s+(system|volume|image|container)\\s+prune\\b",
            explanation = "Removes Docker resources; volume pruning destroys persistent data.",
        ),
        rule(
            id = "docker-compose-down-volumes",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bdocker(\\s+compose|-compose)\\s+down\\b[^|;]*(-v\\b|--volumes)",
            explanation = "Brings the stack down and deletes its volumes, including databases.",
        ),
        rule(
            id = "shutdown-reboot",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\b(shutdown|reboot|halt|poweroff|init\\s+0|init\\s+6)\\b",
            explanation = "Restarts or powers off the host, ending every session on it.",
        ),
        rule(
            id = "service-stop-restart",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\b(systemctl|service)\\s+(stop|restart|disable|mask)\\b",
            explanation = "Stops or restarts a service, interrupting whatever depends on it.",
        ),
        rule(
            id = "chmod-recursive-permissive",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bchmod\\s+(-[a-zA-Z]*R[a-zA-Z]*\\s+)+(777|a\\+rwx)",
            explanation = "Makes files world-writable throughout a tree, which is a security hole.",
        ),
        rule(
            id = "chown-recursive-root",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bchown\\s+(-[a-zA-Z]*R[a-zA-Z]*\\s+)+[^\\s]+\\s+/(\\s|$)",
            explanation = "Changes ownership across the whole filesystem.",
        ),
        rule(
            id = "history-wipe",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bhistory\\s+-c\\b|>\\s*~?/?\\.bash_history",
            explanation = "Clears shell history, which removes the record of what was run.",
        ),
        rule(
            id = "iptables-flush",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\b(iptables|nft)\\b[^|;]*(-F\\b|flush)",
            explanation = "Flushes firewall rules, which can expose the host or lock you out.",
        ),
        rule(
            id = "overwrite-authorized-keys",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = ">\\s*[^\\s>]*authorized_keys",
            explanation = "Overwrites authorized_keys; a mistake here locks you out of the host. " +
                "Use >> to append.",
        ),

        // --- caution -----------------------------------------------------------
        rule(
            id = "package-install",
            risk = CommandRisk.CAUTION,
            pattern = "\\b(apt|apt-get|yum|dnf|apk|pacman|brew)\\s+(install|remove|upgrade|update)\\b",
            explanation = "Changes installed system packages.",
        ),
        rule(
            id = "dependency-install",
            risk = CommandRisk.CAUTION,
            pattern = "\\b(npm|pnpm|yarn|bun|pip|pip3|composer|gem|cargo|go)\\s+(install|add|i|get)\\b",
            explanation = "Installs project dependencies, which runs third-party install scripts.",
        ),
        rule(
            id = "database-migration",
            risk = CommandRisk.CAUTION,
            pattern = "\\b(migrate|db:migrate|migration:run|alembic\\s+upgrade|rails\\s+db:migrate)\\b",
            explanation = "Applies database migrations, which change the live schema.",
        ),
        rule(
            id = "service-start",
            risk = CommandRisk.CAUTION,
            pattern = "\\b(systemctl|service)\\s+(start|reload|enable)\\b",
            explanation = "Changes the running state of a service.",
        ),
        rule(
            id = "sudo",
            risk = CommandRisk.CAUTION,
            pattern = "\\bsudo\\b|\\bdoas\\b",
            explanation = "Runs with elevated privileges.",
        ),
        rule(
            id = "git-push",
            risk = CommandRisk.CAUTION,
            pattern = "\\bgit\\s+push\\b",
            explanation = "Publishes commits to a remote others can see.",
        ),
        rule(
            id = "git-checkout-discard",
            risk = CommandRisk.CAUTION,
            pattern = "\\bgit\\s+(checkout|restore)\\s+(--\\s+)?\\.",
            explanation = "Discards local modifications to tracked files.",
        ),
        rule(
            id = "kill-process",
            risk = CommandRisk.CAUTION,
            pattern = "\\b(kill|pkill|killall)\\b",
            explanation = "Terminates running processes.",
        ),
        rule(
            id = "output-redirect-truncate",
            risk = CommandRisk.CAUTION,
            pattern = "(?<![>|])>(?!>)\\s*[^\\s|&>]+",
            explanation = "Redirects output with >, which replaces the target file's contents.",
        ),
    )

    /**
     * Extra scrutiny for anything the agent proposes.
     *
     * Piping a downloaded script into a shell is a legitimate, if unwise, thing for
     * a human to choose. It is never something an autonomous agent should do on the
     * user's server without an explicit look, because the content being executed is
     * not visible in the command itself.
     */
    private val AGENT_ONLY_RULES: List<Rule> = listOf(
        rule(
            id = "credential-file-read",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\b(cat|less|more|head|tail|strings)\\b[^|;]*(\\.env\\b|id_rsa\\b|id_ed25519\\b|\\.pem\\b|credentials\\b|\\.netrc\\b)",
            explanation = "Reads a file that normally holds secrets; its contents would enter " +
                "the AI conversation.",
        ),
        rule(
            id = "outbound-data-transfer",
            risk = CommandRisk.DESTRUCTIVE,
            pattern = "\\bcurl\\b[^|;]*(-d\\b|--data|-F\\b|-T\\b|--upload-file)",
            explanation = "Sends data from the server to an external endpoint.",
        ),
    )
}
