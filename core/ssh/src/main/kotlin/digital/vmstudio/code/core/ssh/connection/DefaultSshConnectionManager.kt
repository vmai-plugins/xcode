package digital.vmstudio.code.core.ssh.connection

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.ssh.host.HostKeyRepository
import digital.vmstudio.code.core.ssh.host.HostKeyVerdict
import digital.vmstudio.code.core.ssh.host.TofuHostKeyVerifier
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class DefaultSshConnectionManager @Inject constructor(
    private val serverRepository: ServerRepository,
    private val credentialStore: SecureCredentialStore,
    private val hostKeyRepository: HostKeyRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SshConnectionManager {

    private val sessions = ConcurrentHashMap<String, SshSession>()

    /** One lock per server so connecting to A never serialises behind B. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    private val _states = MutableStateFlow<Map<String, SshConnectionState>>(emptyMap())
    override val states: StateFlow<Map<String, SshConnectionState>> = _states.asStateFlow()

    private val _pendingHostKeys = MutableStateFlow<Map<String, HostKeyVerdict>>(emptyMap())
    override val pendingHostKeys: StateFlow<Map<String, HostKeyVerdict>> =
        _pendingHostKeys.asStateFlow()

    override suspend fun trustPendingHostKey(serverId: String): VmResult<SshSession> {
        val verdict = _pendingHostKeys.value[serverId]
        val candidate = when (verdict) {
            is HostKeyVerdict.Unknown -> verdict.candidate
            is HostKeyVerdict.Mismatch -> verdict.candidate
            else -> return VmResult.Failure(
                VmError.Security(
                    summary = "No host key is awaiting approval",
                    reason = "The connection attempt that produced this prompt is no longer " +
                        "current.",
                    suggestedAction = "Try connecting again.",
                ),
            )
        }

        return when (val trusted = hostKeyRepository.trust(candidate)) {
            is VmResult.Failure -> trusted
            is VmResult.Success -> {
                _pendingHostKeys.value = _pendingHostKeys.value - serverId
                VmLog.w(
                    LogCategory.SECURITY,
                    TAG,
                    "User accepted host key ${candidate.fingerprintSha256} for " +
                        "${candidate.host}:${candidate.port}",
                )
                                // Return the reconnect result so the caller (e.g. the trust dialog)
                // can react to a downstream auth or connection failure that occurs
                // after the key was accepted. Discarding it would hide the reason.
                reconnect(serverId)
            }
        }
    }

    override fun rejectPendingHostKey(serverId: String) {
        _pendingHostKeys.value = _pendingHostKeys.value - serverId
    }

    override fun state(serverId: String): Flow<SshConnectionState> =
        states.map { it[serverId] ?: SshConnectionState.Disconnected }.distinctUntilChanged()

    override fun connectedServer(serverId: String): Server? = sessions[serverId]?.server

    override suspend fun session(serverId: String): VmResult<SshSession> =
        lockFor(serverId).withLock {
            sessions[serverId]?.takeIf { it.isConnected }?.let { return@withLock VmResult.Success(it) }
            // A session object that exists but is no longer connected is stale;
            // drop it before reconnecting so its socket is released.
            sessions.remove(serverId)?.let { runCatching { it.close() } }
            establish(serverId)
        }

    override suspend fun reconnect(serverId: String): VmResult<SshSession> =
        lockFor(serverId).withLock {
            sessions.remove(serverId)?.let { runCatching { it.close() } }
            establish(serverId)
        }

    override suspend fun disconnect(serverId: String) {
        lockFor(serverId).withLock {
            sessions.remove(serverId)?.let { session ->
                withContext(ioDispatcher) { runCatching { session.close() } }
                VmLog.i(LogCategory.SSH, TAG, "Disconnected from ${session.server.name}")
            }
            updateState(serverId, SshConnectionState.Disconnected)
        }
    }

    override suspend fun disconnectAll() {
        sessions.keys.toList().forEach { disconnect(it) }
    }

    override suspend fun <T> withSession(
        serverId: String,
        block: suspend (SshSession) -> VmResult<T>,
    ): VmResult<T> = session(serverId).flatMap { block(it) }

    // --- connection establishment ----------------------------------------------

    private suspend fun establish(serverId: String): VmResult<SshSession> {
        val server = when (val result = serverRepository.get(serverId)) {
            is VmResult.Failure -> {
                updateState(serverId, SshConnectionState.Failed(result.error))
                return result
            }
            is VmResult.Success -> result.value
        }

        updateState(serverId, SshConnectionState.Connecting(attempt = 1))
        SshSecurityProviders.ensureInitialised()

        val verifier = TofuHostKeyVerifier(hostKeyRepository)
        var client: SSHClient? = null

        return try {
            val session = withContext(ioDispatcher) {
                val created = buildClient(server, verifier)
                client = created

                runInterruptible { created.connect(server.host, server.port) }
                authenticate(created, server)
                configureKeepAlive(created, server)

                val info = ServerProbe.probe(created, server, ioDispatcher)
                SshSession(
                    server = server,
                    serverInfo = info,
                    client = created,
                    ioDispatcher = ioDispatcher,
                )
            }

            sessions[serverId] = session
            updateState(
                serverId,
                SshConnectionState.Connected(
                    connectedAtMillis = System.currentTimeMillis(),
                    serverInfo = session.serverInfo,
                ),
            )
            serverRepository.markConnected(serverId)
            VmLog.i(
                LogCategory.SSH,
                TAG,
                "Connected to ${server.name} (${server.displayTarget}) " +
                    "shell=${session.serverInfo.shell} os=${session.serverInfo.operatingSystem}",
            )
            VmResult.Success(session)
        } catch (cancellation: CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) { closeQuietly(client) }
            updateState(serverId, SshConnectionState.Disconnected)
            throw cancellation
        } catch (throwable: Throwable) {
            closeQuietly(client)
            val verdict = verifier.verdict
            // Surface an unresolved key so the UI can prompt; a trusted verdict
            // means the failure was something else entirely.
            _pendingHostKeys.value = when (verdict) {
                is HostKeyVerdict.Unknown, is HostKeyVerdict.Mismatch ->
                    _pendingHostKeys.value + (serverId to verdict)
                else -> _pendingHostKeys.value - serverId
            }
            val error = SshErrorMapper.map(server, throwable, verdict)
            updateState(serverId, SshConnectionState.Failed(error))
            VmLog.w(LogCategory.SSH, TAG, "Connection to ${server.name} failed: ${error.summary}")
            VmResult.Failure(error)
        }
    }

    private fun buildClient(server: Server, verifier: TofuHostKeyVerifier): SSHClient {
        val config = DefaultConfig().apply {
            // Without an explicit provider sshj never sends keepalives, and a NAT
            // or firewall silently drops an idle session with no notification.
            keepAliveProvider = KeepAliveProvider.KEEP_ALIVE
        }
        return SSHClient(config).apply {
            addHostKeyVerifier(verifier)
            connectTimeout = server.connectTimeoutMillis
            // Socket read timeout. Left generous: a long-running command legitimately
            // produces no traffic for minutes, and keepalives cover a dead peer.
            timeout = SOCKET_TIMEOUT_MILLIS
        }
    }

    private fun configureKeepAlive(client: SSHClient, server: Server) {
        if (server.keepAliveIntervalSeconds <= 0) return
        runCatching {
            client.connection.keepAlive.keepAliveInterval = server.keepAliveIntervalSeconds
        }
    }

    /**
     * Resolves the stored credential and authenticates.
     *
     * The secret is read at the last possible moment and wiped immediately after,
     * so plaintext exists for the duration of one handshake rather than for the
     * lifetime of the connection.
     */
    private suspend fun authenticate(client: SSHClient, server: Server) {
        when (server.authMethod) {
            SshAuthMethod.PASSWORD -> {
                val credentialId = server.passwordCredentialId
                    ?: throw MissingCredentialException("No password is stored for this server.")
                val secret = readSecret(credentialId)
                // Copied out rather than used inside `useAsChars`, because the
                // authentication call is a suspending interruptible section and
                // `useAsChars` takes a plain lambda. The copy is scrubbed here
                // instead.
                val chars = secret.useAsChars { it.copyOf() }
                try {
                    runInterruptible { client.authPassword(server.username, chars) }
                } finally {
                    chars.fill(Char(0))
                    secret.wipe()
                }
            }

            SshAuthMethod.PRIVATE_KEY, SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE -> {
                val keyId = server.privateKeyCredentialId
                    ?: throw MissingCredentialException("No private key is stored for this server.")
                val keySecret = readSecret(keyId)
                val passphraseSecret = server.passphraseCredentialId?.let { readSecret(it) }
                try {
                    val provider = keySecret.useAsString { pem ->
                        // Second argument null tells sshj the first is key *content*
                        // rather than a filesystem path.
                        client.loadKeys(pem, null, passphraseFinder(passphraseSecret))
                    }
                    runInterruptible { client.authPublickey(server.username, provider) }
                } finally {
                    keySecret.wipe()
                    passphraseSecret?.wipe()
                }
            }

            SshAuthMethod.AGENT -> throw UnsupportedOperationException(
                "Agent authentication is not available yet.",
            )
        }
    }

    private suspend fun readSecret(credentialId: String): Secret =
        when (val result = credentialStore.read(credentialId)) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> throw CredentialUnavailableException(result.error)
        }

    private fun passphraseFinder(secret: Secret?): PasswordFinder? {
        if (secret == null) return null
        return object : PasswordFinder {
            override fun reqPassword(resource: Resource<*>?): CharArray =
                secret.useAsChars { it.copyOf() }

            // Retrying with the same stored passphrase would loop forever; a wrong
            // passphrase must surface as an auth failure the user can act on.
            override fun shouldRetry(resource: Resource<*>?): Boolean = false
        }
    }

    private fun closeQuietly(client: SSHClient?) {
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
    }

    private fun lockFor(serverId: String): Mutex =
        locks.computeIfAbsent(serverId) { Mutex() }

    private fun updateState(serverId: String, state: SshConnectionState) {
        _states.value = _states.value + (serverId to state)
    }

        private companion object {
        const val TAG = "SshConnectionManager"
        // 60s read timeout: long enough for a slow command to produce output, short
        // enough that a silently-dead connection (no keepalive response) is detected
        // instead of hanging a feature coroutine indefinitely. Keepalives cover idle
        // peers; this covers peers that never fully disconnect but stop responding.
        const val SOCKET_TIMEOUT_MILLIS = 60_000
    }
}

internal class MissingCredentialException(message: String) : Exception(message)

internal class CredentialUnavailableException(val error: VmError) : Exception(error.summary)
