package digital.vmstudio.code.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.omniroute.agent.FileEditApprovalGate
import digital.vmstudio.code.core.ai.omniroute.agent.FileEditApprovalRequest
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class FileEditApprovalViewModel @Inject constructor(
    private val approvalGate: FileEditApprovalGate,
) : ViewModel() {

    val pending: StateFlow<FileEditApprovalRequest?> = approvalGate.pending

    fun resolve(id: String, approved: Boolean) = approvalGate.resolve(id, approved)
}
