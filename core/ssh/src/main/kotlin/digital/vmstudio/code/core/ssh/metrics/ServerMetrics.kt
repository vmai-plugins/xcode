package digital.vmstudio.code.core.ssh.metrics

/**
 * One row of the process table, already ordered by CPU descending.
 */
data class ProcessItem(
    val user: String,
    val pid: String,
    val cpuPercent: Double,
    val memPercent: Double,
    val command: String,
)

/**
 * A point-in-time health sample from a remote machine.
 *
 * Every measurement is optional: minimal hosts, hardened images and containers may
 * not report all of them, and the UI renders a dash rather than a fabricated zero.
 * The same principle as [digital.vmstudio.code.core.ssh.connection.ServerInfo] —
 * an unknown value is a missing label, never a plausible guess.
 */
data class ServerMetrics(
    /** Total non-idle CPU, 0–100, or null when no source could be parsed. */
    val cpuPercent: Double? = null,
    val ramTotalMb: Int = 0,
    val ramUsedMb: Int = 0,
    val diskTotalGb: Double = 0.0,
    val diskUsedGb: Double = 0.0,
    /** Human-readable, as the host itself reports it: "42 days, 3:14". */
    val uptime: String? = null,
    /** 1, 5 and 15-minute load averages; fewer when the host reports fewer. */
    val loadAverage: List<Double> = emptyList(),
    val topProcesses: List<ProcessItem> = emptyList(),
    val collectedAtMillis: Long = 0L,
) {
    val ramFraction: Float?
        get() = if (ramTotalMb > 0) (ramUsedMb.toDouble() / ramTotalMb).toFloat() else null

    val diskFraction: Float?
        get() = if (diskTotalGb > 0) (diskUsedGb / diskTotalGb).toFloat() else null

    /** True when at least one measurement was parsed from the host's output. */
    val hasData: Boolean
        get() = cpuPercent != null ||
            ramTotalMb > 0 ||
            diskTotalGb > 0 ||
            uptime != null ||
            loadAverage.isNotEmpty() ||
            topProcesses.isNotEmpty()
}
