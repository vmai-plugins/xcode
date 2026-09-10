package digital.vmstudio.code.core.ssh.connection

import digital.vmstudio.code.core.common.error.VmError

/**
 * Observable state of one server's connection.
 *
 * Modelled explicitly rather than as a boolean so the UI can distinguish "never
 * tried" from "trying" from "failed, here is why" — the difference matters when a
 * user is debugging why their server will not connect.
 */
sealed interface SshConnectionState {

    data object Disconnected : SshConnectionState

    data class Connecting(val attempt: Int) : SshConnectionState

    data class Connected(
        val connectedAtMillis: Long,
        val serverInfo: ServerInfo,
    ) : SshConnectionState

    /** The transport dropped and a reconnect is scheduled. */
    data class Reconnecting(
        val attempt: Int,
        val nextAttemptInMillis: Long,
        val lastError: VmError?,
    ) : SshConnectionState

    data class Failed(val error: VmError) : SshConnectionState

    val isConnected: Boolean get() = this is Connected

    val isBusy: Boolean get() = this is Connecting || this is Reconnecting
}

/**
 * What the app learned about a host after connecting.
 *
 * Every field is optional because none of it is guaranteed: a locked-down host may
 * refuse `uname`, and a restricted shell may not report a version. The server
 * dashboard shows what is available and says so when something is not, rather than
 * inventing a value.
 */
data class ServerInfo(
    val shell: String,
    val shellPath: String,
    val operatingSystem: String? = null,
    val kernelVersion: String? = null,
    val architecture: String? = null,
    val hostname: String? = null,
    val homeDirectory: String? = null,
    val sshServerVersion: String? = null,
)
