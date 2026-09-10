package digital.vmstudio.code.core.ai.provider

import digital.vmstudio.code.core.ai.claudecode.ClaudeCodeCliProvider
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiProviderRegistry.checkHealth] exists so the agent screen's automatic check and
 * Settings' explicit "Run test" don't each independently re-probe the same server —
 * these tests pin down that the sharing, the TTL and the "never cache a failure"
 * rule actually hold, not just the classification the earlier duplication code did.
 */
class AiProviderRegistryTest {

    private fun health(available: Boolean) = AiProviderHealth(
        kind = AiProviderKind.CLAUDE_CODE_CLI,
        isAvailable = available,
    )

    private fun registry(claudeCode: ClaudeCodeCliProvider = mockk(relaxed = true)) =
        AiProviderRegistry(
            claudeCode = claudeCode,
            omniRoute = mockk(relaxed = true),
            preferences = mockk<UserPreferencesSource> {
                every { preferences } returns flowOf(UserPreferences())
            },
        )

    @Test
    fun `a second call within the ttl reuses the result rather than probing again`() = runTest {
        val provider = mockk<ClaudeCodeCliProvider>(relaxed = true) {
            coEvery { kind } returns AiProviderKind.CLAUDE_CODE_CLI
            coEvery { checkHealth("srv-1") } returns VmResult.Success(health(true))
        }
        val registry = registry(provider)

        registry.checkHealth(provider, "srv-1")
        registry.checkHealth(provider, "srv-1")

        coVerify(exactly = 1) { provider.checkHealth("srv-1") }
    }

    @Test
    fun `forceRefresh always probes live even with a fresh cache entry`() = runTest {
        val provider = mockk<ClaudeCodeCliProvider>(relaxed = true) {
            coEvery { kind } returns AiProviderKind.CLAUDE_CODE_CLI
            coEvery { checkHealth("srv-1") } returns VmResult.Success(health(true))
        }
        val registry = registry(provider)

        registry.checkHealth(provider, "srv-1")
        registry.checkHealth(provider, "srv-1", forceRefresh = true)

        coVerify(exactly = 2) { provider.checkHealth("srv-1") }
    }

    @Test
    fun `different servers are cached independently`() = runTest {
        val provider = mockk<ClaudeCodeCliProvider>(relaxed = true) {
            coEvery { kind } returns AiProviderKind.CLAUDE_CODE_CLI
            coEvery { checkHealth(any()) } returns VmResult.Success(health(true))
        }
        val registry = registry(provider)

        registry.checkHealth(provider, "srv-1")
        registry.checkHealth(provider, "srv-2")

        coVerify(exactly = 1) { provider.checkHealth("srv-1") }
        coVerify(exactly = 1) { provider.checkHealth("srv-2") }
    }

    @Test
    fun `a failure is never cached, so the next call probes again`() = runTest {
        val error = VmError.Network(summary = "unreachable")
        val provider = mockk<ClaudeCodeCliProvider>(relaxed = true) {
            coEvery { kind } returns AiProviderKind.CLAUDE_CODE_CLI
            coEvery { checkHealth("srv-1") } returns VmResult.Failure(error)
        }
        val registry = registry(provider)

        registry.checkHealth(provider, "srv-1")
        registry.checkHealth(provider, "srv-1")

        coVerify(exactly = 2) { provider.checkHealth("srv-1") }
    }

    @Test
    fun `a forced failure clears the cache so the next normal call re-probes too`() = runTest {
        // Regression case for the reason this exists: the server goes down between
        // the agent screen's automatic check and a manual "Run test" tap, and the
        // stale "available" cached seconds earlier must not survive either call.
        val provider = mockk<ClaudeCodeCliProvider>(relaxed = true) {
            coEvery { kind } returns AiProviderKind.CLAUDE_CODE_CLI
        }
        val registry = registry(provider)

        coEvery { provider.checkHealth("srv-1") } returns VmResult.Success(health(true))
        registry.checkHealth(provider, "srv-1")

        coEvery { provider.checkHealth("srv-1") } returns
            VmResult.Failure(VmError.Network(summary = "down"))
        val forced = registry.checkHealth(provider, "srv-1", forceRefresh = true)
        assertTrue("forceRefresh must bypass the cache", forced is VmResult.Failure)

        // Nothing is cached after a failure, so this call reaches the provider too
        // rather than quietly falling back to the success from before the outage.
        val next = registry.checkHealth(provider, "srv-1")
        assertTrue("a failure must not leave a stale success behind", next is VmResult.Failure)

        coVerify(exactly = 3) { provider.checkHealth("srv-1") }
    }

    @Test
    fun `a successful result is returned as-is on a cache hit`() = runTest {
        val expected = health(true).copy(version = "2.1.0")
        val provider = mockk<ClaudeCodeCliProvider>(relaxed = true) {
            coEvery { kind } returns AiProviderKind.CLAUDE_CODE_CLI
            coEvery { checkHealth("srv-1") } returns VmResult.Success(expected)
        }
        val registry = registry(provider)

        registry.checkHealth(provider, "srv-1")
        val cached = registry.checkHealth(provider, "srv-1")

        assertEquals(VmResult.Success(expected), cached)
    }
}
