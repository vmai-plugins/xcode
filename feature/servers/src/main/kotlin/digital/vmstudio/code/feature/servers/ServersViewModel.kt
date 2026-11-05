package digital.vmstudio.code.feature.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.network.radar.DevRadar
import digital.vmstudio.code.core.network.radar.DiscoveredHost
import digital.vmstudio.code.core.network.radar.DiscoveredPort
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.model.ServerDraft
import digital.vmstudio.code.core.ssh.model.ServerGroup
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServersUiState(
    val isLoading: Boolean = true,
    val servers: List<Server> = emptyList(),
    val groups: List<ServerGroup> = emptyList(),
    val isScanningRadar: Boolean = false,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val error: VmError? = null,
) {
    val isEmpty: Boolean get() = !isLoading && servers.isEmpty()

    /** Servers grouped for display; ungrouped servers appear under a null key. */
    val grouped: Map<ServerGroup?, List<Server>>
        get() = servers.groupBy { server ->
            groups.firstOrNull { it.id == server.groupId }
        }
}

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connectionManager: SshConnectionManager,
    private val devRadar: DevRadar,
) : ViewModel() {

    private val transientError = MutableStateFlow<VmError?>(null)
    private val isScanningRadar = MutableStateFlow(false)
    private val discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    private var scanJob: Job? = null

    val uiState: StateFlow<ServersUiState> = combine(
        serverRepository.servers,
        serverRepository.groups,
        transientError,
        isScanningRadar,
        discoveredHosts,
    ) { servers, groups, error, scanning, hosts ->
        ServersUiState(
            isLoading = false,
            servers = servers,
            groups = groups,
            isScanningRadar = scanning,
            discoveredHosts = hosts,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServersUiState(),
    )

    fun startRadarScan() {
        scanJob?.cancel()
        discoveredHosts.value = emptyList()
        isScanningRadar.value = true

        scanJob = viewModelScope.launch {
            try {
                devRadar.scanSubnet { host ->
                    discoveredHosts.update { current ->
                        if (current.none { it.ip == host.ip }) {
                            current + host
                        } else {
                            current.map { if (it.ip == host.ip) host else it }
                        }
                    }
                }
            } finally {
                isScanningRadar.value = false
            }
        }
    }

    fun stopRadarScan() {
        scanJob?.cancel()
        isScanningRadar.value = false
    }

    fun getConnectCode(host: DiscoveredHost, port: DiscoveredPort): String {
        return devRadar.generateConnectCode(host, port)
    }

    fun quickAddServer(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val draft = ServerDraft(
                name = name,
                host = host,
                port = port.toString(),
                username = username,
                authMethod = SshAuthMethod.PASSWORD,
                password = password,
                environment = ServerEnvironment.DEVELOPMENT,
            )
            when (val res = serverRepository.save(draft)) {
                is VmResult.Success -> {
                    transientError.value = null
                    onSuccess(res.value.id)
                }
                is VmResult.Failure -> {
                    transientError.value = res.error
                }
            }
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            when (val result = serverRepository.delete(serverId)) {
                is VmResult.Failure -> transientError.value = result.error
                is VmResult.Success -> {
                    transientError.value = null
                    connectionManager.disconnect(serverId)
                }
            }
        }
    }

    fun dismissError() {
        transientError.value = null
    }
}
