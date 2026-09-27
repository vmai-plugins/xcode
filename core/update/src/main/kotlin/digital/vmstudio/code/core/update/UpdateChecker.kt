package digital.vmstudio.code.core.update

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.update.model.UpdateInfo

interface UpdateChecker {
    /** Null means the installed version is already the latest published release. */
    suspend fun checkForUpdate(): VmResult<UpdateInfo?>
}
