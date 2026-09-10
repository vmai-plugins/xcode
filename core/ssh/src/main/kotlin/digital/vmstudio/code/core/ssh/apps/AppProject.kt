package digital.vmstudio.code.core.ssh.apps

/** The stack a scanned application folder is built on. */
enum class AppFramework(val displayName: String) {
    NODEJS("Node.js"),
    PYTHON("Python"),
    DOCKER("Docker"),
    GOLANG("Go"),
    RUST("Rust"),
    STATIC_HTML("HTML"),
    UNKNOWN("App"),
}

/** Process state of a scanned application. */
enum class AppStatus(val label: String) {
    RUNNING("Running"),
    STOPPED("Stopped"),
    CRASHED("Crashed"),
    UNKNOWN("Unknown"),
}

/**
 * One application discovered under the apps root on a server.
 *
 * Everything except [name], [path] and [framework] is optional: a minimal host
 * without pm2 or `ss` yields a card that names the app rather than fabricating
 * process data. The same "unknown is a missing label" rule as server metrics.
 *
 * Fields are values captured by the scanner, not live handles — refreshing the
 * list produces fresh instances.
 */
data class AppProject(
    val name: String,
    val path: String,
    val framework: AppFramework = AppFramework.UNKNOWN,
    /** Current git branch, when the folder is a git work tree. */
    val branch: String? = null,
    /** `origin` remote URL, when configured. */
    val remoteUrl: String? = null,
    val status: AppStatus = AppStatus.UNKNOWN,
    /** True when pm2 manages this app at all. */
    val pm2Managed: Boolean = false,
    val pid: String? = null,
    val port: Int? = null,
    val cpuPercent: Double? = null,
    val memoryBytes: Long? = null,
    /** Raw `pm_uptime` from pm2, in milliseconds. */
    val uptimeMillis: Long? = null,
) {
    val isRunning: Boolean get() = status == AppStatus.RUNNING

    val memoryMb: Int?
        get() = memoryBytes?.let { (it / BYTES_PER_MB).toInt() }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024
    }
}