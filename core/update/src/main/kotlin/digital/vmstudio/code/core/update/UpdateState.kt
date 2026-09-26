package digital.vmstudio.code.core.update

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.update.model.UpdateInfo
import java.io.File

/** State of the self-update flow, shared by every screen that shows it. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState
    data class Failed(val error: VmError) : UpdateState
}
