package digital.vmstudio.code.feature.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandLimits
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

data class VpsTelemetry(
    val uptime: String = "",
    val ramSummary: String = "",
    val ramUsedPercent: Int = 0,
    val diskSummary: String = "",
    val diskUsedPercent: Int = 0,
)

data class DockerContainerInfo(
    val name: String,
    val image: String,
    val status: String,
    val isRunning: Boolean,
)

data class ServerDetailUiState(
    val server: Server? = null,
    val connectionState: SshConnectionState = SshConnectionState.Disconnected,
    /** Set when a host key needs the user's decision before connecting. */
    val pendingHostKey: HostKeyVerdict? = null,
    val error: VmError? = null,
    val isBusy: Boolean = false,
    val telemetry: VpsTelemetry? = null,
    val isLoadingTelemetry: Boolean = false,
    val containers: List<DockerContainerInfo> = emptyList(),
    val hasDocker: Boolean = false,
    val containerLogs: Pair<String, String>? = null,
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
    private val telemetry = MutableStateFlow<VpsTelemetry?>(null)
    private val loadingTelemetry = MutableStateFlow(false)
    private val containers = MutableStateFlow<List<DockerContainerInfo>>(emptyList())
    private val hasDocker = MutableStateFlow(false)
    private val containerLogs = MutableStateFlow<Pair<String, String>?>(null)

    val uiState: StateFlow<ServerDetailUiState> = combine(
        serverRepository.observe(serverId),
        connectionManager.state(serverId),
        connectionManager.pendingHostKeys,
        transientError,
        busy,
        telemetry,
        loadingTelemetry,
        containers,
        hasDocker,
    ) { params ->
        val server = params[0] as? Server
        val connectionState = params[1] as SshConnectionState
        @Suppress("UNCHECKED_CAST")
        val pendingKeys = params[2] as Map<String, HostKeyVerdict>
        val error = params[3] as? VmError
        val isBusy = params[4] as Boolean
        val tel = params[5] as? VpsTelemetry
        val isLoadingTel = params[6] as Boolean
        @Suppress("UNCHECKED_CAST")
        val conts = params[7] as List<DockerContainerInfo>
        val dockerAvailable = params[8] as Boolean

        ServerDetailUiState(
            server = server,
            connectionState = connectionState,
            pendingHostKey = pendingKeys[serverId],
            error = if (pendingKeys[serverId] != null) null else error,
            isBusy = isBusy,
            telemetry = tel,
            isLoadingTelemetry = isLoadingTel,
            containers = conts,
            hasDocker = dockerAvailable,
            containerLogs = containerLogs.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServerDetailUiState(),
    )

    init {
        viewModelScope.launch {
            connectionManager.state(serverId).collect { state ->
                if (state.isConnected) {
                    refreshTelemetry()
                }
            }
        }
    }

    fun connect() {
        if (busy.value) return
        busy.value = true
        transientError.value = null
        viewModelScope.launch {
            when (val result = connectionManager.session(serverId)) {
                is VmResult.Failure -> transientError.value = result.error
                is VmResult.Success -> {
                    transientError.value = null
                    refreshTelemetry()
                }
            }
            busy.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect(serverId)
            telemetry.value = null
            containers.value = emptyList()
            hasDocker.value = false
        }
    }

    fun refreshTelemetry() {
        if (loadingTelemetry.value) return
        loadingTelemetry.value = true
        viewModelScope.launch {
            val sessionResult = connectionManager.session(serverId)
            if (sessionResult is VmResult.Success) {
                val session = sessionResult.value
                try {
                    // System resources
                    val res = session.execute(
                        "uptime; echo '---'; free -m; echo '---'; df -h /",
                        CommandLimits(timeoutSeconds = 8),
                    )
                    if (res.exitCode == 0) {
                        telemetry.value = parseTelemetry(res.stdout)
                    }

                    // Docker containers
                    val dockerRes = session.execute(
                        "docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null",
                        CommandLimits(timeoutSeconds = 8),
                    )
                    if (dockerRes.exitCode == 0 && dockerRes.stdout.isNotBlank()) {
                        hasDocker.value = true
                        containers.value = parseContainers(dockerRes.stdout)
                    } else if (dockerRes.exitCode == 0) {
                        hasDocker.value = true
                        containers.value = emptyList()
                    } else {
                        hasDocker.value = false
                    }
                } catch (_: Exception) {
                    // Non-fatal telemetry probe
                }
            }
            loadingTelemetry.value = false
        }
    }

    fun restartContainer(name: String) {
        viewModelScope.launch {
            val sessionResult = connectionManager.session(serverId)
            if (sessionResult is VmResult.Success) {
                sessionResult.value.execute("docker restart '$name'", CommandLimits(timeoutSeconds = 20))
                refreshTelemetry()
            }
        }
    }

    fun viewContainerLogs(name: String) {
        viewModelScope.launch {
            val sessionResult = connectionManager.session(serverId)
            if (sessionResult is VmResult.Success) {
                val res = sessionResult.value.execute("docker logs --tail 100 '$name'", CommandLimits(timeoutSeconds = 10))
                containerLogs.value = name to (res.stdout.ifBlank { res.stderr }.ifBlank { "No logs found." })
            }
        }
    }

    fun dismissContainerLogs() {
        containerLogs.value = null
    }

    private fun parseTelemetry(output: String): VpsTelemetry {
        val sections = output.split("---")
        val uptimeLine = sections.getOrNull(0)?.trim()?.lines()?.firstOrNull() ?: ""

        var ramSummary = ""
        var ramPercent = 0
        sections.getOrNull(1)?.let { freeOut ->
            val memLine = freeOut.lines().firstOrNull { it.startsWith("Mem:") || it.contains("Mem:") }
            if (memLine != null) {
                val parts = memLine.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (parts.size >= 3) {
                    val total = parts[1].toLongOrNull() ?: 1L
                    val used = parts[2].toLongOrNull() ?: 0L
                    ramPercent = ((used.toDouble() / total) * 100).toInt().coerceIn(0, 100)
                    ramSummary = "${used}MB / ${total}MB ($ramPercent%)"
                }
            }
        }

        var diskSummary = ""
        var diskPercent = 0
        sections.getOrNull(2)?.let { dfOut ->
            val rootLine = dfOut.lines().drop(1).firstOrNull { it.trim().endsWith(" /") || it.contains(" /") }
            if (rootLine != null) {
                val parts = rootLine.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (parts.size >= 5) {
                    val size = parts[1]
                    val used = parts[2]
                    val pct = parts[4].removeSuffix("%").toIntOrNull() ?: 0
                    diskPercent = pct.coerceIn(0, 100)
                    diskSummary = "$used / $size ($pct%)"
                }
            }
        }

        return VpsTelemetry(
            uptime = uptimeLine.substringAfter("up ").substringBefore(",").trim().ifBlank { uptimeLine },
            ramSummary = ramSummary,
            ramUsedPercent = ramPercent,
            diskSummary = diskSummary,
            diskUsedPercent = diskPercent,
        )
    }

    private fun parseContainers(output: String): List<DockerContainerInfo> =
        output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size >= 4) {
                DockerContainerInfo(
                    name = parts[0].trim(),
                    image = parts[1].trim(),
                    status = parts[2].trim(),
                    isRunning = parts[3].trim().equals("running", ignoreCase = true),
                )
            } else null
        }

    /** Records the user's acceptance of the presented key, then connects. */
    fun trustHostKeyAndConnect() {
        busy.value = true
        viewModelScope.launch {
            when (val result = connectionManager.trustPendingHostKey(serverId)) {
                is VmResult.Failure -> transientError.value = result.error
                is VmResult.Success -> {
                    transientError.value = null
                    refreshTelemetry()
                }
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
