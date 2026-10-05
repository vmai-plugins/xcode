package digital.vmstudio.code.core.terminal.session

import digital.vmstudio.code.core.common.dispatcher.ApplicationScope
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.terminal.emulator.TerminalEmulator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every open terminal tab.
 *
 * Sessions live in the application scope rather than a ViewModel: a build started
 * in a terminal must keep running when the user navigates to the editor, and must
 * survive a rotation. The UI observes; it does not own.
 */
@Singleton
class TerminalSessionManager @Inject constructor(
    private val connectionManager: SshConnectionManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val sessionsById = ConcurrentHashMap<String, TerminalSession>()

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    fun session(id: String): TerminalSession? = sessionsById[id]

    /**
     * Opens a shell on [serverId], connecting first if necessary.
     *
     * [columns] and [rows] come from the measured terminal view; passing them at
     * creation means the remote shell draws its first prompt at the right width
     * rather than at 80x24 and then reflowing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun open(
        serverId: String,
        columns: Int,
        rows: Int,
        scrollbackLines: Int = TerminalEmulator.DEFAULT_SCROLLBACK,
        title: String? = null,
    ): VmResult<TerminalSession> =
        connectionManager.session(serverId).flatMap { sshSession ->
            sshSession.openShell(columns.coerceAtLeast(MIN_COLUMNS), rows.coerceAtLeast(MIN_ROWS))
                .map { channel ->
                    val id = UUID.randomUUID().toString()
                    val session = TerminalSession(
                        id = id,
                        serverId = serverId,
                        title = title ?: defaultTitle(sshSession.server.name),
                        channel = channel,
                        emulator = TerminalEmulator(
                            columns = columns.coerceAtLeast(MIN_COLUMNS),
                            rows = rows.coerceAtLeast(MIN_ROWS),
                            maxScrollback = scrollbackLines,
                        ),
                        scope = applicationScope,
                        // One slot per session confines that session's emulator
                        // mutations without serialising them behind every other tab.
                        // The reader's blocking read runs on the unrestricted
                        // ioDispatcher instead - see TerminalSession's own doc on
                        // readerDispatcher for why sharing this slot with it would
                        // starve input on an idle shell.
                        emulatorDispatcher = ioDispatcher.limitedParallelism(1),
                        readerDispatcher = ioDispatcher,
                    )
                    session.start()
                    sessionsById[id] = session
                    _sessions.value = _sessions.value + session
                    _activeSessionId.value = id
                    VmLog.i(
                        LogCategory.TERMINAL,
                        TAG,
                        "Opened terminal $id on ${sshSession.server.name} (${columns}x$rows)",
                    )
                    session
                }
        }

    fun setActive(sessionId: String?) {
        _activeSessionId.value = sessionId
    }

    fun close(sessionId: String) {
        sessionsById.remove(sessionId)?.let { session ->
            session.close()
            _sessions.value = _sessions.value.filterNot { it.id == sessionId }
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = _sessions.value.lastOrNull()?.id
            }
            VmLog.i(LogCategory.TERMINAL, TAG, "Closed terminal $sessionId")
        }
    }

    /** Closes every session for a server, used when its connection is dropped. */
    private fun defaultTitle(serverName: String): String {
        val existing = _sessions.value.count { it.title.startsWith(serverName) }
        return if (existing == 0) serverName else "$serverName ${existing + 1}"
    }

    private companion object {
        const val TAG = "TerminalSessionManager"
        const val MIN_COLUMNS = 20
        const val MIN_ROWS = 4
    }
}
