package digital.vmstudio.code.core.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Immutable snapshot of editor state for rendering.
 */
@Immutable
data class EditorSnapshot(
    val textFieldValue: TextFieldValue,
    val language: EditorLanguage,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isDirty: Boolean,
    val fileName: String,
)

/**
 * Mutable editor state with undo/redo support.
 *
 * The state is held outside Compose (in a ViewModel) so it survives
 * recomposition and is observed via [snapshot]. The [TextFieldValue] is the
 * source of truth for the text field.
 *
 * Two baselines are tracked and kept distinct on purpose:
 *  - [savedText] is what is on disk; [isDirty] is measured against it, so a
 *    round-trip edit that returns the text to the saved content clears the dirty
 *    flag and an undo checkpoint never falsely marks the file clean.
 *  - [checkpointText] is the last undo checkpoint; [commitChange] moves it
 *    without touching the saved baseline.
 */
class EditorState(
    initialContent: String = "",
    val fileName: String,
    val language: EditorLanguage = EditorLanguage.fromFileName(fileName),
) {
    var textFieldValue by mutableStateOf(
        TextFieldValue(
            text = initialContent,
            selection = TextRange(0),
        )
    )
        private set

    var isDirty by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var checkpointText = initialContent
    private var savedText = initialContent

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Updates the text from the UI. Does not push to the undo stack — that
     * happens on [commitChange] when the user pauses or performs an action.
     */
    fun updateTextFieldValue(newValue: TextFieldValue) {
        textFieldValue = newValue
        isDirty = newValue.text != savedText
    }

    /**
     * Commits the current text as an undoable change. Call this when the
     * user pauses typing or performs an action that should be a checkpoint.
     */
    fun commitChange() {
        val currentText = textFieldValue.text
        if (currentText != checkpointText) {
            undoStack.addLast(checkpointText)
            redoStack.clear()
            checkpointText = currentText
            // Each entry is a full copy of the file, so the history is bounded
            // by total size too, or a large file runs the app out of memory.
            while (undoStack.size > MAX_UNDO_SIZE ||
                (undoStack.size > 1 && undoStack.sumOf { it.length } > MAX_UNDO_CHARS)
            ) {
                undoStack.removeFirst()
            }
        }
    }

    fun undo() {
        // Typing since the last checkpoint becomes one, so Redo can bring it back.
        commitChange()
        if (undoStack.isEmpty()) return
        redoStack.addLast(checkpointText)
        val previous = undoStack.removeLast()
        checkpointText = previous
        applyText(previous)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(checkpointText)
        val next = redoStack.removeLast()
        checkpointText = next
        applyText(next)
    }

    /**
     * Records [written] as what is on disk. Typing that happened while the save
     * was in flight is not in it, so the file stays dirty until saved again.
     */
    fun markSaved(written: String = textFieldValue.text) {
        savedText = written
        isDirty = textFieldValue.text != written
    }

    fun snapshot(): EditorSnapshot = EditorSnapshot(
        textFieldValue = textFieldValue,
        language = language,
        canUndo = canUndo,
        canRedo = canRedo,
        isDirty = isDirty,
        fileName = fileName,
    )

    private fun applyText(text: String) {
        textFieldValue = textFieldValue.copy(text = text, selection = TextRange(text.length))
        isDirty = text != savedText
    }

    private companion object {
        const val MAX_UNDO_SIZE = 100
        const val MAX_UNDO_CHARS = 4_000_000
    }
}
