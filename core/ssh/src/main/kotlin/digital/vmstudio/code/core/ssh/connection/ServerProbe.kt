package digital.vmstudio.code.core.ssh.connection

import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.ssh.model.Server
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import java.util.concurrent.TimeUnit

/**
 * Learns what kind of machine we just connected to.
 *
 * Every value is best-effort. A hardened host may have no `uname`, a restricted
 * shell may refuse most of this, and a container may report nothing useful. The
 * probe therefore never fails the connection — an unknown OS is a missing label in
 * the UI, not a reason to refuse to work.
 */
internal object ServerProbe {

    /**
     * One round trip, written in POSIX shell only.
     *
     * `${SHELL:-}` and `$(...)` are POSIX; bashisms are avoided deliberately because
     * the login shell may be `sh`, `dash`, `ash` on Alpine, or `zsh`. Each value is
     * prefixed so partial output from a chatty login profile can still be parsed.
     */
    private const val PROBE_SCRIPT =
        "echo \"VMSHELL=\${SHELL:-}\"; " +
            "echo \"VMHOME=\${HOME:-}\"; " +
            "echo \"VMOS=\$(uname -s 2>/dev/null)\"; " +
            "echo \"VMKERNEL=\$(uname -r 2>/dev/null)\"; " +
            "echo \"VMARCH=\$(uname -m 2>/dev/null)\"; " +
            "echo \"VMHOST=\$(uname -n 2>/dev/null)\""

    private const val PROBE_TIMEOUT_MILLIS = 10_000L
    private const val TAG = "ServerProbe"

    suspend fun probe(
        client: SSHClient,
        server: Server,
        ioDispatcher: CoroutineDispatcher,
    ): ServerInfo = withContext(ioDispatcher) {
        val output = runCatching { runProbe(client) }
            .onFailure {
                VmLog.d(LogCategory.SSH, TAG, "Environment probe failed on ${server.name}")
            }
            .getOrDefault(emptyMap())

        val detectedShellPath = server.preferredShell?.takeIf { it.isNotBlank() }
            ?: output["VMSHELL"]?.takeIf { it.isNotBlank() }
            ?: FALLBACK_SHELL

        ServerInfo(
            shell = detectedShellPath.substringAfterLast('/').ifBlank { "sh" },
            shellPath = detectedShellPath,
            operatingSystem = output["VMOS"]?.takeIf { it.isNotBlank() },
            kernelVersion = output["VMKERNEL"]?.takeIf { it.isNotBlank() },
            architecture = output["VMARCH"]?.takeIf { it.isNotBlank() },
            hostname = output["VMHOST"]?.takeIf { it.isNotBlank() },
            homeDirectory = output["VMHOME"]?.takeIf { it.isNotBlank() },
            sshServerVersion = runCatching { client.transport.serverVersion }.getOrNull(),
        )
    }

    private suspend fun runProbe(client: SSHClient): Map<String, String> = runInterruptible {
        client.startSession().use { session ->
            val command = session.exec(PROBE_SCRIPT)
            val text = command.inputStream.bufferedReader().readText()
            command.join(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            text.lineSequence()
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0 || !line.startsWith("VM")) {
                        null
                    } else {
                        line.substring(0, index) to line.substring(index + 1).trim()
                    }
                }
                .toMap()
        }
    }

    /**
     * `/bin/sh` is required by POSIX and is the one shell that can be assumed to
     * exist. Falling back to bash here would break Alpine and BusyBox hosts.
     */
    private const val FALLBACK_SHELL = "/bin/sh"
}
