package digital.vmstudio.code.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PreviewMode {
    FILE,
    PORT,
}

data class HtmlPreviewUiState(
    val mode: PreviewMode = PreviewMode.FILE,
    val path: String = "",
    val htmlContent: String? = null,
    val liveUrl: String = "",
    val selectedPort: Int = 3000,
    val availablePorts: List<Int> = listOf(3000, 5173, 8080, 8000, 4200, 8888),
    val serverHost: String = "localhost",
    val isLoading: Boolean = false,
    val error: VmError? = null,
    val isMobileView: Boolean = true,
)

/**
 * Manages both static workspace HTML preview and live VPS/container web app testing.
 */
@HiltViewModel
class HtmlPreviewViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HtmlPreviewUiState())
    val state: StateFlow<HtmlPreviewUiState> = _state.asStateFlow()

    private var currentServerId: String = ""

    fun init(serverId: String, initialPath: String) {
        currentServerId = serverId
        viewModelScope.launch {
            val serverResult = serverRepository.get(serverId)
            val host = if (serverResult is VmResult.Success) {
                serverResult.value.host
            } else {
                "localhost"
            }
            _state.update {
                it.copy(
                    path = initialPath,
                    serverHost = host,
                    liveUrl = "http://$host:3000",
                )
            }
            if (initialPath.endsWith(".html", ignoreCase = true) ||
                initialPath.endsWith(".htm", ignoreCase = true)
            ) {
                loadStaticFile(initialPath)
            } else {
                // If opening a directory, default to Live Port Preview
                selectPort(3000)
            }
        }
    }

    fun loadStaticFile(path: String) {
        _state.update { it.copy(mode = PreviewMode.FILE, path = path, isLoading = true, error = null) }
        val effectiveServerId = when {
            path.startsWith("/vmhub/") || currentServerId.isBlank() -> RemoteFileSystem.LOCAL_SERVER_ID
            else -> currentServerId
        }
        viewModelScope.launch {
            when (val result = remoteFileSystem.readText(effectiveServerId, path)) {
                is VmResult.Success -> {
                    _state.update { it.copy(htmlContent = result.value, isLoading = false) }
                }
                is VmResult.Failure -> {
                    _state.update { it.copy(error = result.error, isLoading = false) }
                }
            }
        }
    }

    fun selectPort(port: Int) {
        val host = _state.value.serverHost
        val url = "http://$host:$port"
        _state.update {
            it.copy(
                mode = PreviewMode.PORT,
                selectedPort = port,
                liveUrl = url,
                isLoading = false,
                error = null,
            )
        }
    }

    fun updateCustomUrl(url: String) {
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }
        _state.update { it.copy(mode = PreviewMode.PORT, liveUrl = cleanUrl) }
    }

    fun toggleMobileView() {
        _state.update { it.copy(isMobileView = !it.isMobileView) }
    }

    fun refresh() {
        val current = _state.value
        if (current.mode == PreviewMode.FILE && current.path.isNotBlank()) {
            loadStaticFile(current.path)
        }
    }
}
