package digital.vmstudio.code.core.ssh.metrics

/**
 * Parses the output of the diagnostics script [ServerMetricsCollector] runs.
 *
 * Pure and dependency-free so it can be tested against real transcripts of
 * procps-ng (Debian/Ubuntu), old procps and BusyBox (Alpine) output. Parsing is
 * defensive by design: any section that cannot be read yields an absent value on
 * [ServerMetrics], never an exception, because a partially-filled dashboard is
 * still worth more than an error for a host whose `ps` is unusual.
 */
object ServerMetricsParser {

    internal const val UPTIME_MARKER = "---VMS_UPTIME---"
    internal const val MEM_MARKER = "---VMS_MEM---"
    internal const val DISK_MARKER = "---VMS_DISK---"
    internal const val CPU_MARKER = "---VMS_CPU---"
    internal const val PS_MARKER = "---VMS_PS---"

    internal val SECTION_MARKERS =
        listOf(UPTIME_MARKER, MEM_MARKER, DISK_MARKER, CPU_MARKER, PS_MARKER)

    private const val MAX_PROCESSES = 8

    /** KiB per GiB: `df -kP` reports 1024-blocks, the UI shows GB. */
    private const val KIB_PER_GIB = 1_048_576.0

    /** `df -kP` output: Filesystem, 1024-blocks, Used, Available, Capacity, Mount. */
    private const val DISK_MIN_TOKENS = 6

    /** `ps -eo user,pid,pcpu,pmem,comm`: USER, PID, %CPU, %MEM, COMMAND. */
    private const val PS_ROW_TOKENS = 5

    /** ps failed to report %MEM; shown as zero rather than omitting the row. */
    private const val NO_MEM_PERCENT = 0.0

    // Column offsets, named so the row parser reads as a table.
    private const val PS_USER_COLUMN = 0
    private const val PS_PID_COLUMN = 1
    private const val PS_CPU_COLUMN = 2
    private const val PS_MEM_COLUMN = 3
    private const val PS_COMMAND_COLUMN = 4
    private const val DISK_TOTAL_COLUMN = 1
    private const val DISK_USED_COLUMN = 2

    private val WHITESPACE = Regex("\\s+")

    private val REGEX_LOAD_AVERAGE = Regex(
        "load average:\\s*([\\d.]+)(?:[,\\s]+([\\d.]+))?(?:[,\\s]+([\\d.]+))?",
    )

    fun parse(rawOutput: String, collectedAtMillis: Long): ServerMetrics {
        val sections = sections(rawOutput)
        val (uptime, loadAverage) = parseUptime(sections[UPTIME_MARKER].orEmpty())
        val memory = parseMemory(sections[MEM_MARKER].orEmpty())
        val disk = parseDisk(sections[DISK_MARKER].orEmpty())

        return ServerMetrics(
            cpuPercent = parseCpu(sections[CPU_MARKER].orEmpty()),
            ramTotalMb = memory?.first ?: 0,
            ramUsedMb = memory?.second ?: 0,
            diskTotalGb = disk?.second ?: 0.0,
            diskUsedGb = disk?.first ?: 0.0,
            uptime = uptime,
            loadAverage = loadAverage,
            topProcesses = parseProcesses(sections[PS_MARKER].orEmpty()),
            collectedAtMillis = collectedAtMillis,
        )
    }

    private fun sections(output: String): Map<String, List<String>> {
        val result = LinkedHashMap<String, MutableList<String>>()
        var current: String? = null
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val marker = SECTION_MARKERS.firstOrNull { trimmed.startsWith(it) }
            if (marker != null) {
                current = marker
                result.getOrPut(marker) { mutableListOf() }
            } else if (current != null && trimmed.isNotEmpty()) {
                result.getValue(current!!).add(trimmed)
            }
        }
        return result
    }

    /**
     * procps: ` 14:32:07 up 42 days,  3:14,  2 users,  load average: 0.15, 0.08, 0.01`
     * BusyBox omits the user count. Both end with the load averages.
     */
    private fun parseUptime(lines: List<String>): Pair<String?, List<Double>> {
        val text = lines.joinToString(" ")
        if (text.isEmpty()) return null to emptyList()

        val loadAverage = mutableListOf<Double>()
        REGEX_LOAD_AVERAGE.find(text)?.let { match ->
            match.groupValues.drop(1).forEach { group ->
                group.toDoubleOrNull()?.let(loadAverage::add)
            }
        }

        val uptime = if (text.contains(" up ")) {
            val tail = text.substringAfter(" up ")
            val beforeLoad = tail.indexOf("load average").let { index ->
                if (index >= 0) tail.substring(0, index) else tail
            }
            beforeLoad
                .trim()
                .replace(Regex(",?\\s*\\d+\\s+users?,?$"), "")
                .trim()
                .trimEnd(',', ' ')
                .ifBlank { null }
        } else {
            null
        }

        return uptime to loadAverage
    }

    /**
     * `free -m`: procps prints a header plus `Mem: total used free shared buff/cache
     * available`; BusyBox prints `Mem: total used free`. In both, tokens 1 and 2
     * after the label are total and used.
     */
    private fun parseMemory(lines: List<String>): Pair<Int, Int>? {
        val memLine = lines.firstOrNull { it.startsWith("Mem:") } ?: return null
        val tokens = memLine.split(WHITESPACE)
        val total = tokens.getOrNull(1)?.toIntOrNull() ?: return null
        val used = tokens.getOrNull(2)?.toIntOrNull() ?: return null
        if (total <= 0 || used < 0) return null
        return total to used
    }

    /**
     * `df -kP /`: Filesystem 1024-blocks Used Available Capacity Mounted-on.
     * Guarded on token count and numeric values, because a wrapped line from a
     * long device name leaves a fragment that must not parse as numbers.
     */
    private fun parseDisk(lines: List<String>): Pair<Double, Double>? =
        lines.asSequence()
            .map { it.split(WHITESPACE) }
            .filter { it.size >= DISK_MIN_TOKENS }
            .mapNotNull { tokens ->
                // Named column offsets keep this readable; the size filter above
                // guarantees the components exist.
                val totalKb = tokens[DISK_TOTAL_COLUMN].toLongOrNull() ?: return@mapNotNull null
                val usedKb = tokens[DISK_USED_COLUMN].toLongOrNull() ?: return@mapNotNull null
                if (totalKb <= 0 || usedKb < 0) {
                    null
                } else {
                    usedKb / KIB_PER_GIB to totalKb / KIB_PER_GIB
                }
            }
            .firstOrNull()

    /**
     * Three output shapes are handled:
     * - procps-ng:  `%Cpu(s):  1.7 us,  0.7 sy,  0.0 ni, 97.0 id, ...`
     * - old procps: `Cpu(s):  1.7%us,  0.7%sy, 97.0%id, ...`
     * - BusyBox:    `CPU:  12% usr  5% sys   0% nic  80% idle ...`
     *
     * Usage is `100 - idle` where idle is reported (the figure top's own summary
     * bar shows); otherwise user + system. Decimal commas from non-C locales are
     * normalised: list separators ("0.7, 0.0") are separated from decimal commas
     * ("1,7 us") by the space that follows a list separator.
     */
    private fun parseCpu(lines: List<String>): Double? {
        for (line in lines) {
            val normalized = line.replace(Regex(",\\s+"), " ")
            var idle: Double? = null
            var user = 0.0
            var system = 0.0

            REGEX_CPU_FIELDS.findAll(normalized).forEach { match ->
                val value = match.groupValues[1]
                    .trim()
                    .trimEnd('.', ',')
                    .replace(',', '.')
                    .toDoubleOrNull() ?: return@forEach
                when (match.groupValues[2]) {
                    "id", "idle" -> if (idle == null) idle = value
                    "us", "usr" -> user += value
                    "sy", "sys" -> system += value
                    else -> Unit
                }
            }

            val knownIdle = idle
            if (knownIdle != null) return (100.0 - knownIdle).coerceIn(0.0, 100.0)
            if (user > 0 || system > 0) return (user + system).coerceIn(0.0, 100.0)
        }
        return null
    }

    private val REGEX_CPU_FIELDS =
        Regex("([\\d.,]+)\\s*%?\\s*(us|sy|ni|id|wa|hi|si|st|usr|sys|nic|idle|io|irq|sirq|iowait|steal)")

    /** `ps -eo user,pid,pcpu,pmem,comm`: USER PID %CPU %MEM COMMAND. */
    private fun parseProcesses(lines: List<String>): List<ProcessItem> =
        lines.drop(1)
            .mapNotNull(::parseProcessRow)
            .sortedByDescending { it.cpuPercent }
            .take(MAX_PROCESSES)

    private fun parseProcessRow(line: String): ProcessItem? {
        val tokens = line.split(WHITESPACE, limit = PS_ROW_TOKENS)
        if (tokens.size < PS_ROW_TOKENS) return null
        val pid = tokens[PS_PID_COLUMN]
        if (pid.toIntOrNull() == null) return null
        val cpu = tokens[PS_CPU_COLUMN].replace(',', '.').toDoubleOrNull() ?: return null
        val mem = tokens[PS_MEM_COLUMN].replace(',', '.').toDoubleOrNull() ?: NO_MEM_PERCENT
        return ProcessItem(
            user = tokens[PS_USER_COLUMN],
            pid = pid,
            cpuPercent = cpu,
            memPercent = mem,
            command = tokens[PS_COMMAND_COLUMN].trim(),
        )
    }
}
