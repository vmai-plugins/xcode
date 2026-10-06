package digital.vmstudio.code.core.terminal.session

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.ssh.connection.ShellChannel
import digital.vmstudio.code.core.terminal.emulator.TerminalEmulator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Lifecycle of one terminal tab. */
sealed interface TerminalSessionState {
    data object Starting : TerminalSessionState
    data object Running : TerminalSessionState
    /** The remote shell exited normally. */
    data object Closed : TerminalSessionState
    data class Failed(val error: VmError) : TerminalSessionState
}

/** An immutable copy of one row, safe to hand to the renderer. */
class TerminalRowSnapshot(
    val chars: CharArray,
    val styles: LongArray,
    val length: Int,
)

/**
 * Immutable view of the terminal for rendering.
 *
 * A snapshot rather than direct access to the emulator: the emulator is mutated on
 * a confined dispatcher while Compose reads on the main thread, and handing the
 * live buffer across that boundary would be a data race that shows up as garbled
 * output under load.
 */
class TerminalScreen(
    val columns: Int,
    val rows: Int,
    val lines: List<TerminalRowSnapshot>,
    val cursorRow: Int,
    val cursorColumn: Int,
    val cursorVisible: Boolean,
    val scrollbackSize: Int,
    /** True while a full-screen program (vim, less, top) owns the screen. */
    val alternateScreenActive: Boolean,
    val title: String?,
    val revision: Long,
) {
    companion object {
        val EMPTY = TerminalScreen(
            columns = 0,
            rows = 0,
            lines = emptyList(),
            cursorRow = 0,
            cursorColumn = 0,
            cursorVisible = false,
            scrollbackSize = 0,
            alternateScreenActive = false,
            title = null,
            revision = -1,
        )
    }
}

/**
 * One interactive shell, bridging an SSH channel to the emulator.
 *
 * The emulator is confined to [emulatorDispatcher], a single-threaded context.
 * Every mutation — remote output, resize, clear — is dispatched there, which is
 * what makes the emulator's lack of internal locking safe and cheap.
 *
 * Output is coalesced: the reader feeds bytes as fast as they arrive but publishes
 * a snapshot at most every [FRAME_INTERVAL_MILLIS]. Without this, a command like
 * `yes` would publish millions of snapshots a second and the UI would spend all its
 * time allocating rather than drawing.
 */
class TerminalSession internal constructor(
    val id: String,
    val serverId: String,
    val title: String,
    private val channel: ShellChannel,
    private val emulator: TerminalEmulator,
    private val scope: CoroutineScope,
    private val emulatorDispatcher: CoroutineDispatcher,
    /**
     * Where the reader loop's blocking `read()` call runs - deliberately not
     * [emulatorDispatcher]. That dispatcher is `limitedParallelism(1)`: a single
     * execution slot, not thread confinement with preemption. A blocking read on an
     * idle shell (sitting at a prompt, nothing to read until the user's own input
     * reaches it) would hold that one slot for as long as there is nothing to read,
     * starving every [send]/[resize]/[clear]/[scrollBy] queued behind it - typing
     * into an idle terminal would do nothing until the remote happened to emit
     * unsolicited output. Only the actual emulator mutation needs to be confined.
     */
    private val readerDispatcher: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow<TerminalSessionState>(TerminalSessionState.Starting)
    val state: StateFlow<TerminalSessionState> = _state.asStateFlow()

    private val _screen = MutableStateFlow(TerminalScreen.EMPTY)
    val screen: StateFlow<TerminalScreen> = _screen.asStateFlow()

    private var readerJob: Job? = null

    /** Rows scrolled back from the bottom; 0 means following live output. */
    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()

    internal fun start() {
        readerJob = scope.launch(readerDispatcher) {
            _state.value = TerminalSessionState.Running
            val buffer = ByteArray(READ_BUFFER_BYTES)
            var lastPublish = 0L
            var trailingPublish: Job? = null

            try {
                while (true) {
                    val read = runInterruptible { readOrEof(buffer) }
                    if (read < 0) break

                    withContext(emulatorDispatcher) {
                        emulator.write(buffer, read)

                        val now = System.currentTimeMillis()
                        if (now - lastPublish >= FRAME_INTERVAL_MILLIS) {
                            trailingPublish?.cancel()
                            trailingPublish = null
                            publishSnapshot()
                            lastPublish = now
                        } else if (trailingPublish == null) {
                            // The end of a burst inside one frame (a prompt, the last
                            // line of `ls`) used to wait for the next byte to show up.
                            trailingPublish = scope.launch(emulatorDispatcher) {
                                delay(FRAME_INTERVAL_MILLIS)
                                trailingPublish = null
                                publishSnapshot()
                                lastPublish = System.currentTimeMillis()
                            }
                        }
                    }
                }
                // Always publish the tail, or the last line before the shell exited
                // would never reach the screen.
                withContext(emulatorDispatcher) { publishSnapshot() }
                _state.value = TerminalSessionState.Closed
                VmLog.i(LogCategory.TERMINAL, TAG, "Shell session $id ended")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                withContext(emulatorDispatcher) { publishSnapshot() }
                _state.value = TerminalSessionState.Failed(
                    VmError.Ssh(
                        summary = "Terminal disconnected",
                        reason = throwable.message,
                        suggestedAction = "Reconnect to continue working on this server.",
                        retryable = true,
                        cause = throwable,
                    ),
                )
                VmLog.w(LogCategory.TERMINAL, TAG, "Shell session $id failed", throwable)
            }
        }
    }

    private fun readOrEof(buffer: ByteArray): Int = try {
        channel.input.read(buffer)
    } catch (_: IOException) {
        // The channel closing under a blocking read is how a normal exit presents.
        -1
    }

    /** Sends raw text, as typed. */
    fun send(text: String) {
        if (text.isEmpty()) return
        scope.launch(emulatorDispatcher) {
            runCatching {
                runInterruptible {
                    channel.output.write(text.toByteArray(Charsets.UTF_8))
                    channel.output.flush()
                }
            }.onFailure {
                VmLog.w(LogCategory.TERMINAL, TAG, "Write to session $id failed", it)
            }
            // Local echo is the remote's job; scrolling back to the bottom on input
            // is not, and users expect typing to return them to the prompt.
            _scrollOffset.value = 0
        }
    }

    fun sendKey(key: TerminalKey) = send(TerminalKeyEncoder.encode(key))

    fun sendControl(char: Char) {
        TerminalKeyEncoder.encodeControl(char)?.let(::send)
    }

    fun interrupt() = send(TerminalKeyEncoder.INTERRUPT)

    fun resize(columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0) return
        scope.launch(emulatorDispatcher) {
            emulator.resize(columns, rows)
            channel.resize(columns, rows)
            publishSnapshot()
        }
    }

    /** Clears the screen and scrollback locally, as `clear` would remotely. */
    fun clear() {
        scope.launch(emulatorDispatcher) {
            emulator.clearAll()
            _scrollOffset.value = 0
            publishSnapshot()
        }
    }

    /** Scrolls the view; [delta] is positive to go back through history. */
    fun scrollBy(delta: Int) {
        scope.launch(emulatorDispatcher) {
            val maximum = emulator.buffer.scrollbackSize
            _scrollOffset.value = (_scrollOffset.value + delta).coerceIn(0, maximum)
            publishSnapshot()
        }
    }

    fun scrollToBottom() {
        scope.launch(emulatorDispatcher) {
            _scrollOffset.value = 0
            publishSnapshot()
        }
    }

    /** Full buffer text, for copy-all and for the agent reading command output. */
    suspend fun allText(): String = withContext(emulatorDispatcher) { emulator.buffer.allText() }

    fun close() {
        readerJob?.cancel()
        runCatching { channel.close() }
        _state.value = TerminalSessionState.Closed
    }

    /**
     * Copies the visible window into an immutable snapshot.
     *
     * Must be called on [emulatorDispatcher]; it reads emulator state directly.
     */
    private fun publishSnapshot() {
        val offset = _scrollOffset.value
        val bottomRow = emulator.buffer.totalRows - offset
        val topRow = (bottomRow - emulator.rows).coerceAtLeast(0)

        val lines = ArrayList<TerminalRowSnapshot>(emulator.rows)
        for (absoluteRow in topRow until topRow + emulator.rows) {
            val line = if (absoluteRow < emulator.buffer.totalRows) {
                emulator.buffer.lineAt(absoluteRow)
            } else {
                null
            }
            lines += if (line == null) {
                TerminalRowSnapshot(CharArray(0), LongArray(0), 0)
            } else {
                TerminalRowSnapshot(
                    chars = line.chars.copyOf(line.length),
                    styles = line.styles.copyOf(line.length),
                    length = line.length,
                )
            }
        }

        _screen.value = TerminalScreen(
            columns = emulator.columns,
            rows = emulator.rows,
            lines = lines,
            cursorRow = emulator.cursorRow,
            cursorColumn = emulator.cursorColumn,
            // The cursor belongs to the live screen; showing it while scrolled back
            // would place it on unrelated history.
            cursorVisible = emulator.cursorVisible && offset == 0,
            scrollbackSize = emulator.buffer.scrollbackSize,
            alternateScreenActive = emulator.alternateScreenActive,
            title = emulator.title,
            revision = emulator.revision,
        )
    }

    private companion object {
        const val TAG = "TerminalSession"
        const val READ_BUFFER_BYTES = 16 * 1024

        /** ~30 fps. Fast enough to feel live, slow enough to survive `yes`. */
        const val FRAME_INTERVAL_MILLIS = 33L
    }
}
