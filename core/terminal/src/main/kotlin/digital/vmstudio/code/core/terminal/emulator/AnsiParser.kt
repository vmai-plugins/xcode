package digital.vmstudio.code.core.terminal.emulator

/**
 * Byte-stream parser for ANSI/VT escape sequences.
 *
 * A state machine rather than a regex, for two reasons a terminal cannot avoid:
 * output arrives in arbitrary chunks, so a sequence can be split across reads and
 * the parser must resume mid-sequence; and UTF-8 characters can be split the same
 * way. Both are handled by keeping decode state between [feed] calls.
 *
 * Structure follows the state diagram at vt100.net: GROUND, ESCAPE, CSI parameter
 * collection, and string states (OSC/DCS/APC) consumed until their terminator.
 */
internal class AnsiParser(private val emulator: TerminalEmulator) {

    private enum class State { GROUND, ESCAPE, CSI, OSC, STRING, CHARSET }

    private var state = State.GROUND

    private val params = ArrayList<Int>(MAX_PARAMS)
    private var currentParam: Int = NO_PARAM
    private var privateMarker: Char? = null
    private val stringBuffer = StringBuilder()

    // Partial UTF-8 sequence carried across feed() calls.
    private var utf8Remaining = 0
    private var utf8CodePoint = 0

    fun feed(bytes: ByteArray, length: Int) {
        var index = 0
        while (index < length) {
            val byte = bytes[index].toInt() and 0xFF
            when (state) {
                State.GROUND -> ground(byte)
                State.ESCAPE -> escape(byte)
                State.CSI -> csi(byte)
                State.OSC -> osc(byte)
                State.STRING -> string(byte)
                State.CHARSET -> {
                    // Character set designation (ESC ( B and friends): consumed and
                    // ignored, since only the default set is supported.
                    state = State.GROUND
                }
            }
            index++
        }
    }

    // --- ground ------------------------------------------------------------------

    private fun ground(byte: Int) {
        if (utf8Remaining > 0) {
            if (byte and 0xC0 == 0x80) {
                utf8CodePoint = (utf8CodePoint shl 6) or (byte and 0x3F)
                utf8Remaining--
                if (utf8Remaining == 0) emitCodePoint(utf8CodePoint)
                return
            }
            // Malformed continuation: emit a replacement and reprocess this byte so
            // one bad byte does not swallow the character after it.
            emitCodePoint(REPLACEMENT)
            utf8Remaining = 0
        }

        when {
            byte == ESC -> beginEscape()
            byte == 0x07 -> emulator.bell()
            byte == 0x08 -> emulator.backspace()
            byte == 0x09 -> emulator.tab()
            byte == 0x0A || byte == 0x0B || byte == 0x0C -> emulator.lineFeed()
            byte == 0x0D -> emulator.carriageReturn()
            byte < 0x20 -> Unit // Other C0 controls are not implemented.
            byte < 0x80 -> emulator.printChar(byte.toChar())

            byte and 0xE0 == 0xC0 -> {
                utf8CodePoint = byte and 0x1F
                utf8Remaining = 1
            }
            byte and 0xF0 == 0xE0 -> {
                utf8CodePoint = byte and 0x0F
                utf8Remaining = 2
            }
            byte and 0xF8 == 0xF0 -> {
                utf8CodePoint = byte and 0x07
                utf8Remaining = 3
            }
            else -> emitCodePoint(REPLACEMENT)
        }
    }

    private fun emitCodePoint(codePoint: Int) {
        when {
            codePoint < 0x20 -> Unit
            // Outside the BMP: written as a surrogate pair. The cell grid stores
            // UTF-16 units, so an emoji occupies two cells; that is imperfect but
            // predictable, and avoids a variable-width cell model.
            codePoint > 0xFFFF -> {
                val adjusted = codePoint - 0x10000
                emulator.printChar((0xD800 + (adjusted shr 10)).toChar())
                emulator.printChar((0xDC00 + (adjusted and 0x3FF)).toChar())
            }
            else -> emulator.printChar(codePoint.toChar())
        }
    }

    // --- escape ------------------------------------------------------------------

    private fun beginEscape() {
        state = State.ESCAPE
        params.clear()
        currentParam = NO_PARAM
        privateMarker = null
        stringBuffer.setLength(0)
    }

    private fun escape(byte: Int) {
        when (val char = byte.toChar()) {
            '[' -> state = State.CSI
            ']' -> state = State.OSC
            'P', '_', '^' -> state = State.STRING // DCS / APC / PM
            '(', ')', '*', '+' -> state = State.CHARSET
            'D' -> { emulator.lineFeed(); state = State.GROUND }
            'E' -> {
                emulator.carriageReturn()
                emulator.lineFeed()
                state = State.GROUND
            }
            'M' -> { emulator.reverseLineFeed(); state = State.GROUND }
            'H' -> { emulator.setTabStop(); state = State.GROUND }
            '7' -> { emulator.saveCursor(); state = State.GROUND }
            '8' -> { emulator.restoreCursor(); state = State.GROUND }
            'c' -> { emulator.reset(); state = State.GROUND }
            else -> {
                // Unknown two-character sequence; discard and resume.
                if (char == ESC.toChar()) beginEscape() else state = State.GROUND
            }
        }
    }

    // --- CSI ---------------------------------------------------------------------

    private fun csi(byte: Int) {
        val char = byte.toChar()
        when {
            char in '0'..'9' -> {
                if (currentParam == NO_PARAM) currentParam = 0
                // Clamp rather than overflow: a malformed sequence with a huge
                // parameter must not wrap to a negative row index.
                currentParam = (currentParam * 10 + (byte - 0x30)).coerceAtMost(MAX_PARAM_VALUE)
            }

            char == ';' -> {
                pushParam()
            }

            char == '?' || char == '<' || char == '=' || char == '>' -> {
                privateMarker = char
            }

            // Intermediate bytes (space through /) are collected and ignored.
            byte in 0x20..0x2F -> Unit

            byte in 0x40..0x7E -> {
                pushParam()
                dispatchCsi(char)
                state = State.GROUND
            }

            byte == ESC -> beginEscape()

            else -> state = State.GROUND
        }
    }

    private fun pushParam() {
        if (params.size < MAX_PARAMS) {
            params.add(if (currentParam == NO_PARAM) NO_PARAM else currentParam)
        }
        currentParam = NO_PARAM
    }

    /** Parameter [index], or [default] when absent or written as an empty field. */
    private fun param(index: Int, default: Int): Int {
        val value = params.getOrNull(index) ?: NO_PARAM
        return if (value == NO_PARAM) default else value
    }

    private fun dispatchCsi(final: Char) {
        val isPrivate = privateMarker == '?'
        when (final) {
            'A' -> emulator.moveCursorBy(-param(0, 1), 0)
            'B' -> emulator.moveCursorBy(param(0, 1), 0)
            'C' -> emulator.moveCursorBy(0, param(0, 1))
            'D' -> emulator.moveCursorBy(0, -param(0, 1))
            'E' -> { emulator.moveCursorBy(param(0, 1), 0); emulator.setColumn(0) }
            'F' -> { emulator.moveCursorBy(-param(0, 1), 0); emulator.setColumn(0) }
            'G', '`' -> emulator.setColumn(param(0, 1) - 1)
            'd' -> emulator.setRow(param(0, 1) - 1)
            'H', 'f' -> emulator.moveCursor(param(0, 1) - 1, param(1, 1) - 1)
            'J' -> emulator.eraseInDisplay(param(0, 0))
            'K' -> emulator.eraseInLine(param(0, 0))
            'L' -> emulator.insertLines(param(0, 1))
            'M' -> emulator.deleteLines(param(0, 1))
            'P' -> emulator.deleteCharacters(param(0, 1))
            'X' -> emulator.eraseCharacters(param(0, 1))
            '@' -> emulator.insertCharacters(param(0, 1))
            'S' -> emulator.buffer.scrollUp(0, emulator.rows - 1, param(0, 1), TerminalStyle.DEFAULT)
            'T' -> emulator.buffer.scrollDown(0, emulator.rows - 1, param(0, 1), TerminalStyle.DEFAULT)
            'm' -> emulator.applyGraphicRendition(
                if (params.isEmpty()) IntArray(0) else params.map { if (it == NO_PARAM) 0 else it }.toIntArray(),
            )
            'r' -> emulator.setScrollRegion(param(0, 1) - 1, param(1, emulator.rows) - 1)
            'h' -> params.forEach { emulator.setMode(it, enabled = true, private = isPrivate) }
            'l' -> params.forEach { emulator.setMode(it, enabled = false, private = isPrivate) }
            's' -> emulator.saveCursor()
            'u' -> emulator.restoreCursor()
            'g' -> emulator.clearTabStop(all = param(0, 0) == 3)
            // Device status and attribute reports are not answered: nothing in this
            // client consumes the reply, and an unsolicited response would appear as
            // stray text at the prompt.
            'n', 'c' -> Unit
            else -> Unit
        }
    }

    // --- OSC and other string sequences ------------------------------------------

    private fun osc(byte: Int) {
        when {
            byte == 0x07 -> { // BEL terminator
                dispatchOsc()
                state = State.GROUND
            }
            byte == ESC -> {
                // Expect ST (ESC \); treat the sequence as finished either way.
                dispatchOsc()
                state = State.ESCAPE
            }
            byte == 0x9C -> {
                dispatchOsc()
                state = State.GROUND
            }
            stringBuffer.length < MAX_STRING_LENGTH -> stringBuffer.append(byte.toChar())
            else -> Unit // Overlong: keep consuming until the terminator.
        }
    }

    private fun dispatchOsc() {
        val text = stringBuffer.toString()
        stringBuffer.setLength(0)
        val separator = text.indexOf(';')
        if (separator <= 0) return
        // 0 sets icon name and title, 2 sets the title.
        when (text.substring(0, separator)) {
            "0", "2" -> emulator.setTitle(text.substring(separator + 1))
        }
    }

    private fun string(byte: Int) {
        // DCS/APC/PM payloads are consumed and discarded; only the terminator matters.
        // The two-byte form of ST is ESC \; jumping straight to GROUND here (instead
        // of ESCAPE, as osc() correctly does) fed that trailing \ to ground() as an
        // ordinary character, printing a stray backslash after every such sequence.
        when (byte) {
            ESC -> state = State.ESCAPE
            0x9C -> state = State.GROUND
        }
    }

    private companion object {
        const val ESC = 0x1B
        const val NO_PARAM = -1
        const val MAX_PARAMS = 32
        const val MAX_PARAM_VALUE = 65_535
        const val MAX_STRING_LENGTH = 1_024
        const val REPLACEMENT = 0xFFFD
    }
}
