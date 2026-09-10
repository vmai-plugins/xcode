package digital.vmstudio.code.core.ssh.apps

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.command.CommandResult
import javax.inject.Inject

/**
 * Start, stop, restart and inspect applications on a server.
 *
 * Every action here is a real remote command, and the state-changing ones
 * (start, stop, restart, clone, pull) go through [CommandGuard.run] — the same
 * gate the terminal and the agent use — so the classification, production
 * escalation and approval policy apply identically to a button tap and a typed
 * command. Logs and inspection are read-only and also run through the guard,
 * which keeps a single path for every command in the app.
 */
class AppProcessManager @Inject constructor(
    private val guard: CommandGuard,
) {

    suspend fun start(serverId: String, app: AppProject): VmResult<CommandResult> =
        guard.run(serverId, startCommand(app), CommandLimits.LONG_RUNNING)

    suspend fun stop(serverId: String, app: AppProject): VmResult<CommandResult> =
        guard.run(serverId, stopCommand(app))

    suspend fun restart(serverId: String, app: AppProject): VmResult<CommandResult> =
        guard.run(serverId, restartCommand(app))

    suspend fun logs(serverId: String, app: AppProject, lines: Int = DEFAULT_LOG_LINES): VmResult<CommandResult> =
        guard.run(serverId, logsCommand(app, lines))

    suspend fun gitPull(serverId: String, app: AppProject): VmResult<CommandResult> =
        guard.run(serverId, "cd ${sh(app.path)} && git pull", CommandLimits.LONG_RUNNING)

    suspend fun cloneFromGitHub(
        serverId: String,
        repoUrl: String,
        appsRoot: String,
        customName: String? = null,
        branch: String? = null,
    ): VmResult<CommandResult> {
        val url = repoUrl.trim()
        val derived = url.substringAfterLast('/').removeSuffix(".git").trim()
        val folder = customName?.trim()?.takeIf { it.isNotBlank() }
            ?: derived.takeIf { it.isNotBlank() }
            ?: FALLBACK_FOLDER_NAME
        val branchFlag = branch?.trim()?.takeIf { it.isNotBlank() }?.let { "-b ${sh(it)} "}.orEmpty()
        val command = "git clone $branchFlag${sh(url)} ${sh("$appsRoot/$folder")}"
        return guard.run(serverId, command, CommandLimits.LONG_RUNNING)
    }

    // --- command builders ---------------------------------------------------------

    private fun startCommand(app: AppProject): String = when (app.framework) {
        AppFramework.NODEJS -> listOf(
            "cd ${sh(app.path)}",
            "(pm2 describe ${sh(app.name)} >/dev/null 2>&1 && pm2 restart ${sh(app.name)}",
            "|| pm2 start npm --name ${sh(app.name)} -- start",
            "|| pm2 start index.js --name ${sh(app.name)}",
            "|| pm2 start server.js --name ${sh(app.name)})",
        ).joinToString(" ")

        AppFramework.PYTHON -> listOf(
            "cd ${sh(app.path)}",
            "(pm2 describe ${sh(app.name)} >/dev/null 2>&1 && pm2 restart ${sh(app.name)}",
            "|| (test -f main.py && pm2 start python3 --name ${sh(app.name)} -- main.py)",
            "|| (test -f app.py && pm2 start python3 --name ${sh(app.name)} -- app.py)",
            "|| (test -f server.py && pm2 start python3 --name ${sh(app.name)} -- server.py))",
        ).joinToString(" ")

        AppFramework.DOCKER -> "cd ${sh(app.path)} && (docker compose up -d || docker-compose up -d)"

        AppFramework.GOLANG -> listOf(
            "cd ${sh(app.path)}",
            "(go build -o app_bin . && pm2 start ./app_bin --name ${sh(app.name)})",
        ).joinToString(" ")

        AppFramework.RUST -> listOf(
            "cd ${sh(app.path)}",
            "(cargo build --release && pm2 start ./target/release/${sh(app.name)} --name ${sh(app.name)})",
        ).joinToString(" ")

        AppFramework.STATIC_HTML -> "cd ${sh(app.path)} && " +
            "pm2 start npx --name ${sh(app.name)} -- serve -p ${app.port ?: DEFAULT_STATIC_PORT} ."

        AppFramework.UNKNOWN -> "cd ${sh(app.path)} && " +
            "(pm2 start npm --name ${sh(app.name)} -- start " +
            "|| pm2 start python3 --name ${sh(app.name)} -- main.py)"
    }

    private fun stopCommand(app: AppProject): String = when (app.framework) {
        AppFramework.DOCKER -> "cd ${sh(app.path)} && (docker compose down || docker-compose down)"
        else -> "pm2 stop ${sh(app.name)} || true"
    }

    private fun restartCommand(app: AppProject): String = when (app.framework) {
        AppFramework.DOCKER ->
            "cd ${sh(app.path)} && (docker compose restart ${sh(app.name)} || docker-compose restart)"
        else -> "pm2 restart ${sh(app.name)}"
    }

    private fun logsCommand(app: AppProject, lines: Int): String = when (app.framework) {
        AppFramework.DOCKER ->
            "cd ${sh(app.path)} && (docker compose logs --tail=$lines || docker-compose logs --tail=$lines)"
        else -> "pm2 logs ${sh(app.name)} --lines $lines --nostream"
    }

    /** Single-quotes for the shell: embedded quotes become POSIX `'\''`. */
    private fun sh(value: String): String = "'" + value.replace("'", """'\''""") + "'"

    private companion object {
        const val DEFAULT_LOG_LINES = 100
        const val DEFAULT_STATIC_PORT = 3000
        const val FALLBACK_FOLDER_NAME = "app"
    }
}