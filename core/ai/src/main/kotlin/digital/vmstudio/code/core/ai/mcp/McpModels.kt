package digital.vmstudio.code.core.ai.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Configuration for an external Model Context Protocol (MCP) server.
 */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val apiKey: String? = null,
    val isEnabled: Boolean = true,
)

/**
 * Tool metadata advertised by an MCP server via `tools/list`.
 */
@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject,
)

@Serializable
data class McpJsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
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
