package digital.vmstudio.code.core.ssh.apps

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses the output of [AppScanner]'s one-round-trip script into application
 * cards, pm2 process state and listening ports.
 *
 * Pure (apart from the Json object model) so it can be tested against real
 * transcripts. Every segment is best-effort: `pm2` or `ss` absent, a git folder
 * nested in another app, or a JSON field renamed by a future pm2 release all
 * degrade one field rather than failing the scan.
 */
object AppScannerParser {

    internal const val APP_MARKER = "---VMS_APP---"
    internal const val PM2_MARKER = "---VMS_PM2---"
    internal const val PORTS_MARKER = "---VMS_PORTS---"

    private const val KEY_PATH = "PATH"
    private const val KEY_NAME = "NAME"
    private const val KEY_FRAMEWORK = "FRAMEWORK"
    private const val KEY_BRANCH = "BRANCH"
    private const val KEY_REMOTE = "REMOTE"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val PID_BY_LINE = Regex("pid=(\\d+)")

    /** Index of the captured digits within the pid matcher (0 is the full match). */
    private const val PID_CAPTURE_GROUP = 1

    fun parse(rawOutput: String): List<AppProject> {
        val (appBlocks, pm2Lines, portLines) = splitSections(rawOutput)
        val pm2ByName = parsePm2(pm2Lines)
        val portByPid = parsePorts(portLines)

        return appBlocks.mapNotNull { block ->
            val name = block[KEY_NAME] ?: return@mapNotNull null
            val pm2 = pm2ByName[name]
            val pid = pm2?.pid
            AppProject(
                name = name,
                path = block[KEY_PATH] ?: name,
                framework = parseFramework(block[KEY_FRAMEWORK]),
                branch = block[KEY_BRANCH],
                remoteUrl = block[KEY_REMOTE],
                status = pm2?.status ?: AppStatus.UNKNOWN,
                pm2Managed = pm2 != null,
                pid = pid,
                port = pid?.let { portByPid[it] },
                cpuPercent = pm2?.cpuPercent,
                memoryBytes = pm2?.memoryBytes,
                uptimeMillis = pm2?.uptimeMillis,
            )
        }
    }

    // --- section splitting -------------------------------------------------------

    private data class Sections(
        val appBlocks: List<Map<String, String>>,
        val pm2Lines: List<String>,
        val portLines: List<String>,
    )

    private fun splitSections(output: String): Sections {
        val appBlocks = mutableListOf<Map<String, String>>()
        val pm2Lines = mutableListOf<String>()
        val portLines = mutableListOf<String>()

        var currentApp: MutableMap<String, String>? = null
        var sink: MutableList<String> = portLines

        for (rawLine in output.lineSequence()) {
            val trimmed = rawLine.trim()
            when {
                trimmed == APP_MARKER -> {
                    currentApp = LinkedHashMap()
                    appBlocks += currentApp
                    sink = portLines
                }
                trimmed == PM2_MARKER -> {
                    currentApp = null
                    sink = pm2Lines
                }
                trimmed == PORTS_MARKER -> {
                    currentApp = null
                    sink = portLines
                }
                else -> {
                    val app = currentApp
                    if (app != null) {
                        val index = trimmed.indexOf('=')
                        if (index > 0) {
                            val key = trimmed.substring(0, index)
                            if (key == KEY_PATH || key == KEY_NAME || key == KEY_FRAMEWORK ||
                                key == KEY_BRANCH || key == KEY_REMOTE
                            ) {
                                app[key] = trimmed.substring(index + 1).trim()
                            }
                        }
                    } else if (trimmed.isNotEmpty()) {
                        sink += trimmed
                    }
                }
            }
        }

        return Sections(appBlocks, pm2Lines, portLines)
    }

    // --- pm2 ---------------------------------------------------------------------

    private data class Pm2Info(
        val status: AppStatus,
        val pid: String?,
        val cpuPercent: Double?,
        val memoryBytes: Long?,
        val uptimeMillis: Long?,
    )

    private fun parsePm2(lines: List<String>): Map<String, Pm2Info> {
        val text = lines.joinToString("\n")
        if (text.isBlank() || !text.trimStart().startsWith("[")) return emptyMap()
        val array = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray
            ?: return emptyMap()

        val result = LinkedHashMap<String, Pm2Info>()
        array.forEach { element ->
            val run = element as? JsonObject ?: return@forEach
            val name = run.string(JSON_NAME) ?: return@forEach
            val env = run[JSON_PM2_ENV] as? JsonObject
            val monit = run[JSON_MONIT] as? JsonObject
            result[name] = Pm2Info(
                status = mapStatus(env?.string(JSON_STATUS)),
                pid = run.long(JSON_PID)?.toString(),
                cpuPercent = monit?.double(JSON_CPU),
                memoryBytes = monit?.long(JSON_MEMORY),
                uptimeMillis = env?.long(JSON_UPTIME),
            )
        }
        return result
    }

    private fun mapStatus(raw: String?): AppStatus = when (raw) {
        "online", "launching" -> AppStatus.RUNNING
        "errored" -> AppStatus.CRASHED
        "stopped", "stopping" -> AppStatus.STOPPED
        else -> AppStatus.UNKNOWN
    }

    // --- ports -------------------------------------------------------------------

    /**
     * `ss -tulnp` lines: `LISTEN 0 128 0.0.0.0:3000 0.0.0.0:* users:(("node",pid=1234,..))`
     * The local-address field is token 4; the pid rides in the process field.
     * A pid may be absent (no permission), in which case the row cannot be joined.
     */
    private fun parsePorts(lines: List<String>): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        lines.forEach { line ->
            val tokens = line.split(WHITESPACE)
            if (tokens.size < 4) return@forEach
            val port = tokens[3].substringAfterLast(':').toIntOrNull() ?: return@forEach
            val pid = PID_BY_LINE.find(line)?.groupValues?.getOrNull(PID_CAPTURE_GROUP) ?: return@forEach
            result.putIfAbsent(pid, port)
        }
        return result
    }

    // --- framework ---------------------------------------------------------------

    private fun parseFramework(raw: String?): AppFramework = when (raw?.lowercase()) {
        "nodejs" -> AppFramework.NODEJS
        "python" -> AppFramework.PYTHON
        "docker" -> AppFramework.DOCKER
        "golang" -> AppFramework.GOLANG
        "rust" -> AppFramework.RUST
        "statichtml" -> AppFramework.STATIC_HTML
        else -> AppFramework.UNKNOWN
    }

    // --- Json object helpers, matching the AI layer's idioms ----------------------

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private val WHITESPACE = Regex("\\s+")

    private const val JSON_NAME = "name"
    private const val JSON_PID = "pid"
    private const val JSON_PM2_ENV = "pm2_env"
    private const val JSON_STATUS = "status"
    private const val JSON_UPTIME = "pm_uptime"
    private const val JSON_MONIT = "monit"
    private const val JSON_CPU = "cpu"
    private const val JSON_MEMORY = "memory"
}