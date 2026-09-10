package digital.vmstudio.code.core.ssh.apps

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.asFailure
import digital.vmstudio.code.core.common.result.asSuccess
import digital.vmstudio.code.core.common.result.errorOrNull
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.getOrNull
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import javax.inject.Inject

/**
 * Discovers applications under a server's apps root in a single SSH round trip.
 *
 * Like [digital.vmstudio.code.core.ssh.connection.ServerProbe], the script is
 * app-authored, read-only and POSIX: it walks the apps root for directories,
 * fingerprints each one for a framework, and asks pm2 and `ss` about live state.
 * Because it is the app's own diagnostics it does not pass through the approval
 * flow — the same treatment the environment probe gets at connect time.
 */
class AppScanner @Inject constructor(
    private val connectionManager: SshConnectionManager,
) {

    suspend fun scan(serverId: String, appsRoot: String = DEFAULT_APPS_ROOT): VmResult<List<AppProject>> =
        connectionManager.session(serverId).flatMap { session ->
            val result = session.execute(
                scriptFor(session.serverInfo.homeDirectory, appsRoot),
                CommandLimits(timeoutMillis = SCAN_TIMEOUT_MILLIS, maxOutputBytes = MAX_OUTPUT_BYTES),
            )
            val output = result.getOrNull()?.stdout
                ?: return@flatMap result.errorOrNull()?.asFailure()
                    ?: VmError.Unexpected("No response from the apps scan").asFailure()

            val apps = AppScannerParser.parse(output)
            if (apps.isEmpty()) {
                return@flatMap VmError.NotFound(
                    summary = "No apps found under $appsRoot",
                    reason = "The scan ran but found no subdirectories, or none were readable.",
                    suggestedAction = "Create a project folder under the apps root, or open " +
                        "the terminal to scaffold one.",
                ).asFailure()
            }
            apps.asSuccess()
        }

    /**
     * Expands a leading `~` in Kotlin using the home directory the connection
     * probe already discovered, so the path can then be single-quoted without
     * worrying that quoting defeats tilde expansion (single quotes would make the
     * shell treat `~/apps` as a literal relative directory).
     */
    private fun scriptFor(home: String?, appsRoot: String): String {
        val expanded = expandHome(home, appsRoot)
        val quoted = expanded.replace("'", """'\''""")
        return SCAN_SCRIPT_TEMPLATE.replace(APPS_ROOT_PLACEHOLDER, "'$quoted'")
    }

    private fun expandHome(home: String?, appsRoot: String): String = when {
        home == null || home.isBlank() -> appsRoot
        appsRoot == "~" -> home
        appsRoot.startsWith("~/") -> home + appsRoot.substring(1)
        else -> appsRoot
    }

    private companion object {
        const val DEFAULT_APPS_ROOT = "~/apps"
        const val APPS_ROOT_PLACEHOLDER = "@@APPS_ROOT@@"
        const val SCAN_TIMEOUT_MILLIS = 20_000L
        const val MAX_OUTPUT_BYTES = 4 shl 20

        val SCAN_SCRIPT_TEMPLATE = listOf(
            "APPS_ROOT=@@APPS_ROOT@@",
            "echo ${AppScannerParser.APP_MARKER}",
            "find \"\$APPS_ROOT\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | while IFS= read -r dir; do",
            "  echo ${AppScannerParser.APP_MARKER}",
            "  echo \"PATH=\$dir\"",
            "  echo \"NAME=\$(basename \"\$dir\")\"",
            "  if [ -f \"\$dir/package.json\" ]; then echo \"FRAMEWORK=nodejs\"",
            "  elif [ -f \"\$dir/requirements.txt\" ] || [ -f \"\$dir/pyproject.toml\" ] || [ -f \"\$dir/Pipfile\" ]; then echo \"FRAMEWORK=python\"",
            "  elif [ -f \"\$dir/docker-compose.yml\" ] || [ -f \"\$dir/docker-compose.yaml\" ] || [ -f \"\$dir/Dockerfile\" ]; then echo \"FRAMEWORK=docker\"",
            "  elif [ -f \"\$dir/go.mod\" ]; then echo \"FRAMEWORK=golang\"",
            "  elif [ -f \"\$dir/Cargo.toml\" ]; then echo \"FRAMEWORK=rust\"",
            "  elif [ -f \"\$dir/index.html\" ]; then echo \"FRAMEWORK=statichtml\"",
            "  else echo \"FRAMEWORK=unknown\"; fi",
            "  branch=\$(git -C \"\$dir\" rev-parse --abbrev-ref HEAD 2>/dev/null)",
            "  if [ -n \"\$branch\" ]; then echo \"BRANCH=\$branch\"; fi",
            "  remote=\$(git -C \"\$dir\" config --get remote.origin.url 2>/dev/null)",
            "  if [ -n \"\$remote\" ]; then echo \"REMOTE=\$remote\"; fi",
            "done",
            "echo ${AppScannerParser.PM2_MARKER}",
            "pm2 jlist 2>/dev/null",
            "echo ${AppScannerParser.PORTS_MARKER}",
            "ss -tulnp 2>/dev/null",
        ).joinToString("\n")
    }
}