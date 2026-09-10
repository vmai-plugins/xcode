package digital.vmstudio.code.core.ssh.connection

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.host.HostKeyVerdict
import digital.vmstudio.code.core.ssh.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns every live SSH connection.
 *
 * One connection per server, shared by the terminal, the file browser, Git and the
 * agent. SSH multiplexes channels over a single transport, so opening a second TCP
 * connection per feature would waste a handshake, double the authentication cost,
 * and give the user several "connected" indicators that can disagree.
 */
interface SshConnectionManager {

    /** Connection state for every server that has ever been connected this session. */
    val states: StateFlow<Map<String, SshConnectionState>>

    fun state(serverId: String): Flow<SshConnectionState>

    /**
     * Returns the live session, connecting if needed. Concurrent callers for the
     * same server share one connection attempt rather than racing.
     */
    suspend fun session(serverId: String): VmResult<SshSession>

    /** Forces a fresh connection, discarding any existing one. */
    suspend fun reconnect(serverId: String): VmResult<SshSession>

    suspend fun disconnect(serverId: String)

    suspend fun disconnectAll()

    /**
     * Convenience for a scoped operation. The session is not closed afterwards —
     * the manager owns its lifetime — but this keeps call sites from holding a
     * reference to a session that may since have dropped.
     */
    suspend fun <T> withSession(
        serverId: String,
        block: suspend (SshSession) -> VmResult<T>,
    ): VmResult<T>

    /** The server behind a live session, if any. */
    fun connectedServer(serverId: String): Server?

    /**
     * Host keys awaiting a human decision, keyed by server.
     *
     * A connection attempt that meets an unrecognised or changed key fails and
     * records the key here rather than prompting on the transport thread. The UI
     * shows the fingerprint; only if the user accepts does [trustPendingHostKey]
     * store it. This is the one place in the app where a security decision is
     * deliberately routed through a person.
     */
    val pendingHostKeys: StateFlow<Map<String, HostKeyVerdict>>

    /**
     * Records the user's acceptance of the pending key for [serverId] and connects.
     *
     * Fails if there is no pending decision, so a stale dialog cannot silently
     * trust a key from a connection attempt that has since been superseded.
     */
    suspend fun trustPendingHostKey(serverId: String): VmResult<SshSession>

    /** Discards a pending decision without trusting it. */
    fun rejectPendingHostKey(serverId: String)
}
