package digital.vmstudio.code.core.ai.mcp

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpManager @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val mcpClient: McpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    val servers: Flow<List<McpServerConfig>> = preferencesRepository.preferences.map { prefs ->
        parseServers(prefs.mcpServersJson)
    }

    suspend fun getServers(): List<McpServerConfig> {
        val prefs = preferencesRepository.preferences.first()
        return parseServers(prefs.mcpServersJson)
    }

    suspend fun getEnabledServers(projectPath: String? = null): List<McpServerConfig> {
        return getServers().filter { server ->
            server.isEnabled && (
                server.projectPath.isNullOrBlank() ||
                    projectPath.isNullOrBlank() ||
                    server.projectPath.trim() == projectPath.trim()
            )
        }
    }

    suspend fun addOrUpdateServer(config: McpServerConfig): VmResult<Unit> = vmCatching {
        val current = getServers().toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
        } else {
            current.add(config)
        }
        preferencesRepository.setMcpServersJson(json.encodeToString(current))
    }

    suspend fun removeServer(id: String): VmResult<Unit> = vmCatching {
        val current = getServers().filterNot { it.id == id }
        preferencesRepository.setMcpServersJson(json.encodeToString(current))
    }

    suspend fun toggleServer(id: String, enabled: Boolean): VmResult<Unit> = vmCatching {
        val current = getServers().map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        preferencesRepository.setMcpServersJson(json.encodeToString(current))
    }

    suspend fun testServer(server: McpServerConfig): VmResult<List<McpToolDefinition>> {
        return mcpClient.listTools(server)
    }

    suspend fun listAllTools(projectPath: String? = null): Map<McpServerConfig, List<McpToolDefinition>> {
        val enabled = getEnabledServers(projectPath)
        val result = mutableMapOf<McpServerConfig, List<McpToolDefinition>>()
        for (server in enabled) {
            when (val toolsResult = mcpClient.listTools(server)) {
                is VmResult.Success -> result[server] = toolsResult.value
                is VmResult.Failure -> result[server] = emptyList()
            }
        }
        return result
    }

    suspend fun executeMcpTool(
        serverNameOrId: String?,
        toolName: String,
        arguments: JsonObject,
        projectPath: String? = null,
    ): VmResult<String> {
        val enabled = getEnabledServers(projectPath)
        if (enabled.isEmpty()) {
            return VmResult.Failure(
                VmError.Validation(
                    summary = "No active MCP servers",
                    reason = "No Model Context Protocol servers are configured or enabled in Settings > Connectors > MCP.",
                    suggestedAction = "Configure an MCP server or enable an existing one under Settings > Connectors.",
                ),
            )
        }

        val targetServer = if (!serverNameOrId.isNullOrBlank()) {
            enabled.firstOrNull { it.id == serverNameOrId || it.name.equals(serverNameOrId, ignoreCase = true) }
                ?: return VmResult.Failure(
                    VmError.NotFound(
                        summary = "MCP server not found",
                        reason = "Could not find an active MCP server matching \"$serverNameOrId\".",
                        suggestedAction = "Check your configured MCP servers in Settings > Connectors.",
                    ),
                )
        } else {
            enabled.first()
        }

        return mcpClient.callTool(targetServer, toolName, arguments)
    }

    suspend fun readMcpResource(
        serverNameOrId: String?,
        uri: String,
        projectPath: String? = null,
    ): VmResult<String> {
        val enabled = getEnabledServers(projectPath)
        if (enabled.isEmpty()) {
            return VmResult.Failure(
                VmError.Validation(
                    summary = "No active MCP servers",
                    reason = "No Model Context Protocol servers are configured or enabled.",
                    suggestedAction = "Configure an MCP server in Settings > Connectors.",
                ),
            )
        }

        val targetServer = if (!serverNameOrId.isNullOrBlank()) {
            enabled.firstOrNull { it.id == serverNameOrId || it.name.equals(serverNameOrId, ignoreCase = true) }
                ?: enabled.first()
        } else {
            enabled.first()
        }

        return mcpClient.readResource(targetServer, uri)
    }

    fun createFromCurated(
        curated: CuratedConnector,
        apiKey: String? = null,
        endpointUrl: String? = null,
        projectPath: String? = null,
    ): McpServerConfig {
        return McpServerConfig(
            id = UUID.randomUUID().toString(),
            name = curated.name,
            endpointUrl = endpointUrl?.trim()?.ifBlank { curated.defaultEndpointUrl } ?: curated.defaultEndpointUrl,
            apiKey = apiKey?.trim(),
            isEnabled = true,
            serverType = curated.serverType,
            projectPath = projectPath?.trim(),
            description = curated.description,
        )
    }

    fun createWordPressPreset(
        siteUrl: String,
        name: String = "WordPress Site",
        apiKey: String? = null,
        projectPath: String? = null,
    ): McpServerConfig {
        val trimmed = siteUrl.trim().removeSuffix("/")
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val cleanRoot = withScheme
            .substringBefore("/wp-admin")
            .substringBefore("/wp-json")
            .removeSuffix("/")

        val hostName = cleanRoot.removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore(":")
        val effectiveName = if (name.isNotBlank() && name != "WordPress Site") name else if (hostName.isNotBlank()) hostName else "WordPress Site"

        val endpoint = when {
            trimmed.endsWith("/wp-json/mcp/v1", ignoreCase = true) -> trimmed
            trimmed.endsWith("/wp-json/mcp", ignoreCase = true) -> "$trimmed/v1"
            trimmed.contains("/wp-json/xcodes/v1", ignoreCase = true) -> {
                if (trimmed.endsWith("/mcp") || trimmed.endsWith("/mcp/tools")) trimmed else "$trimmed/mcp"
            }
            trimmed.contains("/wp-json/mcp", ignoreCase = true) -> trimmed
            else -> "$cleanRoot/wp-json/xcodes/v1/mcp"
        }
        val effectiveApiKey = apiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: digital.vmstudio.code.core.common.preferences.UserPreferences.COMPANION_MASTER_KEY

        return McpServerConfig(
            id = UUID.randomUUID().toString(),
            name = effectiveName,
            endpointUrl = endpoint,
            apiKey = effectiveApiKey,
            isEnabled = true,
            serverType = "WORDPRESS",
            projectPath = projectPath?.trim(),
            description = "WordPress MCP bridge for posts, pages, themes, plugins, and database operations.",
        )
    }

    private fun parseServers(rawJson: String?): List<McpServerConfig> {
        if (rawJson.isNullOrBlank() || rawJson.trim() == "[]") return emptyList()
        return try {
            json.decodeFromString<List<McpServerConfig>>(rawJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
