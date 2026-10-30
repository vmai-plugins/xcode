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
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

data class LaptopActionResult(
    val title: String,
    val output: String,
    val isSuccess: Boolean,
)

data class ServerDetailUiState(
    val server: Server? = null,
    val connectionState: SshConnectionState = SshConnectionState.Disconnected,
    /** Set when a host key needs the user's decision before connecting. */
    val pendingHostKey: HostKeyVerdict? = null,
    val error: VmError? = null,
    val isBusy: Boolean = false,
    val isAutoConnect: Boolean = false,
    val telemetry: VpsTelemetry? = null,
    val isLoadingTelemetry: Boolean = false,
    val containers: List<DockerContainerInfo> = emptyList(),
    val hasDocker: Boolean = false,
    val containerLogs: Pair<String, String>? = null,
    val laptopActionResult: LaptopActionResult? = null,
    val isLaptopActionBusy: Boolean = false,
) {
    val isConnected: Boolean get() = connectionState.isConnected

    val serverInfo get() = (connectionState as? SshConnectionState.Connected)?.serverInfo
}

@HiltViewModel
class ServerDetailViewModel @Inject constructor(
    private val connectionManager: SshConnectionManager,
    private val serverRepository: ServerRepository,
    private val preferencesRepository: digital.vmstudio.code.core.common.preferences.UserPreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "ServerDetail requires a serverId"
    }

    private val _uiState = MutableStateFlow(ServerDetailUiState())
    val uiState: StateFlow<ServerDetailUiState> = _uiState.asStateFlow()

    init {
        // Observe server configuration
        viewModelScope.launch {
            serverRepository.observe(serverId).collect { s ->
                _uiState.update { it.copy(server = s) }
            }
        }

        // Observe auto-connect preference
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(isAutoConnect = prefs.autoConnectServerId == serverId) }
            }
        }

        // Observe connection state
        viewModelScope.launch {
            connectionManager.state(serverId).collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state.isConnected) {
                    refreshTelemetry()
                }
            }
        }

        // Observe pending host keys
        viewModelScope.launch {
            connectionManager.pendingHostKeys.collect { keys ->
                val key = keys[serverId]
                _uiState.update { it.copy(pendingHostKey = key) }
            }
        }
    }

    fun connect() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = connectionManager.session(serverId)) {
                is VmResult.Failure -> _uiState.update { it.copy(error = result.error, isBusy = false) }
                is VmResult.Success -> {
                    _uiState.update { it.copy(error = null, isBusy = false) }
                    refreshTelemetry()
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect(serverId)
            _uiState.update {
                it.copy(
                    telemetry = null,
                    containers = emptyList(),
                    hasDocker = false,
                )
            }
        }
    }

    fun toggleAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoConnectServerId(if (enabled) serverId else null)
        }
    }

    fun refreshTelemetry() {
        if (_uiState.value.isLoadingTelemetry) return
        _uiState.update { it.copy(isLoadingTelemetry = true) }
        viewModelScope.launch {
            val sessionResult = connectionManager.session(serverId)
            if (sessionResult is VmResult.Success) {
                val session = sessionResult.value
                try {
                    // System resources
                    val resResult = session.execute(
                        "uptime; echo '---'; free -m; echo '---'; df -h /",
                        CommandLimits(timeoutMillis = 8_000),
                    )
                    if (resResult is VmResult.Success && resResult.value.exitCode == 0) {
                        val tel = parseTelemetry(resResult.value.stdout)
                        _uiState.update { it.copy(telemetry = tel) }
                    }

                    // Docker containers
                    val dockerResResult = session.execute(
                        "docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null",
                        CommandLimits(timeoutMillis = 8_000),
                    )
                    if (dockerResResult is VmResult.Success) {
                        val dockerRes = dockerResResult.value
                        if (dockerRes.exitCode == 0 && dockerRes.stdout.isNotBlank()) {
                            val conts = parseContainers(dockerRes.stdout)
                            _uiState.update { it.copy(hasDocker = true, containers = conts) }
                        } else if (dockerRes.exitCode == 0) {
                            _uiState.update { it.copy(hasDocker = true, containers = emptyList()) }
                        } else {
                            _uiState.update { it.copy(hasDocker = false) }
                        }
                    } else {
                        _uiState.update { it.copy(hasDocker = false) }
                    }
                } catch (_: Exception) {
                    // Non-fatal telemetry probe
                }
            }
            _uiState.update { it.copy(isLoadingTelemetry = false) }
        }
    }

    fun restartContainer(name: String) {
        viewModelScope.launch {
            when (val sessionResult = connectionManager.session(serverId)) {
                is VmResult.Success -> {
                    val res = sessionResult.value.execute("docker restart '$name'", CommandLimits(timeoutMillis = 20_000))
                    if (res is VmResult.Failure) {
                        _uiState.update { it.copy(error = res.error) }
                    } else {
                        refreshTelemetry()
                    }
                }
                is VmResult.Failure -> {
                    _uiState.update { it.copy(error = sessionResult.error) }
                }
            }
        }
    }

    fun viewContainerLogs(name: String) {
        viewModelScope.launch {
            when (val sessionResult = connectionManager.session(serverId)) {
                is VmResult.Success -> {
                    val res = sessionResult.value.execute(
                        "docker logs --tail 100 '$name'",
                        CommandLimits(timeoutMillis = 10_000),
                    )
                    when (res) {
                        is VmResult.Success -> {
                            val output = res.value.stdout.ifBlank { res.value.stderr }.ifBlank { "No logs found." }
                            _uiState.update { it.copy(containerLogs = name to output) }
                        }
                        is VmResult.Failure -> {
                            _uiState.update { it.copy(error = res.error) }
                        }
                    }
                }
                is VmResult.Failure -> {
                    _uiState.update { it.copy(error = sessionResult.error) }
                }
            }
        }
    }

    fun dismissContainerLogs() {
        _uiState.update { it.copy(containerLogs = null) }
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
                    val total = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: 1L
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
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val result = connectionManager.trustPendingHostKey(serverId)) {
                is VmResult.Failure -> _uiState.update { it.copy(error = result.error, isBusy = false) }
                is VmResult.Success -> {
                    _uiState.update { it.copy(error = null, isBusy = false) }
                    refreshTelemetry()
                }
            }
        }
    }

    fun rejectHostKey() {
        connectionManager.rejectPendingHostKey(serverId)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun lockLaptop() {
        executeLaptopCommand(
            title = "Lock Workstation",
            command = "powershell -Command \"rundll32.exe user32.dll,LockWorkStation\" 2>/dev/null || (which pmset >/dev/null 2>&1 && pmset displaysleepnow) || loginctl lock-session 2>/dev/null || xdg-screensaver lock 2>/dev/null",
        )
    }

    fun sleepLaptop() {
        executeLaptopCommand(
            title = "Sleep / Suspend",
            command = "powershell -Command \"rundll32.exe powrprof.dll,SetSuspendState 0,1,0\" 2>/dev/null || systemctl suspend 2>/dev/null || pmset sleepnow 2>/dev/null",
        )
    }

    fun toggleMute() {
        executeLaptopCommand(
            title = "Toggle Mute",
            command = "powershell -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]173)\" 2>/dev/null || osascript -e 'set volume output muted not (output muted of (get volume settings))' 2>/dev/null || amixer set Master toggle 2>/dev/null",
        )
    }

    fun captureLaptopScreenshot() {
        executeLaptopCommand(
            title = "Remote Screenshot",
            command = "powershell -Command \"Add-Type -AssemblyName System.Windows.Forms; \$s=[System.Windows.Forms.Screen]::PrimaryScreen.Bounds; \$b=New-Object System.Drawing.Bitmap \$s.Width,\$s.Height; \$g=[System.Drawing.Graphics]::FromImage(\$b); \$g.CopyFromScreen(\$s.Location,[System.Drawing.Point]::Empty,\$s.Size); \$p=[System.IO.Path]::GetTempPath()+'screenshot.png'; \$b.Save(\$p); Write-Output ('Saved to ' + \$p)\" 2>/dev/null || (screencapture -x /tmp/screenshot.png 2>/dev/null && echo 'Saved to /tmp/screenshot.png') || (scrot /tmp/screenshot.png 2>/dev/null && echo 'Saved to /tmp/screenshot.png')",
        )
    }

    fun checkLaptopBattery() {
        executeLaptopCommand(
            title = "Battery & Power Status",
            command = "powershell -Command \"Get-CimInstance -ClassName Win32_Battery | Select-Object -Property EstimatedChargeRemaining, BatteryStatus | Format-List\" 2>/dev/null || pmset -g batt 2>/dev/null || (cat /sys/class/power_supply/BAT*/capacity 2>/dev/null | awk '{print \"Battery: \" \$1 \"%\"}') || echo 'Battery telemetry unavailable'",
        )
    }

    fun dismissLaptopResult() {
        _uiState.update { it.copy(laptopActionResult = null) }
    }

    private fun executeLaptopCommand(title: String, command: String) {
        if (_uiState.value.isLaptopActionBusy) return
        _uiState.update { it.copy(isLaptopActionBusy = true) }
        viewModelScope.launch {
            val sessionResult = connectionManager.session(serverId)
            if (sessionResult is VmResult.Success) {
                val res = sessionResult.value.execute(command, CommandLimits(timeoutMillis = 10_000))
                val output = when (res) {
                    is VmResult.Success -> res.value.stdout.ifBlank { res.value.stderr }.ifBlank { "Command completed successfully." }
                    is VmResult.Failure -> "Failed: ${res.error.summary}"
                }
                _uiState.update {
                    it.copy(
                        isLaptopActionBusy = false,
                        laptopActionResult = LaptopActionResult(title, output, res is VmResult.Success),
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLaptopActionBusy = false,
                        laptopActionResult = LaptopActionResult(title, "Server not connected", false),
                    )
                }
            }
        }
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
    }
}
