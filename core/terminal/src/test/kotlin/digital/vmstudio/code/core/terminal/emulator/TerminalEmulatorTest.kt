package digital.vmstudio.code.core.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {

    private fun emulator(columns: Int = 20, rows: Int = 5, scrollback: Int = 100) =
        TerminalEmulator(columns, rows, scrollback)

    private fun TerminalEmulator.lineText(row: Int) = buffer.screenLine(row).text()

    /** Control characters built from code points so the source stays plain ASCII. */
    private val ESC = Char(27).toString()
    private val BEL = Char(7).toString()

    // --- plain text --------------------------------------------------------------

    @Test
    fun `printable text lands on the first row`() {
        val term = emulator()

        term.write("hello")

        assertEquals("hello", term.lineText(0))
        assertEquals(5, term.cursorColumn)
        assertEquals(0, term.cursorRow)
    }

    @Test
    fun `carriage return and line feed move the cursor`() {
        val term = emulator()

        term.write("one\r\ntwo")

        assertEquals("one", term.lineText(0))
        assertEquals("two", term.lineText(1))
        assertEquals(1, term.cursorRow)
    }

    @Test
    fun `backspace moves back without erasing`() {
        val term = emulator()

        term.write("abc")

        assertEquals(2, term.cursorColumn)
        assertEquals("abc", term.lineText(0))
    }

    @Test
    fun `tab advances to the next eight column stop`() {
        val term = emulator(columns = 40)

        term.write("ab\tc")

        assertEquals(9, term.cursorColumn)
    }

    // --- wrapping ----------------------------------------------------------------

    @Test
    fun `text wraps at the right margin`() {
        val term = emulator(columns = 5, rows = 3)

        term.write("abcdefg")

        assertEquals("abcde", term.lineText(0))
        assertEquals("fg", term.lineText(1))
    }

    @Test
    fun `a line exactly the width of the screen does not consume the next row`() {
        val term = emulator(columns = 5, rows = 3)

        term.write("abcde")

        assertEquals("abcde", term.lineText(0))
        assertEquals(
            "the wrap must stay pending until another character is printed",
            0,
            term.cursorRow,
        )
        assertEquals("", term.lineText(1))
    }

    @Test
    fun `wrapping past the last row scrolls`() {
        val term = emulator(columns = 4, rows = 2)

        term.write("aaaabbbbcccc")

        assertEquals("bbbb", term.lineText(0))
        assertEquals("cccc", term.lineText(1))
        assertTrue("scrolled-off content is retained", term.buffer.scrollbackSize >= 1)
        assertEquals("aaaa", term.buffer.lineAt(0).text())
    }

    @Test
    fun `scrollback is bounded`() {
        val term = emulator(columns = 4, rows = 2, scrollback = 3)

        repeat(30) { term.write("line\r\n") }

        assertTrue("scrollback must not grow without limit", term.buffer.scrollbackSize <= 3)
    }

    // --- cursor addressing --------------------------------------------------------

    @Test
    fun `cursor position is one-based in the escape sequence`() {
        val term = emulator()

        term.write("$ESC[3;5Hx")

        assertEquals(2, term.cursorRow)
        assertEquals("    x", term.lineText(2))
    }

    @Test
    fun `cursor movement sequences move relatively`() {
        val term = emulator()

        term.write("$ESC[5;5H")
        term.write("$ESC[2A")
        term.write("$ESC[3D")

        assertEquals(2, term.cursorRow)
        assertEquals(1, term.cursorColumn)
    }

    @Test
    fun `cursor movement is clamped to the screen`() {
        val term = emulator(columns = 10, rows = 4)

        term.write("$ESC[99;99H")

        assertEquals(3, term.cursorRow)
        assertEquals(9, term.cursorColumn)
    }

    @Test
    fun `save and restore cursor round trips`() {
        val term = emulator()

        term.write("$ESC[3;7H${ESC}7$ESC[1;1H${ESC}8")

        assertEquals(2, term.cursorRow)
        assertEquals(6, term.cursorColumn)
    }

    // --- erasing ------------------------------------------------------------------

    @Test
    fun `erase to end of line clears from the cursor`() {
        val term = emulator()

        term.write("abcdef")
        term.write("$ESC[1;4H")
        term.write("$ESC[K")

        assertEquals("abc", term.lineText(0))
    }

    @Test
    fun `erase whole screen clears every row`() {
        val term = emulator()

        term.write("one\r\ntwo\r\nthree")
        term.write("$ESC[2J")

        assertEquals("", term.lineText(0))
        assertEquals("", term.lineText(1))
        assertEquals("", term.lineText(2))
    }

    @Test
    fun `erase characters blanks in place without moving the cursor`() {
        val term = emulator()

        term.write("abcdef")
        term.write("$ESC[1;2H")
        term.write("$ESC[3X")

        assertEquals(1, term.cursorColumn)
        assertEquals("a   ef", term.buffer.screenLine(0).textRange(0, 6))
    }

    @Test
    fun `delete characters shifts the remainder left`() {
        val term = emulator()

        term.write("abcdef")
        term.write("$ESC[1;2H$ESC[2P")

        assertEquals("adef", term.lineText(0))
    }

    @Test
    fun `insert characters shifts the remainder right`() {
        val term = emulator()

        term.write("abcdef")
        term.write("$ESC[1;2H$ESC[2@")

        assertEquals("a  bcdef", term.buffer.screenLine(0).textRange(0, 8))
    }

    // --- colour and attributes ----------------------------------------------------

    @Test
    fun `basic foreground colour is applied then reset`() {
        val term = emulator()

        term.write("$ESC[31mred$ESC[0mplain")

        val redStyle = term.buffer.screenLine(0).styleAt(0)
        assertEquals(1L, TerminalStyle.foreground(redStyle))

        val plainStyle = term.buffer.screenLine(0).styleAt(3)
        assertTrue(TerminalStyle.isDefault(TerminalStyle.foreground(plainStyle)))
    }

    @Test
    fun `bright colours map to the upper palette`() {
        val term = emulator()

        term.write("$ESC[91mx")

        assertEquals(9L, TerminalStyle.foreground(term.buffer.screenLine(0).styleAt(0)))
    }

    @Test
    fun `256 colour sequences are parsed`() {
        val term = emulator()

        term.write("$ESC[38;5;208mx")

        assertEquals(208L, TerminalStyle.foreground(term.buffer.screenLine(0).styleAt(0)))
    }

    @Test
    fun `truecolour sequences are parsed`() {
        val term = emulator()

        term.write("$ESC[38;2;18;52;86mx")

        val color = TerminalStyle.foreground(term.buffer.screenLine(0).styleAt(0))
        assertTrue(TerminalStyle.isRgb(color))
        assertEquals(18, TerminalStyle.red(color))
        assertEquals(52, TerminalStyle.green(color))
        assertEquals(86, TerminalStyle.blue(color))
    }

    @Test
    fun `background colour and combined attributes are parsed`() {
        val term = emulator()

        term.write("$ESC[1;4;44mx")

        val style = term.buffer.screenLine(0).styleAt(0)
        assertTrue(TerminalStyle.hasFlag(style, TerminalStyle.FLAG_BOLD))
        assertTrue(TerminalStyle.hasFlag(style, TerminalStyle.FLAG_UNDERLINE))
        assertEquals(4L, TerminalStyle.background(style))
    }

    @Test
    fun `bold is cleared by 22 without clearing colour`() {
        val term = emulator()

        term.write("$ESC[1;31m$ESC[22mx")

        val style = term.buffer.screenLine(0).styleAt(0)
        assertFalse(TerminalStyle.hasFlag(style, TerminalStyle.FLAG_BOLD))
        assertEquals(1L, TerminalStyle.foreground(style))
    }

    // --- scroll regions and alternate screen --------------------------------------

    @Test
    fun `scroll region confines scrolling`() {
        val term = emulator(columns = 10, rows = 5)

        term.write("$ESC[2;4r")
        term.write("$ESC[1;1Htop")
        term.write("$ESC[4;1Hd\n")
        term.write("e")

        assertEquals("the row above the region must not scroll", "top", term.lineText(0))
    }

    @Test
    fun `alternate screen preserves and restores the main screen`() {
        val term = emulator()

        term.write("main content")
        term.write("$ESC[?1049h")
        assertTrue(term.alternateScreenActive)
        assertEquals("", term.lineText(0))

        term.write("alt content")
        assertEquals("alt content", term.lineText(0))

        term.write("$ESC[?1049l")
        assertFalse(term.alternateScreenActive)
        assertEquals("main content", term.lineText(0))
    }

    @Test
    fun `cursor visibility mode is tracked`() {
        val term = emulator()

        term.write("$ESC[?25l")
        assertFalse(term.cursorVisible)

        term.write("$ESC[?25h")
        assertTrue(term.cursorVisible)
    }

    // --- OSC ----------------------------------------------------------------------

    @Test
    fun `window title is captured from an OSC sequence`() {
        val term = emulator()

        term.write("$ESC]0;deploy@server: ~/app${BEL}ready")

        assertEquals("deploy@server: ~/app", term.title)
        assertEquals("the OSC payload must not be printed", "ready", term.lineText(0))
    }

    @Test
    fun `osc terminated by string terminator is handled`() {
        val term = emulator()

        term.write("$ESC]2;title here$ESC\\x")

        assertEquals("title here", term.title)
    }

    // --- chunked and malformed input ----------------------------------------------

    @Test
    fun `an escape sequence split across writes is still parsed`() {
        val term = emulator()

        term.write("$ESC[3")
        term.write(";5")
        term.write("Hx")

        assertEquals(2, term.cursorRow)
        assertEquals("    x", term.lineText(2))
    }

    @Test
    fun `a utf8 character split across writes is reassembled`() {
        val bytes = "é".toByteArray(Charsets.UTF_8)
        val term = emulator()

        term.write(byteArrayOf(bytes[0]))
        term.write(byteArrayOf(bytes[1]))

        assertEquals("é", term.lineText(0))
    }

    @Test
    fun `multibyte utf8 is decoded`() {
        val term = emulator()

        term.write("héllo → 世界")

        assertEquals("héllo → 世界", term.lineText(0))
    }

    @Test
    fun `an unknown escape sequence is discarded without printing`() {
        val term = emulator()

        // Z is a valid CSI final byte (cursor backward tab), which this emulator
        // does not implement. It must be consumed silently, not printed.
        term.write("$ESC[999Zok")

        assertEquals("ok", term.lineText(0))
    }

    @Test
    fun `an enormous parameter cannot produce a negative cursor`() {
        val term = emulator()

        term.write("$ESC[99999999999;99999999999H")

        assertTrue(term.cursorRow >= 0)
        assertTrue(term.cursorColumn >= 0)
    }

    @Test
    fun `a lone escape byte does not corrupt following text`() {
        val term = emulator()

        term.write("$ESC")
        term.write("Zafter")

        assertEquals("after", term.lineText(0))
    }

    // --- resize --------------------------------------------------------------------

    @Test
    fun `shrinking rows keeps earlier output in scrollback`() {
        val term = emulator(columns = 10, rows = 4)

        term.write("one\r\ntwo\r\nthree\r\nfour")
        term.resize(10, 2)

        assertEquals(2, term.rows)
        assertTrue(term.buffer.scrollbackSize >= 2)
        assertTrue(term.buffer.allText().contains("one"))
    }

    @Test
    fun `growing rows restores lines from scrollback`() {
        val term = emulator(columns = 10, rows = 2)

        term.write("one\r\ntwo\r\nthree\r\nfour")
        val beforeScrollback = term.buffer.scrollbackSize
        term.resize(10, 4)

        assertEquals(4, term.rows)
        assertTrue(term.buffer.scrollbackSize < beforeScrollback)
    }

    @Test
    fun `narrowing columns truncates without crashing`() {
        val term = emulator(columns = 20, rows = 3)

        term.write("a-fairly-long-line")
        term.resize(8, 3)

        assertEquals(8, term.columns)
        assertEquals("a-fairly", term.lineText(0))
    }

    @Test
    fun `alternate screen resize preserves dimension consistency and line sizes`() {
        val term = emulator(columns = 20, rows = 5)
        term.write("main line")
        term.write("$ESC[?1049h") // enter alternate screen
        assertTrue(term.alternateScreenActive)

        term.resize(30, 8)
        assertEquals(30, term.columns)
        assertEquals(8, term.rows)

        term.write("$ESC[?1049l") // exit alternate screen
        assertFalse(term.alternateScreenActive)
        assertEquals(30, term.columns)
        assertEquals(8, term.rows)
        assertEquals("main line", term.lineText(0))
        assertEquals(30, term.buffer.screenLine(0).columns)
    }

    // --- revision ------------------------------------------------------------------

    @Test
    fun `revision advances on write so the renderer can skip idle frames`() {
        val term = emulator()
        val before = term.revision

        term.write("x")

        assertNotEquals(before, term.revision)
    }
}
