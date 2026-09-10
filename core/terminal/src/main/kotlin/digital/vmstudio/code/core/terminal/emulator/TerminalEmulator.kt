package digital.vmstudio.code.core.terminal.emulator

/**
 * A VT100/xterm-subset terminal emulator.
 *
 * **Why hand-written.** Termux's emulator is not published in a form usable as a
 * dependency, and the alternatives are either abandoned or JVM-desktop-oriented.
 * The subset implemented here is chosen to cover what developer tooling actually
 * emits: colour (including 256-colour and truecolour), cursor addressing, line and
 * screen erase, scroll regions, the alternate screen used by `vim`, `less` and
 * `top`, and window titles.
 *
 * Not implemented, deliberately: double-width/height lines, national replacement
 * character sets, and mouse reporting. Each is rare in a development shell and
 * carries real complexity.
 *
 * The class is not thread-safe; [TerminalSession] confines it to a single
 * coroutine.
 */
class TerminalEmulator(
    columns: Int,
    rows: Int,
    maxScrollback: Int = DEFAULT_SCROLLBACK,
) {

    val buffer = TerminalBuffer(columns, rows, maxScrollback)

    var cursorRow: Int = 0
        private set

    var cursorColumn: Int = 0
        private set

    var cursorVisible: Boolean = true
        private set

    /** Window title from OSC 0/2, when the shell sets one. */
    var title: String? = null
        private set

    /** True while an alternate screen (vim, less, top) is active. */
    var alternateScreenActive: Boolean = false
        private set

    val columns: Int get() = buffer.columns

    val rows: Int get() = buffer.rows

    /** Incremented on every change, so the renderer can skip identical frames. */
    var revision: Long = 0L
        private set

    private var style: Long = TerminalStyle.DEFAULT

    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private var savedRow = 0
    private var savedColumn = 0
    private var savedStyle = TerminalStyle.DEFAULT

    /** Auto-wrap mode (DECAWM); on by default, as in a real terminal. */
    private var autoWrap = true

    /**
     * Set when the cursor has written the last column but not yet wrapped. Real
     * terminals defer the wrap until the next character arrives, which is what
     * makes a line exactly as wide as the screen not produce a blank line.
     */
    private var wrapPending = false

    private var mainScreen: MutableList<TerminalLine>? = null
    private var mainCursorRow = 0
    private var mainCursorColumn = 0

    private val parser = AnsiParser(this)

    private val tabStops = sortedSetOf<Int>().apply {
        for (column in 0 until columns step DEFAULT_TAB_WIDTH) add(column)
    }

    // --- input ------------------------------------------------------------------

    fun write(bytes: ByteArray, length: Int = bytes.size) {
        parser.feed(bytes, length)
        revision++
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) return
        buffer.resize(newColumns, newRows, style)
        mainScreen?.let { saved ->
            saved.forEach { it.resize(newColumns) }
            while (saved.size > newRows) {
                saved.removeAt(0)
            }
            while (saved.size < newRows) {
                saved.add(TerminalLine(newColumns).also { it.clear(style) })
            }
        }
        scrollTop = 0
        scrollBottom = buffer.rows - 1
        cursorRow = cursorRow.coerceIn(0, buffer.rows - 1)
        cursorColumn = cursorColumn.coerceIn(0, buffer.columns - 1)
        tabStops.clear()
        for (column in 0 until newColumns step DEFAULT_TAB_WIDTH) tabStops.add(column)
        revision++
    }

    fun reset() {
        buffer.clearScreen(TerminalStyle.DEFAULT)
        buffer.clearScrollback()
        style = TerminalStyle.DEFAULT
        cursorRow = 0
        cursorColumn = 0
        cursorVisible = true
        autoWrap = true
        wrapPending = false
        scrollTop = 0
        scrollBottom = rows - 1
        alternateScreenActive = false
        mainScreen = null
        revision++
    }

    /** Clears the visible screen and scrollback, as the `clear` command would. */
    fun clearAll() {
        buffer.clearScreen(style)
        buffer.clearScrollback()
        cursorRow = 0
        cursorColumn = 0
        wrapPending = false
        revision++
    }

    // --- printable text ----------------------------------------------------------

    internal fun printChar(char: Char) {
        if (wrapPending && autoWrap) {
            buffer.screenLine(cursorRow).wrapped = true
            cursorColumn = 0
            lineFeed()
            wrapPending = false
        }

        buffer.screenLine(cursorRow).setCell(cursorColumn, char, style)

        if (cursorColumn + 1 >= columns) {
            // Defer the wrap: a character written in the final column must not move
            // the cursor to the next row until something else is printed.
            wrapPending = autoWrap
            if (!autoWrap) cursorColumn = columns - 1
        } else {
            cursorColumn++
        }
    }

    internal fun lineFeed() {
        wrapPending = false
        if (cursorRow == scrollBottom) {
            buffer.scrollUp(scrollTop, scrollBottom, 1, style)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    internal fun reverseLineFeed() {
        wrapPending = false
        if (cursorRow == scrollTop) {
            buffer.scrollDown(scrollTop, scrollBottom, 1, style)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    internal fun carriageReturn() {
        cursorColumn = 0
        wrapPending = false
    }

    internal fun backspace() {
        wrapPending = false
        if (cursorColumn > 0) cursorColumn--
    }

    internal fun tab() {
        wrapPending = false
        val next = tabStops.firstOrNull { it > cursorColumn } ?: (columns - 1)
        cursorColumn = next.coerceAtMost(columns - 1)
    }

    internal fun bell() {
        // Intentionally silent. An audible or vibrating bell fires constantly from
        // shell completion and would be hostile on a phone.
    }

    // --- cursor movement ----------------------------------------------------------

    internal fun moveCursor(row: Int, column: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorColumn = column.coerceIn(0, columns - 1)
        wrapPending = false
    }

    internal fun moveCursorBy(rowDelta: Int, columnDelta: Int) =
        moveCursor(cursorRow + rowDelta, cursorColumn + columnDelta)

    internal fun setColumn(column: Int) {
        cursorColumn = column.coerceIn(0, columns - 1)
        wrapPending = false
    }

    internal fun setRow(row: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        wrapPending = false
    }

    internal fun saveCursor() {
        savedRow = cursorRow
        savedColumn = cursorColumn
        savedStyle = style
    }

    internal fun restoreCursor() {
        cursorRow = savedRow.coerceIn(0, rows - 1)
        cursorColumn = savedColumn.coerceIn(0, columns - 1)
        style = savedStyle
        wrapPending = false
    }

    internal fun setScrollRegion(top: Int, bottom: Int) {
        val newTop = top.coerceIn(0, rows - 1)
        val newBottom = bottom.coerceIn(newTop, rows - 1)
        scrollTop = newTop
        scrollBottom = newBottom
        // DECSTBM homes the cursor.
        moveCursor(0, 0)
    }

    // --- erasing ------------------------------------------------------------------

    /** ED: 0 = to end, 1 = to start, 2 = whole screen, 3 = screen and scrollback. */
    internal fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                buffer.screenLine(cursorRow).clearRange(cursorColumn, columns, style)
                for (row in cursorRow + 1 until rows) buffer.screenLine(row).clear(style)
            }
            1 -> {
                buffer.screenLine(cursorRow).clearRange(0, cursorColumn + 1, style)
                for (row in 0 until cursorRow) buffer.screenLine(row).clear(style)
            }
            2 -> buffer.clearScreen(style)
            3 -> {
                buffer.clearScreen(style)
                buffer.clearScrollback()
            }
        }
        wrapPending = false
    }

    /** EL: 0 = to end of line, 1 = to start, 2 = whole line. */
    internal fun eraseInLine(mode: Int) {
        val line = buffer.screenLine(cursorRow)
        when (mode) {
            0 -> line.clearRange(cursorColumn, columns, style)
            1 -> line.clearRange(0, cursorColumn + 1, style)
            2 -> line.clear(style)
        }
        wrapPending = false
    }

    /** ECH: erase [count] characters from the cursor without moving it. */
    internal fun eraseCharacters(count: Int) {
        buffer.screenLine(cursorRow)
            .clearRange(cursorColumn, cursorColumn + count.coerceAtLeast(1), style)
    }

    internal fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        buffer.scrollDown(cursorRow, scrollBottom, count.coerceAtLeast(1), style)
    }

    internal fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        buffer.scrollUp(cursorRow, scrollBottom, count.coerceAtLeast(1), style)
    }

    internal fun insertCharacters(count: Int) {
        val line = buffer.screenLine(cursorRow)
        val shift = count.coerceIn(1, columns - cursorColumn)
        for (column in columns - 1 downTo cursorColumn + shift) {
            line.setCell(column, line.charAt(column - shift), line.styleAt(column - shift))
        }
        line.clearRange(cursorColumn, cursorColumn + shift, style)
    }

    internal fun deleteCharacters(count: Int) {
        val line = buffer.screenLine(cursorRow)
        val shift = count.coerceIn(1, columns - cursorColumn)
        for (column in cursorColumn until columns - shift) {
            line.setCell(column, line.charAt(column + shift), line.styleAt(column + shift))
        }
        line.clearRange(columns - shift, columns, style)
    }

    // --- attributes ----------------------------------------------------------------

    internal fun applyGraphicRendition(params: IntArray) {
        if (params.isEmpty()) {
            style = TerminalStyle.DEFAULT
            return
        }

        var index = 0
        while (index < params.size) {
            when (val code = params[index]) {
                0 -> style = TerminalStyle.DEFAULT
                1 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_BOLD)
                2 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_DIM)
                3 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_ITALIC)
                4 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_UNDERLINE)
                5, 6 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_BLINK)
                7 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_INVERSE)
                8 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_INVISIBLE)
                9 -> style = TerminalStyle.addFlag(style, TerminalStyle.FLAG_STRIKETHROUGH)
                21, 22 -> style = TerminalStyle.removeFlag(
                    TerminalStyle.removeFlag(style, TerminalStyle.FLAG_BOLD),
                    TerminalStyle.FLAG_DIM,
                )
                23 -> style = TerminalStyle.removeFlag(style, TerminalStyle.FLAG_ITALIC)
                24 -> style = TerminalStyle.removeFlag(style, TerminalStyle.FLAG_UNDERLINE)
                25 -> style = TerminalStyle.removeFlag(style, TerminalStyle.FLAG_BLINK)
                27 -> style = TerminalStyle.removeFlag(style, TerminalStyle.FLAG_INVERSE)
                28 -> style = TerminalStyle.removeFlag(style, TerminalStyle.FLAG_INVISIBLE)
                29 -> style = TerminalStyle.removeFlag(style, TerminalStyle.FLAG_STRIKETHROUGH)

                in 30..37 -> style =
                    TerminalStyle.withForeground(style, TerminalStyle.indexed(code - 30))
                in 40..47 -> style =
                    TerminalStyle.withBackground(style, TerminalStyle.indexed(code - 40))
                // Bright variants map to palette entries 8..15.
                in 90..97 -> style =
                    TerminalStyle.withForeground(style, TerminalStyle.indexed(code - 90 + 8))
                in 100..107 -> style =
                    TerminalStyle.withBackground(style, TerminalStyle.indexed(code - 100 + 8))

                39 -> style = TerminalStyle.withForeground(
                    style,
                    TerminalStyle.indexed(TerminalStyle.COLOR_DEFAULT),
                )
                49 -> style = TerminalStyle.withBackground(
                    style,
                    TerminalStyle.indexed(TerminalStyle.COLOR_DEFAULT),
                )

                38, 48 -> {
                    val consumed = parseExtendedColor(params, index, foreground = code == 38)
                    index += consumed
                }
            }
            index++
        }
    }

    /**
     * Handles `38;5;n` (256-colour) and `38;2;r;g;b` (truecolour).
     * Returns how many extra parameters were consumed.
     */
    private fun parseExtendedColor(params: IntArray, index: Int, foreground: Boolean): Int {
        if (index + 1 >= params.size) return 0
        return when (params[index + 1]) {
            5 -> {
                if (index + 2 >= params.size) return 1
                val color = TerminalStyle.indexed(params[index + 2].coerceIn(0, 255))
                style = if (foreground) {
                    TerminalStyle.withForeground(style, color)
                } else {
                    TerminalStyle.withBackground(style, color)
                }
                2
            }
            2 -> {
                if (index + 4 >= params.size) return 1
                val color = TerminalStyle.rgb(
                    params[index + 2].coerceIn(0, 255),
                    params[index + 3].coerceIn(0, 255),
                    params[index + 4].coerceIn(0, 255),
                )
                style = if (foreground) {
                    TerminalStyle.withForeground(style, color)
                } else {
                    TerminalStyle.withBackground(style, color)
                }
                4
            }
            else -> 1
        }
    }

    // --- modes ---------------------------------------------------------------------

    internal fun setMode(mode: Int, enabled: Boolean, private: Boolean) {
        if (!private) return
        when (mode) {
            7 -> autoWrap = enabled
            25 -> cursorVisible = enabled
            // 1047/1049/47 all select the alternate screen; 1049 also saves the
            // cursor, which is what makes vim restore the prompt position on exit.
            47, 1047, 1049 -> setAlternateScreen(enabled, saveCursor = mode == 1049)
        }
    }

    private fun setAlternateScreen(enabled: Boolean, saveCursor: Boolean) {
        if (enabled == alternateScreenActive) return
        if (enabled) {
            if (saveCursor) {
                mainCursorRow = cursorRow
                mainCursorColumn = cursorColumn
            }
            mainScreen = buffer.snapshotScreen()
            buffer.replaceScreen(buffer.freshScreen(style))
            cursorRow = 0
            cursorColumn = 0
        } else {
            mainScreen?.let { buffer.replaceScreen(it) }
            mainScreen = null
            if (saveCursor) {
                cursorRow = mainCursorRow.coerceIn(0, rows - 1)
                cursorColumn = mainCursorColumn.coerceIn(0, columns - 1)
            }
        }
        alternateScreenActive = enabled
        wrapPending = false
    }

    internal fun setTitle(value: String) {
        title = value
    }

    internal fun setTabStop() = tabStops.add(cursorColumn)

    internal fun clearTabStop(all: Boolean) {
        if (all) tabStops.clear() else tabStops.remove(cursorColumn)
    }

    /** Current pen, exposed for tests and for the renderer's default background. */
    internal fun currentStyle(): Long = style

    companion object {
        const val DEFAULT_SCROLLBACK = 5_000
        const val DEFAULT_TAB_WIDTH = 8
    }
}
