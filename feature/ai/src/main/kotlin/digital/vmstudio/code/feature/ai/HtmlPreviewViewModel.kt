package digital.vmstudio.code.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HtmlPreviewState {
    data object Loading : HtmlPreviewState
    data class Ready(val path: String, val html: String) : HtmlPreviewState
    data class Failed(val error: VmError) : HtmlPreviewState
}

/** Reads a web page the agent wrote on the server so the chat can render it. */
@HiltViewModel
class HtmlPreviewViewModel @Inject constructor(
    private val remoteFileSystem: RemoteFileSystem,
) : ViewModel() {

    private val _state = MutableStateFlow<HtmlPreviewState>(HtmlPreviewState.Loading)
    val state: StateFlow<HtmlPreviewState> = _state.asStateFlow()

    fun load(serverId: String, path: String) {
        _state.value = HtmlPreviewState.Loading
        viewModelScope.launch {
            _state.value = when (val result = remoteFileSystem.readText(serverId, path)) {
                is VmResult.Success -> HtmlPreviewState.Ready(path, result.value)
                is VmResult.Failure -> HtmlPreviewState.Failed(result.error)
            }
        }
    }
}
