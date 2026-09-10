package digital.vmstudio.code.feature.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.connection.SshConnectionState
import digital.vmstudio.code.core.ssh.host.HostKeyVerdict
import digital.vmstudio.code.core.ssh.model.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerDetailUiState(
    val server: Server? = null,
    val connectionState: SshConnectionState = SshConnectionState.Disconnected,
    /** Set when a host key needs the user's decision before connecting. */
    val pendingHostKey: HostKeyVerdict? = null,
    val error: VmError? = null,
    val isBusy: Boolean = false,
) {
    val isConnected: Boolean get() = connectionState.isConnected

    val serverInfo get() = (connectionState as? SshConnectionState.Connected)?.serverInfo
}

@HiltViewModel
class ServerDetailViewModel @Inject constructor(
    private val connectionManager: SshConnectionManager,
    serverRepository: digital.vmstudio.code.core.ssh.repository.ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "ServerDetail requires a serverId"
    }

    private val transientError = MutableStateFlow<VmError?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<ServerDetailUiState> = combine(
        serverRepository.observe(serverId),
        connectionManager.state(serverId),
        connectionManager.pendingHostKeys,
        transientError,
        busy,
    ) { server, connectionState, pendingKeys, error, isBusy ->
        ServerDetailUiState(
            server = server,
            connectionState = connectionState,
            pendingHostKey = pendingKeys[serverId],
            // A host-key prompt is not an error banner; the dialog carries it.
            error = if (pendingKeys[serverId] != null) null else error,
            isBusy = isBusy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServerDetailUiState(),
    )

    fun connect() {
        if (busy.value) return
        busy.value = true
        transientError.value = null
        viewModelScope.launch {
            when (val result = connectionManager.session(serverId)) {
                is VmResult.Failure -> transientError.value = result.error
                is VmResult.Success -> transientError.value = null
            }
            busy.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect(serverId)
        }
    }

    /** Records the user's acceptance of the presented key, then connects. */
    fun trustHostKeyAndConnect() {
        busy.value = true
        viewModelScope.launch {
            when (val result = connectionManager.trustPendingHostKey(serverId)) {
                is VmResult.Failure -> transientError.value = result.error
                is VmResult.Success -> transientError.value = null
            }
            busy.value = false
        }
    }

    fun rejectHostKey() {
        connectionManager.rejectPendingHostKey(serverId)
    }

    fun dismissError() {
        transientError.value = null
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
    }
}
