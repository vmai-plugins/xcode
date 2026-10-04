package digital.vmstudio.code.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface NewChatTarget {
    data object Loading : NewChatTarget

    /** No server yet: the one thing the user must do before chatting. */
    data object NoServer : NewChatTarget

    data class Server(val serverId: String) : NewChatTarget
}

/** Picks the server a fresh chat opens on: the one connected to most recently. */
@HiltViewModel
class NewChatViewModel @Inject constructor(
    serverRepository: ServerRepository,
) : ViewModel() {

    val target: StateFlow<NewChatTarget> = serverRepository.servers
        .map { servers ->
            val latest = servers.maxByOrNull { it.lastConnectedAtMillis }
            if (latest == null) NewChatTarget.NoServer else NewChatTarget.Server(latest.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NewChatTarget.Loading,
        )
}
