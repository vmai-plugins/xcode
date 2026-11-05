package digital.vmstudio.code.core.ai.antigravity

/**
 * Metadata for a Google Antigravity session discovered on a remote laptop or server.
 */
data class AntigravitySessionSummary(
    val id: String,
    val title: String,
    val timeIso: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val isLatest: Boolean = false,
)
