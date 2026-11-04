package digital.vmstudio.code.core.ai.mcp

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for communicating with external Model Context Protocol (MCP) servers
 * over HTTP JSON-RPC 2.0 (compatible with WordPress MCP, custom app MCP, and SSE bridges).
 */
@Singleton
class McpClient @Inject constructor(
    private val httpClient: VmHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    /**
     * Queries an MCP server for its list of available tools.
     */
    suspend fun listTools(server: McpServerConfig): VmResult<List<McpToolDefinition>> {
        val rpcRequest = McpJsonRpcRequest(
            id = UUID.randomUUID().toString(),
            method = "tools/list",
            params = buildJsonObject {},
        )

        return executeRpc(server, rpcRequest).flatMap { response ->
            vmCatching {
                if (response.error != null) {
                    throw IllegalStateException("MCP error (${response.error.code}): ${response.error.message}")
                }
                val resultObj = response.result as? JsonObject
                    ?: throw IllegalStateException("Expected object result from tools/list")
                val toolsArray = resultObj["tools"] as? JsonArray ?: JsonArray(emptyList())

                toolsArray.mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        val name = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                        val desc = (obj["description"] as? JsonPrimitive)?.content ?: ""
                        val schema = obj["inputSchema"] as? JsonObject ?: buildJsonObject { put("type", "object") }
                        McpToolDefinition(name = name, description = desc, inputSchema = schema)
                    }.getOrNull()
                }
            }
        }
    }

    /**
     * Calls a specific tool on the remote MCP server.
     */
    suspend fun callTool(
        server: McpServerConfig,
        toolName: String,
        arguments: JsonObject,
    ): VmResult<String> {
        val params = buildJsonObject {
            put("name", toolName)
            put("arguments", arguments)
        }
        val rpcRequest = McpJsonRpcRequest(
            id = UUID.randomUUID().toString(),
            method = "tools/call",
            params = params,
        )

        return executeRpc(server, rpcRequest).flatMap { response ->
            vmCatching {
                if (response.error != null) {
                    throw IllegalStateException("MCP tool execution error: ${response.error.message}")
                }
                val resultObj = response.result as? JsonObject
                val contentArray = resultObj?.get("content") as? JsonArray
                if (contentArray != null && contentArray.isNotEmpty()) {
                    contentArray.joinToString("\n") { element ->
                        val item = element as? JsonObject
                        (item?.get("text") as? JsonPrimitive)?.content ?: element.toString()
                    }
                } else {
                    response.result?.toString() ?: "(empty response)"
                }
            }
        }
    }

    /**
     * Queries an MCP server for its list of available resources.
     */
    suspend fun listResources(server: McpServerConfig): VmResult<List<McpResourceDefinition>> {
        val rpcRequest = McpJsonRpcRequest(
            id = UUID.randomUUID().toString(),
            method = "resources/list",
            params = buildJsonObject {},
        )

        return executeRpc(server, rpcRequest).flatMap { response ->
            vmCatching {
                if (response.error != null) {
                    throw IllegalStateException("MCP error (${response.error.code}): ${response.error.message}")
                }
                val resultObj = response.result as? JsonObject
                    ?: throw IllegalStateException("Expected object result from resources/list")
                val resourcesArray = resultObj["resources"] as? JsonArray ?: JsonArray(emptyList())

                resourcesArray.mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        val uri = (obj["uri"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                        val name = (obj["name"] as? JsonPrimitive)?.content ?: uri
                        val desc = (obj["description"] as? JsonPrimitive)?.content ?: ""
                        val mime = (obj["mimeType"] as? JsonPrimitive)?.content
                        McpResourceDefinition(uri = uri, name = name, description = desc, mimeType = mime)
                    }.getOrNull()
                }
            }
        }
    }

    /**
     * Reads a specific resource by URI from the MCP server.
     */
    suspend fun readResource(server: McpServerConfig, uri: String): VmResult<String> {
        val params = buildJsonObject {
            put("uri", uri)
        }
        val rpcRequest = McpJsonRpcRequest(
            id = UUID.randomUUID().toString(),
            method = "resources/read",
            params = params,
        )

        return executeRpc(server, rpcRequest).flatMap { response ->
            vmCatching {
                if (response.error != null) {
                    throw IllegalStateException("MCP resource error: ${response.error.message}")
                }
                val resultObj = response.result as? JsonObject
                val contents = resultObj?.get("contents") as? JsonArray
                if (contents != null && contents.isNotEmpty()) {
                    contents.joinToString("\n") { element ->
                        val item = element as? JsonObject
                        (item?.get("text") as? JsonPrimitive)?.content ?: element.toString()
                    }
                } else {
                    response.result?.toString() ?: "(empty resource)"
                }
            }
        }
    }

    private suspend fun executeRpc(
        server: McpServerConfig,
        rpcRequest: McpJsonRpcRequest,
    ): VmResult<McpJsonRpcResponse> {
        val payload = json.encodeToString(McpJsonRpcRequest.serializer(), rpcRequest)
        val rawEndpoint = server.endpointUrl.trim()
        val normalizedUrl = when {
            rawEndpoint.startsWith("http://", ignoreCase = true) || rawEndpoint.startsWith("https://", ignoreCase = true) -> rawEndpoint
            else -> "https://$rawEndpoint"
        }

        val request = vmCatching {
            val builder = Request.Builder()
                .url(normalizedUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (!server.apiKey.isNullOrBlank()) {
                val key = server.apiKey.trim().replace("\r", "").replace("\n", "")
                val authHeader = if (key.startsWith("Bearer ", ignoreCase = true)) key else "Bearer $key"
                builder.header("Authorization", authHeader)
                if (server.serverType.equals("WORDPRESS", ignoreCase = true)) {
                    builder.header("X-VMPJ-Key", key)
                }
            }

            if (!server.projectPath.isNullOrBlank()) {
                val cleanProjectPath = server.projectPath.trim().replace("\r", "").replace("\n", "")
                builder.header("X-MCP-Project-Path", cleanProjectPath)
            }
            builder.build()
        }.let {
            when (it) {
                is VmResult.Success -> it.value
                is VmResult.Failure -> return VmResult.Failure(
                    VmError.Network(
                        summary = "Invalid MCP endpoint or header configuration",
                        reason = it.error.summary + (it.error.reason?.let { r -> ": $r" } ?: ""),
                    ),
                )
            }
        }

        return httpClient.execute(request, RetryPolicy(maxAttempts = 2))
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString(McpJsonRpcResponse.serializer(), body)
                }
            }
    }
}
