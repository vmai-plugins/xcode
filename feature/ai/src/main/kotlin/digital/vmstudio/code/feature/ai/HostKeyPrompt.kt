package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.host.HostKeyCandidate
import digital.vmstudio.code.core.ssh.host.HostKeyVerdict
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lets the chat approve a server's SSH key itself.
 *
 * The agent's tools reach the server over SSH, which refuses a host it has never
 * seen. Only the server screen used to ask, so a chat opened straight onto a new
 * server failed every tool with "Unrecognised host" and no way to fix it. Opening
 * the chat now connects once, which raises the same trust prompt in place.
 */
@HiltViewModel
class HostKeyPromptViewModel @Inject constructor(
    private val connectionManager: SshConnectionManager,
) : ViewModel() {

    private var serverId: String? = null

    val pending: StateFlow<HostKeyVerdict?> = connectionManager.pendingHostKeys
        .map { keys -> serverId?.let(keys::get) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Connects once so an unknown host raises its prompt before the first tool call. */
    fun watch(server: String) {
        serverId = server
        viewModelScope.launch { connectionManager.session(server) }
    }

    fun trust() {
        val server = serverId ?: return
        viewModelScope.launch { connectionManager.trustPendingHostKey(server) }
    }

    fun reject() {
        serverId?.let(connectionManager::rejectPendingHostKey)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
internal fun HostKeyPrompt(
    serverId: String?,
    viewModel: HostKeyPromptViewModel = hiltViewModel(),
) {
    LaunchedEffect(serverId) { serverId?.let(viewModel::watch) }
    val verdict by viewModel.pending.collectAsStateWithLifecycle()
    val current = verdict ?: return
    val changed = current is HostKeyVerdict.Mismatch
    val candidate: HostKeyCandidate = when (current) {
        is HostKeyVerdict.Unknown -> current.candidate
        is HostKeyVerdict.Mismatch -> current.candidate
        else -> return
    }

    VmDialog(
        title = if (changed) "Host key has changed" else "Trust this server?",
        onDismiss = viewModel::reject,
        confirmLabel = if (changed) "Trust the new key" else "Trust and connect",
        onConfirm = viewModel::trust,
        dismissLabel = "Cancel",
        destructive = changed,
        icon = if (changed) Icons.Default.Warning else null,
    ) {
        Text(
            text = if (changed) {
                "The key from ${candidate.host} is different from the one you trusted before. " +
                    "That is normal after a server rebuild, but it is also what interception looks like."
            } else {
                "First connection to ${candidate.host}. Check the fingerprint matches your server."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Column {
            Text(candidate.keyType, style = MaterialTheme.typography.labelMedium)
            Text(candidate.fingerprintSha256, style = VmTheme.code.mono)
        }
    }
}
