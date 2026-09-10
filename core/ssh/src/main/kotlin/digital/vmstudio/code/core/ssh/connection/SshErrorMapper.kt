package digital.vmstudio.code.core.ssh.connection

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.ssh.host.HostKeyVerdict
import digital.vmstudio.code.core.ssh.model.Server
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Turns sshj and JDK network exceptions into errors a developer can act on.
 *
 * This is the whole reason the app can promise never to show "Unknown error": the
 * translation happens here, at the boundary, while the host, port and user are
 * still in scope. `ConnectException` on its own says nothing; "Connection refused
 * on port 22 — check sshd is running" says what to do next.
 */
internal object SshErrorMapper {

    fun map(server: Server, throwable: Throwable, verdict: HostKeyVerdict? = null): VmError {
        // A host-key rejection surfaces from sshj as a generic transport failure, so
        // the verifier's recorded verdict is the only reliable signal.
        when (verdict) {
            is HostKeyVerdict.Mismatch -> return VmError.HostKeyMismatch(
                host = verdict.candidate.host,
                port = verdict.candidate.port,
                expectedFingerprint = verdict.trusted.fingerprintSha256,
                actualFingerprint = verdict.candidate.fingerprintSha256,
                keyType = verdict.candidate.keyType,
            )

            is HostKeyVerdict.Unknown -> return VmError.UnknownHostKey(
                host = verdict.candidate.host,
                port = verdict.candidate.port,
                fingerprint = verdict.candidate.fingerprintSha256,
                keyType = verdict.candidate.keyType,
            )

            else -> Unit
        }

        return when (throwable) {
            // Raised before any network activity, so it is a configuration problem
            // rather than a connection problem and must not offer Retry.
            is MissingCredentialException -> VmError.Ssh(
                summary = "No credential is stored",
                reason = throwable.message,
                suggestedAction = "Edit the server and enter its password or private key.",
                retryable = false,
                host = server.host,
                port = server.port,
                username = server.username,
                cause = throwable,
            )

            is CredentialUnavailableException -> throwable.error

            is UnsupportedOperationException -> VmError.Ssh(
                summary = "Authentication method not supported",
                reason = throwable.message,
                suggestedAction = "Switch this server to password or key authentication.",
                retryable = false,
                host = server.host,
                port = server.port,
                cause = throwable,
            )

            is UnknownHostException -> VmError.Ssh(
                summary = "Host not found",
                reason = "\"${server.host}\" could not be resolved.",
                suggestedAction = "Check the hostname for typos, or use the IP address. " +
                    "If it is an internal host, check you are on the right network or VPN.",
                retryable = true,
                host = server.host,
                port = server.port,
                cause = throwable,
            )

            is ConnectException -> VmError.Ssh(
                summary = "Connection refused",
                reason = "Nothing is accepting connections on port ${server.port}.",
                suggestedAction = "Check that sshd is running and that the port is correct. " +
                    "Some hosts run SSH on a non-standard port.",
                retryable = true,
                host = server.host,
                port = server.port,
                cause = throwable,
            )

            is NoRouteToHostException -> VmError.Ssh(
                summary = "No route to host",
                reason = "The network cannot reach ${server.host}.",
                suggestedAction = "Check your connection, and whether the host is behind a " +
                    "VPN or firewall.",
                retryable = true,
                host = server.host,
                port = server.port,
                cause = throwable,
            )

            is SocketTimeoutException, is TimeoutException -> VmError.Ssh(
                summary = "Connection timed out",
                reason = "${server.host}:${server.port} did not respond within " +
                    "${server.connectTimeoutMillis / 1000} seconds.",
                suggestedAction = "The host may be down, or a firewall may be dropping the " +
                    "connection. Increase the timeout in the server settings if the host is slow.",
                retryable = true,
                host = server.host,
                port = server.port,
                cause = throwable,
            )

            is UserAuthException -> mapAuthFailure(server, throwable)

            is TransportException -> VmError.Ssh(
                summary = "SSH handshake failed",
                reason = throwable.message
                    ?: "The server closed the connection during negotiation.",
                suggestedAction = "The server may require a key exchange or cipher this " +
                    "client does not offer. Check the sshd logs on the host.",
                retryable = true,
                host = server.host,
                port = server.port,
                cause = throwable,
            )

            is IOException -> VmError.Ssh(
                summary = "Connection failed",
                reason = throwable.message,
                suggestedAction = "Check your network connection and try again.",
                retryable = true,
                host = server.host,
                port = server.port,
                cause = throwable,
            )

            else -> VmError.Ssh(
                summary = "Could not connect",
                reason = throwable.message,
                suggestedAction = "Retry, and export diagnostics if this keeps happening.",
                retryable = true,
                host = server.host,
                port = server.port,
                cause = throwable,
            )
        }
    }

    private fun mapAuthFailure(server: Server, throwable: UserAuthException): VmError {
        val message = throwable.message.orEmpty()
        val hint = when {
            message.contains("publickey", ignoreCase = true) ->
                "The server rejected the key. Check the public half is in " +
                    "~/.ssh/authorized_keys for ${server.username}, and that its " +
                    "permissions are 600."

            message.contains("password", ignoreCase = true) ->
                "Check the password, and that the server allows password " +
                    "authentication (PasswordAuthentication yes)."

            else ->
                "Check the username and credential. If the key has a passphrase, " +
                    "choose \"Key + passphrase\" as the authentication method."
        }

        return VmError.Ssh(
            summary = "Authentication failed",
            reason = "${server.username}@${server.host} was rejected by the server.",
            suggestedAction = hint,
            // Not retryable: repeating the same rejected credential wastes an
            // attempt and can trip fail2ban.
            retryable = false,
            host = server.host,
            port = server.port,
            username = server.username,
            cause = throwable,
        )
    }
}
