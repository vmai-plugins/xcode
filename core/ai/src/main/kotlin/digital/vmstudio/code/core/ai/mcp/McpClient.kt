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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for communicating with external MCP (Model Context Protocol) servers over HTTP JSON-RPC 2.0.
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
                        val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
                        val desc = (obj["description"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
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
                        (item?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: element.toString()
                    }
                } else {
                    response.result?.toString() ?: "(empty response)"
                }
            }
        }
    }

    private suspend fun executeRpc(
        server: McpServerConfig,
        rpcRequest: McpJsonRpcRequest,
    ): VmResult<McpJsonRpcResponse> {
        val payload = json.encodeToString(McpJsonRpcRequest.serializer(), rpcRequest)
        val builder = Request.Builder()
            .url(server.endpointUrl)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!server.apiKey.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer ${server.apiKey}")
        }

        return httpClient.execute(builder.build(), RetryPolicy(maxAttempts = 2))
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString(McpJsonRpcResponse.serializer(), body)
                }
            }
    }
}
