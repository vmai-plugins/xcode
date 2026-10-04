package digital.vmstudio.code.core.ai.mcp

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.network.http.VmHttpClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpClientTest {

    @Test
    fun `listTools parses tools correctly from JSON-RPC response`() = runTest {
        val mockHttpClient = mockk<VmHttpClient>()
        val responseJson = """
            {
                "jsonrpc": "2.0",
                "id": "1",
                "result": {
                    "tools": [
                        {
                            "name": "weather",
                            "description": "Get current weather",
                            "inputSchema": {
                                "type": "object",
                                "properties": {"city": {"type": "string"}}
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        coEvery { mockHttpClient.execute(any(), any()) } returns VmResult.Success(responseJson)

        val client = McpClient(mockHttpClient)
        val config = McpServerConfig(
            id = "server-1",
            name = "Test Server",
            endpointUrl = "http://localhost:8080/mcp",
        )

        val result = client.listTools(config)
        assertTrue(result is VmResult.Success)
        val tools = (result as VmResult.Success).value
        assertEquals(1, tools.size)
        assertEquals("weather", tools.first().name)
        assertEquals("Get current weather", tools.first().description)
    }

    @Test
    fun `callTool parses text content from tool call result`() = runTest {
        val mockHttpClient = mockk<VmHttpClient>()
        val responseJson = """
            {
                "jsonrpc": "2.0",
                "id": "1",
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": "The temperature is 22C."
                        }
                    ]
                }
            }
        """.trimIndent()

        coEvery { mockHttpClient.execute(any(), any()) } returns VmResult.Success(responseJson)

        val client = McpClient(mockHttpClient)
        val config = McpServerConfig(
            id = "server-1",
            name = "Test Server",
            endpointUrl = "http://localhost:8080/mcp",
        )

        val result = client.callTool(config, "weather", buildJsonObject { put("city", "London") })
        assertTrue(result is VmResult.Success)
        assertEquals("The temperature is 22C.", (result as VmResult.Success).value)
    }
}
