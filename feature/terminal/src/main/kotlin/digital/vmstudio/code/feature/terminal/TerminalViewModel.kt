package digital.vmstudio.code.feature.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.terminal.session.TerminalKey
import digital.vmstudio.code.core.terminal.session.TerminalKeyEncoder
import digital.vmstudio.code.core.terminal.session.TerminalScreen
import digital.vmstudio.code.core.terminal.session.TerminalSession
import digital.vmstudio.code.core.terminal.session.TerminalSessionManager
import digital.vmstudio.code.core.terminal.session.TerminalSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalUiState(
    val isOpening: Boolean = false,
    val sessions: List<TerminalTab> = emptyList(),
    val activeSessionId: String? = null,
    val screen: TerminalScreen = TerminalScreen.EMPTY,
    val sessionState: TerminalSessionState = TerminalSessionState.Starting,
    val fontSizeSp: Float = 12.5f,
    val error: VmError? = null,
) {
    val activeTab: TerminalTab? get() = sessions.firstOrNull { it.id == activeSessionId }

    val hasSessions: Boolean get() = sessions.isNotEmpty()
}

data class TerminalTab(
    val id: String,
    val title: String,
    val serverId: String,
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sessionManager: TerminalSessionManager,
    private val commandGuard: CommandGuard,
    private val preferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String? = savedStateHandle[ARG_SERVER_ID]

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState

    /** Tracks the active session's screen; re-collected whenever the tab changes. */
    private var screenCollector: kotlinx.coroutines.Job? = null

    /** Local mirror of the shell line being typed, used only for the safety check. */
    private val pendingLine = StringBuilder()

    init {
        viewModelScope.launch {
            // Collected continuously, not just read once: a font-size change made in
            // Settings while this screen's ViewModel is still alive (backgrounded,
            // not recreated) would otherwise never reach an already-open terminal.
            preferencesRepository.preferences.collect { preferences ->
                _uiState.value = _uiState.value.copy(fontSizeSp = preferences.terminalFontSizeSp)
            }
        }
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            sessionManager.sessions.collect { sessions ->
                val relevant = sessions.filter { serverId == null || it.serverId == serverId }
                _uiState.value = _uiState.value.copy(
                    sessions = relevant.map { TerminalTab(it.id, it.title, it.serverId) },
                )
                // If the active tab was closed elsewhere, fall back to the last one
                // rather than leaving the screen showing a dead session.
                val active = _uiState.value.activeSessionId
                if (active == null || relevant.none { it.id == active }) {
                    selectSession(relevant.lastOrNull()?.id)
                }
            }
        }
    }

    /**
     * Opens a session sized to the measured view.
     *
     * Called from the view once it knows its cell grid, so the remote shell's first
     * prompt is drawn at the correct width instead of at 80x24 and then reflowed.
     */
    fun openSession(columns: Int, rows: Int) {
        val server = serverId ?: return
        if (_uiState.value.isOpening) return

        _uiState.value = _uiState.value.copy(isOpening = true, error = null)
        viewModelScope.launch {
            val scrollback = preferencesRepository.preferences.first().terminalScrollbackLines
            when (val result = sessionManager.open(server, columns, rows, scrollback)) {
                is VmResult.Success -> {
                    _uiState.value = _uiState.value.copy(isOpening = false)
                    selectSession(result.value.id)
                }
                is VmResult.Failure -> _uiState.value = _uiState.value.copy(
                    isOpening = false,
                    error = result.error,
                )
            }
        }
    }

    fun selectSession(sessionId: String?) {
        _uiState.value = _uiState.value.copy(activeSessionId = sessionId)
        sessionManager.setActive(sessionId)

        screenCollector?.cancel()
        val session = sessionId?.let(sessionManager::session)
        if (session == null) {
            _uiState.value = _uiState.value.copy(screen = TerminalScreen.EMPTY)
            return
        }

        screenCollector = viewModelScope.launch {
            launch { session.screen.collect { screen -> _uiState.value = _uiState.value.copy(screen = screen) } }
            launch { session.state.collect { state -> _uiState.value = _uiState.value.copy(sessionState = state) } }
        }
    }

    fun closeSession(sessionId: String) = sessionManager.close(sessionId)

    /**
     * Forwards typed text to the shell and mirrors it into [pendingLine].
     *
     * The mirror is what makes the safety check possible at all: characters have to
     * reach the remote shell as they are typed so that interactive programs and
     * password prompts behave, which means the app never sees a "command" — only a
     * keystroke stream. Tracking the line locally reconstructs one.
     */
    fun send(text: String) {
        activeSession()?.send(text)
        pendingLine.append(text)
    }

    /**
     * Deletes [count] characters, as one soft-keyboard edit (predictive-text
     * correction, select-all-then-delete) can remove more than one character in a
     * single onValueChange callback. Sending a single BACKSPACE per callback -
     * regardless of how many characters actually disappeared - would leave stale
     * characters on the remote prompt and trim [pendingLine] by only one character,
     * desyncing the safety check's reconstructed line from what is really there.
     */
    fun sendBackspaces(count: Int) {
        if (count <= 0) return
        pendingLine.setLength((pendingLine.length - count).coerceAtLeast(0))
        activeSession()?.send(TerminalKeyEncoder.encode(TerminalKey.BACKSPACE).repeat(count))
    }

    fun sendKey(key: TerminalKey) {
        when (key) {
            // Enter is the commit point, and the only place a safety check can
            // meaningfully intervene.
            TerminalKey.ENTER -> submitLine()
            TerminalKey.BACKSPACE -> {
                if (pendingLine.isNotEmpty()) pendingLine.setLength(pendingLine.length - 1)
                activeSession()?.sendKey(key)
            }
            // Anything that moves the cursor or edits elsewhere in the line makes the
            // local mirror unreliable, so it is abandoned rather than trusted.
            TerminalKey.ARROW_UP, TerminalKey.ARROW_DOWN,
            TerminalKey.ARROW_LEFT, TerminalKey.ARROW_RIGHT,
            TerminalKey.HOME, TerminalKey.END,
            -> {
                pendingLine.setLength(0)
                activeSession()?.sendKey(key)
            }
            else -> activeSession()?.sendKey(key)
        }
    }

    /**
     * Assesses the reconstructed line before letting the newline through.
     *
     * Best-effort by nature. The mirror is discarded whenever the user edits with
     * arrow keys or history recall, and a full-screen program owns the keystrokes
     * entirely — in both cases the line is sent unchecked, because a guess about
     * what the shell's line buffer contains is worse than no guess. Authoritative
     * enforcement lives on [CommandGuard.run], where the whole command string is
     * known: configured project commands and, later, every agent tool call.
     */
    private fun submitLine() {
        val session = activeSession() ?: return
        val server = serverId
        val line = pendingLine.toString().trim()
        pendingLine.setLength(0)

        val checkable = server != null &&
            line.isNotEmpty() &&
            !_uiState.value.screen.alternateScreenActive

        if (!checkable) {
            session.sendKey(TerminalKey.ENTER)
            return
        }

        viewModelScope.launch {
            when (val authorised = commandGuard.authorize(server!!, line)) {
                is VmResult.Success -> session.sendKey(TerminalKey.ENTER)
                is VmResult.Failure -> {
                    // The characters are already echoed on the remote line buffer, so
                    // simply withholding the newline would leave the command sitting
                    // at the prompt looking accepted. Ctrl+C abandons the line and
                    // returns a fresh prompt.
                    session.interrupt()
                    _uiState.value = _uiState.value.copy(error = authorised.error)
                }
            }
        }
    }

    fun sendControl(char: Char) {
        // A control character ends or edits the line in ways the mirror cannot track.
        pendingLine.setLength(0)
        activeSession()?.sendControl(char)
    }

    fun interrupt() {
        pendingLine.setLength(0)
        activeSession()?.interrupt()
    }

    fun clear() = activeSession()?.clear()

    fun resize(columns: Int, rows: Int) = activeSession()?.resize(columns, rows)

    fun scrollBy(rows: Int) = activeSession()?.scrollBy(rows)

    fun scrollToBottom() = activeSession()?.scrollToBottom()

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** Full buffer text for the copy action. */
    suspend fun bufferText(): String = activeSession()?.allText().orEmpty()

    private fun activeSession(): TerminalSession? =
        _uiState.value.activeSessionId?.let(sessionManager::session)

    companion object {
        const val ARG_SERVER_ID = "serverId"
    }
}
