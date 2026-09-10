package digital.vmstudio.code.feature.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.ssh.model.ServerDraft
import digital.vmstudio.code.core.ssh.model.ServerField
import digital.vmstudio.code.core.ssh.model.ServerValidation
import digital.vmstudio.code.core.ssh.model.ServerValidator
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerEditUiState(
    val draft: ServerDraft = ServerDraft(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Populated only after the first save attempt, so the form is not hostile. */
    val validation: ServerValidation = ServerValidation(),
    val showValidation: Boolean = false,
    val saveError: VmError? = null,
    val savedServerId: String? = null,
) {
    fun errorFor(field: ServerField): VmError.Validation? =
        if (showValidation) validation[field] else null
}

@HiltViewModel
class ServerEditViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String? = savedStateHandle[ARG_SERVER_ID]

    private val _uiState = MutableStateFlow(ServerEditUiState(isEditing = serverId != null))
    val uiState: StateFlow<ServerEditUiState> = _uiState.asStateFlow()

    init {
        serverId?.let(::loadExisting)
    }

    private fun loadExisting(id: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = serverRepository.draftFor(id)) {
                is VmResult.Success -> _uiState.update {
                    it.copy(draft = result.value, isLoading = false)
                }
                is VmResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, saveError = result.error)
                }
            }
        }
    }

    fun updateDraft(transform: (ServerDraft) -> ServerDraft) {
        _uiState.update { state ->
            val updated = transform(state.draft)
            state.copy(
                draft = updated,
                // Once the user has seen errors, revalidate live so fixing a field
                // clears its message immediately.
                validation = if (state.showValidation) {
                    ServerValidator.validate(updated)
                } else {
                    state.validation
                },
                saveError = null,
            )
        }
    }

    fun setName(value: String) = updateDraft { it.copy(name = value) }

    fun setHost(value: String) = updateDraft { it.copy(host = value) }

    fun setPort(value: String) = updateDraft { it.copy(port = value.filter(Char::isDigit)) }

    fun setUsername(value: String) = updateDraft { it.copy(username = value) }

    fun setAuthMethod(value: SshAuthMethod) = updateDraft { draft ->
        draft.copy(
            authMethod = value,
            // Switching method invalidates any previously stored secret, so the
            // user must supply the new one rather than silently keeping the old.
            retainExistingSecret = draft.retainExistingSecret && value == draft.authMethod,
        )
    }

    fun setPassword(value: String) = updateDraft {
        it.copy(password = value, retainExistingSecret = false)
    }

    fun setPrivateKey(value: String) = updateDraft {
        it.copy(privateKeyPem = value, retainExistingSecret = false)
    }

    fun setPassphrase(value: String) = updateDraft {
        it.copy(passphrase = value, retainExistingSecret = false)
    }

    fun setEnvironment(value: ServerEnvironment) = updateDraft { it.copy(environment = value) }

    fun setPreferredShell(value: String) = updateDraft { it.copy(preferredShell = value) }

    fun setDefaultDirectory(value: String) = updateDraft { it.copy(defaultDirectory = value) }

    fun setNotes(value: String) = updateDraft { it.copy(notes = value) }

    fun setConnectTimeout(value: String) = updateDraft {
        it.copy(connectTimeoutSeconds = value.filter(Char::isDigit))
    }

    fun setKeepAlive(value: String) = updateDraft {
        it.copy(keepAliveSeconds = value.filter(Char::isDigit))
    }

    fun save() {
        val current = _uiState.value
        val validation = ServerValidator.validate(current.draft)
        if (!validation.isValid) {
            _uiState.update { it.copy(validation = validation, showValidation = true) }
            return
        }

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = serverRepository.save(current.draft)) {
                is VmResult.Success -> _uiState.update {
                    // Clear secret fields from UI state the moment they are stored:
                    // ViewModel state survives configuration changes and can be
                    // captured in a heap dump.
                    it.copy(
                        isSaving = false,
                        savedServerId = result.value.id,
                        draft = it.draft.copy(
                            password = "",
                            privateKeyPem = "",
                            passphrase = "",
                        ),
                    )
                }
                is VmResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, saveError = result.error)
                }
            }
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(savedServerId = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(saveError = null) }
    }

    override fun onCleared() {
        // Best-effort scrub of any secret still held in the draft.
        _uiState.update {
            it.copy(draft = it.draft.copy(password = "", privateKeyPem = "", passphrase = ""))
        }
        super.onCleared()
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
    }
}
