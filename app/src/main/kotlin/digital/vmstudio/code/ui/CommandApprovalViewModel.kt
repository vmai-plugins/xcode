package digital.vmstudio.code.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ssh.command.CommandApprovalGate
import digital.vmstudio.code.core.ssh.command.CommandApprovalRequest
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CommandApprovalViewModel @Inject constructor(
    private val approvalGate: CommandApprovalGate,
) : ViewModel() {

    val pending: StateFlow<CommandApprovalRequest?> = approvalGate.pending

    fun resolve(id: String, approved: Boolean) = approvalGate.resolve(id, approved)
}
