package digital.vmstudio.code.core.ai.omniroute

import digital.vmstudio.code.core.ai.background.AgentWakeLockManager
import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.model.AgentRunConfig
import digital.vmstudio.code.core.ai.omniroute.agent.OmniRouteAgentLoop
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.SseEvent
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OmniRouteProviderTest {

    @Test
    fun `partial response followed by a stream failure is reported as failed`() = runBlocking {
        val httpClient = mockk<VmHttpClient>()
        val credentialStore = mockk<SecureCredentialStore>()
        val preferences = object : UserPreferencesSource {
            override val preferences: Flow<UserPreferences> = flowOf(
                UserPreferences(
                    aiBaseUrl = "https://vmstudio.digital",
                    aiApiKeyCredentialId = "gateway-key",
                    aiSelectedModelId = "test-model",
                    aiToolsEnabled = false,
                ),
            )
        }
        val agentLoop = mockk<OmniRouteAgentLoop>(relaxed = true)
        val wakeLockManager = mockk<AgentWakeLockManager>(relaxed = true)
        coEvery { credentialStore.read("gateway-key") } returns VmResult.Success(Secret.of("test-key"))
        every { httpClient.stream(any()) } returns flow {
            emit(
                SseEvent(
                    name = null,
                    data = """{"choices":[{"delta":{"content":"partial reply"}}]}""",
                    isDone = false,
                ),
            )
            throw IOException("connection dropped")
        }

        val provider = OmniRouteProvider(
            httpClient = httpClient,
            credentialStore = credentialStore,
            preferences = preferences,
            agentLoop = agentLoop,
            wakeLockManager = wakeLockManager,
        )
        val events = provider.run(
            AgentRunConfig(serverId = "", workingDirectory = "", prompt = "test"),
        ).toList()

        assertTrue(events.any { it == AgentEvent.AssistantDelta("partial reply") })
        assertTrue(events.any { it is AgentEvent.Failed })
        assertFalse(events.any { it is AgentEvent.Completed })
        coVerify(exactly = 0) { httpClient.execute(any(), any<RetryPolicy>()) }
        assertTrue((events.last() as AgentEvent.Failed).error.summary.isNotBlank())
    }
}
