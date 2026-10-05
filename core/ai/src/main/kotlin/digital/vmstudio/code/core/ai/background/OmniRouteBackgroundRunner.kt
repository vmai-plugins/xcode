package digital.vmstudio.code.core.ai.background

import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.ConversationTurn
import digital.vmstudio.code.core.ai.omniroute.OmniRouteDialect
import digital.vmstudio.code.core.ai.omniroute.OmniRouteProvider
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the OmniRoute agent loop on the server instead of on the phone, so a task
 * keeps going when the screen is off or the app is closed.
 *
 * The runner is a single standard-library Python script (bundled as a resource)
 * that the app copies to `~/.xcodes/bin` over SSH and starts detached. It calls the
 * gateway itself and records progress in `~/.xcodes/runs/<id>`; the app lists,
 * reads and stops runs with short SSH commands. Its runs use ids starting with
 * [RUN_ID_PREFIX], which is how [BackgroundAgentRunner] tells them apart from
 * Claude Code's.
 *
 * Unattended, so nothing can ask for approval: read-only tools in Plan and Ask
 * modes, file edits in Auto-edit, commands only in Full auto. Production servers
 * only get read-only runs.
 */
@Singleton
class OmniRouteBackgroundRunner @Inject constructor(
    private val connectionManager: SshConnectionManager,
    private val remoteFileSystem: RemoteFileSystem,
    private val serverRepository: ServerRepository,
    private val omniRoute: OmniRouteProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    @Suppress("LongParameterList")
    suspend fun start(
        serverId: String,
        workingDirectory: String,
        prompt: String,
        model: String,
        permissionMode: AgentPermissionMode,
        history: List<ConversationTurn>,
        contextChars: Int,
    ): VmResult<String> = withContext(ioDispatcher) {
        val mode = when (val resolved = modeFor(serverId, permissionMode)) {
            is VmResult.Failure -> return@withContext resolved
            is VmResult.Success -> resolved.value
        }
        val home = when (val installed = ensureInstalled(serverId)) {
            is VmResult.Failure -> return@withContext installed
            is VmResult.Success -> installed.value
        }
        val access = when (val gateway = omniRoute.gatewayAccess()) {
            is VmResult.Failure -> return@withContext gateway
            is VmResult.Success -> gateway.value
        }

        val runId = RUN_ID_PREFIX + randomHex()
        val runDir = "$home/.xcodes/runs/$runId"
        val task = buildJsonObject {
            put("base_url", access.baseUrl)
            val isAnthropic = access.dialect == OmniRouteDialect.ANTHROPIC_MESSAGES
            put("dialect", if (isAnthropic) "anthropic" else "openai")
            put("model", model)
            put("prompt", prompt)
            put("cwd", workingDirectory)
            put("mode", mode)
            put("context_chars", contextChars)
            put("system_prompt", systemPrompt(workingDirectory, mode))
            putJsonArray("history") {
                history.filter { it.text.isNotBlank() }.forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.fromUser) "user" else "assistant")
                        put("content", turn.text)
                    }
                }
            }
        }.toString()

        // The run directory is created 0700 first, so the key file written into it
        // is never readable by other users, even for the moment before the runner
        // reads and deletes it. Both go over SFTP, never on a command line.
        val keyText = access.key.copyBytes()
        try {
            execute(serverId, "mkdir -m 700 ${quote(runDir)}")
                .then { remoteFileSystem.writeText(serverId, "$runDir/task.json", task) }
                .then { remoteFileSystem.writeText(serverId, "$runDir/key", String(keyText, Charsets.UTF_8)) }
                .then { execute(serverId, launchCommand(home, runDir, workingDirectory)) }
                .let { result ->
                    when (result) {
                        is VmResult.Failure -> result
                        is VmResult.Success -> {
                            VmLog.i(LogCategory.AI, TAG, "Server-side run $runId started")
                            VmResult.Success(runId)
                        }
                    }
                }
        } finally {
            keyText.fill(0)
            access.key.wipe()
        }
    }

    /** Runs recorded on the server, newest first; empty when the runner was never installed. */
    suspend fun list(serverId: String, workingDirectory: String? = null): VmResult<List<BackgroundRun>> =
        withContext(ioDispatcher) {
            val scope = workingDirectory?.let { " " + quote(it) }.orEmpty()
            when (val result = execute(serverId, "$RUNNER list$scope 2>/dev/null || echo '[]'")) {
                is VmResult.Failure -> result
                is VmResult.Success -> VmResult.Success(BackgroundRunParser.parse(result.value))
            }
        }

    suspend fun logs(serverId: String, runId: String): VmResult<String> =
        withContext(ioDispatcher) { execute(serverId, "$RUNNER logs ${quote(runId)}") }

    suspend fun stop(serverId: String, runId: String): VmResult<Unit> =
        withContext(ioDispatcher) { execute(serverId, "$RUNNER stop ${quote(runId)}").map() }

    suspend fun remove(serverId: String, runId: String): VmResult<Unit> =
        withContext(ioDispatcher) { execute(serverId, "$RUNNER rm ${quote(runId)}").map() }

    // --- internals -----------------------------------------------------------------

    private suspend fun modeFor(serverId: String, permissionMode: AgentPermissionMode): VmResult<String> {
        val mode = when (permissionMode) {
            AgentPermissionMode.PLAN, AgentPermissionMode.MANUAL -> "plan"
            AgentPermissionMode.ACCEPT_EDITS -> "edit"
            AgentPermissionMode.BYPASS -> "full"
        }
        if (mode == "plan") return VmResult.Success(mode)
        val server = when (val found = serverRepository.get(serverId)) {
            is VmResult.Failure -> return found
            is VmResult.Success -> found.value
        }
        return if (server.environment == ServerEnvironment.PRODUCTION) {
            VmResult.Failure(
                VmError.Ai(
                    summary = "Unattended changes are off for production servers",
                    reason = "A background run cannot ask before editing or running commands.",
                    suggestedAction = "Use Plan mode for a background run here, or run it in the foreground.",
                    retryable = false,
                    provider = "omniroute",
                ),
            )
        } else {
            VmResult.Success(mode)
        }
    }

    /** Copies the runner to the server when it is missing or outdated; returns the home directory. */
    private suspend fun ensureInstalled(serverId: String): VmResult<String> {
        val probe = "mkdir -p ~/.xcodes/bin ~/.xcodes/runs && chmod 700 ~/.xcodes ~/.xcodes/runs && " +
            "echo \"HOME=\$HOME\" && " +
            "(command -v python3 >/dev/null && echo PYTHON=yes || echo PYTHON=no) && " +
            "(python3 ~/.xcodes/bin/xcodes_agent.py version 2>/dev/null | sed 's/^/VERSION=/' || true)"
        val output = when (val result = execute(serverId, probe)) {
            is VmResult.Failure -> return result
            is VmResult.Success -> result.value
        }
        val values = output.lines().mapNotNull { line ->
            val name = line.substringBefore('=', "")
            if (name.isEmpty()) null else name to line.substringAfter('=').trim()
        }.toMap()
        val home = values["HOME"]?.takeIf { it.startsWith("/") }
            ?: return VmResult.Failure(installError("Could not find the home directory on the server."))
        if (values["PYTHON"] != "yes") {
            return VmResult.Failure(
                installError(
                    "Python 3 is needed on the server. Install it (e.g. apt install python3) and try again.",
                ),
            )
        }
        if (values["VERSION"] == RUNNER_VERSION) return VmResult.Success(home)

        val script = javaClass.getResourceAsStream(RUNNER_RESOURCE)?.bufferedReader()?.use { it.readText() }
            ?: return VmResult.Failure(installError("The runner is missing from this build of the app."))
        return when (val written = remoteFileSystem.writeText(serverId, "$home/$RUNNER_PATH", script)) {
            is VmResult.Failure -> written
            is VmResult.Success -> VmResult.Success(home)
        }
    }

    private fun launchCommand(home: String, runDir: String, workingDirectory: String): String {
        val run = "python3 ${quote("$home/$RUNNER_PATH")} run ${quote(runDir)} " +
            "> ${quote("$runDir/stdout.log")} 2>&1 < /dev/null"
        // setsid detaches from the SSH session so closing it cannot stop the run.
        return "cd ${quote(workingDirectory)} && " +
            "if command -v setsid >/dev/null; then setsid nohup $run & else nohup $run & fi; echo started"
    }

    private suspend fun execute(serverId: String, command: String): VmResult<String> =
        connectionManager.withSession(serverId) { session ->
            when (val result = session.execute(command, CommandLimits.QUICK)) {
                is VmResult.Failure -> result
                is VmResult.Success -> VmResult.Success(result.value.combinedOutput())
            }
        }

    private fun systemPrompt(workingDirectory: String, mode: String): String = buildString {
        append("You are an autonomous coding agent working in $workingDirectory on a server. ")
        append("Nobody is watching this run, so finish the task on your own and end with a short summary. ")
        append("Inspect before changing: list_directory, read_file, grep_search. ")
        append("Use web_fetch for documentation. ")
        when (mode) {
            "plan" -> append("This run is read-only: investigate and report, do not try to change files. ")
            "edit" -> append("You may edit files with edit_file and write_file; you cannot run commands. ")
            else -> append("You may edit files and run commands (builds, tests, git); check command output. ")
        }
        append("The user reads results on a phone: be brief.")
    }

    private fun installError(reason: String) = VmError.Ai(
        summary = "Could not set up background runs on the server",
        reason = reason,
        retryable = false,
        provider = "omniroute",
    )

    private fun randomHex(): String {
        val bytes = ByteArray(RUN_ID_BYTES).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private inline fun <T, R> VmResult<T>.then(next: (T) -> VmResult<R>): VmResult<R> = when (this) {
        is VmResult.Failure -> this
        is VmResult.Success -> next(value)
    }

    private fun VmResult<String>.map(): VmResult<Unit> = when (this) {
        is VmResult.Failure -> this
        is VmResult.Success -> VmResult.Success(Unit)
    }

    companion object {
        const val RUN_ID_PREFIX = "xo-"
        private const val TAG = "OmniRouteBackground"
        private const val RUNNER_VERSION = "2"
        private const val RUNNER_RESOURCE = "/xcodes/xcodes_agent.py"
        private const val RUNNER_PATH = ".xcodes/bin/xcodes_agent.py"
        private const val RUNNER = "python3 ~/.xcodes/bin/xcodes_agent.py"
        private const val RUN_ID_BYTES = 4

        /** Single-quotes for the shell; a quote inside becomes '\''. */
        fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
