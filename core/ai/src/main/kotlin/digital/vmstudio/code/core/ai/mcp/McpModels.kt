package digital.vmstudio.code.core.ai.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Configuration for an external Model Context Protocol (MCP) server.
 * Supports standard HTTP/SSE endpoints, WordPress REST MCP bridge, and project-scoped servers.
 */
@Serializable
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val endpointUrl: String,
    val apiKey: String? = null,
    val isEnabled: Boolean = true,
    val serverType: String = "HTTP", // "HTTP", "WORDPRESS", "SSE"
    val projectPath: String? = null,
    val description: String = "",
)

/**
 * Tool metadata advertised by an MCP server via `tools/list`.
 */
@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject = buildJsonObject { put("type", "object") },
)

/**
 * Resource metadata advertised by an MCP server via `resources/list`.
 */
@Serializable
data class McpResourceDefinition(
    val uri: String,
    val name: String,
    val description: String = "",
    val mimeType: String? = null,
)

@Serializable
data class McpJsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String = UUID.randomUUID().toString(),
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class McpJsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: McpJsonRpcError? = null,
)

@Serializable
data class McpJsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

/**
 * A curated top-tier connector template displayed in the Connectors Marketplace.
 */
@Serializable
data class CuratedConnector(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val defaultEndpointUrl: String,
    val serverType: String = "HTTP", // "HTTP", "SSE", "WORDPRESS"
    val requiresApiKey: Boolean = true,
    val apiKeyPlaceholder: String = "Enter API Key or OAuth Token",
    val category: String = "Top Connectors",
)

object CuratedConnectorsCatalog {
    val TOP_10: List<CuratedConnector> = listOf(
        CuratedConnector(
            id = "google_drive",
            name = "Google Drive",
            description = "Search, read, and upload files instantly",
            author = "Google",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/gdrive",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Google OAuth Token or Drive API Key",
            category = "Productivity",
        ),
        CuratedConnector(
            id = "gmail",
            name = "Gmail",
            description = "Draft replies, summarize threads, & search your inbox",
            author = "Google",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/gmail",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Google OAuth Token or App Password",
            category = "Productivity",
        ),
        CuratedConnector(
            id = "google_calendar",
            name = "Google Calendar",
            description = "Manage your schedule and coordinate meetings effortlessly",
            author = "Google",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/calendar",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Google OAuth Token or Calendar Key",
            category = "Productivity",
        ),
        CuratedConnector(
            id = "microsoft_365",
            name = "Microsoft 365",
            description = "Connect your Outlook mail, calendar, and files",
            author = "Anthropic",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/m365",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Microsoft Graph Access Token",
            category = "Productivity",
        ),
        CuratedConnector(
            id = "slack",
            name = "Slack",
            description = "Send messages, create canvases, and fetch Slack data",
            author = "Slack",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/slack",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Slack Bot Token (xoxb-...) or User Token",
            category = "Communication",
        ),
        CuratedConnector(
            id = "canva",
            name = "Canva",
            description = "Search, create, autofill, and export Canva designs",
            author = "Canva",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/canva",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Canva Connect API Key",
            category = "Design",
        ),
        CuratedConnector(
            id = "notion",
            name = "Notion",
            description = "Connect your Notion workspace to search, update, and power workflows",
            author = "Notion",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/notion",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Notion Internal Integration Secret (secret_...)",
            category = "Productivity",
        ),
        CuratedConnector(
            id = "google_sheets",
            name = "Google Sheets",
            description = "Read, analyze, and update your spreadsheets",
            author = "Google",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/gsheets",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Google OAuth Token or Sheets API Key",
            category = "Productivity",
        ),
        CuratedConnector(
            id = "atlassian_mcp",
            name = "Atlassian MCP",
            description = "Search, read and update Jira, Confluence, Bitbucket, and Loom",
            author = "Atlassian",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/atlassian",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Atlassian API Token (email:token)",
            category = "Developer Tools",
        ),
        CuratedConnector(
            id = "figma",
            name = "Figma",
            description = "Generate diagrams, inspect design tokens, and extract code from Figma context",
            author = "Figma",
            defaultEndpointUrl = "https://mcp.vmstudio.digital/v1/figma",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Figma Personal Access Token (figd_...)",
            category = "Design",
        ),
        CuratedConnector(
            id = "wordpress",
            name = "WordPress (WP-CLI & REST)",
            description = "Manage WordPress posts, pages, plugins, themes, database, and files",
            author = "VMStudio",
            defaultEndpointUrl = "https://saketdhamashram.org/wp-json/mcp/v1",
            serverType = "WORDPRESS",
            requiresApiKey = true,
            apiKeyPlaceholder = "Application Password or VM Hub Key",
            category = "Developer Tools",
        ),
    )
}
