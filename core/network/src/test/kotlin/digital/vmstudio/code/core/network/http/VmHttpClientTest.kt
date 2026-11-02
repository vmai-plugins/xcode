package digital.vmstudio.code.core.network.http

import digital.vmstudio.code.core.common.net.NetworkMonitor
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class VmHttpClientTest {

    @Test
    fun `Hub server errors are not mislabeled as AI provider outages`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setBody("""{"code":"google_auth_not_configured","message":"Google account sign-in is not configured on this server."}""")
            )
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"upstream unavailable"}"""))
            server.start()

            val okHttp = OkHttpClient.Builder()
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> =
                            if (hostname == "vmstudio.digital" || hostname == "ai.vmstudio.digital") {
                                listOf(InetAddress.getByName("127.0.0.1"))
                            } else {
                                Dns.SYSTEM.lookup(hostname)
                            }
                    }
                )
                .build()
            val networkMonitor = object : NetworkMonitor {
                override val isOnline: Flow<Boolean> = flowOf(true)
                override fun isOnlineNow(): Boolean = true
            }
            val client = VmHttpClient(okHttp, networkMonitor, Dispatchers.IO)

            val hubRequest = Request.Builder()
                .url("http://vmstudio.digital:${server.port}/wp-json/xcodes/v1/auth/google")
                .get()
                .build()
            val hubResult = client.execute(hubRequest, RetryPolicy(maxAttempts = 1))

            assertTrue(hubResult is VmResult.Failure)
            val hubError = (hubResult as VmResult.Failure).error
            assertEquals("Server error (503)", hubError.summary)
            assertTrue(hubError.reason.orEmpty().contains("google_auth_not_configured"))

            val aiRequest = Request.Builder()
                .url("http://ai.vmstudio.digital:${server.port}/v1/models")
                .get()
                .build()
            val aiResult = client.execute(aiRequest, RetryPolicy(maxAttempts = 1))

            assertTrue(aiResult is VmResult.Failure)
            assertEquals("The provider is unavailable", (aiResult as VmResult.Failure).error.summary)
        }
    }
}
