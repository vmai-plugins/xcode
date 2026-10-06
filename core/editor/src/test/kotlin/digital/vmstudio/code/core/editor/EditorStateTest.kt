package digital.vmstudio.code.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies editor state management: undo/redo, dirty tracking, and commit behavior.
 */
class EditorStateTest {

    @Test
    fun `initial state is not dirty`() {
        val state = EditorState(initialContent = "hello", fileName = "test.kt")
        assertFalse("Initial state should not be dirty", state.isDirty)
        assertFalse("Initial state should not allow undo", state.canUndo)
        assertFalse("Initial state should not allow redo", state.canRedo)
    }

    @Test
    fun `updateTextFieldValue marks dirty`() {
        val state = EditorState(initialContent = "hello", fileName = "test.kt")
        state.updateTextFieldValue(
            state.textFieldValue.copy(text = "hello world"),
        )
        assertTrue("State should be dirty after edit", state.isDirty)
    }

    @Test
    fun `commitChange checkpoints for undo but keeps unsaved changes dirty`() {
        val state = EditorState(initialContent = "hello", fileName = "test.kt")
        state.updateTextFieldValue(state.textFieldValue.copy(text = "hello world"))
        state.commitChange()

        // A checkpoint is not a save: the buffer still differs from disk.
        assertTrue("State should stay dirty until saved", state.isDirty)
        assertTrue("Undo should be available after commit", state.canUndo)
    }

    @Test
    fun `undo reverts to previous content`() {
        val state = EditorState(initialContent = "hello", fileName = "test.kt")
        state.updateTextFieldValue(state.textFieldValue.copy(text = "hello world"))
        state.commitChange()

        state.undo()

        assertEquals("hello", state.textFieldValue.text)
        assertFalse("State should not be dirty after undo", state.isDirty)
        assertTrue("Redo should be available after undo", state.canRedo)
    }

    @Test
    fun `redo reapplies undone change`() {
        val state = EditorState(initialContent = "hello", fileName = "test.kt")
        state.updateTextFieldValue(state.textFieldValue.copy(text = "hello world"))
        state.commitChange()
        state.undo()

        state.redo()

        assertEquals("hello world", state.textFieldValue.text)
        assertTrue("Redoing back to unsaved content is dirty again", state.isDirty)
    }

    @Test
    fun `multiple commits create undo history`() {
        val state = EditorState(initialContent = "a", fileName = "test.kt")

        state.updateTextFieldValue(state.textFieldValue.copy(text = "ab"))
        state.commitChange()
        state.updateTextFieldValue(state.textFieldValue.copy(text = "abc"))
        state.commitChange()
        state.updateTextFieldValue(state.textFieldValue.copy(text = "abcd"))
        state.commitChange()

        assertTrue(state.canUndo)
        state.undo()
        assertEquals("abc", state.textFieldValue.text)
        state.undo()
        assertEquals("ab", state.textFieldValue.text)
        state.undo()
        assertEquals("a", state.textFieldValue.text)
        assertFalse("No more undo history", state.canUndo)
    }

    @Test
    fun `commit without change does not add to undo stack`() {
        val state = EditorState(initialContent = "hello", fileName = "test.kt")
        state.commitChange()

        assertFalse("No undo available without changes", state.canUndo)
    }

    @Test
    fun `markSaved clears dirty flag`() {
        val state = EditorState(initialContent = "hello", fileName = "test.kt")
        state.updateTextFieldValue(state.textFieldValue.copy(text = "hello world"))
        state.markSaved()

        assertFalse("State should not be dirty after save", state.isDirty)
    }

    @Test
    fun `typing during a save keeps the file dirty`() {
        val state = EditorState(initialContent = "a", fileName = "test.kt")
        state.updateTextFieldValue(state.textFieldValue.copy(text = "ab"))
        val written = state.textFieldValue.text
        state.updateTextFieldValue(state.textFieldValue.copy(text = "abc"))
        state.markSaved(written)

        assertTrue("Text typed after the save started is not on the server", state.isDirty)
    }

    @Test
    fun `undo right after typing can be redone`() {
        val state = EditorState(initialContent = "a", fileName = "test.kt")
        state.updateTextFieldValue(state.textFieldValue.copy(text = "ab"))
        state.undo()
        assertEquals("a", state.textFieldValue.text)
        state.redo()
        assertEquals("ab", state.textFieldValue.text)
    }

    @Test
    fun `language is detected from filename`() {
        val kotlinState = EditorState(initialContent = "", fileName = "main.kt")
        val javaState = EditorState(initialContent = "", fileName = "Main.java")
        val plainState = EditorState(initialContent = "", fileName = "readme")

        assertEquals(EditorLanguage.KOTLIN, kotlinState.language)
        assertEquals(EditorLanguage.JAVA, javaState.language)
        assertEquals(EditorLanguage.PLAIN, plainState.language)
    }
}
