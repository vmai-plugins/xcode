package digital.vmstudio.code.core.terminal.emulator

/**
 * One row of the terminal.
 *
 * Characters and their packed styles live in parallel primitive arrays. [length]
 * tracks how far the row is actually written so rendering and text selection do not
 * walk trailing blanks on every frame.
 */
class TerminalLine(columns: Int) {

    var chars: CharArray = CharArray(columns) { ' ' }
        private set

    var styles: LongArray = LongArray(columns) { TerminalStyle.DEFAULT }
        private set

    /** Index one past the last written column. */
    var length: Int = 0
        private set

    /** Set when the row was wrapped rather than ended by a newline. */
    var wrapped: Boolean = false
        internal set

    val columns: Int get() = chars.size

    fun charAt(column: Int): Char = if (column in chars.indices) chars[column] else ' '

    fun styleAt(column: Int): Long =
        if (column in styles.indices) styles[column] else TerminalStyle.DEFAULT

    fun setCell(column: Int, char: Char, style: Long) {
        if (column !in chars.indices) return
        chars[column] = char
        styles[column] = style
        if (column >= length) length = column + 1
    }

    fun clear(style: Long = TerminalStyle.DEFAULT) {
        chars.fill(' ')
        styles.fill(style)
        length = 0
        wrapped = false
    }

    /** Clears columns in `[from, to)`. */
    fun clearRange(from: Int, to: Int, style: Long) {
        val start = from.coerceAtLeast(0)
        val end = to.coerceAtMost(chars.size)
        for (index in start until end) {
            chars[index] = ' '
            styles[index] = style
        }
        if (end >= length) length = start.coerceAtMost(length)
    }

    fun resize(columns: Int) {
        if (columns == chars.size) return
        val newChars = CharArray(columns) { ' ' }
        val newStyles = LongArray(columns) { TerminalStyle.DEFAULT }
        val copy = minOf(columns, chars.size)
        chars.copyInto(newChars, 0, 0, copy)
        styles.copyInto(newStyles, 0, 0, copy)
        chars = newChars
        styles = newStyles
        length = length.coerceAtMost(columns)
    }

    /** Trailing blanks are dropped so copied text has no phantom whitespace. */
    fun text(): String = String(chars, 0, length).trimEnd()

    fun textRange(from: Int, to: Int): String {
        val start = from.coerceIn(0, chars.size)
        val end = to.coerceIn(start, chars.size)
        return String(chars, start, end - start)
    }

    fun copyFrom(other: TerminalLine) {
        resize(other.columns)
        other.chars.copyInto(chars)
        other.styles.copyInto(styles)
        length = other.length
        wrapped = other.wrapped
    }
}

/**
 * The visible screen plus its scrollback.
 *
 * Scrollback is a fixed-capacity ring: an unbounded buffer is an out-of-memory bug
 * waiting for the first `cat` of a large log, and dropping the oldest lines is what
 * every real terminal does.
 */
class TerminalBuffer(
    var columns: Int,
    var rows: Int,
    private val maxScrollback: Int,
) {

    private var screen: MutableList<TerminalLine> =
        MutableList(rows) { TerminalLine(columns) }

    private val scrollback = ArrayDeque<TerminalLine>()

    /** Lines currently retained above the visible screen. */
    val scrollbackSize: Int get() = scrollback.size

    /** Total addressable rows, scrollback first. */
    val totalRows: Int get() = scrollback.size + rows

    fun screenLine(row: Int): TerminalLine = screen[row.coerceIn(0, rows - 1)]

    /**
     * Row by absolute index, where 0 is the oldest scrollback line and
     * [totalRows] - 1 is the bottom of the screen.
     */
    fun lineAt(absoluteRow: Int): TerminalLine = when {
        absoluteRow < scrollback.size -> scrollback[absoluteRow]
        else -> screen[(absoluteRow - scrollback.size).coerceIn(0, rows - 1)]
    }

    /**
     * Scrolls the region `[top, bottom]` up by [count], pushing lines that leave
     * the top of a full-screen region into scrollback.
     */
    fun scrollUp(top: Int, bottom: Int, count: Int, style: Long) {
        if (count <= 0) return
        val effective = count.coerceAtMost(bottom - top + 1)
        val retainToScrollback = top == 0 && bottom == rows - 1

        repeat(effective) {
            val removed = screen.removeAt(top)
            if (retainToScrollback) {
                scrollback.addLast(removed)
                while (scrollback.size > maxScrollback) scrollback.removeFirst()
                screen.add(bottom, TerminalLine(columns).also { it.clear(style) })
            } else {
                removed.clear(style)
                screen.add(bottom, removed)
            }
        }
    }

    /** Scrolls the region down, used by reverse index and insert-line. */
    fun scrollDown(top: Int, bottom: Int, count: Int, style: Long) {
        if (count <= 0) return
        val effective = count.coerceAtMost(bottom - top + 1)
        repeat(effective) {
            val removed = screen.removeAt(bottom)
            removed.clear(style)
            screen.add(top, removed)
        }
    }

    fun clearScreen(style: Long) {
        screen.forEach { it.clear(style) }
    }

    fun clearScrollback() = scrollback.clear()

    /**
     * Resizes the screen.
     *
     * Reflow of wrapped lines is deliberately not attempted: doing it correctly
     * requires tracking logical lines through the alternate screen and scroll
     * regions, and doing it incorrectly mangles output. Columns are truncated or
     * padded, which is what most terminals do on a width change anyway.
     */
    fun resize(newColumns: Int, newRows: Int, style: Long) {
        if (newColumns != columns) {
            screen.forEach { it.resize(newColumns) }
            scrollback.forEach { it.resize(newColumns) }
            columns = newColumns
        }

        if (newRows == rows) return

        if (newRows < rows) {
            // Shrinking: rows leaving the top are kept as scrollback rather than
            // discarded, so output the user has already seen is not lost.
            repeat(rows - newRows) {
                val removed = screen.removeAt(0)
                scrollback.addLast(removed)
                while (scrollback.size > maxScrollback) scrollback.removeFirst()
            }
        } else {
            repeat(newRows - rows) {
                // Prefer pulling a line back out of scrollback so growing the window
                // reveals previous output instead of blank rows.
                val restored = scrollback.removeLastOrNull()
                if (restored != null) {
                    restored.resize(newColumns)
                    screen.add(0, restored)
                } else {
                    screen.add(TerminalLine(newColumns).also { it.clear(style) })
                }
            }
        }
        rows = newRows
    }

    /** Plain text of the whole buffer, for copy-all and diagnostics. */
    fun allText(): String = buildString {
        for (index in 0 until totalRows) {
            appendLine(lineAt(index).text())
        }
    }

    /** Replaces the screen wholesale, used when switching to the alternate buffer. */
    internal fun replaceScreen(lines: MutableList<TerminalLine>) {
        screen = lines
        rows = lines.size
    }

    internal fun snapshotScreen(): MutableList<TerminalLine> = screen

    internal fun freshScreen(style: Long): MutableList<TerminalLine> =
        MutableList(rows) { TerminalLine(columns).also { it.clear(style) } }
}
