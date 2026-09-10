package digital.vmstudio.code.core.ssh.metrics

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.asFailure
import digital.vmstudio.code.core.common.result.asSuccess
import digital.vmstudio.code.core.common.result.errorOrNull
import digital.vmstudio.code.core.common.result.getOrNull
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.connection.SshSession
import javax.inject.Inject

/**
 * Collects a health sample from one server in a single round trip.
 *
 * The script is read-only and written for maximum portability rather than
 * accuracy: every command is POSIX or near-universal (`uptime`, `free`, `df`,
 * `top`, `ps`), each is guarded with `2>/dev/null`, and every value is prefixed
 * with a marker so a chatty login profile or a missing command degrades one
 * section instead of failing the sample. These are the app's own diagnostics,
 * authored and reviewed here — not user- or agent-supplied commands — so they do
 * not go through the interactive approval flow, exactly like the environment
 * probe at connect time.
 */
class ServerMetricsCollector @Inject constructor() {

    suspend fun collect(session: SshSession): VmResult<ServerMetrics> {
        val result = session.execute(DIAGNOSTICS_SCRIPT, CommandLimits.QUICK)
        val stdout = result.getOrNull()?.stdout
            ?: return result.errorOrNull()?.asFailure()
                ?: VmError.Unexpected("No response from ${session.server.name}").asFailure()

        val metrics = ServerMetricsParser.parse(stdout, System.currentTimeMillis())
        if (metrics.hasData) return metrics.asSuccess()

        return VmError.Command(
            summary = "The server did not report any metrics",
            reason = "The diagnostics commands (uptime, free, df, top, ps) produced no readable output.",
            suggestedAction = "Open a terminal session on this server and run 'uptime' to check " +
                "that basic commands work in this account's environment.",
            retryable = true,
            command = DIAGNOSTICS_SCRIPT,
        ).asFailure()
    }

    private companion object {
        val DIAGNOSTICS_SCRIPT = listOf(
            "echo ${ServerMetricsParser.UPTIME_MARKER}; uptime 2>/dev/null",
            "echo ${ServerMetricsParser.MEM_MARKER}; free -m 2>/dev/null",
            "echo ${ServerMetricsParser.DISK_MARKER}; df -kP / 2>/dev/null | tail -n 1",
            "echo ${ServerMetricsParser.CPU_MARKER}; " +
                "top -bn1 2>/dev/null | grep -E 'Cpu\\(s\\)|^%Cpu|^CPU:' | head -n 2",
            "echo ${ServerMetricsParser.PS_MARKER}; " +
                "ps -eo user,pid,pcpu,pmem,comm --sort=-pcpu 2>/dev/null | head -n 9",
        ).joinToString("; ")
    }
}
