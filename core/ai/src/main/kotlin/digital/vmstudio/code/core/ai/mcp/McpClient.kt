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

        val primaryResult = executeRpc(server, rpcRequest).flatMap { response ->
            parseToolsFromRpcResponse(response)
        }

        if (primaryResult is VmResult.Failure && server.serverType.equals("WORDPRESS", ignoreCase = true)) {
            val err = primaryResult.error.summary + " " + (primaryResult.error.reason ?: "")
            val isNotFound = (primaryResult.error as? digital.vmstudio.code.core.common.error.VmError.Network)?.statusCode == 404 ||
                err.contains("404") ||
                err.contains("rest_no_route", ignoreCase = true) ||
                err.contains("not found", ignoreCase = true)
            if (isNotFound) {
                return fetchWordPressLegacyTools(server)
            }
        }
        return primaryResult
    }

    private fun parseToolsFromRpcResponse(response: McpJsonRpcResponse): VmResult<List<McpToolDefinition>> = vmCatching {
        if (response.error != null) {
            throw IllegalStateException("MCP error (${response.error.code}): ${response.error.message}")
        }
        val resultObj = response.result as? JsonObject
            ?: throw IllegalStateException("Expected object result from tools/list")
        val toolsArray = (resultObj["tools"] as? JsonArray)
            ?: (resultObj["result"] as? JsonArray)
            ?: JsonArray(emptyList())

        parseToolsArray(toolsArray)
    }

    private fun parseToolsArray(toolsArray: JsonArray): List<McpToolDefinition> {
        return toolsArray.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                val fnObj = obj["function"] as? JsonObject
                val name = (obj["name"] as? JsonPrimitive)?.content
                    ?: (fnObj?.get("name") as? JsonPrimitive)?.content
                    ?: return@mapNotNull null
                val desc = (obj["description"] as? JsonPrimitive)?.content
                    ?: (fnObj?.get("description") as? JsonPrimitive)?.content
                    ?: ""
                val schema = (obj["inputSchema"] as? JsonObject)
                    ?: (obj["parameters"] as? JsonObject)
                    ?: (fnObj?.get("parameters") as? JsonObject)
                    ?: buildJsonObject { put("type", "object") }
                McpToolDefinition(name = name, description = desc, inputSchema = schema)
            }.getOrNull()
        }
    }

    private suspend fun fetchWordPressLegacyTools(server: McpServerConfig): VmResult<List<McpToolDefinition>> {
        val root = extractSiteRoot(server.endpointUrl)
        val legacyUrl = "$root/wp-json/xcodes/v1/mcp/tools"
        val masterKey = digital.vmstudio.code.core.common.preferences.UserPreferences.COMPANION_MASTER_KEY
        val effectiveKey = server.apiKey?.trim()?.takeIf { it.isNotBlank() } ?: masterKey

        val request = Request.Builder()
            .url(legacyUrl)
            .header("Accept", "application/json")
            .header("X-VMPJ-Key", effectiveKey)
            .header("X-VMPJ-Companion-Key", masterKey)
            .header("Authorization", "Bearer $effectiveKey")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy(maxAttempts = 1)).flatMap { body ->
            vmCatching {
                val rootObj = json.parseToJsonElement(body).jsonObject
                val toolsArray = (rootObj["tools"] as? JsonArray)
                    ?: (rootObj["result"] as? JsonArray)
                    ?: JsonArray(emptyList())
                parseToolsArray(toolsArray)
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

        val primaryResult = executeRpc(server, rpcRequest).flatMap { response ->
            parseToolCallResponse(response)
        }
        if (primaryResult is VmResult.Failure && server.serverType.equals("WORDPRESS", ignoreCase = true)) {
            val err = primaryResult.error.summary + " " + (primaryResult.error.reason ?: "")
            val isNotFound = (primaryResult.error as? digital.vmstudio.code.core.common.error.VmError.Network)?.statusCode == 404 ||
                err.contains("404") ||
                err.contains("rest_no_route", ignoreCase = true) ||
                err.contains("not found", ignoreCase = true)
            if (isNotFound) {
                return executeWordPressLegacyTool(server, toolName, arguments)
            }
        }
        return primaryResult
    }

    private fun parseToolCallResponse(response: McpJsonRpcResponse): VmResult<String> = vmCatching {
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
            val resultField = resultObj?.get("result")
            if (resultField != null) {
                (resultField as? JsonPrimitive)?.content ?: resultField.toString()
            } else {
                response.result?.toString() ?: "(empty response)"
            }
        }
    }

    private suspend fun executeWordPressLegacyTool(
        server: McpServerConfig,
        toolName: String,
        arguments: JsonObject,
    ): VmResult<String> {
        val root = extractSiteRoot(server.endpointUrl)
        val legacyUrl = "$root/wp-json/xcodes/v1/mcp/execute"
        val masterKey = digital.vmstudio.code.core.common.preferences.UserPreferences.COMPANION_MASTER_KEY
        val effectiveKey = server.apiKey?.trim()?.takeIf { it.isNotBlank() } ?: masterKey

        val body = buildJsonObject {
            put("tool", toolName)
            put("arguments", arguments)
            if (!server.projectPath.isNullOrBlank()) {
                put("workspace_path", server.projectPath)
            }
        }.toString()

        val request = Request.Builder()
            .url(legacyUrl)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-VMPJ-Key", effectiveKey)
            .header("X-VMPJ-Companion-Key", masterKey)
            .header("Authorization", "Bearer $effectiveKey")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return httpClient.execute(request, RetryPolicy(maxAttempts = 1)).flatMap { respBody ->
            vmCatching {
                val rootObj = runCatching { json.parseToJsonElement(respBody).jsonObject }.getOrNull()
                val resultField = rootObj?.get("result")
                when {
                    resultField is JsonPrimitive -> resultField.content
                    resultField != null -> resultField.toString()
                    rootObj?.containsKey("content") == true -> {
                        val contentArray = rootObj["content"] as? JsonArray
                        contentArray?.joinToString("\n") { (it as? JsonObject)?.get("text")?.let { t -> (t as? JsonPrimitive)?.content } ?: it.toString() } ?: respBody
                    }
                    else -> respBody
                }
            }
        }
    }

    private fun extractSiteRoot(url: String): String {
        val raw = url.trim().removeSuffix("/")
        val withScheme = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            "https://$raw"
        }
        return withScheme.substringBefore("/wp-json")
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
        val rawEndpoint = server.endpointUrl.trim().removeSuffix("/")
        val normalizedUrl = when {
            server.serverType.equals("WORDPRESS", ignoreCase = true) -> {
                val fullUrl = if (rawEndpoint.startsWith("http://", ignoreCase = true) || rawEndpoint.startsWith("https://", ignoreCase = true)) {
                    rawEndpoint
                } else {
                    "https://$rawEndpoint"
                }
                when {
                    fullUrl.endsWith("/wp-json/mcp/v1", ignoreCase = true) -> fullUrl
                    fullUrl.endsWith("/wp-json/mcp", ignoreCase = true) -> "$fullUrl/v1"
                    fullUrl.endsWith("/wp-json", ignoreCase = true) -> "$fullUrl/mcp/v1"
                    fullUrl.contains("/wp-json/xcodes/v1", ignoreCase = true) -> {
                        if (fullUrl.endsWith("/mcp")) fullUrl else "$fullUrl/mcp"
                    }
                    fullUrl.contains("/wp-json") -> fullUrl
                    else -> "$fullUrl/wp-json/mcp/v1"
                }
            }
            rawEndpoint.startsWith("http://", ignoreCase = true) || rawEndpoint.startsWith("https://", ignoreCase = true) -> rawEndpoint
            else -> "https://$rawEndpoint"
        }

        val request = vmCatching {
            val builder = Request.Builder()
                .url(normalizedUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

            val masterKey = digital.vmstudio.code.core.common.preferences.UserPreferences.COMPANION_MASTER_KEY
            if (!server.apiKey.isNullOrBlank()) {
                val key = server.apiKey.trim().replace("\r", "").replace("\n", "")
                val authHeader = if (key.startsWith("Bearer ", ignoreCase = true)) key else "Bearer $key"
                builder.header("Authorization", authHeader)
                if (server.serverType.equals("WORDPRESS", ignoreCase = true)) {
                    builder.header("X-VMPJ-Key", key)
                    builder.header("X-VMPJ-Companion-Key", masterKey)
                }
            } else if (server.serverType.equals("WORDPRESS", ignoreCase = true)) {
                builder.header("Authorization", "Bearer $masterKey")
                builder.header("X-VMPJ-Key", masterKey)
                builder.header("X-VMPJ-Companion-Key", masterKey)
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
                    val parsed = runCatching { json.decodeFromString(McpJsonRpcResponse.serializer(), body) }.getOrNull()
                    if (parsed != null && (parsed.result != null || parsed.error != null)) {
                        parsed
                    } else {
                        val root = json.parseToJsonElement(body)
                        McpJsonRpcResponse(
                            jsonrpc = "2.0",
                            id = rpcRequest.id,
                            result = root,
                        )
                    }
                }
            }
    }
}
