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
            id = "wordpress",
            name = "WordPress MCP Bridge",
            description = "Inspect database, manage posts, query WP-CLI and run companion tools via VM Project Hub",
            author = "VMStudio",
            defaultEndpointUrl = "https://vmstudio.digital/wp-json/xcodes/v1/mcp",
            serverType = "WORDPRESS",
            requiresApiKey = false,
            apiKeyPlaceholder = "Optional Hub or App Key",
            category = "Developer Tools",
        ),
        CuratedConnector(
            id = "brave_search",
            name = "Brave Search MCP",
            description = "Real-time web search and news discovery for AI coding agents",
            author = "Brave Software",
            defaultEndpointUrl = "https://api.search.brave.com/res/v1/web/search",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Brave Search API Key (BSA...)",
            category = "Search & Web",
        ),
        CuratedConnector(
            id = "github_mcp",
            name = "GitHub MCP Server",
            description = "Search repositories, read code, create pull requests and manage issues",
            author = "GitHub",
            defaultEndpointUrl = "https://api.github.com/mcp",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "GitHub Personal Access Token (ghp_...)",
            category = "Version Control",
        ),
        CuratedConnector(
            id = "postgres_mcp",
            name = "PostgreSQL MCP",
            description = "Execute read/write SQL queries, inspect table schemas and run migrations",
            author = "Model Context Protocol",
            defaultEndpointUrl = "http://localhost:5432/mcp",
            serverType = "HTTP",
            requiresApiKey = false,
            apiKeyPlaceholder = "postgres://user:pass@host:5432/db",
            category = "Databases",
        ),
        CuratedConnector(
            id = "sqlite_mcp",
            name = "SQLite Database MCP",
            description = "Inspect local SQLite databases, execute schema introspection and run queries",
            author = "Model Context Protocol",
            defaultEndpointUrl = "http://localhost:8080/mcp",
            serverType = "HTTP",
            requiresApiKey = false,
            apiKeyPlaceholder = "sqlite:///path/to/db.sqlite",
            category = "Databases",
        ),
        CuratedConnector(
            id = "puppeteer_mcp",
            name = "Puppeteer Browser MCP",
            description = "Headless browser automation, screenshot capture, and web page scraping",
            author = "Model Context Protocol",
            defaultEndpointUrl = "http://localhost:3000/mcp",
            serverType = "HTTP",
            requiresApiKey = false,
            apiKeyPlaceholder = "Optional Auth Token",
            category = "Web Automation",
        ),
        CuratedConnector(
            id = "memory_mcp",
            name = "Memory & Knowledge Graph",
            description = "Persistent graph-based memory and semantic context across AI conversations",
            author = "Anthropic",
            defaultEndpointUrl = "http://localhost:8000/mcp",
            serverType = "HTTP",
            requiresApiKey = false,
            apiKeyPlaceholder = "Optional Bearer Token",
            category = "Memory & Context",
        ),
        CuratedConnector(
            id = "slack",
            name = "Slack MCP",
            description = "Send notifications, fetch channel history and collaborate with your team",
            author = "Slack",
            defaultEndpointUrl = "https://slack.com/api/mcp",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Slack Bot Token (xoxb-...)",
            category = "Communication",
        ),
        CuratedConnector(
            id = "canva",
            name = "Canva",
            description = "Search, create, autofill, and export Canva designs",
            author = "Canva",
            defaultEndpointUrl = "https://api.canva.com/rest/v1/mcp",
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
            defaultEndpointUrl = "https://api.notion.com/v1/mcp",
            serverType = "HTTP",
            requiresApiKey = true,
            apiKeyPlaceholder = "Notion Internal Integration Secret (secret_...)",
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
    )
}
