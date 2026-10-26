package digital.vmstudio.code.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.ai.repository.AgentConversationRepository
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A stored conversation, as the list needs it. */
data class ConversationSummary(
    val id: String,
    val title: String,
    val workingDirectory: String?,
    val serverId: String?,
    val updatedAtMillis: Long,
    val totalTokens: Long,
    /** Present when the backend can continue this conversation rather than restart. */
    val isResumable: Boolean,
)

data class AgentConversationsUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val chatColors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: VmError? = null,
)

/**
 * Lists past agent conversations.
 *
 * This exists because the transcript was already being persisted with the provider's
 * resume id and nothing ever read it back: leaving the chat lost the conversation,
 * and `--resume` only worked within a single visit to the screen.
 */
@HiltViewModel
class AgentConversationsViewModel @Inject constructor(
    private val conversations: AgentConversationRepository,
    private val projectHubClient: digital.vmstudio.code.core.project.VmProjectHubClient,
    private val userPreferences: digital.vmstudio.code.core.common.preferences.UserPreferencesRepository,
) : ViewModel() {

    private val error = MutableStateFlow<VmError?>(null)

    init {
        syncWithHub()
    }

    val uiState: StateFlow<AgentConversationsUiState> = combine(
        conversations.observeConversations().map { list ->
            list.map { conversation ->
                ConversationSummary(
                    id = conversation.id,
                    title = conversation.title,
                    workingDirectory = conversation.workingDirectory,
                    serverId = conversation.serverId,
                    updatedAtMillis = conversation.updatedAtMillis,
                    totalTokens = conversation.promptTokens + conversation.completionTokens,
                    isResumable = conversation.isResumable,
                )
            }
        },
        userPreferences.preferences.map { it.chatColors },
        error,
    ) { list, colors, currentError ->
        AgentConversationsUiState(
            conversations = list,
            chatColors = colors,
            isLoading = false,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AgentConversationsUiState(),
    )

    fun syncWithHub() {
        viewModelScope.launch {
            when (val res = projectHubClient.syncAllChatsFromServer()) {
                is VmResult.Failure -> error.value = res.error
                is VmResult.Success -> Unit
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                projectHubClient.deleteChatFromServer(id)
            } catch (_: Exception) {}
            when (val result = conversations.delete(id)) {
                is VmResult.Failure -> error.value = result.error
                is VmResult.Success -> Unit
            }
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch {
            when (val result = conversations.rename(id, title)) {
                is VmResult.Failure -> error.value = result.error
                is VmResult.Success -> Unit
            }
        }
    }

    fun updateColor(id: String, hex: String?) {
        viewModelScope.launch {
            userPreferences.setChatColor(id, hex)
        }
    }

    fun dismissError() {
        error.value = null
    }
}
