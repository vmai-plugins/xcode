package digital.vmstudio.code.core.ssh.command

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.errorOrNull
import digital.vmstudio.code.core.common.result.getOrNull
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.connection.SshConnectionState
import digital.vmstudio.code.core.ssh.connection.SshSession
import digital.vmstudio.code.core.ssh.host.HostKeyVerdict
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.model.ServerDraft
import digital.vmstudio.code.core.ssh.model.ServerGroup
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the wiring, not the classification.
 *
 * [CommandSafetyTest] already pins down which commands are dangerous. What was
 * missing — and what let a fully tested safety engine sit unused — is any test that
 * something actually *consults* it before running a command. These are those tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommandGuardTest {

    private val server = Server(
        id = "srv-1",
        name = "Test VPS",
        host = "vps.example.com",
        port = 22,
        username = "deploy",
        authMethod = SshAuthMethod.PASSWORD,
        environment = ServerEnvironment.DEVELOPMENT,
    )

    private fun guard(
        server: Server = this.server,
        preferences: UserPreferences = UserPreferences(),
        gate: CommandApprovalGate = DefaultCommandApprovalGate(),
    ) = DefaultCommandGuard(
        serverRepository = FakeServerRepository(server),
        connectionManager = UnusableConnectionManager,
        approvalGate = gate,
        preferences = FixedPreferences(preferences),
    )

    // --- safe commands pass straight through ------------------------------------

    @Test
    fun `a safe command is authorised without prompting`() = runTest {
        val gate = RecordingGate()
        val result = guard(gate = gate).authorize("srv-1", "git status")

        assertTrue(result is VmResult.Success)
        assertEquals(CommandRisk.SAFE, result.getOrNull()?.risk)
        assertEquals("no prompt should be raised", 0, gate.requestCount)
    }

    // --- blocked commands are refused, never prompted ---------------------------

    @Test
    fun `a blocked command is refused and never reaches the approval gate`() = runTest {
        val gate = RecordingGate()

        val result = guard(gate = gate).authorize("srv-1", "rm -rf /")

        val error = result.errorOrNull()
        assertTrue(error is VmError.PermissionDenied)
        assertEquals(
            "offering a confirmation would imply there is an answer other than no",
            0,
            gate.requestCount,
        )
    }

    @Test
    fun `a blocked command stays blocked even with confirmation disabled`() = runTest {
        val gate = RecordingGate()

        val result = guard(
            preferences = UserPreferences(confirmDestructiveCommands = false),
            gate = gate,
        ).authorize("srv-1", "mkfs.ext4 /dev/sdb1")

        assertTrue(result.errorOrNull() is VmError.PermissionDenied)
        assertEquals(0, gate.requestCount)
    }

    // --- destructive commands prompt --------------------------------------------

    @Test
    fun `a destructive command prompts and proceeds when approved`() = runTest {
        val gate = ScriptedGate(approve = true)

        val result = guard(gate = gate).authorize("srv-1", "rm -rf build")

        assertTrue(result is VmResult.Success)
        assertEquals(1, gate.requestCount)
        assertEquals(CommandRisk.DESTRUCTIVE, gate.lastRequest?.assessment?.risk)
    }

    @Test
    fun `a destructive command is refused when denied`() = runTest {
        val gate = ScriptedGate(approve = false)

        val result = guard(gate = gate).authorize("srv-1", "rm -rf build")

        val error = result.errorOrNull()
        assertTrue(error is VmError.PermissionDenied)
        assertEquals("Command not run", error?.summary)
        assertEquals(1, gate.requestCount)
    }

    @Test
    fun `the request carries the context the dialog needs`() = runTest {
        val gate = ScriptedGate(approve = false)

        guard(
            server = server.copy(environment = ServerEnvironment.PRODUCTION),
            gate = gate,
        ).authorize("srv-1", "systemctl restart nginx")

        val request = requireNotNull(gate.lastRequest)
        assertEquals("Test VPS", request.serverName)
        assertEquals("srv-1", request.serverId)
        assertTrue(request.isProduction)
        assertTrue(request.assessment.findings.isNotEmpty())
    }

    // --- the preference actually governs behaviour ------------------------------

    @Test
    fun `disabling confirmation lets a user's own destructive command run unprompted`() =
        runTest {
            val gate = RecordingGate()

            val result = guard(
                preferences = UserPreferences(confirmDestructiveCommands = false),
                gate = gate,
            ).authorize("srv-1", "rm -rf build")

            assertTrue(result is VmResult.Success)
            assertEquals("the setting must actually take effect", 0, gate.requestCount)
        }

    @Test
    fun `disabling confirmation never applies to a command the agent proposed`() = runTest {
        val gate = ScriptedGate(approve = false)

        val result = guard(
            preferences = UserPreferences(confirmDestructiveCommands = false),
            gate = gate,
        ).authorize("srv-1", "rm -rf build", requestedByAgent = true)

        assertEquals(
            "the agent must never be able to skip a destructive confirmation",
            1,
            gate.requestCount,
        )
        assertTrue(result.errorOrNull() is VmError.PermissionDenied)
    }

    @Test
    fun `production escalates a cautious command into a prompt`() = runTest {
        val development = ScriptedGate(approve = true)
        guard(gate = development).authorize("srv-1", "npm install")
        assertEquals("no prompt on a development server", 0, development.requestCount)

        val production = ScriptedGate(approve = true)
        guard(
            server = server.copy(environment = ServerEnvironment.PRODUCTION),
            gate = production,
        ).authorize("srv-1", "npm install")
        assertEquals("production escalates it", 1, production.requestCount)
    }

    // --- unknown server ----------------------------------------------------------

    @Test
    fun `an unknown server fails without prompting`() = runTest {
        val gate = RecordingGate()
        val guard = DefaultCommandGuard(
            serverRepository = FakeServerRepository(null),
            connectionManager = UnusableConnectionManager,
            approvalGate = gate,
            preferences = FixedPreferences(UserPreferences()),
        )

        val result = guard.authorize("missing", "ls")

        assertTrue(result.errorOrNull() is VmError.NotFound)
        assertEquals(0, gate.requestCount)
    }

    // --- the gate itself ---------------------------------------------------------

    @Test
    fun `the gate publishes the pending request and clears it after a decision`() = runTest {
        val gate = DefaultCommandApprovalGate()
        val request = CommandApprovalRequest(
            command = "rm -rf build",
            assessment = CommandSafety.assess("rm -rf build"),
            serverId = "srv-1",
            serverName = "Test VPS",
            environment = ServerEnvironment.DEVELOPMENT,
            requestedByAgent = false,
        )

        val pending = async { gate.request(request) }

        // Wait for the gate to publish before deciding.
        val published = gate.pending.first { it != null }
        assertEquals(request.id, published?.id)

        gate.resolve(request.id, approved = true)

        assertTrue(pending.await())
        assertNull("the prompt must clear once decided", gate.pending.first())
    }

    @Test
    fun `resolving a stale id does not decide the outstanding request`() = runTest {
        val gate = DefaultCommandApprovalGate()
        val request = CommandApprovalRequest(
            command = "rm -rf build",
            assessment = CommandSafety.assess("rm -rf build"),
            serverId = "srv-1",
            serverName = "Test VPS",
            environment = ServerEnvironment.DEVELOPMENT,
            requestedByAgent = false,
        )

        val pending = async { gate.request(request) }
        gate.pending.first { it != null }

        gate.resolve("some-other-id", approved = true)
        assertFalse("a stale dialog must not approve anything", pending.isCompleted)

        gate.resolve(request.id, approved = false)
        assertFalse(pending.await())
    }

    @Test
    fun `denyPending refuses the outstanding request`() = runTest {
        val gate = DefaultCommandApprovalGate()
        val pending = async {
            gate.request(
                CommandApprovalRequest(
                    command = "rm -rf build",
                    assessment = CommandSafety.assess("rm -rf build"),
                    serverId = "srv-1",
                    serverName = "Test VPS",
                    environment = ServerEnvironment.DEVELOPMENT,
                    requestedByAgent = false,
                ),
            )
        }
        gate.pending.first { it != null }

        gate.denyPending()

        assertFalse(pending.await())
    }
}

// --- fakes -------------------------------------------------------------------------

private class FixedPreferences(private val value: UserPreferences) : UserPreferencesSource {
    override val preferences: Flow<UserPreferences> = flowOf(value)
}

private class FakeServerRepository(private val server: Server?) : ServerRepository {
    override val servers: Flow<List<Server>> = flowOf(listOfNotNull(server))
    override val groups: Flow<List<ServerGroup>> = flowOf(emptyList())
    override fun observe(serverId: String): Flow<Server?> = flowOf(server)
    override suspend fun get(serverId: String): VmResult<Server> =
        server?.let { VmResult.Success(it) }
            ?: VmResult.Failure(VmError.NotFound(summary = "Server not found"))

    override suspend fun save(draft: ServerDraft) = error("not used")
    override suspend fun delete(serverId: String) = error("not used")
    override suspend fun markConnected(serverId: String) = error("not used")
    override suspend fun saveGroup(group: ServerGroup) = error("not used")
    override suspend fun deleteGroup(groupId: String) = error("not used")
    override suspend fun draftFor(serverId: String) = error("not used")
}

/**
 * Fails loudly if execution is attempted. Every test here stops at authorisation, so
 * reaching the connection layer would mean the guard let something through.
 */
private object UnusableConnectionManager : SshConnectionManager {
    override val states = MutableStateFlow<Map<String, SshConnectionState>>(emptyMap())
    override val pendingHostKeys = MutableStateFlow<Map<String, HostKeyVerdict>>(emptyMap())
    override fun state(serverId: String) = flowOf(SshConnectionState.Disconnected)
    override fun connectedServer(serverId: String): Server? = null
    override suspend fun session(serverId: String): VmResult<SshSession> =
        error("authorisation should have stopped before execution")

    override suspend fun reconnect(serverId: String): VmResult<SshSession> = error("not used")
    override suspend fun disconnect(serverId: String) = error("not used")
    override suspend fun disconnectAll() = error("not used")
    override suspend fun <T> withSession(
        serverId: String,
        block: suspend (SshSession) -> VmResult<T>,
    ): VmResult<T> = error("authorisation should have stopped before execution")

    override suspend fun trustPendingHostKey(serverId: String): VmResult<SshSession> =
        error("not used")

    override fun rejectPendingHostKey(serverId: String) = error("not used")
}

/** Records whether a prompt was raised, and always denies. */
private open class RecordingGate : CommandApprovalGate {
    var requestCount = 0
        protected set
    var lastRequest: CommandApprovalRequest? = null
        protected set

    override val pending = MutableStateFlow<CommandApprovalRequest?>(null)

    override suspend fun request(request: CommandApprovalRequest): Boolean {
        requestCount++
        lastRequest = request
        return false
    }

    override fun resolve(id: String, approved: Boolean) = Unit
    override fun denyPending() = Unit
}

/** Answers every prompt the same way. */
private class ScriptedGate(private val approve: Boolean) : RecordingGate() {
    override suspend fun request(request: CommandApprovalRequest): Boolean {
        requestCount++
        lastRequest = request
        return approve
    }
}
