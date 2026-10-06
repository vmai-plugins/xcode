package digital.vmstudio.code.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface NewChatTarget {
    data object Loading : NewChatTarget

    /** A general chat: no server saved yet, or the last chat had no project. */
    data object General : NewChatTarget

    data class Server(val serverId: String) : NewChatTarget
}

/**
 * Where a fresh chat opens: on the server connected to most recently, or as a
 * general chat when there is no server or the last chat had no project. A server
 * is no longer required to talk to a model.
 */
@HiltViewModel
class NewChatViewModel @Inject constructor(
    serverRepository: ServerRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val target: StateFlow<NewChatTarget> = combine(
        serverRepository.servers,
        preferences.preferences,
    ) { servers, prefs ->
        val latest = servers.maxByOrNull { it.lastConnectedAtMillis }
        if (latest == null || prefs.lastChatGeneral) {
            NewChatTarget.General
        } else {
            NewChatTarget.Server(latest.id)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NewChatTarget.Loading,
        )
}
